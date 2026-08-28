# Flock-Sucker Evidence, Sensor, Performance, and Local AI Design

## Purpose

Flock-Sucker is an operator-grade counter-surveillance instrument. It should expose as much observable device/radio evidence as Android and attached hardware legitimately provide, preserve provenance, explain every classification, and make repeated observations auditable in text and on a map.

The product voice remains Flock-Sucker throughout: half mil-spec instrument panel, half Fallout field terminal; serious tools, terse attitude, no fake capabilities.

## Baseline

Implementation baseline: `knowurknottty/Flock-Sucker` `main` at `d03b8c0cd154b5cf11a382b9fffe484e838504a8`.

The app already contains BLE, Wi-Fi, cellular, NTN/satellite, GNSS, RF, ultrasonic/audio, Flipper, detection handlers, deduplication, encrypted persistence, IPC, mapping, and AI settings. The current gaps are observability, event-level history, counter semantics, scanner proof-of-life, local AI execution, and performance/state consistency.

## Global Requirements

1. Evidence first: preserve raw observation and inference separately.
2. No fake support: a scanner/model/runtime is `READY` only after a real functional probe succeeds.
3. Minimal third-party dependencies: prefer Android platform APIs, Room, existing Compose stack, and pinned native components.
4. High/critical verbosity: higher threat level increases visible technical detail and provenance, never lowers evidence thresholds.
5. Privacy-preserving: capture only data Android or explicitly attached hardware exposes; no unauthorized access to remote devices.
6. Rebrand consistency: user-visible copy, docs, notifications, and status text use Flock-Sucker except migration identifiers that must remain stable.
7. Measured performance: five optimization passes means five measure→change→verify iterations, not an arbitrary promised multiplier.
8. Device acceptance: Android hardware is authoritative for scanners, audio, GNSS, map, IPC, and local inference.

## Canonical Device + Append-Only Sighting Ledger

`Detection` remains the canonical device/identity summary. Add append-only `Sighting` rows for accepted observations.

Each sighting stores: ID, detection ID, timestamp, monotonic sequence, protocol, source scanner, detector-health generation, RSSI/signal metrics, location/accuracy when permitted, matched rule IDs, confidence, raw observable metadata or digest, disposition (`accepted_repeat`, `new_device`, `throttled`, `suppressed`, `persistence_failed`), and provenance needed to reconstruct the decision.

`Detection.seenCount`, first/last seen, latest RSSI/location, and active state are summary fields derived from sightings. Existing installs retain aggregate counts without fabricating historical points.

## Counts and Scan Funnel

Home status shows separate counters:
- `UNIQUE DEVICES`
- `SIGHTINGS`
- `HIGH THREAT`

Advanced service health exposes per-scanner funnel metrics:
`raw observations → candidates → explicit suppressions → throttle drops → accepted repeats → new devices → persistence failures`.

A stable unique-device count must never imply scanning is idle when sightings are increasing.

## Auditable Device History

Any detection with more than one sighting exposes `EVIDENCE HISTORY` with every timestamped observation, location/accuracy, signal trend, method/rules, manufacturer/OUI/service UUID evidence, threat classification and exact reasons, scanner/source health, and expandable raw metadata.

High/critical detections default to expanded provenance and reasoning.

## Device-Specific Map Overlay

`MAP HISTORY` opens the existing map in a device-scoped overlay mode: one point per located sighting, chronological numbering, timestamps, optional chronological path, visible legend, first/last badges, and tap-through to the exact sighting evidence drawer. The app never invents coordinates for sightings without location.

## Scanner Correctness Contract

Every scanner reports: configured/enabled, permission state, hardware/API availability, running state, last start/stop reason, last observation time, raw observation count, candidate count, accepted sighting count, last error, failure count, restart count, stale threshold, and watchdog state.

Required lanes: BLE, Wi-Fi, cellular, NTN/satellite, GNSS, RF, ultrasonic/audio, Flipper/external inputs.

A field-validation release gate must prove that a radio-rich route produces raw BLE/Wi-Fi observations even if zero observations classify as threats.

## GNSS Explanation Layer

Every visible GNSS satellite becomes an inspectable evidence object.

Current signal-color thresholds are made explicit with a visible legend:
- green: C/N0 ≥ 40 dB-Hz
- yellow: 25–39.9 dB-Hz
- red: <25 dB-Hz

Red means weak signal quality, not malicious behavior.

