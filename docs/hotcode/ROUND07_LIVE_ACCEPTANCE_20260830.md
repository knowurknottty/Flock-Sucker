# Round 07 Live Acceptance — 2026-08-30

## Scope

Final live acceptance of the R7 privileged scanning hot path on the Moto G Power (2022) / Tonga. Device serial and location are intentionally omitted.

## Canonical source and artifact

- Source branch: `inversion-labs/r7-sol-privileged-hotpath`
- Source head under test: `f784c51b7a69053c920419cd5aff0e126d2b55b0`
- Package: `com.flockyou.debug`
- Version: `1.0.0-system-debug`
- Active package flags include `SYSTEM`, `UPDATED_SYSTEM_APP`, `PRIVILEGED`, and `DEBUGGABLE`.
- Active APK SHA-256: `c2aaa16dbec598c92a5f3b2d229d5f2f751720507fcce6767117c05904b3c5e4`
- Android Studio deployable intermediate APK SHA-256: identical to active APK.

## Privilege boundary

- `BLUETOOTH_PRIVILEGED`: granted
- `CONNECTIVITY_INTERNAL`: granted
- `LOCAL_MAC_ADDRESS`: granted
- `READ_PRIVILEGED_PHONE_STATE`: granted
- `NETWORK_SETTINGS`: denied as expected
- `PEERS_MAC_ADDRESS`: denied as expected

## Flock Boost live proof

Flock Boost was enabled through the visible Android Studio physical-device mirror. Scanning was then started through the same visible UI.

The live `:scanning` process repeatedly emitted:

`BleRuntimeScanPlan(aggressive=true, reportDelayMs=0, aggressiveMatching=true, maxAdvertisementMatches=true, requestExtendedAdvertisements=true, phyRequest=ALL_SUPPORTED)`

Controller evidence simultaneously reported:

`BleControllerCapabilities(extendedAdvertising=true, le2mPhy=true, codedPhy=true)`

## GNSS live proof

The same live scanner reported `READY`, successful raw-measurement callback registration, and delivered measurement batches. At delivery 200 the batch contained 18 measurements, 18 code locks and 1 valid ADR sample, with carrier frequency, baseband C/N0 and AGC all observed.

## Studio / verification state

- Android Studio project identity: `Flock-Sucker`.
- Android Studio Index MCP: `http://127.0.0.1:29171/index-mcp/streamable-http`.
- Physical-device mirroring is enabled on connection.
- Active app build variant restored to `systemDebug` after acceptance.
- Pinned toolchain remains AGP `9.1.1` + Gradle `9.3.1`; no IDE auto-upgrade accepted.
- Full `systemDebug` source gate before deployment: 804 tests, 0 failures/errors/skips; APK assembly successful.

## Post-reconnect revalidation

After a later USB reconnect, Android Studio again performed a normal `app` deployment and activated `com.flockyou.MainActivity` at 11:09:22 local time. The active `/data/app` APK SHA-256 was `c2aaa16dbec598c92a5f3b2d229d5f2f751720507fcce6767117c05904b3c5e4`, exactly matching Studio's `app/build/intermediates/apk/system/debug/app-system-debug.apk`.

The visible Settings UI still showed **Flock Boost ON**. The new live `:scanning` process repeatedly emitted the full Boost plan from 11:10:08 through at least 11:11:22:

`BleRuntimeScanPlan(aggressive=true, reportDelayMs=0, aggressiveMatching=true, maxAdvertisementMatches=true, requestExtendedAdvertisements=true, phyRequest=ALL_SUPPORTED)`

The privileged grant boundary remained unchanged, and GNSS raw-measurement delivery remained live with carrier frequency, baseband C/N0, AGC, code-lock and valid ADR evidence.
