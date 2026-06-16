# Healthy Lifestyle Assistant

Multi-Agent System for Fitness, Nutrition and Daily Habits — MAIST project.

## Stack

| Layer | Technology |
|-------|------------|
| GUI | Spring Boot + Thymeleaf + Bootstrap |
| Agents | JADE 4.6 (FIPA ACL) |
| Ontology | OWL + Apache Jena (SPARQL, runtime manipulation) |
| Database | H2 (user profiles, daily logs, ACL audit trail) |

## Agents (4 types)

1. **CoordinatorAgent** – routes REQUEST to specialist agents, aggregates INFORM replies
2. **NutritionAgent** – meal recommendations via OWL SPARQL queries
3. **FitnessAgent** – exercise plans via OWL SPARQL queries
4. **GatewayAgent** – Spring ↔ JADE bridge (O2A protocol)

## ACL Flow

```
Web UI → AgentGateway → GatewayAgent (O2A)
  → CoordinatorAgent (ACL REQUEST)
    → NutritionAgent / FitnessAgent (ACL REQUEST)
    ← INFORM (ontology-backed response)
  ← INFORM → GatewayAgent → AgentResponseStore → Web UI
```

## Requirements

- Java 17+
- Maven 3.8+

## Run

```bash
mvn spring-boot:run
```

Open: http://localhost:8080

Demo user: `demo`

## API Examples

```bash
# Create/update profile
curl -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","goal":"WEIGHT_LOSS","activityLevel":"MODERATE"}'

# Agent consultation (FIPA ACL under the hood)
curl -X POST http://localhost:8080/api/users/demo/consult \
  -H 'Content-Type: application/json' \
  -d '{"type":"NUTRITION","query":"light dinner"}'

# Add food to ontology at runtime
curl -X POST http://localhost:8080/api/ontology/foods \
  -H 'Content-Type: application/json' \
  -d '{"name":"Тиква","calories":45,"protein":1.0}'
```

## Documentation

Full project documentation (BG): [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md)

## Project Structure

```
src/main/java/bg/pu/hla/
├── agent/          # JADE agents + ACL + gateway
├── ontology/       # Jena OWL service
├── domain/         # JPA entities
├── repository/     # Spring Data
├── service/        # Business logic
└── web/            # REST + MVC controllers

src/main/resources/
├── ontology/healthy_lifestyle.owl
└── templates/index.html
```
