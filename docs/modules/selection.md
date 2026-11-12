# Model registry and selection

Operators register each model under a unique alias. A registry entry binds context capacity, structured output support, pricing, an input token bound and a provider. Callers can constrain aliases without using provider identifiers. Missing provider or pricing information makes a model ineligible.

The policy filters by provider availability, capability, task evidence, time, context and budget. For example, a JSON extraction task skips a text-only model even if it costs less. It orders eligible models by reserved cost and then alias. The alias tie break makes the same snapshot reproducible. Cost uses decimal arithmetic and prices per million tokens. The input bound must include framing and schema overhead. Return a negative bound when safe estimation is unavailable.

Evidence is keyed by task, profile and alias. Fixture values exercise the algorithm and carry no claim about live model quality. Missing evidence excludes a model. See the [registry](../../src/main/java/router/ModelRegistry.java), [policy](../../src/main/java/router/RoutingPolicy.java) and [policy checks](../../src/test/java/router/PolicyCheck.java). Run `./gradlew --offline check`.
