# Contributing to Flock-Sucker

Thanks for contributing. Flock-Sucker is an evidence-oriented counter-surveillance project, so changes should be explicit about **what was observed, how it was classified, and what can still be a false positive**.

## Development setup

### Prerequisites

- JDK 17
- Android SDK Platform 37
- Git with submodule support
- Android Studio compatible with the repository's AGP/Kotlin toolchain, or the checked-in Gradle wrapper
- Optional: `ufbt` for building the Flipper Zero companion FAP

The repository currently pins Gradle 9.3.1, Android Gradle Plugin 9.1.1, Kotlin/Compose 2.4.10 and KSP 2.3.10. Use the wrapper instead of a separately installed Gradle version.

### Clone

```bash
git clone --recurse-submodules https://github.com/knowurknottty/Flock-Sucker.git
cd Flock-Sucker
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

No Google Maps API key is required: current map screens use OpenStreetMap/osmdroid. See the README's network disclosure before adding any new external service.

## Build variants

```bash
./gradlew :app:assembleSideloadDebug
./gradlew :app:assembleSystemDebug
./gradlew :app:assembleOemDebug
```

- `sideload` is the normal APK flavor.
- `system` is intended for privileged ROM integration; requested privileged permissions still require ROM allowlisting/signing support.
- `oem` is intended for platform/OEM integration and can use OEM build properties. Platform signing does not, by itself, guarantee every hidden/modem capability on every Android target.

See [OEM_INTEGRATION.md](OEM_INTEGRATION.md) and [docs/OEM_INTEGRATION_GUIDE.md](docs/OEM_INTEGRATION_GUIDE.md).

## Verification gates

At minimum, run the tests and build for the flavor you changed. Portable scanner/runtime changes should normally pass all three debug variants:

```bash
./gradlew :app:testSideloadDebugUnitTest :app:assembleSideloadDebug
./gradlew :app:testSystemDebugUnitTest :app:assembleSystemDebug
./gradlew :app:testOemDebugUnitTest :app:assembleOemDebug
```

For focused work, run the narrowest relevant test first, then broaden before opening the PR. Use `git diff --check` for documentation/configuration changes.

## Detection and evidence changes

A new signature or heuristic should include:

1. The evidence source or technical rationale.
2. The exact observable being matched: UUID, manufacturer data, OUI, SSID, behavior, API state, etc.
3. Expected false positives and spoofability.
4. The weakest defensible device/classification claim.
5. Unit or fixture coverage for positive and negative cases.
6. Documentation when the public capability/boundary changes.

Do not convert “identifier resembles vendor X” into “confirmed surveillance by vendor X.” Vehicle-radio detections, consumer-camera OUIs, SSIDs and BLE names are especially important places to preserve that boundary.

## Performance changes

Classify performance work as **portable** or **device-specific**. Device tuning should not be merged into general defaults merely because it helped one handset.

Use the canonical process documents:

- [Device-Specific Android Optimization Playbook](docs/agent-workflows/DEVICE_SPECIFIC_ANDROID_OPTIMIZATION_PLAYBOOK.md)
- [MAXSTATS Android Performance Evidence Gate](docs/agent-workflows/MAXSTATS_ANDROID_PERF_EVIDENCE.md)
- [Engine / Transmission Performance Plan](docs/superpowers/plans/2026-08-28-engine-transmission-performance.md)

Scanner-performance claims should use the proof-of-life/ingress telemetry when applicable: raw observations, candidates, accepted sightings, suppressions, throttle drops and persistence failures.

## Privacy and network changes

Core detection and supported local-AI analysis are on-device, but the application intentionally has network-capable surfaces (for example OSM tiles, explicit maintainer-triggered IEEE OUI refresh, model acquisition, Tor/IP/DNS diagnostics and explicit Shodan browser searches).

Any PR adding or changing networking must document:

- destination/service;
- trigger (automatic, scheduled, UI-driven or explicit user action);
- data included in the request;
- Tor/proxy behavior and fallback semantics;
- failure behavior;
- user-facing disclosure if materially changed.

Do not introduce blanket claims such as “No Network Calls.”

## Database and schema changes

Room schema export is enabled. If the schema version changes, include the generated schema JSON and migration/test evidence. Do not fabricate history during migrations; the v11 sighting-ledger migration is the reference example for preserving that rule.

Persistent database pages are protected by SQLCipher (AES-256-CBC + per-page HMAC); the database passphrase wrapper uses Android Keystore AES/GCM. Keep those layers distinct in code and documentation.

## Local AI changes

The supported analysis stack includes rule-based fallback, MediaPipe models, Gemini Nano where the Android device supports it, and native llama.cpp GGUF.

For GGUF, file compatibility is not runtime readiness. Tests/docs must preserve the rule that READY requires a successful load/self-test/inference path.

## Flipper Zero changes

The companion application lives at `flipper_app/flock_bridge`. Passive scanner work and active probing are different risk surfaces. Active transmissions/probes must be documented as authorization-required security research and should not be presented as necessary for ordinary Android passive detection.

## Pull requests

Keep commits focused. A useful PR description includes:

- what changed;
- why the evidence supports it;
- verification commands/results;
- known limitations or unverified hardware behavior;
- whether the change is portable, device-specific, privileged-build-only or optional-hardware-dependent.

Suggested commit prefixes: `feat`, `fix`, `docs`, `refactor`, `test`, `perf`, `chore`, `ci`.

## Releases and signing

GitHub Actions contains the canonical CI/release workflows. Release signing uses repository secrets; do not commit keystores, private keys or passwords. Inspect `.github/workflows/` before changing release instructions because workflow inputs can evolve.

## Questions and issues

Use [GitHub Issues](https://github.com/knowurknottty/Flock-Sucker/issues) for reproducible bugs, feature proposals and evidence discussions.