Each satellite explains: constellation name/code, SVID meaning, C/N0 and color reason, used-in-fix state, elevation, azimuth, plain-language sky direction, optional sky-plot position, ephemeris/almanac availability and meaning, carrier frequency and band/signal name when derivable, raw-measurement availability, pseudorange/carrier-phase/Doppler/multipath fields when Android exposes them, anomaly flags with exact triggering rule/threshold, and current fix contribution.

Signal quality, geometry quality, and threat/anomaly classification must be visually distinct.

## Audio/Ultrasonic Proof of Life

The ultrasonic screen becomes a real instrument panel. While enabled it shows permission/input state, sample rate, channels, encoding, buffers and FFT windows processed, windows/sec, last-buffer age, dropped/overrun count, live noise floor, peak frequency/amplitude, detector state, last error, and last successful analysis time.

A deterministic self-test injects a synthetic ultrasonic tone directly into the analyzer to prove FFT/classification wiring. Optional acoustic loopback may use speaker/mic only when explicitly invoked. Raw conversation audio is never persisted.

## Five-Pass Engine Optimization

Profile scanner callbacks, channels/queues, handlers, deduplication, classifier rules, location enrichment, persistence, correlation, RF/GNSS/audio DSP, and AI invocation boundaries.

Each of five iterations records p50/p95 observation→classification latency, p50/p95 observation→persistence latency, CPU time, allocations/GC, queue depth/drop count, scanner duty metrics where available, and correctness/event-loss counters. Each pass attacks the dominant measured bottleneck and reruns correctness gates before the next pass.

## Five-Pass Transmission Optimization

`Transmission` here means internal state/data movement:
`scanner → service state → serialization → Binder/Messenger IPC → repository/Room → ViewModel → Compose`.

Five measured passes target event ordering, appropriate delivery semantics, delta/coalesced updates, bounded payloads, backpressure observability, duplicate serialization removal, stale-state recovery, recomposition reduction, and efficient high-frequency counters separated from large payload refreshes.

## Real On-Device AI

The current MediaPipe path is not considered supported because setup/download never produced working inference.

Add a `LocalLlmEngine` abstraction with a real GGUF implementation backed by pinned llama.cpp Android/NDK code.

Initial model:
- ID: `gemma-flock-q8-0`
- artifact: `gemma-mlx-probe-fused-q8_0.gguf`
- URL: `https://mega.nz/file/WzAiwIba#-lYBgLIkxmAgzmd_CXcKEjMIhuuYlvpfWFUeVXMnxlc`
- size: `291545376`
- SHA-256: `82b323bf05eba698b87a39d1eca8ea31506222aff25b415f6388135069725b57`

Lifecycle: `NOT_INSTALLED → DOWNLOADING → VERIFYING → INSTALLED → LOADING → READY → ERROR`.

`READY` requires a real inference smoke test. UI supports download/progress, hash verification, load, prompt, streamed tokens, cancellation, unload/reload, and restart persistence. CPU/NEON arm64 is mandatory first; Vulkan is optional after correctness/profiling.

Dead MediaPipe entries are removed from the supported catalog unless a genuine end-to-end probe becomes green.

## Deterministic Test Fixtures

Provide test-only fixtures for 500/1,000+ canonical detections, multiple sightings across locations, BLE/Wi-Fi candidate/noncandidate mixtures, scanner drop/backpressure stress, GNSS snapshots across constellations/CN0 bands, synthetic ultrasonic FFT input, IPC burst/delta sequences, and local LLM load/inference cancellation.

Synthetic data is visibly test-only and never masquerades as live observation data.

## Five-Reviewer Code Review Convergence

After CAPT's proposal-dispatch blocker is repaired, run five independent reviews against one pinned Flock-Sucker SHA: GLM-5.3-Flash A, GLM-5.3-Flash B, HY3, Mimo 2.5, and Sol.

Review correctness, security/privacy, detection coverage, dependency reduction, performance, IPC/state consistency, UX/auditability, test gaps, rebrand consistency, and one novel feature from each reviewer. Rank convergence findings by severity, confidence, independent agreement, and device evidence.

## Acceptance

A release candidate is accepted only when every enabled scanner has visible proof-of-life; live sightings rise on real observations; repeated sightings are auditable in text/map; GNSS colors/names/positions are explained; audio visibly proves sample/DSP activity; local Gemma downloads, verifies, loads and produces tokens on-device; five engine and five transmission benchmark passes are recorded; Flock-Sucker branding is consistent; and Android device QA passes without relying on synthetic data for live claims.