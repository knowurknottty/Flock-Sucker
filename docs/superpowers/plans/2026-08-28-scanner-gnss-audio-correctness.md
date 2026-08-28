# Scanner, GNSS, and Audio Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove every scanner is actually working, make GNSS satellite data fully explainable, and make ultrasonic/audio processing visibly auditable.

**Architecture:** Standardize scanner health on one observable contract carried through the existing scanning-service IPC. Keep sensor-specific payloads separate, but expose common proof-of-life counters and timestamps. Build GNSS and audio explanation layers from real platform measurements rather than decorative status.

**Tech Stack:** Android Bluetooth/Wi-Fi/Telephony/GNSS/AudioRecord APIs, Kotlin coroutines, Messenger/Binder IPC, Compose.

**Spec:** `docs/superpowers/specs/2026-08-28-flock-sucker-evidence-performance-ai-design.md`

## Global Constraints

- Global `SCANNING` never substitutes for subsystem health.
- A red GNSS satellite means weak C/N0 unless a separate anomaly rule says otherwise.
- Audio proof-of-life must not persist raw conversation audio.
- Field-validation claims require physical-device evidence.

---

### Task 1: Standardize scanner health

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceModels.kt`
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceState.kt`
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceIpc.kt`
- Modify: `app/src/main/java/com/flockyou/service/ScanningServiceBroadcaster.kt`
- Create: `app/src/test/java/com/flockyou/service/DetectorHealthContractTest.kt`

**Interfaces:**
- Produces: extended `DetectorHealthStatus` fields for permission/API/running/proof-of-life counters and last observation/start/stop/error metadata.

- [ ] Write failing serialization tests for BLE, Wi-Fi, cellular, GNSS, NTN, RF, ultrasonic, and Flipper health snapshots.
- [ ] Run targeted tests and confirm current health data lacks sufficient proof-of-life fields.
- [ ] Extend the model without removing existing fields, update Gson IPC payload compatibility, and add helpers for observation/start/stop/error transitions.
- [ ] Re-run tests and verify old JSON snapshots still deserialize with safe defaults.
- [ ] Commit `feat(health): standardize scanner proof-of-life contract`.

### Task 2: Instrument BLE and Wi-Fi raw observation paths

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/ScanningService.kt`
- Modify: `app/src/main/java/com/flockyou/service/ScanStatisticsFunnel.kt`
- Modify BLE/Wi-Fi handler files under `app/src/main/java/com/flockyou/detection/handler/`
- Create: `app/src/test/java/com/flockyou/service/ScannerObservationFunnelTest.kt`

- [ ] Write failing tests proving every raw callback increments raw-observation metrics before classification, and accepted repeats increment sighting metrics separately from unique detection creation.
- [ ] Run tests and reproduce the current semantic gap where visible totals can remain fixed during active scanning.
- [ ] Instrument callback entry, candidate decision, suppression/throttle, repeat/new result, and persistence failure with monotonic counters.
- [ ] Verify no expensive JSON/log formatting occurs on every callback in release builds.
- [ ] Commit `fix(scan): make ble and wifi activity independently observable`.

