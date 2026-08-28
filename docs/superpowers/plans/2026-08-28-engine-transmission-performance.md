# Engine and Transmission Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run five measured optimization iterations over Flock-Sucker's core detection engines, then five task-specific iterations over its internal state/IPC/data-transmission path, without trading away detection accuracy or auditability.

**Architecture:** Add lightweight benchmark/telemetry seams first, then optimize only measured bottlenecks. Engine work covers sensor callback→classification→persistence. Transmission work covers service state→Binder/Messenger→repository/ViewModel→Compose and separates high-frequency counters from bulky state payloads.

**Tech Stack:** Kotlin, coroutines/channels/Flow, Android tracing/Perfetto-compatible markers, Room, Messenger/Binder IPC, Compose, Android device profiling.

**Spec:** `docs/superpowers/specs/2026-08-28-flock-sucker-evidence-performance-ai-design.md`

## Global Constraints

- Five passes means five recorded measure→change→verify cycles for each lane.
- Every optimization pass must keep correctness/event-loss tests green.
- No synthetic benchmark result may be presented as field performance.
- Dependency count should not increase unless the measured gain justifies it and no platform alternative exists.

---

### Task 1: Establish performance telemetry and fixtures

**Files:**
- Create: `app/src/main/java/com/flockyou/monitoring/PipelinePerformanceMonitor.kt`
- Create: `app/src/test/java/com/flockyou/monitoring/PipelinePerformanceMonitorTest.kt`
- Extend existing deterministic stress fixtures under `app/src/test/`.

**Interfaces:**
- Produces: bounded histograms/counters for observation→candidate, candidate→classification, classification→persistence, queue depth/drops, serialization bytes, IPC message counts, and UI refresh latency.

- [ ] Write failing tests proving histogram storage is bounded and p50/p95 calculation is deterministic.
- [ ] Implement low-allocation monotonic timing using `elapsedRealtimeNanos()` and fixed-size/ring or aggregated buckets.
- [ ] Add deterministic 500/1,000+ observation fixtures and burst patterns.
- [ ] Verify telemetry can be disabled in production without altering pipeline behavior.
- [ ] Commit `perf(obs): add bounded pipeline telemetry`.

