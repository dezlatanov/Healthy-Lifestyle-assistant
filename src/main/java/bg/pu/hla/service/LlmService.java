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
                    [Demo — без OpenAI API key]
                    Контекстът по-долу съдържа персонализирани данни (BMI, TDEE, макроси, ястия от онтологията).
                    Задай OPENAI_API_KEY за пълен отговор от gpt-4o като senior диетолог + тренер.

                    Въпрос: %s
                    """.formatted(userPrompt);
        }
        return """
                [Demo — LLM-only без ontology grounding]
                Задай OPENAI_API_KEY за реален отговор.

                Въпрос: %s
                """.formatted(userPrompt);
    }

    public static String hybridSystemPrompt() {
        return """
                Ти си екип от senior специалисти: клиничен диетолог (RD) + сертифициран фитнес тренер (CSCS) \
                с 15+ години опит. Работиш в платформата Healthy Lifestyle Assistant.

                ПРАВИЛА (задължителни):
                1. Отговаряй на езика на потребителя (български или английски).
                2. Използвай САМО фактите от контекста: ястия, упражнения, калории, BMI, TDEE, макроси, навици.
                3. НИКОГА не измисляй калории, грамове или храни, които не са в контекста.
                4. Обяснявай ЗАЩО препоръката пасва на BMI, възраст, цел и дневника.
                5. Структурирай отговора:
                   - Кратко резюме (1–2 изречения)
                   - Хранене (конкретни ястия от контекста + timing)
                   - Движение/тренировка (конкретни упражнения + честота)
                   - 2–3 actionable съвета за днес
                6. Тон: професионален, подкрепящ, без медицински диагнози и без лекарства.
                7. Ако BMI е извън норма — спомени устойчив подход, не екстремни диети.
                8. Дължина: 3–5 кратки параграфа, без излишна празна приказка.
                """;
    }

    public static String llmOnlySystemPrompt() {
        return """
                Ти си senior wellness coach (диетолог + тренер). Отговаряй на езика на потребителя.
                Давай професионални, структурирани съвети. Без медицински диагнози.
                ВНИМАНИЕ: нямаш verified ontology data — отбелязвай, че числата са ориентировъчни.
                """;
    }
}
