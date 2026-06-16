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

    private final AppProperties appProperties;
    private jade.wrapper.AgentContainer container;

    @EventListener(ApplicationReadyEvent.class)
    public void startJadePlatform() {
        try {
            jade.core.Runtime runtime = jade.core.Runtime.instance();
            jade.core.Profile profile = new jade.core.ProfileImpl();
            profile.setParameter(jade.core.Profile.MAIN_HOST, appProperties.getJade().getHost());
            profile.setParameter(jade.core.Profile.MAIN_PORT, String.valueOf(appProperties.getJade().getPort()));
            profile.setParameter(jade.core.Profile.LOCAL_HOST, appProperties.getJade().getHost());
            profile.setParameter(jade.core.Profile.GUI, "false");

            container = runtime.createMainContainer(profile);

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

            coordinator.start();
            nutrition.start();
            fitness.start();
            gateway.start();

            log.info("JADE platform started with 4 agents on port {}", appProperties.getJade().getPort());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start JADE platform", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (container != null) {
            try {
                container.kill();
            } catch (Exception e) {
                log.warn("Error shutting down JADE container", e);
            }
        }
    }

    public jade.wrapper.AgentContainer getContainer() {
        return container;
    }
}
