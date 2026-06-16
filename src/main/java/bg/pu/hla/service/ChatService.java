package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.pu.hla.agent.AgentGateway;
import bg.pu.hla.domain.*;
import bg.pu.hla.domain.ChatMessage.ChatRole;
import bg.pu.hla.repository.ChatMessageRepository;
import bg.pu.hla.repository.ChatSessionRepository;
import bg.pu.hla.repository.UserProfileRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserProfileRepository userRepo;
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final AgentGateway agentGateway;
    private final HybridChatService hybridChatService;

    public Map<String, Object> chatViaAgents(String username, String message) {
        UserProfile user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        ChatSession session = getOrCreateSession(user, message);
        saveUserMessage(session, message);

        Map<String, Object> result = agentGateway.sendConsultation(user, ConsultationType.CHAT, message);

        saveAssistantMessage(session, result);
        touchSession(session);

        result.put("sessionId", session.getId());
        return result;
    }

    public Map<String, Object> chatDirect(String username, String message, ChatMode mode) {
        return hybridChatService.buildResponse(username, message, mode);
    }

    public List<Map<String, Object>> getChatHistory(String username) {
        return userRepo.findByUsername(username)
                .flatMap(user -> sessionRepo.findFirstByUserOrderByUpdatedAtDesc(user))
                .map(session -> messageRepo.findBySessionOrderByCreatedAtAsc(session).stream()
                        .map(m -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("role", m.getRole().name());
                            row.put("content", m.getContent());
                            row.put("mode", m.getModeUsed() != null ? m.getModeUsed().name() : null);
                            row.put("sources", m.getOntologySources());
                            row.put("createdAt", m.getCreatedAt());
                            return row;
                        })
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    protected ChatSession getOrCreateSession(UserProfile user, String message) {
        return sessionRepo.findFirstByUserOrderByUpdatedAtDesc(user)
                .orElseGet(() -> sessionRepo.save(ChatSession.builder()
                        .user(user)
                        .title(truncate(message, 60))
                        .build()));
    }

    @Transactional
    protected void saveUserMessage(ChatSession session, String message) {
        messageRepo.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.USER)
                .content(message)
                .build());
    }

    @Transactional
    protected void saveAssistantMessage(ChatSession session, Map<String, Object> result) {
        messageRepo.save(ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(String.valueOf(result.get("response")))
                .modeUsed(ChatMode.HYBRID)
                .ontologySources(String.valueOf(result.getOrDefault("ontologySources", "")))
                .sparqlContext(String.valueOf(result.getOrDefault("sparqlUsed", "")))
                .build());
    }

    @Transactional
    protected void touchSession(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepo.save(session);
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
