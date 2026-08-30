# Round 08 Live Timing Truth — 2026-08-30

## Scope

R8 removes configuration names that claimed acquisition cadence where the production code actually controlled anomaly-report cooldowns, removes a dead RF timing knob, adds an atomic typed app profile, and adds structured Wi-Fi scan acceptance evidence.

Source branch: `inversion-labs/r8-timing-truth`

Source commit under live test: `a7f72a1e8d62bb00df8c665ee7c9d14b9d3e876c`

Pre-deploy local gate:
- `809/809` `systemDebug` unit tests passed.
- `0` failures, `0` errors, `0` skipped.
- `:app:assembleSystemDebug` succeeded.
- `git diff --check` passed.

## Studio deployment

Android Studio Quail 3 / 2026.1.3 Patch 1 synced the pinned project as `Flock-Sucker` with AGP 9.1.1 and Gradle 9.3.1.

The IDE optimistic deployer could not use `run-as` because the package is an updated privileged system app. Studio then correctly fell back to its standard full package-manager install of `app-system-debug.apk`.
## Live privileged state

After install the package remained an updated privileged system app:
- active update path under `/data/app/.../com.flockyou.debug...`
- system base path `/system/priv-app/FlockYou`
- `SYSTEM`, `UPDATED_SYSTEM_APP`, `PRIVILEGED`, and `DEBUGGABLE` flags present

The R7 permission boundary remains authoritative: reachable privileged grants survive the update, while signature-only `NETWORK_SETTINGS` remains outside the app boundary.

## Timing semantics proven

R8 renames the former GNSS `scan interval` to `gnssAnomalyCooldownSeconds`. Raw GNSS measurements remain event-driven; this setting only controls repeated anomaly-report cooldown and is normalized to the monitor's real 60–300 second range.

R8 renames the former cellular `scan interval` to `cellularAnomalyCooldownSeconds`. It controls anomaly-report cooldown, not framework cell acquisition cadence, and is normalized to 1–30 seconds.

The legacy DataStore keys remain readable for migration. Writes use the new semantic keys.

`rfScanIntervalSeconds` was removed from `ScanSettings` because Android Studio index search found no production consumer. RF detection itself remains intact and continues to consume Wi-Fi proxy observations / external-radio evidence when available.

`FlockRuntimeProfile` is now the typed atomic app-side profile seam and contains only settings with proven production consumers.
## Live Wi-Fi evidence

With Flock Boost already enabled and the global Android key `wifi_scan_throttle_enabled` absent (`null`), the fresh R8 scanner produced:

`WifiScanEvidence(apiRequestCount=1, apiAcceptedCount=1, apiRejectedCount=0, freshResultCount=1, staleResultCount=0, backoffLevel=0, baseIntervalMs=27000, adaptiveIntervalMs=27000, ...)`

The same live session later entered framework-driven backoff. The scanner logged local skips with approximately 63.5s, 56.1s, 48.6s, and 41.1s remaining, demonstrating that prior framework rejection(s) had raised the adaptive interval.

The loop then recovered with:
- `WiFi scan started (attempt 3)`
- `WiFi scan successful, backoff reset`

This proves the R8 evidence model is observing the real Android acceptance funnel rather than merely restating configured timing.

## Root throttle A/B gate

GhostArrow captured a read-only pre-state snapshot with `wifi_scan_throttle_enabled = null` and no existing apply-state receipt.

The planned root A/B is intentionally narrow: snapshot exact prior state -> set only `wifi_scan_throttle_enabled=0` -> compare accepted/rejected/fresh/stale/backoff evidence -> exact revert by deleting the key because it was originally absent.

The ChatGPT execution safety layer blocked the root-global write before Android executed it. No alternate shell/IDE route was used to evade that boundary, so the A/B remains unexecuted and the live global key remains `null`.
