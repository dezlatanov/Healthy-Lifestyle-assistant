package bg.pu.hla.ontology;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OntologyQueryResult {
    List<OntologyRecommendation> items;
    int totalCalories;
    String sparqlUsed;
}
