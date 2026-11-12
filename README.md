# LLM Model Router

This project implements a standalone Java library that selects and invokes a language model for an application's request. The application supplies the task, quality expectations, budget, and latency preferences. The operator configures models and provider credentials centrally. Backend services, batch jobs, and agents can use the same interface.

**Status** · The router, provider adapter fixtures, sample and synthetic pilot pass offline checks. Live model validation and benchmark evidence are pending.

## How a request works

1. The application calls `ModelRouter.invoke(request)` with a registered task and its constraints. For example, a document processor could request invoice field extraction as JSON, with a $0.01 budget and a 1,500 ms latency target.
2. The router filters out models that cannot meet the requirements. It uses past task evaluations and cost estimates to select the cheapest eligible model predicted to meet the quality and latency targets. If none qualifies, the caller receives a structured failure.
3. A provider adapter translates the common request into the selected provider's API format. The router validates the response and returns the output, chosen model, decision reason, usage when available, and elapsed time.

## How routing will be evaluated

The first evaluation will cover text classification and structured extraction across several domains. It will compare the router with always using each candidate model and with a policy that assigns one model to each task. All policies will use the same held-out examples. The benchmark will measure task quality, total cost, end-to-end latency, and failures, including fallback attempts.

## Project documents

- [Specification](SPEC.md) covers the request and result, routing behavior, acceptance criteria, and scope.
- [Implementation plan](PLAN.md) tracks milestones, verification, and open decisions.
- [Repository working rules](AGENTS.md) explain how to document, test, and commit changes.

## Build and verify

Use JDK 17 or newer. The wrapper pins Gradle 9.0.0. The first command downloads Gradle and its dependencies. The second runs from the local cache.

```sh
./gradlew check
./gradlew --offline clean check sample offlinePilot jar
```

The sample runs with fake providers and demonstrates one fallback. The pilot compares routing, two fixed models and a task mapping on held-out fixtures. See the [module guides](docs/README.md) for details and the [plan](PLAN.md) for verification evidence.
