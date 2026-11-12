# LLM Model Router — Plan

## Current status

Documentation foundation complete. No application code, build configuration, provider integration, or benchmark results exist yet. The implementation language is Java; exact runtime and build choices remain part of milestone 1.

## Ordered milestones

| Milestone | Status | Deliverables | Verification required |
| --- | --- | --- | --- |
| 0. Define the project | Complete | SPEC.md, PLAN.md, AGENTS.md | Review scope, interface, acceptance criteria, exclusions, module-guide rule, and commit workflow |
| 1. Establish the Java project and contract | Not started | Build setup, request/result/error types, fake provider, first module guides | Clean build and offline contract tests; invalid inputs and unknown profiles rejected |
| 2. Add registry and provider adapters | Not started | Central configuration, two adapters, normalized usage and failures | Offline adapter tests including timeout, malformed response, unavailable usage, and secret redaction; optional capped live smoke checks |
| 3. Implement deterministic selection | Not started | Capability filtering, measured profiles, cost/latency estimates, decision reasons | Tests for eligibility, missing evidence, no qualifying candidate, deterministic ties, and budget boundaries |
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

## Blockers and pending decisions

- Live integration and measurement require provider/model choices, credentials, and a declared evaluation spending limit. These do not block offline implementation.
- Java version and build tool will be selected at milestone 1 after inspecting the development environment.
- Task quality thresholds and the p95 latency target must be calibrated and frozen before held-out evaluation.

## Update rules

Update this plan in the same commit as milestone progress. Use `Not started`, `In progress`, `Blocked`, or `Complete`; complete only when the associated verification has passed. Keep remaining work visible and distinguish an implemented feature from an experimentally demonstrated benefit.
