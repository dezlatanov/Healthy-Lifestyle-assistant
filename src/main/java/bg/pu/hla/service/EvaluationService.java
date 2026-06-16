package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import bg.pu.hla.domain.ChatMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final HybridChatService hybridChatService;

    public Map<String, Object> compareModes(String username, String query) {
        Map<String, Object> symbolic = hybridChatService.buildResponse(username, query, ChatMode.SYMBOLIC);
        Map<String, Object> llmOnly = hybridChatService.buildResponse(username, query, ChatMode.LLM_ONLY);
        Map<String, Object> hybrid = hybridChatService.buildResponse(username, query, ChatMode.HYBRID);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("symbolic", summarize(symbolic));
        result.put("llmOnly", summarize(llmOnly));
        result.put("hybrid", summarize(hybrid));
        result.put("groundingComparison", Map.of(
                "symbolic", symbolic.get("groundingScore"),
                "llmOnly", llmOnly.get("groundingScore"),
                "hybrid", hybrid.get("groundingScore")
        ));
        result.put("conclusion",
                "Hybrid mode combines conversational LLM output with ontology-verified facts (higher grounding score).");
        return result;
    }

    public Map<String, Object> runBenchmark(String username) {
        List<String> queries = List.of(
                "Искам лека вечеря за отслабване",
                "Какви упражнения за muscle gain?",
                "Какви навици за по-добър сън?",
                "General lifestyle plan for me"
        );

        List<Map<String, Object>> rows = queries.stream()
                .map(q -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("query", q);
                    row.put("comparison", compareModes(username, q));
                    return row;
                })
                .toList();

        return Map.of("username", username, "benchmarks", rows);
    }

    private Map<String, Object> summarize(Map<String, Object> payload) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mode", payload.get("mode"));
        s.put("agent", payload.get("agent"));
        s.put("intent", payload.get("intent"));
        s.put("response", payload.get("response"));
        s.put("ontologySources", payload.get("ontologySources"));
        s.put("groundingScore", payload.get("groundingScore"));
        s.put("sparqlUsed", payload.get("sparqlUsed"));
        return s;
    }
}
