# Repository working rules

## Scope and implementation

- Read SPEC.md and PLAN.md before changing code. Implement the next small milestone; update both when an agreed design change affects them.
- Build a standalone Java library. Keep routing decisions separate from provider translation. Do not require an agent framework.
- Prefer the simplest complete implementation. Avoid speculative abstractions, dependencies, services, and features outside the specification.
- Validate public inputs. Preserve model restrictions, budget accounting, and deadlines across every attempt. Never log credentials; avoid recording raw user inputs by default.
- Never claim measured quality, savings, latency, or completion without evidence. Clearly label estimates and planned behavior.

## Explain every module

- Every logical module introduced or materially changed MUST have its own Markdown guide in the same commit, normally `docs/modules/<module-name>.md`. A module means a cohesive responsibility, not every class or file.
- Each guide must explain: the problem solved; why the module exists; where it fits in the request flow; inputs and outputs with a concrete example; the important implementation steps; key decisions and alternatives; failure cases; how to run or test it; and known limitations.
- Link to the relevant source files and tests using repository-relative paths. Keep explanations synchronized with the code; distinguish implemented behavior from future plans.
- Write for a reader learning the system: define unfamiliar terms, explain why before how, and use examples that let the reader trace a request end to end. Explain tradeoffs rather than listing class names.
- Apply that clarity to every Markdown file. Do not include private motivations or career-preparation framing in repository documentation.
- Add a concise documentation index when the first module guide is created. No placeholder guides for nonexistent modules.

## Testing and verification

- Add meaningful offline tests for new behavior and bug fixes, especially filtering, cost arithmetic, deadlines, fallback limits, response validation, and provider error handling.
- Use fake providers for normal tests. Make live calls opt-in, with credentials and a declared spending limit; never silently run paid evaluations.
- Run checks appropriate to the change. Documentation-only changes need consistency and link checks, not artificial code tests.
- Record exact commands and observed evidence in PLAN.md as milestones progress. Do not mark unrun checks as passed.
- Freeze evaluation splits and thresholds before held-out testing; include all attempts and failures in reported cost and latency.

## Commits and pushes

- Make small, coherent commits as work progresses. Each commit should contain one understandable change together with its relevant tests, module guide, and plan update. Do not bundle the whole project into a monolithic commit.
- Use a descriptive imperative subject, such as `feat: filter models by required capabilities`. Add a commit body explaining why, behavior changed, verification performed, and any material limitations.
- Verify and review the diff before committing. Stage only relevant files; never commit secrets, local credentials, or unrelated user changes.
- Push each verified commit to the configured working branch and remote when available and authorized. If destination or authentication is missing, preserve local commits, record the blocker, and ask for the missing information. Do not claim a push succeeded without checking its result.
- Do not invent a remote or create a hosted repository without an explicit destination and visibility choice. Never force-push, rewrite shared history, or discard unrelated changes without explicit authorization.
