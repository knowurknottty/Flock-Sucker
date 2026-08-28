# Real On-Device GGUF AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the nonfunctional setup-only AI experience with a real on-device GGUF model lifecycle and inference path that can download, verify, load, run, stream, cancel, unload, and survive app restart.

**Architecture:** Introduce a small `LocalLlmEngine` interface and a pinned llama.cpp Android/NDK implementation for arm64-v8a. Keep model download/catalog/state management in Kotlin, native inference behind a narrow JNI boundary, and mark a model `READY` only after a real generation smoke test.

**Tech Stack:** Kotlin, Android NDK/CMake or prefab-compatible native build, llama.cpp pinned revision, JNI, coroutines/Flow, Compose.

**Spec:** `docs/superpowers/specs/2026-08-28-flock-sucker-evidence-performance-ai-design.md`

## Global Constraints

- Do not present MediaPipe as supported unless an end-to-end generation test passes.
- Do not mark a GGUF model Ready based only on download/file existence.
- Initial mandatory ABI is `arm64-v8a` CPU/NEON; GPU/Vulkan is optional later.
- Model downloads require exact size and SHA-256 verification.
- Model failure must not block scanning or detection persistence.

---

### Task 1: Define the engine and lifecycle contracts

**Files:**
- Create: `app/src/main/java/com/flockyou/ai/LocalLlmEngine.kt`
- Create: `app/src/main/java/com/flockyou/ai/LocalModelState.kt`
- Modify existing `LlmEngineManager`/AI manager files under `app/src/main/java/com/flockyou/ai/`
- Create: `app/src/test/java/com/flockyou/ai/LocalLlmEngineContractTest.kt`

**Interfaces:**
- `LocalLlmEngine.load(modelPath, config)`
- `LocalLlmEngine.generate(request): Flow<GenerationEvent>`
- `LocalLlmEngine.cancel(requestId)`
- `LocalLlmEngine.unload()`
- `LocalLlmEngine.health(): EngineHealth`
- Lifecycle enum: `NOT_INSTALLED, DOWNLOADING, VERIFYING, INSTALLED, LOADING, READY, ERROR`.

- [ ] Write contract tests using a fake engine proving lifecycle transitions, streaming tokens, cancellation, error recovery, and unload.
- [ ] Run tests and confirm existing code has no complete executable lifecycle.
- [ ] Add the interfaces/pure state machine without binding to llama.cpp yet.
- [ ] Re-run tests and commit `refactor(ai): define executable local llm contract`.

### Task 2: Correct the model catalog and hosted Gemma metadata

**Files:**
- Modify: `app/src/main/java/com/flockyou/ai/FineTunedModelArtifacts.kt`
- Modify existing AI settings/catalog UI.
- Create: `app/src/test/java/com/flockyou/ai/ModelCatalogTest.kt`

**Model metadata:**
- ID: `gemma-flock-q8-0`
- File: `gemma-mlx-probe-fused-q8_0.gguf`
- URL: `https://mega.nz/file/WzAiwIba#-lYBgLIkxmAgzmd_CXcKEjMIhuuYlvpfWFUeVXMnxlc`
- Size: `291545376`
- SHA-256: `82b323bf05eba698b87a39d1eca8ea31506222aff25b415f6388135069725b57`
- Runtime: `LLAMA_CPP_GGUF`

- [ ] Write tests asserting exact metadata and that unsupported MediaPipe entries cannot report Ready.
- [ ] Replace `runtimeCompatible=false` with runtime selection rather than pretending the artifact itself is incompatible.
- [ ] UI must show expected size/hash/runtime before download.
- [ ] Commit `feat(ai): register hosted Gemma for gguf runtime`.

### Task 3: Implement robust download and verification

**Files:**
- Create/modify model download repository under `app/src/main/java/com/flockyou/ai/`
- Create: `app/src/test/java/com/flockyou/ai/ModelDownloadVerifierTest.kt`

- [ ] Write tests for partial download, cancellation, size mismatch, SHA mismatch, atomic rename after verification, and restart recovery.
- [ ] Download to a temporary app-private file, stream SHA-256 while verifying, and atomically move only after exact match.
- [ ] Surface byte progress and failure reason without exposing a model as installed prematurely.
- [ ] Commit `feat(ai): verify model downloads atomically`.

### Task 4: Pin and build llama.cpp for Android

**Files:**
- Add native source/submodule/vendor metadata under `app/src/main/cpp/` or project-native convention.
- Modify: `app/build.gradle.kts`
- Add `CMakeLists.txt` if the project does not already have an equivalent native build file.
- Create: `app/src/test/java/com/flockyou/ai/LlamaNativeAvailabilityTest.kt`

