# R9 Lazy AI Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep heavyweight on-device inference out of `:scanning` and normal startup; load it only when a real analysis request reaches `DetectionAnalyzer.analyzeDetection()`.

**Architecture:** Add a deterministic process-role policy plus API-26-compatible process-name resolver. `FlockYouApplication` runs package-global bootstrap only in the canonical main process, removes its `DetectionAnalyzer` dependency entirely, and limits AI startup work to scheduling/canceling workers. Existing `analyzeDetection()` lazy initialization remains unchanged.

**Tech Stack:** Kotlin, Android API 26+, Hilt, WorkManager, kotlinx.coroutines, JUnit4, MockK, Android Studio Index MCP, Gradle systemDebug.

**Spec:** `docs/superpowers/specs/2026-08-30-r9-lazy-ai-bootstrap-design.md`

## Global Constraints

- Preserve Flock Boost and all R8 BLE/Wi-Fi/GNSS behavior.
- No new process, Binder/IPC layer, root dependency, or radio-scanner dependency on AI.
- `:scanning` and unknown secondary processes fail conservative: no package-global AI/bootstrap work.
- Main startup must not call `DetectionAnalyzer.initializeModel()`.
- First real AI analysis must retain the existing lazy-init/fallback behavior in `DetectionAnalyzer.analyzeDetection()`.
- Preserve minSdk 26: process-name resolution needs a pre-API-28 fallback.
- Keep the pinned AGP/Gradle/Java toolchain unchanged.

---
### Task 1: Capture the accepted R8 before-state

**Files:**
- Create: `docs/hotcode/ROUND09_LAZY_AI_BOOTSTRAP_20260830.md`

**Interfaces:**
- Consumes: live R8 package on Tonga and accepted branch head `8200278`.
- Produces: before/after evidence for scanner PID, model-load logs, and memory footprint.

- [ ] **Step 1: Record live R8 scanner identity and PSS**

Run read-only ADB probes against the connected Tonga: resolve the `:scanning` PID, capture `dumpsys meminfo` TOTAL PSS/RSS, and inspect `/proc/<pid>/maps` for GGUF/Gemma/llama mappings when permitted.

- [ ] **Step 2: Record live R8 model initialization evidence**

Capture scanner-process log lines matching `initializeModel`, `LlmEngineManager`, `llama`, `Gemma`, `GGUF`, and model-load completion. Do not mutate app state.

- [ ] **Step 3: Start the R9 receipt**

Write the exact before-state evidence to `docs/hotcode/ROUND09_LAZY_AI_BOOTSTRAP_20260830.md`, clearly labeled R8 baseline.

---
### Task 2: Add deterministic process-role policy

**Files:**
- Create: `app/src/main/java/com/flockyou/bootstrap/ProcessBootstrapPolicy.kt`
- Create: `app/src/test/java/com/flockyou/bootstrap/ProcessBootstrapPolicyTest.kt`

**Interfaces:**
- Produces: `ProcessRole`, `ProcessBootstrapPolicy.classify(currentProcessName, mainProcessName)`, and `ProcessNameResolver.currentProcessName(context)`.
- Consumers: `FlockYouApplication.onCreate()` in Task 3.

- [ ] **Step 1: Write the failing pure-policy tests**

```kotlin
@Test fun `package process is main`() =
    assertEquals(ProcessRole.MAIN, ProcessBootstrapPolicy.classify("com.flockyou.debug", "com.flockyou.debug"))

@Test fun `scanning process is secondary`() =
    assertEquals(ProcessRole.SECONDARY, ProcessBootstrapPolicy.classify("com.flockyou.debug:scanning", "com.flockyou.debug"))

@Test fun `missing process identity is unknown and conservative`() {
    assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify(null, "com.flockyou.debug"))
    assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify("", "com.flockyou.debug"))
}
```

- [ ] **Step 2: Run the focused test and require RED**

Run: `./gradlew :app:testSystemDebugUnitTest --tests 'com.flockyou.bootstrap.ProcessBootstrapPolicyTest'`
Expected: compile/test failure because the policy does not exist.
- [ ] **Step 3: Implement the minimal process policy and resolver**

```kotlin
internal enum class ProcessRole { MAIN, SECONDARY, UNKNOWN }

internal object ProcessBootstrapPolicy {
    fun classify(currentProcessName: String?, mainProcessName: String?): ProcessRole = when {
        currentProcessName.isNullOrBlank() || mainProcessName.isNullOrBlank() -> ProcessRole.UNKNOWN
        currentProcessName == mainProcessName -> ProcessRole.MAIN
        else -> ProcessRole.SECONDARY
    }
}
```

