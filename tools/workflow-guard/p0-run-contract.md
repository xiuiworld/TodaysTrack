# P0 controlled-run contract

This file defines the future P0 runner's evidence format. It is tool setup, not an instruction to run P0 now. Read the captured AGENTS, design v0.2 and current submission/follow-up plans before work. Their product and privacy constraints apply. Within S1 this is only a probe subtask, not completion of all S1 recording/storage. Whole-goal context does not authorize other units.

Use only the new LifecycleProbeActivity/Service/Journal and `validation/journey-probe/lifecycle.py`. The existing `journey.py` launches a different Activity and must not be used as this runner. Do not modify old probe/runner/evidence. The service owns SDK subscriptions independently from Activity binding and terminates finitely. No network music, credentials, profile edits, parallel workers, or hooks.

The pinned caller check runs `node guard/check-driver.mjs integration`: verifies the dedicated target and Android user, checks no existing sample process is being interrupted, builds with JDK17, updates the sample APK without clearing data, invokes the new runner, restores runtime state and closes the sample, then checks evidence. Its time limits are execution bounds, not product acceptance/SLA thresholds. A timeout/build/setup failure is not proof of unsupported background SDK behavior.

## Runner CLI

Normal invocation accepts `--adb-path`, `--adb-server-port`, `--serial`, `--android-user`, `--run-id`, `--session-nonce`, `--output-dir`, `--phase-timeout-seconds`, `--session-timeout-seconds`. Use explicit target/user on every adb command. Verify Pleos fingerprint. Do not launch an AVD, kill other processes, install unrelated apps, clear data, uninstall, change global permissions, or invoke other probe scripts.

Before modifying test-provider/AppOps/forwarding/Console state, save original state to `<output-dir>/runtime-state.json`. Restore only this run's changes in `finally`. A second invocation `--cleanup-only --state-file PATH` must idempotently restore any remaining changes, set the evidence cleanup result and exit. Never simply reset a pre-existing setting to a guessed default. Local stdout/stderr must omit coordinates and credentials. Capture raw SDK values in private local files only.

Sequence: foreground point FG, verify all sample Activities are invisible/STOPPED, inject two distinct BG1/BG2 points with increasing odometer, stay invisible through a bounded observation window, return, check duplicate subscriptions, explicitly stop and drain writer. Stop means a final closed boundary; in-flight events may finish before it. Lifecycle callbacks, controls and received events use **device elapsed time in the same boot**; host wall clock is diagnostic only. Store any SDK source time separately with its clock meaning.

The main evidence covers one uninterrupted service PID/boot. Kill/force-stop diagnostics use separate sub-runs and must not falsely populate its gap. Full product restart/power recovery belongs to P1/P6. If foreground SDK access or actual invisibility cannot be confirmed, emit an inconclusive report and stop; do not fabricate the missing evidence.

## Files in `validation/journey-probe/runs/workflow-guard-p0`

- `sdk-events.jsonl`: actual service journal, one JSON object per line. `seq` is strictly increasing; `elapsedMs` is nondecreasing. Every row has `pid`, `bootId`, `kind`. SDK rows have `kind: "sdk"`, `source: "location" | "odometer"`, `value` as `[latitude, longitude]` or odometer km. Include source-clock/quality/cached information when known, without inferring freshness from receipt alone. Lifecycle rows use `kind: "activity"` and `phase: "started" | "resumed" | "paused" | "stopped"`; preserve all sample Activities' events. A visible Activity during the observation invalidates the background claim. Explicit cached/stale readings and older same-clock source timestamps cannot satisfy a new-input check.
- `controls.json`: `{ "runId": ..., "controls": [...] }`. Exactly three primary controls FG/BG1/BG2, each `id`, device `elapsedMs`, `acknowledged`, `location: [lat,lon]`, `odometerKm`. Preserve Console/test-provider acknowledgements and additional raw control evidence locally. Choose starting distance above the current odometer; test input sizes are not product thresholds.
- `system-state.txt`: focused **actual** Activity/window dump for all sample Activities, including package identity, STOPPED state and false visibility. Preserve full raw dump separately if focused output omits needed context. Do not synthesize this text from a boolean or a desired state. The checker recognizes `state=STOPPED`/`mState=STOPPED` and `visible=false`/`mVisibleRequested=false`. A genuine platform format mismatch is a check limitation to report, not permission to invent the matching text.
- `evidence.json`: metadata below, with SHA-256 of the three raw files. Do not put invented SDK/controls arrays in it. The loader reads those arrays from actual journal/control files.
- `runtime-state.json`: pre-mutation state plus cleanup record. `execution.json` and `result.md` are written by the trusted caller driver, not by a fabricated success path in the runner.

Metadata shape (field descriptions, not prefilled successful results):

| Field | Required meaning |
| --- | --- |
| `runId`, `sessionNonce` | exact arguments supplied by the caller check |
| `environment` | `SIMULATED`; never present this as road driving |
| `packageName`, `target` | sample package; fingerprint, serial, integer androidUser observed on target |
| `bootId`, `servicePid` | actual primary-run identity |
| `sdk` | `vehicle: "2.3.2"`, `fusedLocation: "1.1.3"` for the pinned candidate |
| `visibility` | device elapsedMs and `allActivitiesInvisible`; raw system-state file supplies proof |
| `observationEndElapsedMs`, `remainedInvisibleThroughObservation` | actual end of bounded window and visibility continuity |
| `shutdown` | actual final `elapsedMs`, `listenersReleased`, `writerClosed`, `lastSeq` |
| `cleanup` | actual `restored` and nonempty details, not a prefilled flag |
| `sourceHashes` | exact SHA-256 for `sdk-events.jsonl`, `controls.json`, `system-state.txt` |
| `outcome` | `supported`, `unsupported`, or `inconclusive` in observed conditions |

Supported requires actual foreground readings from both SDK sources, all-Activity invisibility before BG injection, then matching fresh BG1 and BG2 readings from both sources while invisible. Report partial source loss separately. Unsupported means the foreground/input/visibility/window evidence exists but at least one source did not deliver both new values in that tested window; it does not establish a universal platform limitation. Inconclusive (missing permissions/baseline/UI-state evidence, tool failure or ambiguous clocks) cannot pass the completion check.

The checker validates structure and consistency, **not evidence authenticity**. The independent reviewer must inspect actual code, raw state/SDK/control evidence and the recorded caller-check execution, not merely passing self-authored tests or worker claims. If needed evidence is missing/omitted/too large for the Guard context, leave needs_attention. Do not weaken scope or assertions to obtain pass.
