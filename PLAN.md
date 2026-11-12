# LLM Model Router — Plan

## Current status

The Java contract and offline build are complete. The registry and selection policy pass offline checks. Provider integration, invocation and evaluation are pending.

## Ordered milestones

| Milestone | Status | Deliverables | Verification required |
| --- | --- | --- | --- |
| 0. Define the project | Complete | SPEC.md, PLAN.md, AGENTS.md | Review scope, interface, acceptance criteria, exclusions, module-guide rule, and commit workflow |
| 1. Establish the Java project and contract | Complete | Build setup, request/result/error types, fake provider, first module guides | Clean build and offline contract tests; invalid inputs and unknown profiles rejected |
| 2. Add registry and provider adapters | In progress | Central configuration, two adapters, normalized usage and failures | Offline adapter tests including timeout, malformed response, unavailable usage, and secret redaction; optional capped live smoke checks |
| 3. Implement deterministic selection | Complete | Capability filtering, measured profiles, cost/latency estimates, decision reasons | Tests for eligibility, missing evidence, no qualifying candidate, deterministic ties, and budget boundaries |
| 4. Complete invocation and bounded recovery | Not started | Validation, shared deadline, optional single fallback, attempt measurements | Tests showing fallback preserves restrictions, budget, deadline, and total attempt accounting |
| 5. Build reproducible evaluation | Not started | Labeled datasets, separated splits, fixed-model and task-policy baselines, metrics | Offline metric checks; split-leakage checks; recorded pilot costs before expanding live runs |
| 6. Measure and explain results | Not started | Sample application, live comparison report, tradeoff plots, limitations, updated guides | Repeatable evaluation commands, per-task metrics and uncertainty; clear evidence of any achieved tradeoff |

Complete each milestone in small coherent commits. A milestone may need several commits; do not accumulate all milestones into one change.

## Verification evidence

### Milestone 0 — documentation foundation

- Reviewed SPEC.md for scope, provider-neutral interface, measurable acceptance criteria, and explicit exclusions.
- Reviewed AGENTS.md for module documentation, plain-language explanations, testing expectations, and small commit/push rules.
- Checked that all three required Markdown documents are present and contain no implementation or performance claims presented as completed work.
- The initial Git commit records this documentation foundation. No code tests apply at this stage.
- GitHub accepted the initial commit `c8d90cd`. Local `main` tracks `origin/main`.
- Checked all README links against the repository files and ran `git diff --cached --check` for the README update.

For subsequent milestones, add the date, exact verification command, observed result, and a repository-relative link to any report. Record failures and skipped checks explicitly. Use the relevant commit subject or hash when available; do not add a commit merely to embed its own hash.

### Milestone 1 — Gradle build and Java contract

- 2026-11-12. Selected installed JDK 20.0.1, Java 17 bytecode target and Gradle Wrapper 9.0.0.
- `./gradlew --version` confirmed the wrapper and launcher versions.
- `./gradlew --offline --no-daemon clean check` initially passed contract checks but failed framework discovery. The build now explicitly runs the standalone contract suite through JavaExec. The clean rerun passed compilation, invalid request, unknown profile and unknown usage checks.
- See the [contract guide](docs/modules/contract.md). Continue with the model registry and provider integration.

### Milestones 2 and 3 — registry and selection

- 2026-11-12. `./gradlew --offline --no-daemon check` passed contract and policy suites, including duplicate aliases, capability and context filtering, missing evidence, budget boundaries, unknown pricing, deadline allowance and deterministic ties.
- [Registry and selection guide](docs/modules/selection.md). Provider adapters remain pending. Fixture evidence is synthetic.

## Blockers and pending decisions

- Live integration and measurement require provider/model choices, credentials, and a declared evaluation spending limit. These do not block offline implementation.
- Task quality thresholds and the p95 latency target must be calibrated and frozen before held-out evaluation.

## Update rules

Update this plan in the same commit as milestone progress. Use `Not started`, `In progress`, `Blocked`, or `Complete`; complete only when the associated verification has passed. Keep remaining work visible and distinguish an implemented feature from an experimentally demonstrated benefit.
