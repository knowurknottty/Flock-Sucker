# R9 Lazy AI Bootstrap Design

## Goal

Keep heavyweight on-device inference out of the `:scanning` process and out of normal app startup unless an analysis request actually requires it.

## Observed problem

Android Studio Index proves `ScanningService` has no `DetectionAnalyzer` dependency. The heavyweight model enters `:scanning` because `FlockYouApplication.onCreate()` runs `initializeAiModel()` in every Android process.

Live R8 evidence showed the isolated scanner process loading the roughly 272 MiB Gemma Q8 model and initializing llama.cpp despite radio acquisition being the scanner's primary responsibility.

`DetectionAnalyzer.analyzeDetection()` already implements lazy initialization when AI is enabled, the selected model is not rule-based, and no model is loaded.

## Approved architecture

Use the existing application process boundary. Do not add a new `:ai` process, Binder interface, service, daemon, or root component in R9.

`FlockYouApplication` will distinguish the package's main process from secondary processes. Main-process-only startup owns AI-related WorkManager scheduling and other UI/application bootstrap that requires main-process semantics.

Heavyweight model initialization will not occur from `Application.onCreate()` in any process. Actual analysis requests remain the authoritative lazy-load trigger through `DetectionAnalyzer.analyzeDetection()`.

## Process policy

The canonical main process is the package process name. Secondary manifest processes such as `:scanning` must not initialize the LLM engine, mmap/load model artifacts, or schedule duplicate AI background work during application bootstrap.

Process detection must be deterministic and unit-testable. Prefer a small pure policy around the resolved current process name rather than scattering `Application.getProcessName()` checks through startup code.

Unknown/unresolvable process identity must fail conservative: it may initialize safe notification/process-local prerequisites, but must not eagerly initialize heavyweight AI.

## Startup flow

Every process may execute lightweight, process-safe application setup required for correctness, such as notification-channel creation.

Only the main process performs application-level scheduling/bootstrap that should exist once per package. AI settings are read there to schedule or cancel `BackgroundAnalysisWorker`, but the model itself is not initialized.

When a worker, settings test, or explicit analysis path eventually calls `analyzeDetection()`, the existing lazy initialization path loads the selected runtime model under `modelStateMutex`. Existing rule-based fallback behavior remains unchanged.

## Non-goals

R9 does not change model selection, model formats, inference parameters, false-positive analysis semantics, BLE/Wi-Fi/GNSS acquisition, privileged permissions, root policy, or GhostArrow's device-global Wi-Fi throttle experiment.

R9 does not move WorkManager into a new process or create cross-process inference IPC.

## Failure behavior

If lazy model initialization fails, analysis continues to use the existing rule-based fallback/error behavior; radio scanning must remain unaffected.

A failure to resolve process identity must never cause secondary processes to load the heavyweight model. Logging should expose the resolved process name and chosen bootstrap policy without logging sensitive device identifiers.

## Verification

Host/unit tests must prove the pure process policy classifies the package process as main and `:scanning` as secondary, including an unknown-process conservative case.

Application-bootstrap tests must prove secondary processes do not invoke eager AI initialization or AI worker scheduling. Main-process bootstrap must schedule/cancel AI background work according to settings without invoking `DetectionAnalyzer.initializeModel()`.

The complete `systemDebug` unit suite and `assembleSystemDebug` must remain green.

Live Tonga acceptance requires a fresh R9 install and process restart with Flock Boost/scanning active. The `:scanning` PID must show no llama.cpp/Gemma model-load initialization before an explicit AI analysis request, while BLE/Wi-Fi/GNSS scanning continues normally.

Main-process startup must likewise show no eager heavyweight model load. An explicit AI analysis request must still lazy-load the configured model and complete or fall back according to existing behavior.

## Success criteria

1. `:scanning` starts and scans without loading the heavyweight model.
2. Normal main-process startup does not load the heavyweight model.
3. AI background scheduling remains main-process-only and settings-correct.
4. First real AI analysis remains functional via existing lazy initialization.
5. No new IPC subsystem or privileged/root dependency is introduced.
6. Existing R8 radio acceptance behavior remains intact.
