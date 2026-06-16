package bg.pu.hla.ontology;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OntologyRecommendation {
    String id;
    String label;
    String type;
    String details;
}
