# LLM Model Router — Specification

## Purpose

Build a small, general-purpose Java library that selects and invokes a language model using task requirements, quality expectations, cost limits, and latency preferences. Any application, including an agent, backend service, or batch job, can call it without depending on an agent framework or handling provider-specific settings in each request.

The reusable router is separate from its evaluation workloads. Initial examples use text classification and structured extraction across several domains; they do not restrict the router to customer support.

## Scope

- Java library first. Choose and document the Java version and build tool during project setup.
- Two model candidates across two provider adapters, configured centrally by the operator.
- Text input and text or structured JSON output; single invocation per request by default.
- A model registry recording capabilities, context limits, pricing, and provider configuration references.
- Explicit caller-supplied task identifiers; automatic task detection is deferred.
- Task-specific quality profiles backed by recorded evaluation results.
- A deterministic initial policy that filters ineligible models and selects the least expensive candidate estimated to meet quality and latency preferences.
- Response validation, measurements, and an optional single bounded retry or escalation.
- A reproducible evaluation runner comparing routing with fixed-model and simple task-based baselines.

## Concepts and tradeoffs

A provider adapter translates the common request into one provider's API and normalizes the result. The routing policy decides which model to use; it does not contain provider HTTP code. A quality profile describes measured task expectations, not a guarantee that an individual response is correct.

Start with measured performance tables rather than a learned router. This makes decisions easy to inspect and gives a baseline against which additional complexity must justify itself. Input length alone is not evidence of task difficulty. Higher price is not proof of higher quality.

## Public interface contract

The names below describe the intended contract, not an implemented API.

`ModelRouter.invoke(RouteRequest) -> RouteResult`

Request fields:

| Field | Meaning |
| --- | --- |
| `task` | Registered task identifier used to look up evaluation and validation rules |
| `input` | Text to process, with optional application instructions |
| `qualityProfile` | Named task-specific target such as `standard` or `high` |
| `maxCostUsd` | Request spending ceiling used for conservative admission and attempt budgeting |
| `targetLatencyMs` | Preferred end-to-end response time; not a guarantee |
| `timeoutMs` | Hard local deadline covering selection and all attempts |
| `maxOutputTokens` | Output bound used for capability checks and cost estimation |
| `outputSchema` | Optional structured-output requirement |
| `allowedModels` | Optional restriction to centrally registered model aliases |

Applications do not supply provider credentials, endpoints, or provider-specific model settings. Operators configure these centrally and obtain credentials from environment variables or a secret store. Validate all numeric limits and reject unknown tasks or profiles explicitly.

`RouteResult` contains the output, chosen model alias, policy version, concise decision reason, estimated cost, reported actual usage and cost when available, end-to-end latency, attempt history, validation outcome, and missed-preference indicators. Distinguish unavailable usage data from zero cost. Do not expose secrets or raw provider errors.

Typed failure categories include invalid request, unknown task/profile, no eligible model, deadline exceeded, provider failure, and invalid output. Include safe attempt metadata on failures so failed requests remain measurable.

## Routing and invocation behavior

1. Validate the request and resolve its task profile.
2. Filter by required capabilities, context size including output allowance, allowed models, and provider availability.
3. Estimate task quality, total cost, and latency using versioned measurements. Missing evidence must not be silently treated as adequate quality.
4. Select the cheapest candidate predicted to meet the requested profile and latency target, with a documented deterministic tie-break rule.
5. If no candidate qualifies, return a structured failure. Do not silently relax requirements. A future best-effort mode must be explicit.
6. Invoke the selected provider within the remaining deadline and validate its output.
7. If enabled, permit at most one additional attempt. Reapply every constraint and reserve sufficient remaining budget; never treat fallback as free.
8. Return output and measurements, or a typed failure with attempt metadata.

Estimate costs conservatively from input tokens, maximum output, and applicable provider billing rules. Exclude candidates whose billing cannot be bounded adequately for a capped request. A local timeout cannot guarantee cancellation of remote computation or its charges; report this limitation. Never promise an exact billing cap that a provider cannot enforce.

Schema validity measures structure, not factual correctness. A successful HTTP response is not necessarily a successful task.

## Acceptance criteria

### Functional

- A sample Java application calls the router without an agent framework or provider-specific request configuration.
- Both provider adapters implement the same contract and normalize output, usage, and failures.
- Required capabilities and model restrictions are enforced on initial and fallback attempts.
- Unknown profiles, empty candidate sets, invalid output, timeouts, and provider errors produce documented typed outcomes.
- Identical policy inputs and measurement snapshots produce the same selection.
- Attempts share one deadline and one budget; no unbounded retry loop exists.
- Automated tests run offline using fake providers. Live checks are explicitly opt-in and require configured credentials and a spending limit.

### Evaluation

- Begin with a small labeled pilot; expand to a few hundred examples if the evaluation budget permits.
- Include classification and structured extraction, with straightforward and ambiguous cases across multiple domains.
- Separate development, calibration, and held-out test data. Keep near-duplicate examples in the same split.
- Compare against each fixed candidate model and a simple task-to-model policy on the same test inputs.
- Measure classification accuracy and macro-F1, extraction field accuracy and schema validity, average total cost, cost per successful task, p50/p95 end-to-end latency, failures, fallback rate, and constraint violations.
- Define task success before evaluation. Report per-task results as well as aggregate results, sample counts, and uncertainty intervals.
- Include routing, validation, all attempts, and failures in measured totals. Report offline calibration and evaluation costs separately.
- Measure latency with repeated, interleaved live runs under comparable concurrency. Replayed outputs cannot establish live latency savings.
- Record dataset version, model identifiers, prompts, policy version, prices, dates, concurrency, and reproducible commands.
- Demonstrate at least one measured tradeoff against fixed baselines. A useful aspirational target is 25% lower cost with at most two percentage points of accuracy loss and a declared p95 latency target. This is a hypothesis, not a promised result.
- If evidence does not establish a benefit, report that outcome and its limitations honestly; do not mark the tradeoff objective achieved.

### Documentation

- Every implemented logical module has a Markdown guide committed with it, as required by AGENTS.md.
- Documentation distinguishes planned behavior from working behavior and explains decisions, examples, tests, and limitations.
- PLAN.md links completed milestones to reproducible verification evidence.

## Exclusions from the first version

Training a foundation model; universal optimality claims; claims of superiority over existing routers without matched experiments; agent orchestration; automatic task inference; online learning; embeddings, audio, image, or video routing; streaming; production dashboards; multi-tenant billing; distributed state; broad provider support; and a mandatory HTTP service.

An HTTP interface or learned policy can be considered after the library and evaluation are working. Neither is necessary for the first useful release.
