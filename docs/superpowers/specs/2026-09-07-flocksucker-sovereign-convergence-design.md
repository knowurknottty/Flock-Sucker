# Flock-Sucker Sovereign Convergence Design

## Goal
Converge the outstanding evidence-core and vendor-signature branches into the current Flock-Sucker line, fix the verified compact-screen onboarding blocker, and complete an Inversion Labs identity cutover so current production code and public surfaces no longer present Flock-You as the product identity.

## Canonical identity
- Product: **Flock-Sucker**
- Maintainer/variant owner: **Inversion Labs**
- Android namespace/applicationId family: **`com.inversionlabs.flocksucker`**
- Kotlin package root: **`com.inversionlabs.flocksucker`**
- Canonical internal symbols use `FlockSucker*`, not `FlockYou*`.
- Canonical broadcasts/actions use `com.inversionlabs.flocksucker.*`.
- Canonical OEM/AOSP artifact names use `FlockSucker`, `flocksucker`, and `com.inversionlabs.flocksucker`.

## Attribution and history
Flock-Sucker is an independently maintained Inversion Labs derivative inspired by MaxwellDPS/Flock-You-Android. Attribution belongs in `NOTICE.md`, README history/credits, and historical design records. Current product UI, CI, package names, release names, code symbols, and current technical docs must not imply that Flock-Sucker is Flock-You or is maintained by the upstream project.

Historical documents may retain old names when they are describing historical facts. Legacy compatibility code may retain `flockyou` strings only when a regression allowlist documents why the identifier must remain stable.

## Android identity break
Changing the applicationId from `com.flockyou` to `com.inversionlabs.flocksucker` intentionally creates a new Android application identity. Android will not treat the new package as an in-place update of the old package, and the new app cannot automatically read the old app's private sandbox solely because the code is related.

Do not weaken the sovereignty cutover to preserve the old package ID. Instead:
- document the clean-break behavior;
- preserve data-format/schema compatibility where useful for explicit import/export;
- preserve legacy identifiers only in migration/import tooling, tests, and attribution;
- never claim automatic cross-package migration without proven OS-level access.

## Branch convergence
1. Start from current `origin/main`.
2. Merge `arch/evidence-core-hardening-r1` first. Resolve current-main conflicts semantically, preserving both modern build/deployment-profile behavior and evidence-core detector/evidence semantics.
3. Run evidence, migration, detector-health, and full unit/build gates.
4. Merge `work/vendor-signatures-20260905` onto the converged evidence line. Resolve signature overlaps in favor of the hardened evidence model plus the newer vendor precision data.
5. Run focused vendor-signature tests and the full gates again.

## Verified onboarding fix
The first-run welcome page currently clips the CTA on compact displays. The approved fix keeps the CTA outside a scrollable `LazyColumn`, with informational content scrollable above it. A device/emulator regression must set a compact viewport, cold-install, launch, and fail unless the CTA text is visible and tappable.

## Naming migration scope
The cutover covers:
- Gradle namespace/applicationId/test runner/package-derived build logic;
- Kotlin/Java package declarations, imports, source/test/androidTest directory layout;
- application/database/service class names containing `FlockYou`;
- manifest component references, intent actions, authorities, providers, and permission XML;
- Room schema export package path and database class identity;
- database filename and keystore/prefs identifiers for the new package, with legacy names documented only for migration/import compatibility;
- scripts, test automation, screenshots, adb commands, package IDs;
- GitHub Actions signing subjects, artifact names, release notes, attestation commands;
- OEM/AOSP integration names, module names, permission XML, vendor paths;
- README/current docs/public copy and generated release artifacts.

## Regression policy
Add a branding/identity verifier that fails when current production/public surfaces introduce any of these outside an explicit allowlist:
- `com.flockyou`
- `FlockYou`
- `Flock You`
- `Flock-You`

The allowlist is limited to attribution, historical documents, and explicit legacy migration/import code. The verifier itself must explain every allowed path/pattern.

## Test and release gates
Before merge/publish:
1. focused evidence-core and vendor-signature tests pass;
2. JVM unit suite passes;
3. Android instrumentation sources compile and the existing stale androidTest API drift is repaired sufficiently for the suite to build;
4. compact-screen onboarding regression passes on emulator;
5. generic non-root APK installs, launches, and advances through onboarding without crash signals;
6. naming verifier reports no unapproved legacy identity in current production/public surfaces;
7. GLM-5.3-Flash review runs through CAPT against the pinned convergence SHA and its actionable findings are reconciled;
8. final APK is rebuilt using Android Studio's bundled JBR and configured Android SDK on the Mac;
9. APK package/version/signature/hash are inspected and recorded before publication.

## Non-goals
- Do not rewrite historical git commits.
- Do not touch or discard the dirty `work/adversarial-sensor-suite-r1` checkout.
- Do not claim root/system privileges for the generic non-root build.
- Do not silently preserve misleading upstream product identity merely for convenience.