### Task 2: Engine pass 1 — callback and queue pressure

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/ScanningService.kt`
- Modify scanner callback/channel code identified by profile.
- Add focused tests under `app/src/test/java/com/flockyou/service/`.

- [ ] Capture baseline p50/p95, CPU, allocation, queue-depth and drop metrics on deterministic fixtures and one physical-device scan.
- [ ] Identify the dominant callback/queue cost; expected candidates include per-result allocations, logging, channel overflow, or repeated publication.
- [ ] Implement only the measured fix, preserving raw observation counts and backpressure visibility.
- [ ] Re-run correctness + benchmark and record before/after in `docs/performance/engine-pass-1.md`.
- [ ] Commit `perf(engine): optimize scanner callback pressure pass 1`.

### Task 3: Engine pass 2 — classification and rule matching

**Files:**
- Modify relevant detection handler/pattern files under `app/src/main/java/com/flockyou/detection/` and `data/model/`.

- [ ] Profile rule matching on mixed BLE/Wi-Fi fixtures including nonmatches.
- [ ] Optimize the hottest matcher path using precompiled patterns/indexed lookup/canonicalized keys where measurement supports it; never weaken rules to gain speed.
- [ ] Assert byte-for-byte equivalent classification outputs for the fixture corpus.
- [ ] Record metrics in `docs/performance/engine-pass-2.md` and commit `perf(engine): optimize classification pass 2`.

### Task 4: Engine pass 3 — deduplication and sighting correlation

**Files:**
- Modify: `app/src/main/java/com/flockyou/data/repository/DetectionDeduplicator.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/DetectionRepository.kt`

- [ ] Benchmark repeated-device workloads and composite matching at 500/1,000+ devices.
- [ ] Eliminate unnecessary database scans/string parsing/cache churn while keeping matching semantics identical.
- [ ] Add adversarial tests for MAC randomization, service UUID matches, same-SSID collisions, and proximity composite matching.
- [ ] Record pass metrics and commit `perf(engine): optimize dedup and correlation pass 3`.

### Task 5: Engine pass 4 — persistence and enrichment boundaries

**Files:**
- Modify repository/DAO transaction code in `app/src/main/java/com/flockyou/data/repository/`.
- Modify AI/enrichment scheduling only if profile shows it on the hot path.

- [ ] Measure observation→commit latency, transaction count, SQL statements, and background work scheduling.
- [ ] Batch or transact only operations that preserve event ordering and append-only sighting guarantees.
- [ ] Ensure disabled AI causes zero model-worker churn on the detection hot path.
- [ ] Record pass metrics and commit `perf(engine): optimize persistence pass 4`.

### Task 6: Engine pass 5 — DSP/GNSS/RF and whole-pipeline validation

**Files:**
- Modify only measured hotspots in `UltrasonicDetector`, GNSS/RF monitors, or correlation analyzers.

- [ ] Profile CPU/allocation under concurrent BLE/Wi-Fi/GNSS/audio scanning on hardware.
- [ ] Optimize the dominant remaining compute path, favoring buffer reuse, bounded windows, and avoiding redundant transforms.
- [ ] Run all scanner correctness gates and compare event counts to pre-pass baseline.
- [ ] Record final engine report `docs/performance/engine-pass-5.md` with cumulative and per-pass gains.
- [ ] Commit `perf(engine): complete measured optimization pass 5`.

### Task 7: Transmission pass 1 — inventory message volume and payload sizes

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceBroadcaster.kt`
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceIpc.kt`
- Create: `app/src/test/java/com/flockyou/service/IpcVolumeTest.kt`

- [ ] Record baseline message count, bytes/sec, largest payload, duplicate payload percentage, and decode time during stress scan.
- [ ] Add stable instrumentation around each IPC message type without logging sensitive payload contents.
- [ ] Record `docs/performance/transmission-pass-1.md`; commit `perf(ipc): measure transmission baseline pass 1`.

### Task 8: Transmission pass 2 — split hot counters from bulky state

**Files:**
- Modify IPC/broadcaster/client connection classes.

- [ ] Write failing tests proving sighting/raw-observation counters can update without sending full detector lists/JSON state.
- [ ] Add compact typed counter/state messages and retain periodic full-state resync as recovery.
- [ ] Measure message bytes and UI counter latency.
- [ ] Record metrics and commit `perf(ipc): separate hot counters pass 2`.

### Task 9: Transmission pass 3 — delta/coalesced subsystem payloads

**Files:**
- Modify broadcaster and client projection for seen devices, GNSS, cellular, RF, ultrasonic, health.

- [ ] Build sequence tests for add/update/remove deltas and reconnect full snapshot.
- [ ] Coalesce rapid intermediate states with bounded cadence where no individual event semantics are lost; preserve append-only sightings as individual persisted evidence.
- [ ] Remove repeated serialization of identical large lists.
- [ ] Record metrics and commit `perf(ipc): coalesce state deltas pass 3`.

### Task 10: Transmission pass 4 — stale-state/reconnect correctness

**Files:**
- Modify `ScanningServiceIpc.kt`, service registration/resync paths, ViewModel state projection.

- [ ] Add tests for process death, Binder disconnect, sleep/wake, client re-register, and out-of-order stale message rejection.
- [ ] Add sequence/generation metadata sufficient to reject stale snapshots and request authoritative resync.
- [ ] Verify no UI reports `SCANNING` or a detector Active when authoritative service state is stopped/stale.
- [ ] Record metrics and commit `fix(ipc): reconcile authoritative state pass 4`.

### Task 11: Transmission pass 5 — Compose/recomposition end-to-end

**Files:**
- Modify ViewModel/composables identified by recomposition profile.

- [ ] Profile recomposition counts and frame timing under high observation volume.
- [ ] Narrow state collection, use stable presentation models, and avoid shipping full histories into global UI state.
- [ ] Verify map/history/detail screens still update at useful cadence and no evidence is hidden.
- [ ] Record `docs/performance/transmission-pass-5.md` with cumulative transmission results.
- [ ] Commit `perf(ui): complete transmission optimization pass 5`.

### Task 12: Android performance acceptance

- [ ] Build/install release-like sideload build on connected devices.
- [ ] Capture pre/post CPU, memory, frame/gfx data, and Perfetto/Simpleperf evidence using the Android performance workflow.
- [ ] Run identical scanner fixture/live windows before and after; normalize duration and settings.
- [ ] Verify zero unexplained observation loss and all scanner health counters remain coherent.
- [ ] Publish a compact before/after table in `docs/performance/FINAL.md` and commit `docs(perf): publish five-pass performance evidence`.