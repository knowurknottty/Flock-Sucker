# Flock-Sucker Sovereign Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one tested Flock-Sucker/Inversion Labs mainline containing evidence-core hardening, vendor-signature precision, the compact-screen onboarding fix, and a complete canonical identity cutover to `com.inversionlabs.flocksucker`.

**Architecture:** Converge behavior before renaming so semantic conflicts are resolved against known code, then migrate identity mechanically plus targeted symbol/persistence changes, protected by a strict legacy-name verifier. Preserve historical attribution and narrowly scoped legacy migration identifiers without retaining upstream identity in the active product.

**Tech Stack:** Android/Kotlin, Compose, Room/SQLCipher, Gradle/AGP, GitHub Actions, adb/emulator, Android Studio JBR/SDK, CAPT + GLM-5.3-Flash.

**Spec:** `docs/superpowers/specs/2026-09-07-flocksucker-sovereign-convergence-design.md`

## Global Constraints
- Canonical applicationId/namespace/package root is `com.inversionlabs.flocksucker`.
- Product/public identity is `Flock-Sucker` by `Inversion Labs`.
- Preserve upstream inspiration credit in NOTICE/README without implying affiliation.
- Legacy Flock-You identifiers are permitted only in explicit attribution/history/migration allowlists.
- Do not modify the dirty `work/adversarial-sensor-suite-r1` checkout.
- Do not publish until all release gates in the spec pass.

---

### Task 1: Converge evidence-core onto current main
**Files:** resolve merge conflicts in `app/build.gradle.kts` and `app/src/main/java/com/flockyou/service/ScanningService.kt`; retain all non-conflicting evidence-core files.
**Interfaces:** Produces a current-main-compatible observation/evidence ledger, identity resolver, evidence-based BLE classification, Remote ID evidence, detector-health policy, and migrations.
- [ ] Merge `arch/evidence-core-hardening-r1` with `--no-commit`.
- [ ] Resolve Gradle conflict by preserving current AGP/deployment-profile/build configuration plus evidence-core dependencies/schema requirements.
- [ ] Resolve `ScanningService.kt` by preserving current runtime/privilege paths and evidence-core lifecycle/proof-of-life semantics.
- [ ] Run focused evidence/migration/health tests, then `:app:testSideloadDebugUnitTest :app:assembleSideloadDebug`.
- [ ] Commit the converged evidence-core merge.

### Task 2: Layer vendor-signature hardening
**Files:** `CameraSignatures.kt`, `DetectionPatterns.kt`, `SsidPatterns.kt`, `RfDetectionHandler.kt`, `RfSignalAnalyzer.kt`, corresponding tests.
**Interfaces:** Produces more precise public-safety vendor matching without weakening evidence thresholds.
- [ ] Merge `work/vendor-signatures-20260905` onto the converged evidence line.
- [ ] Resolve overlaps by retaining evidence-core classification semantics and vendor branch signature precision.
- [ ] Run `CameraSignaturesTest`, `PublicSafetyVendorSignaturesTest`, relevant RF/pattern tests, then the full sideload unit/build gate.
- [ ] Commit vendor convergence.

### Task 3: Fix compact-screen onboarding with regression evidence
**Files:** `PermissionSetupWizard.kt`; `scripts/qa/verify_compact_onboarding.sh`.
**Interfaces:** Welcome content scrolls independently while CTA remains visible and tappable on compact displays.
- [ ] Add/retain the compact emulator regression script and verify it fails against the pre-fix layout when reproducible.
- [ ] Change welcome layout to a weighted `LazyColumn` plus pinned CTA.
- [ ] Build/install generic sideload debug and run the compact regression at 720x1600/320dpi.
- [ ] Verify tap advances to Location Access and crash buffer is empty.
- [ ] Commit onboarding fix and regression script.

