# Sample and offline pilot

Run `./gradlew --offline sample` after dependency setup. The sample asks for the current purchase order reference from a note with an obsolete reference. The router starts with the cheaper fake model. That model returns an empty object, so the schema check rejects it. The second model returns the current reference. The output includes the selected alias, reason, total estimated and fixture costs, elapsed time and attempt count.

Run `./gradlew --offline offlinePilot` to compare four policies on the same four held-out cases. The pilot has two development cases, two calibration cases and four test cases spanning classification and extraction. The four policies are the router, each fixed model and a task mapping. Fixture outputs and evidence are scripted and priced with invented rates. Latency is local execution time. The report demonstrates the evaluation mechanics without spending credits or establishing a model tradeoff.

The sample validates its one schema directly. Applications can supply a validator for their own schema dialect. See the [sample](../../src/main/java/router/Sample.java), [evaluation checks](../../src/test/java/router/EvaluationCheck.java) and [invocation checks](../../src/test/java/router/InvocationCheck.java).
