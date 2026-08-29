# Audit Closeout + Two-Device Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use systematic debugging, TDD, and verification-before-completion task-by-task.

**Goal:** Close the actionable findings from the `feat/evidence-performance-ai-r1` audit on converged `main`, prove the remaining runtime questions on two Android devices, then merge only verified changes.

**Architecture:** Preserve bounded scanner behavior and the existing evidence ledger while tightening trust boundaries and artifact provenance. Use pure policy/verifier helpers where possible so security and recency invariants are unit-testable. Keep build inputs deterministic and use GitHub PR checks plus physical-device evidence as final gates.

**Tech Stack:** Kotlin, Android 12+, Room/SQLCipher, OkHttp, MediaPipe, llama.cpp/JNI, Gradle 9.3.1, AGP 9.1.1, JUnit 4, adb.

**Spec:** Read-only audit of `knowurknottty/Flock-Sucker@07578e64a1fccced4cce0e3e869da60535e1ded7`, reconciled against current `origin/main`.

## Global Constraints
- Do not regress sideload/system/OEM build variants.
- Do not weaken emergency-wipe confirmation/settings gates.
- READY for file-backed AI requires artifact verification plus successful runtime initialization/smoke test.
- Preserve append-only sighting evidence and truthful scanner drop accounting.
- Keep device-specific performance tuning out of generic `main`.

---
### Task 1: Trust only real system boot actions

**Files:**
- Create: `app/src/main/java/com/flockyou/service/BootActionPolicy.kt`
- Modify: `app/src/main/java/com/flockyou/service/BootReceiver.kt`
- Modify: `app/src/main/java/com/flockyou/service/nuke/BootWatcher.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/flockyou/service/BootActionPolicyTest.kt`

- [ ] Write a failing test asserting BOOT_COMPLETED and LOCKED_BOOT_COMPLETED are accepted, while both QUICKBOOT actions and arbitrary broadcasts are rejected.
- [ ] Run the focused test and confirm RED.
- [ ] Implement `BootActionPolicy.isTrustedBootAction(action: String?): Boolean` with only the two Android system boot actions.
- [ ] Route both receivers through that policy, remove QUICKBOOT actions from manifest filters, and set both boot receivers `android:exported="false"`.
- [ ] Re-run the focused test and compile sideload debug.

### Task 2: Make UI/history recency mean last seen

**Files:**
- Modify: `app/src/main/java/com/flockyou/data/model/Detection.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/DetectionHistoryPresentationPolicy.kt`
- Modify: `app/src/main/java/com/flockyou/ui/components/DetectionCard.kt`
- Test: `app/src/test/java/com/flockyou/ui/screens/DetectionHistoryPresentationPolicyTest.kt`

- [ ] Add failing tests where first-seen is old but `lastSeenTimestamp` is recent; recent filters and newest ordering must use the recent observation.
- [ ] Run focused tests and confirm RED.
- [ ] Add a single `effectiveLastSeenTimestamp` projection using `max(timestamp, lastSeenTimestamp)` and use it for recency filtering/sorting/card age.
- [ ] Re-run focused tests and compile.
### Task 3: Cryptographically bind model files before promotion

**Files:**
- Create: `app/src/main/java/com/flockyou/ai/ModelArtifactVerifier.kt`
- Modify: `app/src/main/java/com/flockyou/data/AiSettings.kt`
- Modify: `app/src/main/java/com/flockyou/config/NetworkConfig.kt`
- Modify: `app/src/main/java/com/flockyou/ai/DetectionAnalyzer.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/flockyou/ai/ModelArtifactVerifierTest.kt`

- [ ] Write failing verifier tests for correct SHA-256, wrong SHA-256, wrong size, and canonical model metadata.
- [ ] Run the focused tests and confirm RED.
- [ ] Pin Hugging Face URLs to immutable repository revisions and expose expected SHA-256/byte-size BuildConfig values (OEM-overridable as paired URL+digest inputs).
- [ ] Verify completed download `.part` files before atomic promotion; delete bad partials on integrity failure.
- [ ] Import into a temporary file, verify against the selected model contract, then atomically replace the final file only on success.
- [ ] Re-run verifier tests plus existing GGUF runtime/artifact contract tests.

### Task 4: Remove the cleartext/config mismatch and mutable release input

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/flockyou/network/TorAwareHttpClient.kt`
- Modify: `app/src/main/java/com/flockyou/config/NetworkConfig.kt`
- Create: `app/src/main/assets/oui.sha256`

- [ ] Replace the HTTP-only IP metadata default with an HTTPS endpoint and adjust field parsing without weakening `network_security_config.xml`.
- [ ] Add a deterministic `verifyOuiDatabase` Gradle task checking the committed CSV against `oui.sha256`.
- [ ] Make release assembly depend on verification, not a live network refresh; keep `updateOuiDatabase` as an explicit maintainer action that refreshes both CSV and digest.
- [ ] Run `verifyOuiDatabase`, configuration generation, and all three debug variant compile/build gates.
### Task 5: Reconcile already-fixed audit findings

- [ ] Prove the sighting sequence race is closed by the current atomic INSERT implementation and focused concurrency coverage.
- [ ] Prove BLE ingress loss is explicit rather than silent: raw callbacks, processed callbacks, and dropped callbacks remain separately accounted.
- [ ] Run the full sideload unit suite and `git diff --check`.

### Task 6: PR, CI, merge, and exact-build provenance

- [ ] Commit the plan/fixes in reviewable units and push `fix/audit-closeout-r1`.
- [ ] Open a PR to `main`; require exact-head GitHub checks/workflow evidence before merge.
- [ ] Merge only if the head SHA is unchanged and all required local/remote gates are green.
- [ ] Fast-forward the canonical local `main` to merged `origin/main` and build one final sideload debug APK from that exact merge state.
- [ ] Record APK SHA-256, package/version, signing certificate digest, and source commit.

### Task 7: Two-device physical verification

- [ ] Discover/pair/connect both wireless adb targets and record serial/model/API/ABI/RAM.
- [ ] Remove stale Flock package variants from each target and install only the exact post-merge APK.
- [ ] Launch, capture UI tree/screenshots, crash buffer, and startup logcat on each device.
- [ ] Verify synthetic QUICKBOOT broadcasts cannot reach the hardened boot receivers.
- [ ] Verify scanner proof-of-life/raw/processed/drop counters under a bounded live scan window.
- [ ] Stage and SHA-verify the known GGUF artifact where present; run real llama.cpp load/smoke-test generation, cancellation/unload, and capture memory/thermal evidence where supported.
- [ ] Report VERIFIED results separately from remaining environment/device limitations; do not claim a runtime path passed without direct evidence.
