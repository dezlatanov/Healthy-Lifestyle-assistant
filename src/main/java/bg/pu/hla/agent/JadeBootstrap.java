package bg.pu.hla.agent;

import bg.pu.hla.config.AppProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JadeBootstrap {

    private static final int MAX_PORT_ATTEMPTS = 10;

    private final AppProperties appProperties;
    private jade.wrapper.AgentContainer container;

    @EventListener(ApplicationReadyEvent.class)
    public void startJadePlatform() {
        int basePort = appProperties.getJade().getPort();
        Exception lastError = null;

        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int port = basePort + attempt;
            try {
                startOnPort(port);
                if (port != basePort) {
                    log.warn("JADE port {} was busy — started on {}", basePort, port);
                    appProperties.getJade().setPort(port);
                }
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("JADE failed on port {}: {}", port, e.getMessage());
            }
        }

        throw new IllegalStateException(
                "Failed to start JADE — ports " + basePort + "–" + (basePort + MAX_PORT_ATTEMPTS - 1)
                        + " are unavailable. Stop other Java/JADE processes and retry.", lastError);
    }

    private void startOnPort(int port) throws Exception {
        jade.core.Runtime runtime = jade.core.Runtime.instance();
        jade.core.Profile profile = new jade.core.ProfileImpl();
        profile.setParameter(jade.core.Profile.MAIN_HOST, appProperties.getJade().getHost());
        profile.setParameter(jade.core.Profile.MAIN_PORT, String.valueOf(port));
        profile.setParameter(jade.core.Profile.LOCAL_HOST, appProperties.getJade().getHost());
        profile.setParameter(jade.core.Profile.GUI, "false");

        container = runtime.createMainContainer(profile);
        if (container == null) {
            throw new IllegalStateException("JADE container is null (port " + port + " likely in use)");
        }

        jade.wrapper.AgentController coordinator = container.createNewAgent(
                appProperties.getJade().getAgents().getCoordinator(),
                CoordinatorAgent.class.getName(),
                new Object[0]
        );
        jade.wrapper.AgentController nutrition = container.createNewAgent(
                appProperties.getJade().getAgents().getNutrition(),
                NutritionAgent.class.getName(),
                new Object[0]
        );
        jade.wrapper.AgentController fitness = container.createNewAgent(
                appProperties.getJade().getAgents().getFitness(),
                FitnessAgent.class.getName(),
                new Object[0]
        );
        jade.wrapper.AgentController gateway = container.createNewAgent(
                appProperties.getJade().getAgents().getGateway(),
                GatewayAgent.class.getName(),
                new Object[0]
        );
        jade.wrapper.AgentController llmCoach = container.createNewAgent(
                appProperties.getJade().getAgents().getLlmCoach(),
                LlmCoachAgent.class.getName(),
                new Object[0]
        );

        coordinator.start();
        nutrition.start();
        fitness.start();
        gateway.start();
        llmCoach.start();

        log.info("JADE platform started with 5 agents on port {}", port);
    }

    @PreDestroy
    public void shutdown() {
        if (container != null) {
            try {
                container.kill();
                log.info("JADE platform shut down");
            } catch (Exception e) {
                log.warn("Error shutting down JADE container", e);
            } finally {
                container = null;
            }
        }
    }

    public jade.wrapper.AgentContainer getContainer() {
        return container;
    }
}
