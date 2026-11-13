# TodaysTrack agent instructions

## Project context

- Product: 오늘의 트랙 · Today's Track. The folder name is `TodaysTrack`.
- Read `docs/proposal.md` for the current proposal and `docs/reviews/2026-11-12-proposal-review.md` for unresolved evidence and review history.
- This project is currently at the proposal stage. SDK integration, app implementation, and musical output have not been validated.
- Both post-drive and in-drive music generation remain acceptable. The proposal's baseline is post-drive generation; realtime generation is optional.
- Keep section 7 of the proposal blank until the user supplies or explicitly requests reference materials. Do not invent metrics, user research, implementation results, or links.
- Agent-tool setup is not permission to start product implementation.

## Personal development workflow

These are the user's requested operating preferences. Apply them to multi-step software development, debugging, and delivery. They supplement Superpowers; explicit user scope and these preferences take precedence over conflicting skill defaults. Do not turn ordinary questions, research, writing, or small mechanical edits into a development ceremony.

For applicable development work, read `C:/Users/xiui/.codex/skills/goal-aligned-development/SKILL.md` and the relevant installed Superpowers skill. Use skills from the enabled plugin, not an old cache path. If it is unavailable, report that fact; do not claim it was loaded.

- Default to inline execution: one implementer, continuous execution within authorized scope, and a review at the agreed integration milestone. Do not create a permanent watcher, dispatch an implementer/reviewer for every tiny task, or ask whether to continue after each step. Use additional agents only when authorized and useful.
- Preserve original requirements and latest user corrections. Do not invent acceptance thresholds, mandatory checks, or scope. Copy binding global constraints into every task brief and reviewer input; do not rely on automatic extraction.
- Choose work by the user-visible outcome it advances. Keep auxiliary fixes and testing proportional. Required correctness checks remain required; optional improvements must not silently become delivery blockers.
- Separate partial evidence from end-to-end completion. Report unmet and unverified requirements explicitly. Never equate passing self-authored tests or a successful deployment with the user's full goal.
- When an external dependency blocks one task, continue independent authorized work. When the same failure repeats twice without new evidence, stop blind repetition, inspect the cause, and change the approach or isolate that dependency. Do not declare the whole task blocked while useful independent work remains.
- Keep reviews scoped to requirements, introduced defects, and regressions. A scoped re-review checks the fix and its consequences; unrelated improvements go to follow-up. Repeated disputed findings need an evidence-based ruling, not an endless review loop or an automatic pass.
- Reuse relevant verification evidence only when the code and environment it covers remain applicable. Re-run affected checks after changes, failures, or a concrete unresolved concern; do not repeat entire suites for narration or bookkeeping.

## ECC and Superpowers

- ECC is installed as the user-wide native Codex plugin `ecc@ecc`. Do not also copy its skills into this project's `.agents/skills` or run the legacy Codex sync installer.
- Use Superpowers for the applicable development process and ECC skills for relevant technical guidance. Prefer Kotlin, Android, coroutines, testing, and verification skills when the task calls for them.
- ECC examples are guidance, not project requirements. In particular, do not adopt an 80% coverage threshold, a web/Playwright test stack, automatic parallel delegation, or autonomous execution merely because an ECC template includes them.
- Follow the user's authorization and workflow above when a skill proposes delegation, additional approval gates, or broader work.
- Do not enable hooks, launch persistent workers, or write personal memory automatically as a side effect of using a technical skill.
- Ignore `work/` during normal product searches unless inspecting an explicitly requested tool setup. It contains the upstream ECC checkout used to inspect the installation.

## Today's Track Workflow Guard

- Project-wide setup: `tools/workflow-guard/project.profile.json`; entry point: `tools/workflow-guard/project.mjs`; usage and limits: `docs/development/workflow-guard.md`. The active submission plan has four bundles S1~S4; P0~P8 remain follow-up technical references, not an automatic prerequisite queue. Default to inline execution and bundle-level self-review; Guard is optional and off by default. `p0.profile.json` is only a specialized stage recipe, not the project policy.
- Configuration alone authorizes no execution. Each run must quote the user's actual execution instruction and include only the approved stages and bounded work units. Do not add unapproved prerequisites or automatically continue to an unapproved stage.
- Only if Guard is deliberately selected, use one task lane (`parallelism: 1`) with the controller's independent reviewer for approved units, as a bounded exception to inline self-review. Preparation requires explicit opt-in and never starts a run. Do not add parallel implementers, permanent agents, hooks, or automatic execution.
- When Guard is selected, run in the prepared isolated workspace for the selected stages. Keep original source/evidence intact; after the run stops, review the diff and original hashes before integrating only authorized files. Never edit the live controlled workspace through direct tools while the controller owns it.
- Include the current product/design/plan constraints in both worker and reviewer input. A completed experiment may conclude unsupported; report background support, experiment completion, and whole-product completion separately.
- Stage approval never implies paid API approval: require a separate verbatim authorization with request/count and total-cost bounds before any paid check. Device checks follow the actual authorized unit; configuration-only work permits none. Keys must not be requested in chat or printed. S2 is fixture-only; live dispatch belongs to S3 after fixture save/play verification and separate paid approval. S4 is local package/video preparation, not product edits, publishing or email authorization. Deployment needs its separate user workflow. Windroid and the global Guard installation/policy stay unchanged.

## Windroid pause

As instructed on 2026-11-12, Windroid product development remains paused, including related worktrees and prior development plans. Installing/configuring agent tools is not authorization to resume it. Until the user explicitly resumes Windroid: do not implement product changes, run product builds/tests/workloads, launch development workers, dispatch development tasks, or advance its milestones. Read-only inspection and explicitly requested agent configuration are allowed. A later explicit user resume instruction supersedes this pause.
