# Provider adapters

The same adapter contract supports OpenAI Chat Completions and Anthropic Messages. The operator selects a protocol and supplies a credential lookup, typically an environment variable. A registry entry binds it to a model ID and the model's billing and capability configuration. For example, an OpenAI reply reporting 12 prompt and three completion tokens becomes a normalized usage pair of 12 and three.

The adapter uses Java HTTP and Jackson for JSON. It uses fixed HTTPS endpoints without redirects, sets the timeout from the router and strips raw errors. Missing or unsupported usage remains unknown. Structured requests include the schema in the prompt and rely on the router's local validator. This does not guarantee provider-native constrained decoding. Verify each chosen model's output limit, token estimation and billing before enabling live traffic.

Run `./gradlew --offline check` after resolving dependencies with `./gradlew check`. [Adapter source](../../src/main/java/router/HttpProvider.java) and [offline response fixtures](../../src/test/java/router/ProviderCheck.java) cover status errors, timeouts, redaction and normalization. Live credentials, model IDs and an approved allowance are pending.
