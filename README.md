# Flock-Sucker

**Open-source, evidence-aware counter-surveillance for Android**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Flipper Zero](https://img.shields.io/badge/Flipper%20Zero-companion-orange.svg)](flipper_app/flock_bridge/README.md)

> **Watch the watchers — but do not confuse a radio observation with proof of intent.**

Flock-Sucker is an Android counter-surveillance and wireless situational-awareness application. It scans multiple local signal domains, classifies observations against known and heuristic patterns, records accepted sightings, exposes detector health and ingress telemetry, correlates repeated observations, and can run local AI analysis on-device.

The project is deliberately irreverent. Its evidence model is not: a match is a **candidate observation with a confidence boundary**, not an accusation that a person or device is surveilling you.

## Source-truth snapshot

| Area | Current behavior |
|---|---|
| Android baseline | `minSdk 26` (Android 8.0+), `compileSdk 37`, `targetSdk 34` |
| Install modes | `sideload`, `system`, `oem` product flavors |
| Primary sensing domains | BLE, Wi-Fi, cellular, satellite/NTN, GNSS, RF, ultrasonic/audio |
| Extra health lane | Rogue-Wi-Fi analysis is tracked separately from the primary seven domains |
| Modeled device classes | **75** `DeviceType` values; this is a classification vocabulary, not 75 independently proven signatures |
| Default detection lanes | BLE, Wi-Fi, cellular, satellite enabled; GNSS, RF and ultrasonic disabled by default |
| Persistent storage | SQLCipher-encrypted Room database; default retention is 3 days |
| Ephemeral mode | Optional RAM-only detection storage; cleared on service restart and relevant purge transitions |
| Local AI | Rule-based fallback, MediaPipe LLM, Gemini Nano where supported, native llama.cpp GGUF |
| Network model | Core radio scanning/classification can operate locally, but the app has explicit optional/scheduled network surfaces described below |

## What the app actually does

Flock-Sucker is built around an observation pipeline rather than a single “scanner.” Raw sensor events enter protocol-specific collectors, become classification candidates, pass detection and suppression logic, receive scoring/context, and—when accepted—become canonical detections plus append-only sighting records. The UI then exposes current detections, history, map views, service health, score breakdowns and optional local-AI interpretation.

The codebase contains signatures and heuristics for surveillance infrastructure, trackers, consumer cameras, law-enforcement technology, suspicious network behavior, radio anomalies and ordinary devices of interest. Some classes have strong identifiers; others are intentionally heuristic. **The class name alone never upgrades weak evidence into a confirmed identification.**

## Seven signal domains

### 1. Bluetooth Low Energy (BLE)

BLE scanning covers named devices, manufacturer data, service UUIDs, tracker families and behavioral persistence. Examples include AirTag/Find My patterns, Tile, Samsung SmartTag, generic BLE trackers, police/public-safety technology, Flipper-related patterns, beacons and selected vehicle-radio signatures.

The tracking logic can consider repeated sightings, time, RSSI, location diversity and changing addresses. A strong behavioral pattern can raise concern, but rotating identifiers, shared manufacturer IDs and noisy RF environments remain false-positive sources.

### 2. Wi-Fi

Wi-Fi analysis combines SSID/BSSID evidence, OUI/manufacturer data, security configuration, mobility/history and anomaly logic. It includes candidate patterns for ALPR/security-camera infrastructure, consumer cameras, drones, Wi-Fi Pineapple-style tooling, rogue/evil-twin access points, deauthentication activity and networks that appear to follow the user.

The **Rogue Wi-Fi** subsystem is exposed as its own proof-of-life/health lane even though it belongs to the Wi-Fi signal domain.

### 3. Cellular

Cellular monitoring evaluates serving-cell/network state and changes that can be relevant to IMSI-catcher or downgrade analysis: generation changes, suspicious parameters, rapid cell changes, stationary handoffs, signal anomalies and trusted/familiar-cell context.

Android exposes materially different cellular information depending on OS version, hardware, carrier, permissions and whether the app is a normal sideload, privileged system app or OEM-integrated build. Flock-Sucker does **not** claim that every device exposes modem-layer evidence sufficient to identify a cell-site simulator.

### 4. Satellite / NTN

Satellite monitoring tracks Android satellite/non-terrestrial-network state where APIs and hardware expose it, plus network-transition indicators used by the app's satellite analysis. Device, modem, carrier and Android support vary significantly.

This lane is enabled by default in detection settings, but an enabled lane is not proof that the handset has usable satellite/NTN telemetry.

### 5. GNSS

GNSS analysis examines satellite measurements and quality indicators for spoofing, jamming and multipath-like behavior. It considers geometry/signal consistency and environmental factors rather than treating a single bad fix as an attack.

GNSS detection defaults **off** because environmental multipath, indoor reception, urban canyons and device-specific GNSS quality can produce substantial false positives.

### 6. RF

The RF lane handles broader radio anomalies such as interference, jamming-like behavior, drone/network indicators and external-radio observations. Useful coverage depends heavily on hardware and privileges; Flipper Zero or other external radio hardware can extend what the Android handset alone can observe.

RF detection defaults **off** in general settings and should not be described as broadband SDR capability on an ordinary phone.

### 7. Ultrasonic / audio

Ultrasonic detection uses microphone input to look for high-frequency beacon-like energy and patterns. It is **explicit opt-in**, defaults off, and requires microphone permission plus user consent.

Audio hardware varies dramatically between phones; sample-rate limits, microphone filtering and environmental sources can all affect results. An ultrasonic hit is an indicator, not proof of a particular advertiser, tracker or surveillance operator.

## Device and signature classes

The current `DeviceType` enum contains **75 modeled classes** spanning:

- ALPR and public-safety infrastructure: Flock Safety, license-plate readers, traffic/speed/red-light systems, toll readers, ShotSpotter/Raven-style acoustic systems and police technology.
- Trackers and beacons: AirTag, Tile, SmartTag, generic BLE trackers, retail trackers and Bluetooth beacons.
- Cameras and smart-home/security devices: Ring, Nest/Google, Wyze, Arlo, Eufy, Blink, SimpliSafe, ADT, Vivint, CCTV/PTZ/thermal/night-vision classes.
- Network/security tooling: rogue APs, Wi-Fi Pineapple, packet-sniffer/MITM classes, Flipper Zero, HackRF/SDR, Proxmark and other security-tool categories.
- Radio/navigation anomalies: RF jammer/interference/anomaly, GNSS spoofer/jammer, ultrasonic beacon and satellite/NTN classes.
- Vehicles: police/emergency, fleet, Tesla and Waymo candidate-radio classes.
- Generic/fallback classes for observations that do not justify a more specific identity.

A `DeviceType` is a **modeling target**. Different handlers reach those targets with different evidence quality: exact service UUIDs, manufacturer data, SSID/OUI combinations, contextual correlations, behavioral heuristics or generic fallback logic. Read the detailed detector documentation before treating two classes as equivalently strong evidence: [Detection system documentation](docs/detections/README.md).

### Tesla and Waymo: explicit evidence boundaries

**Tesla:** the BLE logic contains bounded Tesla vehicle-radio fingerprints, including Tesla-specific service/name evidence. A Tesla-class detection means the observed radio data matched the implemented vehicle-radio criteria. It does **not** establish surveillance, recording, ownership, driver identity, autonomy state, or intent.

**Waymo:** the implemented Waymo path is intentionally more conservative. It recognizes bounded self-identifying BLE naming evidence; there is no project claim of a stable public Waymo UUID that uniquely authenticates a vehicle. Names are spoofable, so this should be treated as a **Waymo candidate-radio observation**, not cryptographic identity.

The same rule applies elsewhere: a Ring OUI/SSID-like pattern can support a Ring candidate, but OUIs, names and network identifiers can be shared, randomized or spoofed.

## Observation funnel, proof-of-life and truthful telemetry

Flock-Sucker distinguishes “the service says it is running” from **observable scanner proof-of-life**.

`DetectorHealthStatus` tracks, per lane:

- running/healthy state and hardware/API availability;
- permission state;
- last start/stop and last successful observation;
- stale-observation threshold;
- raw observations entering the funnel;
- classification candidates;
- accepted persisted sightings;
- rule-suppressed observations;
- throttle drops;
- persistence failures;
- watchdog/restart/error state.

A detector has proof-of-life only when it is running, healthy, hardware-available and has a recent successful observation inside the stale window. BLE ingress accounting also keeps raw callbacks separate from processed work so bounded-queue drops cannot masquerade as “nothing was received.”

The Service Health UI exposes this telemetry so performance work can answer the right question: **did the radio produce data, did the app process it, or did the funnel drop/suppress it?**

## Sighting ledger, history and map

Database v11 introduced an **append-only sighting ledger**. A canonical detection represents the current identity/aggregate record; each accepted new or repeated observation can create a separate `Sighting` row with sequence, timestamp, evidence disposition, detector-health generation and location when location storage is permitted and available.

Important boundaries:

- Existing installations migrating to v11 do not fabricate historical sighting rows from old aggregate `seenCount` values.
- Sightings without coordinates stay without coordinates; the migration does not manufacture map points.
- Ephemeral mode uses the in-memory repository instead of the persistent SQLCipher database.
- Location persistence can be disabled. OEM builds default location storage off; sideload builds default it on.

The History UI filters/sorts canonical detections, while map/detail views can render located observations with OpenStreetMap tiles. Repeated sightings and distinct locations can feed following/persistence analysis, but geographic coincidence is not proof of tracking.

## Threat scoring and false-positive handling

The principal scoring engine calculates:

```text
threat_score = base_likelihood × impact_factor × confidence
```

and clamps/adjusts the result to `0..100`.

| Score | Level | Meaning in the scoring contract |
|---:|---|---|
| 90–100 | CRITICAL | strongest implemented threat evidence; immediate review |
| 70–89 | HIGH | high-probability concern; investigate |
| 50–69 | MEDIUM | moderate concern; monitor/corroborate |
| 30–49 | LOW | possible concern; log/watch |
| 0–29 | INFO | notable observation; not treated as threatening |

Confidence is affected by signal quality, persistence, multiple indicators, cross-protocol correlation, match quality, known false-positive patterns, consumer-device context and environment. A separate false-positive analyzer can downgrade confidence in low/informational observations.

A score is an **application assessment**, not a forensic probability, legal conclusion or proof that surveillance occurred. Pattern tables and AI descriptions can contain hypotheses that require independent corroboration.

## Shodan research action

Detection detail views can build a Shodan search query from evidence already visible to the user—such as manufacturer, device name/type, protocol, MAC/BSSID-like identifier, SSID and service UUIDs—and open that query in the **external browser**.

This is explicit/user-initiated research. Flock-Sucker does not treat a local radio identifier as a routable Internet host, does not silently query Shodan for every detection, and does not mutate the original scanner evidence based on the browser action.

## Local AI and native GGUF

AI is an interpretation layer above the detection evidence, not the authority that creates radio observations.

The current engine manager supports:

- **Rule-based analysis** as the non-model fallback.
- **MediaPipe LLM** for compatible `.task`/`.bin` local models.
- **Gemini Nano / ML Kit GenAI** where Android hardware/software support it; managed model components may be provisioned by Google/Android services.
- **Native llama.cpp** through the bundled `llamaandroid` module for **GGUF** models.

The native GGUF path is real runtime support, not a filename badge. The project pins llama.cpp as a submodule and exposes a `LocalLlmEngine` lifecycle with load/generate/unload/health semantics. A GGUF artifact is **not READY merely because the file exists**: readiness requires llama.cpp to load it and complete the engine self-test/inference path.

The current Inversion Labs fine-tuned GGUF artifact is import-first when no direct resumable app-download URL is configured; users can import the hash-pinned artifact through the model workflow.

## Flipper Zero integration

`flipper_app/flock_bridge` is the companion Flipper Zero application. On the Android side, Flipper observations are represented as distinct detection sources (BLE, Wi-Fi, Sub-GHz, IR, NFC and WIPS-related paths).

The companion project includes passive scanning plus **active security-research/probing capabilities**. Active transmission is not required for Flock-Sucker's ordinary Android passive scanning and must only be used on systems/radio environments you are authorized to test. Spectrum/transmission rules vary by jurisdiction.

The Flipper build uses `ufbt`; if that tool is absent, the optional FAP bundling step can be skipped while Android builds still succeed. See [Flock Bridge](flipper_app/flock_bridge/README.md).

## Privacy, storage and deletion semantics

### Local processing

Core scan ingestion, classification, scoring, history storage and supported local-model inference are implemented on-device. There is no required cloud LLM backend for detection analysis.

That is **not the same as “the app never uses the network.”** See the explicit network disclosure below.

### Persistent mode

Persistent detection data is stored in Room backed by SQLCipher 4.x. The database-page encryption semantics are SQLCipher's **AES-256-CBC with per-page HMAC integrity**, not AES-GCM.

The 256-bit database passphrase is separately wrapped using an Android Keystore **AES/GCM** key. Those are two different cryptographic layers and should not be conflated.

Default history retention is **3 days**, with selectable 4-hour, 1-day, 3-day, 7-day and 30-day policies. A WorkManager retention task removes records older than the configured period.

### Ephemeral mode

Ephemeral mode defaults off. When enabled, detection storage is routed to an in-memory repository rather than the persistent detections database. The in-memory set is cleared on service restart and relevant mode/purge transitions. Cellular and Flipper paths also contain explicit ephemeral-mode persistence gates.

“RAM-only” does not mean a formal anti-forensics guarantee about the entire Android operating system, swap behavior, logs, screenshots, backups, exported files or unrelated platform telemetry.

### Quick wipe, duress and Nuke Manager

The strongest defensible deletion property is **crypto-erasure**: destruction of the SQLCipher key material needed to decrypt the database. Nuke/secure-delete code can also overwrite and delete files, but modern eMMC/UFS/flash storage uses wear leveling; multi-pass logical overwrite cannot guarantee that every historical physical block is destroyed.

Therefore the project treats overwrite/delete as a **best-effort complement**, not a physical-erasure guarantee.

Nuke infrastructure supports multiple configurable triggers (for example manual actions, duress/failed-auth/dead-man and selected device/network conditions), but trigger availability and defaults vary. Do not assume every destructive trigger is armed on a fresh install.

## Explicit network disclosure

Flock-Sucker requests `INTERNET` because some features use external services. Core radio scanning does not require a cloud service, but the shipped app is **not a zero-network application**.

| Surface | Typical trigger | What leaves the device |
|---|---|---|
| OpenStreetMap tiles | Opening/rendering online map views | Tile coordinates/HTTP metadata to configured OSM tile servers |
| IEEE OUI database | Bundled, SHA-256-verified snapshot; refresh is an explicit maintainer action | OUI CSV request to IEEE only when a maintainer deliberately refreshes the snapshot |
| Local model acquisition | User/model workflow when a downloadable artifact is configured | Model download request |
| Gemini Nano managed components | Platform/Google provisioning where supported | Managed by Android/Google service stack, not a Flock-Sucker cloud LLM API |
| Tor status / exit-IP test | User/network privacy diagnostics | Request to configured Tor-check endpoint |
| IP lookup | Network diagnostics/features that invoke it | Public IP/network request to configured lookup endpoint |
| DNS/network RTT checks | Satellite/network diagnostics | Requests to configured Cloudflare/Google/OpenDNS endpoints |
| Shodan | Explicit “Search Shodan” action | Browser navigation containing the generated search keywords |
| Source/issues/releases | User opens project links | Normal browser/GitHub traffic |

### Tor boundary

Tor proxying defaults **off**. When enabled and a compatible Orbot SOCKS proxy is available, the Tor-aware HTTP client can route supported requests through it. **If Tor is enabled but Orbot is unavailable/not running, current code can fall back to a direct connection.** Do not treat the Tor toggle as a fail-closed anonymity guarantee.

No documentation should claim “No Network Calls,” “zero cloud connectivity,” or equivalent absolute language for the current application.

## Build variants and privilege boundaries

Flock-Sucker has three install-mode flavors:

| Flavor | Intended installation | Boundary |
|---|---|---|
| `sideload` | Normal APK installation | Android runtime permissions and ordinary app sandbox/API limits |
| `system` | Privileged `/system`/`system_ext` integration | Can request/use privileged capabilities only when the ROM, permission allowlist, signing and Android version actually grant them |
| `oem` | Platform/OEM integration | Can be built with OEM package/feature configuration and platform integration; capabilities still depend on the target ROM/framework/modem and granted permissions |

The manifests include privileged permission declarations used by system/OEM builds, but a declaration in an APK is **not proof the OS grants it**. Normal sideload installs do not magically gain privileged phone state, raw modem access, hidden APIs or stable hardware identifiers.

Build examples:

```bash
./gradlew assembleSideloadDebug
./gradlew assembleSystemDebug
./gradlew assembleOemDebug
```

For integration details and the permission-whitelist templates, see [OEM integration](OEM_INTEGRATION.md) and [OEM integration guide](docs/OEM_INTEGRATION_GUIDE.md).

## Device-specific optimization and performance evidence

Performance work in this project follows an evidence-first rule: distinguish raw radio ingress from app processing, establish scanner proof-of-life, keep device-specific tuning isolated, and benchmark equivalent workloads before claiming an optimization.

The three canonical engineering references are:

1. **[Device-Specific Android Optimization Playbook](docs/agent-workflows/DEVICE_SPECIFIC_ANDROID_OPTIMIZATION_PLAYBOOK.md)** — the detailed process for profiling a real Android target, separating portable changes from hardware-specific tuning, building comparable artifacts and validating on-device behavior.
2. **[MAXSTATS Android Performance Evidence Gate](docs/agent-workflows/MAXSTATS_ANDROID_PERF_EVIDENCE.md)** — evidence requirements for accepting performance claims instead of relying on subjective “feels faster” reports.
3. **[Engine / Transmission Performance Plan](docs/superpowers/plans/2026-08-28-engine-transmission-performance.md)** — the end-to-end engine/transmission workflow used to reason about scanner throughput, queues, UI pressure and device-specific bottlenecks.

Device-specialized branches are not automatically merged into general `main`. Portable improvements are extracted and verified independently; hardware policy remains device-specific unless evidence shows it is a safe general default.

## Known limitations and false-positive boundaries

- RF names, SSIDs, MAC/OUI data, BLE names and service advertisements can be spoofed, randomized, shared or incomplete.
- Android scan throttling, background limits, vendor firmware, permissions and power-management policy can create blind spots.
- Signal strength is not a reliable distance measurement by itself; body loss, antenna orientation, multipath and transmit power matter.
- GNSS anomalies frequently have benign environmental explanations.
- Cellular APIs expose only a subset of modem/network state on many sideloaded devices.
- Consumer camera/security vendor identifiers can identify a vendor ecosystem without proving a specific camera is recording you.
- Tesla/Waymo matches identify candidate vehicle-radio evidence under the implemented rules; they do not establish surveillance behavior.
- Repeated co-location supports a following hypothesis but does not establish causation or intent.
- Threat scores and AI output are decision aids. Preserve raw evidence and independently corroborate consequential claims.
- Map coordinates exist only when Android provides location and the user allows storage; absence of a point is not absence of a detection.
- Local AI availability depends on model format, device RAM/CPU/GPU/NPU support and runtime self-tests.
- Flipper active probes and radio transmissions require authorization and may be regulated.

## Project persona and `BOLO MILF`

Flock-Sucker has always used an intentionally abrasive, anti-bureaucratic personality to keep the project from sounding like the surveillance systems it audits. The jokes are allowed to be stupid; the evidence ledger is not.

The creator reports **`BOLO MILF`** as an internal adversarial-QA/audit codename/persona from the project's development history. During this documentation pass we searched the current repository, Git history/refs, GitHub-visible code/issues, retained Obsidian audit/session material, Spotlight-indexed local files, OpenAI local application state and available Pieces storage. **No primary artifact containing the exact or near phrase was recovered.**

Accordingly, this README records `BOLO MILF` as **creator-reported project lore with unverified primary provenance**, not as a verified historical test artifact and not as evidence for any detection claim. If the original screenshot/log/audit artifact is recovered, it should be committed or linked with provenance rather than retroactively inventing a citation.

That distinction is intentional: an evidence project should be willing to say “we remember this, but cannot currently prove it.”

## Installation

### Requirements

- Android 8.0 / API 26 or newer.
- Hardware/permissions appropriate to the signal domains you want to use.
- Microphone permission and explicit consent for ultrasonic detection.
- Optional Flipper Zero for extended radio workflows.
- Optional compatible local model for MediaPipe/llama.cpp AI features.

### Build from source

```bash
git clone --recurse-submodules https://github.com/knowurknottty/Flock-Sucker.git
cd Flock-Sucker
./gradlew :app:assembleSideloadDebug
```

The llama.cpp dependency is pinned as a Git submodule, so use `--recurse-submodules` or run `git submodule update --init --recursive` after cloning.

## Testing

The converged toolchain is Gradle 9.3.1, AGP 9.1.1, Kotlin/Compose 2.4.10 and KSP 2.3.10. Common local gates include:

```bash
./gradlew :app:testSideloadDebugUnitTest :app:assembleSideloadDebug
./gradlew :app:testSystemDebugUnitTest :app:assembleSystemDebug
./gradlew :app:testOemDebugUnitTest :app:assembleOemDebug
```

Release Kotlin compilation is also exercised during convergence work. Flipper FAP packaging is a separate optional toolchain and requires `ufbt`.

## Documentation

- [Detection system master index](docs/detections/README.md)
- [Satellite monitoring](docs/SATELLITE_MONITORING.md)
- [OEM integration](OEM_INTEGRATION.md)
- [OEM integration guide](docs/OEM_INTEGRATION_GUIDE.md)
- [Flock Bridge / Flipper Zero](flipper_app/flock_bridge/README.md)
- [Device-Specific Android Optimization Playbook](docs/agent-workflows/DEVICE_SPECIFIC_ANDROID_OPTIMIZATION_PLAYBOOK.md)
- [MAXSTATS Android Performance Evidence Gate](docs/agent-workflows/MAXSTATS_ANDROID_PERF_EVIDENCE.md)
- [Engine / Transmission Performance Plan](docs/superpowers/plans/2026-08-28-engine-transmission-performance.md)

## Contributing

Start with [CONTRIBUTING.md](CONTRIBUTING.md). For detection changes, include the evidence basis, expected false-positive modes and tests. For performance changes, identify whether the change is portable or device-specific and follow the evidence gate above.

When adding a signature, prefer the weakest defensible claim. “Observed identifier X” is better than “confirmed surveillance device” when X is spoofable or shared.

## Legal and safety boundary

Flock-Sucker is intended for personal privacy awareness, authorized security research and education. Passive observation is not the same as authorization to transmit, probe, interfere with, access or disrupt another system. Active Flipper/security-testing functions should be used only where you have permission and where radio/computer-access laws allow them.

The application can be wrong. Do not use a detection, threat score, map point or AI-generated explanation as the sole basis for confronting a person, interfering with infrastructure, making a legal allegation, or taking a safety-critical action.

## License provenance status

**Do not infer a license from the historical badge.** The current Flock-Sucker tree contains no root `LICENSE` file. The currently accessible upstream `MaxwellDPS/Flock-You-Android` default branch also has no `LICENSE` file even though its README advertises MIT, and GitHub does not identify an upstream license.

Until the authoritative upstream license and derivative obligations are reconstructed and recorded with an immutable source/commit, redistribution should be treated as **license-provenance unresolved**. The repository already contains a provenance workstream for that reconstruction; a future verified license file should replace this notice rather than guessing legal terms.

## Acknowledgments

- [Flipper Zero](https://flipperzero.one) and the `ufbt`/Flipper developer ecosystem.
- [SQLCipher](https://www.zetetic.net/sqlcipher/) for encrypted SQLite storage.
- [llama.cpp](https://github.com/ggml-org/llama.cpp) for the native GGUF runtime integrated through the pinned submodule.
- [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) for map tiles/data attribution.
- The open-source privacy and security research community.

---

**Flock-Sucker: Watch the watchers. Record what you actually observed. Say what you cannot prove.**

[Report a bug](https://github.com/knowurknottty/Flock-Sucker/issues) · [Request a feature](https://github.com/knowurknottty/Flock-Sucker/issues) · [Contribute](CONTRIBUTING.md)
