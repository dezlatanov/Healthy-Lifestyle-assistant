package bg.pu.hla.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Ontology ontology = new Ontology();
    private Jade jade = new Jade();

    @Getter
    @Setter
    public static class Ontology {
        private String path;
    }

    @Getter
    @Setter
    public static class Jade {
        private String host = "localhost";
        private int port = 1099;
        private Agents agents = new Agents();

        @Getter
        @Setter
        public static class Agents {
            private String coordinator = "coordinator";
            private String nutrition = "nutrition-agent";
            private String fitness = "fitness-agent";
            private String gateway = "gateway-agent";
        }
    }
}
