package bg.pu.hla.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import bg.pu.hla.config.AppProperties;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LlmService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public LlmService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.getLlm().getBaseUrl())
                .build();
    }

    public String chat(String systemPrompt, String userPrompt, boolean includeOntologyGrounding) {
        AppProperties.Llm cfg = appProperties.getLlm();
        if (!cfg.isEnabled() || cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            if (cfg.isFallbackWithoutKey()) {
                return fallbackResponse(userPrompt, includeOntologyGrounding);
            }
            throw new IllegalStateException("LLM is not configured. Set OPENAI_API_KEY environment variable.");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.getModel(),
                    "temperature", cfg.getTemperature(),
                    "max_tokens", cfg.getMaxTokens(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText(
                    fallbackResponse(userPrompt, includeOntologyGrounding));
        } catch (Exception e) {
            log.warn("LLM API call failed, using fallback: {}", e.getMessage());
            if (cfg.isFallbackWithoutKey()) {
                return fallbackResponse(userPrompt, includeOntologyGrounding);
            }
            throw new IllegalStateException("LLM request failed: " + e.getMessage(), e);
        }
    }

    private String fallbackResponse(String userPrompt, boolean grounded) {
        if (grounded) {
            return """
                    [Demo mode — без OpenAI API key]
                    Благодаря за въпроса. Използвам данни от OWL онтологията по-долу в контекста.
                    Моля, задай OPENAI_API_KEY за пълен разговорен LLM отговор.
                    
                    Въпрос: %s
                    """.formatted(userPrompt);
        }
        return """
                [Demo mode — LLM-only без ontology grounding]
                Това е демо отговор без API key. За реален LLM-only режим задай OPENAI_API_KEY.
                
                Въпрос: %s
                """.formatted(userPrompt);
    }

    public static String hybridSystemPrompt() {
        return """
                You are a wellness coach assistant for the Healthy Lifestyle Assistant platform.
                Rules:
                - Answer in the same language as the user (Bulgarian or English).
                - Use ONLY the ontology facts provided in the context for meals, exercises, calories, and habits.
                - Never invent nutritional numbers not present in the context.
                - Explain WHY a recommendation fits the user's goal.
                - Cite ontology item labels when recommending.
                - Do not provide medical diagnosis or prescribe medication.
                - Keep answers concise, friendly, and practical (2-4 short paragraphs).
                """;
    }

    public static String llmOnlySystemPrompt() {
        return """
                You are a general wellness coach. Answer in the user's language.
                Do not provide medical diagnosis. Keep answers concise.
                Note: you do NOT have verified ontology data in this mode.
                """;
    }
}