`ProcessNameResolver.currentProcessName(context)` uses `Application.getProcessName()` on API 28+ and `ActivityManager.runningAppProcesses` + `Process.myPid()` on API 26-27. Any failure returns `null` and therefore `UNKNOWN`.

- [ ] **Step 4: Run the focused test and require GREEN**

Run the same focused Gradle test. Expected: all process-policy tests pass.

- [ ] **Step 5: Commit the process policy**

```bash
git add app/src/main/java/com/flockyou/bootstrap/ProcessBootstrapPolicy.kt app/src/test/java/com/flockyou/bootstrap/ProcessBootstrapPolicyTest.kt
git commit -m "feat(r9): add conservative process bootstrap policy"
```

---
### Task 3: Gate package bootstrap and remove eager AI loading

**Files:**
- Modify: `app/src/main/java/com/flockyou/FlockYouApplication.kt`
- Modify: `app/src/main/java/com/flockyou/bootstrap/ProcessBootstrapPolicy.kt`
- Modify: `app/src/test/java/com/flockyou/bootstrap/ProcessBootstrapPolicyTest.kt`

**Interfaces:**
- Consumes: `ProcessBootstrapPolicy` and `ProcessNameResolver` from Task 2.
- Produces: main-process-only package bootstrap and `AiBackgroundWorkAction` (`SCHEDULE` or `CANCEL`) with no `DetectionAnalyzer` dependency in `FlockYouApplication`.

- [ ] **Step 1: Extend the RED tests for bootstrap decisions**

```kotlin
@Test fun `secondary process never runs package bootstrap`() =
    assertFalse(ProcessBootstrapPolicy.shouldRunPackageBootstrap("com.flockyou.debug:scanning", "com.flockyou.debug"))

@Test fun `unknown process never runs package bootstrap`() =
    assertFalse(ProcessBootstrapPolicy.shouldRunPackageBootstrap(null, "com.flockyou.debug"))

@Test fun `AI worker scheduling requires AI and false positive filtering`() {
    assertEquals(AiBackgroundWorkAction.SCHEDULE, aiBackgroundWorkAction(true, true))
    assertEquals(AiBackgroundWorkAction.CANCEL, aiBackgroundWorkAction(true, false))
    assertEquals(AiBackgroundWorkAction.CANCEL, aiBackgroundWorkAction(false, true))
}
```

- [ ] **Step 2: Run the focused tests and require RED**

Expected failure: `shouldRunPackageBootstrap`, `AiBackgroundWorkAction`, and `aiBackgroundWorkAction` are not yet implemented.
- [ ] **Step 3: Implement the bootstrap policy helpers**

Add:

```kotlin
internal fun ProcessBootstrapPolicy.shouldRunPackageBootstrap(
    currentProcessName: String?,
    mainProcessName: String?
): Boolean = classify(currentProcessName, mainProcessName) == ProcessRole.MAIN

internal enum class AiBackgroundWorkAction { SCHEDULE, CANCEL }

internal fun aiBackgroundWorkAction(
    aiEnabled: Boolean,
    falsePositiveFilteringEnabled: Boolean
): AiBackgroundWorkAction = if (aiEnabled && falsePositiveFilteringEnabled) {
    AiBackgroundWorkAction.SCHEDULE
} else {
    AiBackgroundWorkAction.CANCEL
}
```

- [ ] **Step 4: Gate `FlockYouApplication.onCreate()` before package-global startup**

After `super.onCreate()`, resolve current/main process names. If `shouldRunPackageBootstrap(...)` is false, log the role and return. Only the main process creates channels, initializes OEM/OUI bootstrap, schedules AI background work, and initializes dead-man-switch scheduling.

- [ ] **Step 5: Remove eager model ownership from `FlockYouApplication`**

Delete the `DetectionAnalyzer` import, `dagger.Lazy` import, injected `Lazy<DetectionAnalyzer>` field, and every call to `initializeModel()` from Application startup. Rename `initializeAiModel()` to `initializeAiBackgroundWork()` and make it execute only `BackgroundAnalysisWorker.schedule(...)`, `schedulePendingAnalysis(...)`, or `cancel(...)` from `aiBackgroundWorkAction(...)`.
- [ ] **Step 6: Run focused tests and require GREEN**

Run: `./gradlew :app:testSystemDebugUnitTest --tests 'com.flockyou.bootstrap.ProcessBootstrapPolicyTest'`
Expected: all R9 bootstrap-policy tests pass.

- [ ] **Step 7: Verify the Application source invariant with Android Studio Index**

