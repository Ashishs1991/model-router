# Router contract

The contract gives applications and providers a common vocabulary. For example, a caller can request the `classify` task with a `standard` quality profile, a dollar budget and a deadline. The request checks its required values and the profile catalog rejects unknown combinations before provider invocation.

A provider receives the model identifier and remaining time. Its reply carries output and optional token usage. Unknown usage stays absent because charging zero would misrepresent the bill. The result format reserves fields for model selection, cost and attempt history. Typed failures use safe messages and distinguish bad requests from unknown profiles.

Records preserve value semantics and copy collections. Java 17 is the compilation target. The Gradle Wrapper pins the build tool, and contract checks use a fake provider so the build requires no account.

Run `./gradlew --offline clean check` after bootstrapping the wrapper with `./gradlew --version`. See the [request](../../src/main/java/router/RouteRequest.java), [provider contract](../../src/main/java/router/Provider.java) and [contract checks](../../src/test/java/router/ContractCheck.java). Routing and provider transport are separate milestones.