### Task 3: Audit/start-stop every remaining scanner

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/SubsystemManager.kt`
- Modify corresponding monitor/analyzer classes for cellular, satellite, GNSS, RF, ultrasonic, and Flipper integration.
- Create: `app/src/test/java/com/flockyou/service/SubsystemLifecycleTest.kt`

- [ ] Write table-driven lifecycle tests asserting each enabled scanner reports `starting → active` only after its real API callback/registration succeeds, and `error/permission denied/disabled` otherwise.
- [ ] Run tests and identify any subsystem that marks Active optimistically before successful registration.
- [ ] Correct lifecycle transitions and watchdog timestamps per scanner.
- [ ] Add stale detection: a nominally active scanner with no expected callback within its threshold becomes degraded/stale with a reason.
- [ ] Commit `fix(scan): make subsystem status reflect real sensor state`.

### Task 4: Make every GNSS satellite explain itself

**Files:**
- Modify: `app/src/main/java/com/flockyou/monitoring/GnssSatelliteMonitor.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/SatelliteDetectionScreen.kt`
- Create: `app/src/main/java/com/flockyou/ui/screens/GnssSatelliteExplanation.kt`
- Create: `app/src/test/java/com/flockyou/ui/GnssSatelliteExplanationTest.kt`

**Interfaces:**
- Produces: pure `GnssSatelliteExplanation.from(SatelliteInfo, anomalies)` returning signal band, color reason, sky direction, fix role, ephemeris/almanac explanation, and anomaly evidence.

- [ ] Write failing pure tests for C/N0 thresholds: 40→green, 25→yellow, 24.9→red; verify the explanation explicitly says signal quality rather than threat.
- [ ] Add tests mapping azimuth to plain-language direction and elevation to horizon/low/mid/high/overhead sky position.
- [ ] Add tests for GPS/Galileo/GLONASS/BeiDou/QZSS/SBAS constellation labels and SVID formatting.
- [ ] Implement expandable satellite cards showing C/N0, exact color rationale, used-in-fix, elevation/azimuth, frequency/band when available, ephemeris/almanac, raw-measurement support, and separate anomaly evidence.
- [ ] Add a visible legend and optional simple polar sky plot using azimuth/elevation; no third-party plotting library.
- [ ] Run tests and commit `feat(gnss): explain satellite identity geometry and signal state`.

### Task 5: Expose raw GNSS measurement detail when Android supplies it

**Files:**
- Modify: `app/src/main/java/com/flockyou/monitoring/GnssSatelliteMonitor.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/SatelliteDetectionScreen.kt`
- Create: `app/src/test/java/com/flockyou/monitoring/GnssMeasurementProjectionTest.kt`

- [ ] Write failing projection tests for carrier frequency, pseudorange availability, carrier phase, Doppler/pseudorange-rate, multipath indicator, clock bias/drift, DOP, and fix accuracy.
- [ ] Preserve `unknown/not exposed` distinctly from numeric zero.
- [ ] Project only fields actually supplied by Android APIs; do not infer satellite orbital location from SVID alone.
- [ ] Add per-field help text explaining units and interpretation.
- [ ] Commit `feat(gnss): expose platform raw measurement evidence`.

### Task 6: Add audio/ultrasonic proof-of-life telemetry

**Files:**
- Modify: `app/src/main/java/com/flockyou/service/UltrasonicDetector.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/UltrasonicDetectionScreen.kt`
- Create: `app/src/test/java/com/flockyou/service/UltrasonicTelemetryTest.kt`

- [ ] Write failing tests for sample count, FFT-window count, last-buffer timestamp, dropped-buffer count, sample rate/channel/encoding, noise floor, peak frequency/amplitude, and last-success/error state.
- [ ] Instrument the existing AudioRecord/DSP loop using counters/timestamps that do not allocate per sample.
- [ ] Render those values continuously so an operator can prove data is flowing even when no beacon is detected.
- [ ] Add an explicit permission/input-source failure message rather than a generic idle screen.
- [ ] Commit `feat(audio): expose ultrasonic analyzer proof of life`.

### Task 7: Add deterministic ultrasonic self-test

**Files:**
- Create: `app/src/main/java/com/flockyou/service/UltrasonicSelfTest.kt`
- Create: `app/src/test/java/com/flockyou/service/UltrasonicSelfTestTest.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/UltrasonicDetectionScreen.kt`

- [ ] Write a failing test that synthesizes a PCM sine at a supported ultrasonic test frequency, passes it directly to the analyzer, and expects the peak detector within tolerance.
- [ ] Implement the synthesis fixture in test/debug-safe code without routing through the microphone.
- [ ] Add `RUN DSP SELF-TEST` in Advanced mode with PASS/FAIL, measured peak, duration, and analyzer version.
- [ ] Confirm self-test output is clearly marked synthetic and never becomes a live detection.
- [ ] Commit `test(audio): add deterministic ultrasonic dsp self-test`.

### Task 8: Android field acceptance

**Files:**
- Add tests under `app/src/androidTest/java/com/flockyou/`
- Store dogfood evidence under the repo's existing QA/debug evidence convention.

- [ ] Build/install `sideloadDebug` on connected phone and tablet where available.
- [ ] Capture `adb shell dumpsys package`, permission state, and service state before scan.
- [ ] Run a real scan and verify BLE/Wi-Fi raw observation counters increase in a normal radio environment even if threat detections remain zero.
- [ ] Open GNSS and verify visible satellite rows explain name/SVID, color threshold, C/N0, elevation, azimuth, and fix participation.
- [ ] Open Ultrasonic and verify buffers/windows/sec/last-buffer age move while scanning; run DSP self-test and capture PASS.
- [ ] Save screenshots, UI hierarchy, filtered logcat, and detector-health snapshot; commit `test(android): prove scanner gnss and audio health`.