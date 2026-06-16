package bg.pu.hla.ontology;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.ActivityLevel;
import bg.pu.hla.domain.HealthGoal;
import bg.pu.hla.domain.UserProfile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyService {

    private static final String NS = "http://www.semanticweb.org/hla/healthy-lifestyle#";

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;

    private Model model;
    private InfModel infModel;

    @PostConstruct
    public void init() throws IOException {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RULE_INF);
        ontModel.setNsPrefix("hla", NS);

        String path = appProperties.getOntology().getPath();
        try (InputStream in = resourceLoader.getResource(path).getInputStream()) {
            ontModel.read(in, null, "RDF/XML");
        }

        model = ontModel;
        infModel = ontModel;
        long baseSize = ontModel.getBaseModel().size();
        log.info("Ontology loaded: {} base statements, {} with OWL inference",
                baseSize, ontModel.size());
    }

    public synchronized OntologyQueryResult recommendMealsForGoal(HealthGoal goal) {
        String goalUri = mapGoalToUri(goal);
        String sparql = buildMealQuery(goal, goalUri, true);
        List<OntologyRecommendation> meals = runLabelQuery(sparql, "meal", "Meal");

        if (meals.isEmpty()) {
            sparql = buildMealQuery(goal, goalUri, false);
            meals = runLabelQuery(sparql, "meal", "Meal");
        }

        meals = enrichMealsWithCalories(meals);
        int calories = meals.stream().mapToInt(this::readCaloriesFromDetails).sum();
        return OntologyQueryResult.builder()
                .items(meals)
                .totalCalories(calories)
                .sparqlUsed(sparql)
                .build();
    }

    private String buildMealQuery(HealthGoal goal, String goalUri, boolean strict) {
        String foodConstraint = "";
        if (strict) {
            foodConstraint = switch (goal) {
                case MUSCLE_GAIN -> """
                      ?meal hla:containsFood ?food .
                      { ?food a hla:HighProteinFood } UNION { ?food hla:proteinGrams ?p . FILTER(?p >= 15.0) }
                    """;
                case WEIGHT_LOSS -> """
                      ?meal hla:containsFood ?food .
                      { ?food a hla:LowCalorieFood } UNION { ?food hla:calories ?cal . FILTER(?cal <= 120) }
                    """;
                case ENDURANCE -> """
                      ?meal hla:containsFood ?food .
                      { ?food hla:providesNutrient hla:Carbohydrate }
                      UNION { ?food a hla:PlantBasedFood . ?food hla:fiberGrams ?f . FILTER(?f >= 2.0) }
                    """;
                default -> "";
            };
        }

        return """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT DISTINCT ?meal ?label WHERE {
                  ?meal a hla:Meal ;
                        rdfs:label ?label ;
                        hla:mealSuitableForGoal <%s> .
                  %s
                }
                """.formatted(NS, goalUri, foodConstraint);
    }

    public synchronized OntologyQueryResult recommendExercisesForGoal(HealthGoal goal) {
        String goalUri = mapGoalToUri(goal);
        String sparql = """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?exercise ?label ?duration ?intensity WHERE {
                  ?exercise a ?type ;
                            rdfs:label ?label ;
                            hla:exerciseSuitableForGoal <%s> ;
                            hla:durationMinutes ?duration ;
                            hla:intensity ?intensity .
                  FILTER(?type IN (hla:CardioExercise, hla:StrengthExercise, hla:FlexibilityExercise))
                }
                """.formatted(NS, goalUri);

        List<OntologyRecommendation> exercises = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.nextSolution();
                String id = sol.getResource("exercise").getLocalName();
                String label = sol.getLiteral("label").getString();
                int duration = sol.getLiteral("duration").getInt();
                String intensity = sol.getLiteral("intensity").getString();
                exercises.add(OntologyRecommendation.builder()
                        .id(id)
                        .label(label)
                        .type("Exercise")
                        .details(duration + " min, intensity: " + intensity)
                        .build());
            }
        }

        return OntologyQueryResult.builder()
                .items(exercises)
                .totalCalories(0)
                .sparqlUsed(sparql)
                .build();
    }

    public synchronized List<OntologyRecommendation> listHabits() {
        String sparql = """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?habit ?label ?freq ?habitType WHERE {
                  ?habit a hla:Habit ;
                         rdfs:label ?label ;
                         hla:frequencyPerWeek ?freq .
                  OPTIONAL { ?habit hla:targetDailyMl ?water . }
                  OPTIONAL { ?habit hla:targetSleepHours ?sleep . }
                  OPTIONAL { ?habit hla:targetSteps ?steps . }
                  BIND(
                    IF(BOUND(?water), "Hydration",
                    IF(BOUND(?sleep), "Sleep", "Activity")) AS ?habitType)
                }
                """.formatted(NS);

        List<OntologyRecommendation> habits = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.nextSolution();
                habits.add(OntologyRecommendation.builder()
                        .id(sol.getResource("habit").getLocalName())
                        .label(sol.getLiteral("label").getString())
                        .type(sol.getLiteral("habitType").getString())
                        .details("frequency/week: " + sol.getLiteral("freq").getInt())
                        .build());
            }
        }
        return habits;
    }

    public synchronized List<OntologyRecommendation> listHabitsForGoal(HealthGoal goal) {
        String sparql = """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?habit ?label ?freq ?habitType WHERE {
                  ?habit a hla:Habit ;
                         rdfs:label ?label ;
                         hla:frequencyPerWeek ?freq ;
                         hla:supportsGoal <%s> .
                  OPTIONAL { ?habit hla:targetDailyMl ?water . }
                  OPTIONAL { ?habit hla:targetSleepHours ?sleep . }
                  OPTIONAL { ?habit hla:targetSteps ?steps . }
                  BIND(
                    IF(BOUND(?water), "Hydration",
                    IF(BOUND(?sleep), "Sleep", "Activity")) AS ?habitType)
                }
                """.formatted(NS, mapGoalToUri(goal));

        List<OntologyRecommendation> habits = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.nextSolution();
                habits.add(OntologyRecommendation.builder()
                        .id(sol.getResource("habit").getLocalName())
                        .label(sol.getLiteral("label").getString())
                        .type(sol.getLiteral("habitType").getString())
                        .details("frequency/week: " + sol.getLiteral("freq").getInt())
                        .build());
            }
        }
        if (habits.isEmpty()) {
            return listHabits();
        }
        return habits;
    }

    public synchronized List<OntologyRecommendation> listWorkoutPlansForGoal(HealthGoal goal) {
        String sparql = """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?plan ?label WHERE {
                  ?plan a hla:WorkoutPlan ;
                        rdfs:label ?label ;
                        hla:planSuitableForGoal <%s> .
                }
                """.formatted(NS, mapGoalToUri(goal));
        return runLabelQuery(sparql, "plan", "WorkoutPlan");
    }

    public synchronized String addPersonInstance(String username, HealthGoal goal, ActivityLevel activityLevel) {
        UserProfile stub = UserProfile.builder()
                .username(username)
                .goal(goal)
                .activityLevel(activityLevel)
                .build();
        return syncPersonProfile(stub);
    }

    public synchronized String syncPersonProfile(UserProfile user) {
        String personUri = NS + "Person_" + sanitize(user.getUsername());
        Resource person = model.getResource(personUri);
        if (!person.hasProperty(RDF.type)) {
            person.addProperty(RDF.type, model.createResource(NS + "Person"));
        }

        Property usernameProp = model.createProperty(NS + "username");
        Property hasGoalProp = model.createProperty(NS + "hasGoal");
        Property hasActivityProp = model.createProperty(NS + "hasActivityLevel");
        Property ageProp = model.createProperty(NS + "personAge");
        Property weightProp = model.createProperty(NS + "weightKg");
        Property heightProp = model.createProperty(NS + "heightCm");
        Property bmiProp = model.createProperty(NS + "bmi");

        model.removeAll(person, usernameProp, null);
        model.removeAll(person, hasGoalProp, null);
        model.removeAll(person, hasActivityProp, null);
        model.removeAll(person, ageProp, null);
        model.removeAll(person, weightProp, null);
        model.removeAll(person, heightProp, null);
        model.removeAll(person, bmiProp, null);

        person.addProperty(usernameProp, user.getUsername());
        if (user.getGoal() != null) {
            person.addProperty(hasGoalProp, model.createResource(mapGoalToUri(user.getGoal())));
        }
        if (user.getActivityLevel() != null) {
            person.addProperty(hasActivityProp, model.createResource(mapActivityToUri(user.getActivityLevel())));
        }
        if (user.getAge() != null) {
            person.addLiteral(ageProp, user.getAge());
        }
        if (user.getWeightKg() != null) {
            person.addLiteral(weightProp, user.getWeightKg());
        }
        if (user.getHeightCm() != null) {
            person.addLiteral(heightProp, user.getHeightCm());
        }
        if (user.getWeightKg() != null && user.getHeightCm() != null && user.getHeightCm() > 0) {
            double bmi = user.getWeightKg() / Math.pow(user.getHeightCm() / 100.0, 2);
            person.addLiteral(bmiProp, Math.round(bmi * 10.0) / 10.0);
        }

        log.info("Synced person in ontology: {} goal={} age={} weight={}",
                personUri, user.getGoal(), user.getAge(), user.getWeightKg());
        return personUri;
    }

    public synchronized OntologyRecommendation addCustomFoodWithMeal(String nameBg, int calories, double protein,
                                                                      HealthGoal goal) {
        String foodLocalName = "Food_" + UUID.randomUUID().toString().substring(0, 8);
        String mealLocalName = "Meal_" + UUID.randomUUID().toString().substring(0, 8);

        Resource food = model.createResource(NS + foodLocalName, model.createResource(NS + "Food"));
        food.addProperty(RDFS.label, nameBg, "bg");
        food.addProperty(model.createProperty(NS + "calories"), String.valueOf(calories));
        food.addProperty(model.createProperty(NS + "proteinGrams"), String.valueOf(protein));
        food.addProperty(model.createProperty(NS + "providesNutrient"),
                model.createResource(NS + "Protein"));
        if (protein >= 15.0) {
            food.addProperty(RDF.type, model.createResource(NS + "HighProteinFood"));
        }
        if (calories <= 120) {
            food.addProperty(RDF.type, model.createResource(NS + "LowCalorieFood"));
        }

        Resource meal = model.createResource(NS + mealLocalName, model.createResource(NS + "Meal"));
        meal.addProperty(RDFS.label, nameBg, "bg");
        meal.addProperty(model.createProperty(NS + "hasMealType"), model.createResource(NS + "Lunch"));
        meal.addProperty(model.createProperty(NS + "containsFood"), food);
        meal.addProperty(model.createProperty(NS + "mealSuitableForGoal"),
                model.createResource(mapGoalToUri(goal)));

        infModel.rebind();
        log.info("Added custom meal {} for goal {} ({} kcal)", mealLocalName, goal, calories);
        return OntologyRecommendation.builder()
                .id(mealLocalName)
                .label(nameBg)
                .type("Meal")
                .details(calories + " kcal, " + protein + "g protein, goal: " + goal)
                .build();
    }

    public synchronized void linkPersonToHabit(String username, String habitLocalName) {
        Resource person = model.createResource(NS + "Person_" + sanitize(username));
        Resource habit = model.createResource(NS + habitLocalName);
        person.addProperty(model.createProperty(NS + "tracksHabit"), habit);
        infModel.rebind();
    }

    public synchronized List<String> listClasses() {
        List<String> classes = new ArrayList<>();
        String sparql = """
                PREFIX hla: <%s>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?class ?label WHERE {
                  ?class rdfs:subClassOf* hla:Exercise .
                  ?class rdfs:label ?label .
                }
                UNION {
                  ?class a owl:Class .
                  FILTER(STRSTARTS(STR(?class), "%s"))
                  OPTIONAL { ?class rdfs:label ?label }
                }
                """.formatted(NS, NS);

        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.nextSolution();
                String label = sol.getLiteral("label") != null
                        ? sol.getLiteral("label").getString()
                        : sol.getResource("class").getLocalName();
                classes.add(label);
            }
        }
        return classes;
    }

    public synchronized long statementCount() {
        return model.size();
    }

    public synchronized long baseStatementCount() {
        if (model instanceof OntModel ontModel) {
            return ontModel.getBaseModel().size();
        }
        return model.size();
    }

    private List<OntologyRecommendation> runLabelQuery(String sparql, String varName, String type) {
        List<OntologyRecommendation> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.nextSolution();
                results.add(OntologyRecommendation.builder()
                        .id(sol.getResource(varName).getLocalName())
                        .label(sol.getLiteral("label").getString())
                        .type(type)
                        .details("")
                        .build());
            }
        }
        return results;
    }

    private List<OntologyRecommendation> enrichMealsWithCalories(List<OntologyRecommendation> meals) {
        List<OntologyRecommendation> enriched = new ArrayList<>();
        for (OntologyRecommendation meal : meals) {
            int cal = calculateSingleMealCalories(meal.getId());
            enriched.add(OntologyRecommendation.builder()
                    .id(meal.getId())
                    .label(meal.getLabel())
                    .type(meal.getType())
                    .details(cal > 0 ? cal + " kcal" : meal.getDetails())
                    .build());
        }
        return enriched;
    }

    private int calculateSingleMealCalories(String mealLocalName) {
        String sparql = """
                PREFIX hla: <%s>
                SELECT (SUM(?cal) AS ?total) WHERE {
                  <%s%s> hla:containsFood ?food .
                  ?food hla:calories ?cal .
                }
                """.formatted(NS, NS, mealLocalName);

        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, infModel)) {
            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                RDFNode node = rs.nextSolution().get("total");
                if (node != null && node.isLiteral()) {
                    return node.asLiteral().getInt();
                }
            }
        }
        return 0;
    }

    private int readCaloriesFromDetails(OntologyRecommendation meal) {
        if (meal.getDetails() == null) {
            return 0;
        }
        String digits = meal.getDetails().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int calculateMealCalories(List<OntologyRecommendation> meals) {
        int total = 0;
        for (OntologyRecommendation meal : meals) {
            total += calculateSingleMealCalories(meal.getId());
        }
        return total;
    }

    private String mapGoalToUri(HealthGoal goal) {
        return switch (goal) {
            case WEIGHT_LOSS -> NS + "WeightLoss";
            case MUSCLE_GAIN -> NS + "MuscleGain";
            case MAINTENANCE -> NS + "Maintenance";
            case ENDURANCE -> NS + "Endurance";
        };
    }

    private String mapActivityToUri(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> NS + "Sedentary";
            case MODERATE -> NS + "Moderate";
            case ACTIVE -> NS + "Active";
        };
    }

    private String sanitize(String username) {
        return username.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