Use Index MCP to verify:
- `FlockYouApplication.kt` has no `DetectionAnalyzer` reference.
- `FlockYouApplication.kt` has no `initializeModel()` call.
- `FlockYouApplication.onCreate()` references `ProcessBootstrapPolicy`/`ProcessNameResolver`.
- `DetectionAnalyzer.analyzeDetection()` still contains its existing lazy `initializeModel()` path.

- [ ] **Step 8: Commit bootstrap behavior**

```bash
git add app/src/main/java/com/flockyou/FlockYouApplication.kt app/src/main/java/com/flockyou/bootstrap/ProcessBootstrapPolicy.kt app/src/test/java/com/flockyou/bootstrap/ProcessBootstrapPolicyTest.kt
git commit -m "perf(r9): lazy-load AI outside scanning bootstrap"
```

---

### Task 4: Verify the complete source gate

**Files:**
- Modify only if a real regression is exposed.

- [ ] **Step 1: Run the full unfiltered systemDebug unit suite**

Run: `./gradlew :app:testSystemDebugUnitTest`
Expected: prior 809 tests plus the new R9 tests, with zero failures/errors/skips.

- [ ] **Step 2: Build the exact deployment artifact**

Run: `./gradlew :app:assembleSystemDebug`
Expected: `BUILD SUCCESSFUL` on the pinned toolchain.
- [ ] **Step 3: Run static integrity checks**

Run `git diff --check`; verify `settings.gradle.kts`, root `build.gradle.kts`, Gradle wrapper, and daemon JVM settings still match the pinned R8 toolchain. Reject Android Studio auto-upgrades if they reappear.

- [ ] **Step 4: Record exact counts and APK SHA-256**

Persist the total unit-test count and SHA-256 of `app/build/outputs/apk/system/debug/app-system-debug.apk` in the R9 receipt.

---

### Task 5: Deploy and prove the live process boundary

**Files:**
- Complete: `docs/hotcode/ROUND09_LAZY_AI_BOOTSTRAP_20260830.md`

**Interfaces:**
- Consumes: exact GREEN systemDebug APK from Task 4 and Android Studio `app` run configuration targeting Tonga.
- Produces: live proof that radio scanning is model-free until an explicit analysis request.

- [ ] **Step 1: Deploy through Android Studio**

Use the `app` run configuration with `:app = systemDebug`. Accept Studio's normal privileged-app fallback to a standard package-manager install if optimistic `run-as` deployment fails. Verify fresh main and `:scanning` PIDs after install/restart.

- [ ] **Step 2: Start Flock scanning through the visible app UI**

Keep Flock Boost enabled. Require fresh scanner logs showing the established aggressive BLE plan plus R8 Wi-Fi evidence and GNSS registration.

- [ ] **Step 3: Prove no heavyweight model load in `:scanning`**

From the fresh scanner PID, require absence of `initializeModel START`, `LlmEngineManager initialized`, GGUF/Gemma model-load messages, and llama.cpp model initialization before any explicit AI analysis request. Capture scanner PSS/RSS and model-map evidence for comparison with Task 1.
- [ ] **Step 4: Prove normal main startup is also model-cold**

Before explicit AI use, require the fresh main PID to have no eager model initialization. This distinguishes R9 from the weaker “move the model from scanner to main” design.

- [ ] **Step 5: Trigger one explicit AI analysis through the existing visible AI test/analysis UI**

Do not call private methods or edit app storage. Use the app's normal AI Settings/test-analysis surface. Capture the main PID logs and require the existing lazy path (`Model not loaded, attempting lazy initialization` → `initializeModel START`) only after this explicit request.

- [ ] **Step 6: Verify analysis still completes or uses the existing documented fallback**

Require a successful analysis result or the existing rule-based fallback/error semantics. Re-check the scanner PID afterward and prove it still has no model-load initialization.

- [ ] **Step 7: Finish the R9 live receipt**

Record before/after scanner memory, process identities without exposing the device serial, cold-start evidence, lazy-load evidence, radio-health evidence, test count, APK hash, and any remaining uncertainty.

- [ ] **Step 8: Commit and push the acceptance receipt**

```bash
git add docs/hotcode/ROUND09_LAZY_AI_BOOTSTRAP_20260830.md
git commit -m "docs(r9): record lazy AI bootstrap acceptance"
git push -u origin inversion-labs/r9-lazy-ai-bootstrap
```

## Completion Gate

R9 is accepted only if the exact final branch is clean, the full systemDebug suite/build is green, `:scanning` remains model-cold while radio acquisition works, main startup remains model-cold, and a later explicit analysis request successfully exercises the existing lazy model initialization path.
