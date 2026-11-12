# Invocation and recovery

The router validates the task and selects an eligible model. It invokes the adapter with the time remaining on one monotonic deadline, checks the output and returns the decision and attempt records. The operator supplies a schema validator. For example, an extraction workflow can validate the returned JSON against its schema before consuming the fields.

When recovery is enabled, the router may try one different model. It applies the same capability, allowlist, quality, timing and budget rules. It retains each attempt's conservative reservation or reported cost, whichever is larger, for fallback admission. Actual cost remains unknown if any attempt has missing usage. Provider exceptions become safe codes with no original cause. A timeout cancels the local worker. Remote providers may still finish or charge for the request.

Provider calls, token estimation and schema validation share the same local deadline. Callback work runs on interruptible workers. A callback that ignores interruption may keep running after the caller receives a timeout, so use callbacks that respond to cancellation. See [ModelRouter](../../src/main/java/router/ModelRouter.java) and [invocation checks](../../src/test/java/router/InvocationCheck.java). Run `./gradlew --offline check`.