- [ ] Pin an exact llama.cpp commit and record it in source/docs; no floating main branch.
- [ ] Configure `arm64-v8a` CPU/NEON build with only required llama inference components to minimize APK/native footprint.
- [ ] Add a native availability/version JNI probe and a unit/instrumentation assertion that the packaged library loads on arm64.
- [ ] Build `sideloadDebug` and inspect APK native libraries.
- [ ] Commit `build(ai): add pinned llama cpp android backend`.

### Task 5: Implement the narrow JNI inference bridge

**Files:**
- Create: `app/src/main/cpp/flock_llama_jni.cpp`
- Create: `app/src/main/java/com/flockyou/ai/LlamaCppEngine.kt`
- Create: `app/src/androidTest/java/com/flockyou/ai/LlamaCppEngineSmokeTest.kt`

- [ ] Define JNI functions for initialize/load, generate-next/stream callback or bounded token chunks, cancel, unload, and native diagnostics.
- [ ] Ensure all native handles have deterministic lifetime and cancellation checks.
- [ ] Translate native errors to typed Kotlin failures; never crash scanning service for AI errors.
- [ ] Add an instrumentation smoke test that loads a tiny test fixture/model where licensing permits, or runs the native health probe if the hosted model is not prebundled.
- [ ] Commit `feat(ai): execute gguf inference through llama cpp`.

### Task 6: Make the hosted Gemma actually Ready on device

**Files:**
- Modify AI engine manager/state repository.
- Add: `app/src/androidTest/java/com/flockyou/ai/HostedGemmaE2ETest.kt`

- [ ] On physical arm64 device, download or stage the exact hosted model and verify size/hash.
- [ ] Load it with conservative context/thread defaults derived from device memory/CPU.
- [ ] Run a deterministic smoke prompt such as `Return exactly: FLOCK-SUCKER READY` with bounded generation settings and assert nonempty coherent token output; record actual output rather than requiring impossible byte determinism.
- [ ] Test cancellation during generation, unload/reload, and app-process restart followed by rediscovery/load.
- [ ] Only after this test passes set UI state to `READY`.
- [ ] Commit `test(ai): prove hosted Gemma on device`.

### Task 7: Replace setup-only AI UX with an executable console

**Files:**
- Modify the existing AI settings/model screen.
- Modify detection detail/action surfaces that invoke AI.
- Create focused Compose/presentation tests.

- [ ] Render model lifecycle, runtime, size/hash status, loaded memory estimate, context, last inference latency, tokens/sec, and last error.
- [ ] Provide `DOWNLOAD`, `VERIFY`, `LOAD`, `TEST`, `UNLOAD`, and `DELETE` actions only when valid for current lifecycle.
- [ ] Add a small `LOCAL ANALYSIS CONSOLE` where the operator can enter a prompt, see streamed tokens, and cancel.
- [ ] Wire detection analysis through the same engine; never create a second hidden model runner.
- [ ] Remove/label dead MediaPipe actions so there is no setup control that cannot ultimately generate.
- [ ] Commit `feat(ai): make local model workflow executable`.

### Task 8: Protect scanning performance and memory

**Files:**
- Modify AI manager/service integration and background analysis scheduling.
- Add tests under `app/src/test/java/com/flockyou/ai/`.

- [ ] Add tests proving AI disabled/unloaded creates no inference work on scanner hot path.
- [ ] Bound concurrent generations to one per local engine initially, queue/cancel explicitly, and apply memory-pressure unload policy.
- [ ] Record load time, RSS change, first-token latency, tokens/sec, and scanner event loss while the model is active.
- [ ] If AI materially harms scanning, default automatic analysis off and preserve manual analysis rather than hiding scanner degradation.
- [ ] Commit `perf(ai): isolate local inference from scanning hot path`.

### Task 9: Android acceptance

- [ ] Install the built APK on phone/tablet arm64 hardware.
- [ ] From the visible app UI, download Gemma, verify the displayed SHA/size, load it, run the test prompt, and capture streamed output.
- [ ] Cancel a second generation and prove the UI/native engine returns to Ready.
- [ ] Restart the app and prove the verified model remains discoverable and can reload without redownload.
- [ ] Scan concurrently during a manual generation and capture scanner counters plus memory/CPU evidence.
- [ ] Commit screenshots/logs/test output under the repo's dogfood evidence convention as `test(android): prove real local gguf inference`.