### Task 4: Cut over Android/Kotlin identity
**Files:** `app/build.gradle.kts`, source/test/androidTest trees, manifests, Room schema export directories, package-derived scripts/config.
**Interfaces:** All active code compiles under `com.inversionlabs.flocksucker`; active component/broadcast/provider identity follows the new namespace.
- [ ] Add a failing identity regression that asserts namespace/applicationId/package root and rejects active `com.flockyou` references.
- [ ] Rename source/test/androidTest package directories and package/import declarations.
- [ ] Rename `FlockYouApplication`, `FlockYouDatabase`, `FlockYouCarAppService`, and other active `FlockYou*` symbols to `FlockSucker*`.
- [ ] Update manifest actions, authorities, test runner, and build config to the new namespace.
- [ ] Move/update Room schema export package path without changing schema semantics.
- [ ] Run compile/unit gates and fix package migration fallout until green.
- [ ] Commit Android/Kotlin identity cutover.

### Task 5: Migrate active persistence and OEM/release identity
**Files:** database/key/prefs constants, OEM integration docs/config, workflows, release scripts, screenshot/adb automation.
**Interfaces:** New package writes canonical `flocksucker_*` state and emits canonical Flock-Sucker artifacts; historical names survive only in explicit import/migration/attribution contexts.
- [ ] Rename active database filename, keystore alias, prefs/action/module/artifact names to Flock-Sucker equivalents.
- [ ] Add explicit legacy constants/import documentation where old values are required for migration/export compatibility.
- [ ] Rename AOSP/OEM module/XML/vendor path examples to `FlockSucker`/`flocksucker` and `com.inversionlabs.flocksucker`.
- [ ] Rename CI signing subjects, release artifacts, attestation commands, and release display names to Flock-Sucker / Inversion Labs.
- [ ] Update screenshot/adb scripts to the new package ID.
- [ ] Run script syntax/workflow text gates and commit.

### Task 6: Public branding, attribution, and legacy-name enforcement
**Files:** `README.md`, `NOTICE.md`, current docs, branding verifier/tests.
**Interfaces:** Current documentation presents Flock-Sucker/Inversion Labs; historical/upstream references are explicit attribution rather than ambiguous product identity.
- [ ] Create `NOTICE.md` with factual inspiration/upstream attribution and independence statement.
- [ ] Update README/current docs/public copy from Flock-You to Flock-Sucker where references describe the current product.
- [ ] Preserve historical references only in historical design/plan documents and explicit attribution sections.
- [ ] Implement a verifier with a documented allowlist for legitimate legacy strings.
- [ ] Run verifier and branding tests until zero unapproved legacy matches remain.
- [ ] Commit public identity migration.

### Task 7: Repair Android instrumentation compile gate
**Files:** stale `app/src/androidTest/**` tests/helpers and directly corresponding test dependencies only.
**Interfaces:** `compileSideloadDebugAndroidTestKotlin` completes successfully; stale test APIs match current production models/DAOs.
- [ ] Capture the current instrumentation compilation failures.
- [ ] Update stale constructor arguments, DAO calls, enums, and test-only imports to current APIs without weakening assertions.
- [ ] Run `:app:compileSideloadDebugAndroidTestKotlin` until green.
- [ ] Run available connected instrumentation tests on the emulator where environment permits; record any hardware-only skips separately.
- [ ] Commit instrumentation repair.

### Task 8: Governed independent review and final Android Studio build
**Files:** no source changes unless review findings require a new tested commit; release receipt/artifacts only after source freezes.
**Interfaces:** Produces a pinned reviewed source SHA and verified generic non-root APK built with Android Studio's bundled runtime.
- [ ] Run GLM-5.3-Flash through CAPT against the pinned convergence SHA; reconcile P0/P1 and justified P2 findings with tests.
- [ ] Run full JVM, instrumentation-compile, naming, compact-onboarding, install/launch, and crash-buffer gates again.
- [ ] Set `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` and use the configured Android SDK; run a clean generic-stock sideload build.
- [ ] Inspect `aapt` badging, APK signature, ABI set, SHA-256, and source SHA; create receipt.
- [ ] Open PR, review final diff/status, merge only after gates are green.
- [ ] Publish the rebuilt generic non-root artifact with a new immutable filename/hash; do not overwrite the defective old hash silently.
