# Flock-Sucker Evidence-Core Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert Flock-Sucker from lossy heuristic detection persistence into an evidence-first, replayable RF forensics system while permanently preserving the Flock-Sucker product identity.

**Architecture:** Raw scanner ingress first produces immutable `Observation` evidence. Versioned parser/classifier assertions and conservative identity links operate on observations; the existing `Detection` model remains a compatibility/UI projection during migration. Technique events such as advertisement bursts are independent of hardware attribution, and co-travel conclusions require explicit identity and movement evidence.

**Tech Stack:** Android/Kotlin, Room + SQLCipher, Hilt, coroutines/Flow, JUnit4, Android instrumented tests, Gradle flavors (`sideload`, `oem`, `system`).

**Spec:** `docs/superpowers/specs/2026-09-05-flock-sucker-evidence-core-hardening-design.md`

## Global Constraints

- Product name on visible/source surfaces is exactly `Flock-Sucker`; compatibility identifiers such as `com.flockyou` may remain.
- Existing encrypted database filename and package namespace remain unchanged.
- Raw observation evidence is append-only and is written before classification/deduplication.
- Weak evidence may produce similarity assertions but may not establish physical identity.
- Technique detection and hardware attribution are separate outputs.
- Local CI is promotion authority; GitHub CI is supplemental.
- No dirty existing worktree may be reset, cleaned, rebased, or overwritten.

---

### Task 1: Permanent Flock-Sucker branding invariant

**Files:**
- Modify: `scripts/verification/check-product-branding.sh`
- Modify: `app/src/test/java/com/flockyou/ProductBrandingRegressionTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/flockyou/data/export/DetectionExportSerializer.kt`

**Interfaces:**
- Consumes: existing `app_name` and Gradle resource tree.
- Produces: case-insensitive legacy-brand failure gate; resolved visible/export branding `Flock-Sucker`.

- [ ] Add failing regression assertions for `FLOCK YOU`, `Flock You`, and `Flock-You` across visible resource/export files while allowing `com.flockyou` compatibility identifiers.
- [ ] Run `:app:testSideloadDebugUnitTest --tests com.flockyou.ProductBrandingRegressionTest` and verify RED against current `FLOCK YOU` / `Flock-You` residues.
- [ ] Harden `check-product-branding.sh` and replace the remaining visible/export residues with `Flock-Sucker`.
- [ ] Run targeted branding test plus shell branding gate and verify GREEN.
- [ ] Commit as `fix: make Flock-Sucker branding invariant`.

### Task 2: Immutable Observation evidence schema

**Files:**
- Create: `app/src/main/java/com/flockyou/data/model/Observation.kt`
- Create: `app/src/main/java/com/flockyou/data/repository/ObservationDao.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/Database.kt`
- Create: `app/src/test/java/com/flockyou/data/model/ObservationTest.kt`
- Add generated Room schema: `app/schemas/com.flockyou.data.repository.FlockYouDatabase/12.json`

**Interfaces:**
- Produces: `Observation`, `ObservationProtocol`, `ObservationDisposition`, `ObservationDao.insert(observation)`, `FlockYouDatabase.observationDao()`.
- `Observation` preserves observed identifier, address type, raw payload digest/metadata, radio metrics, location, parser/schema version and scanner provenance.

- [ ] Write `ObservationTest` first to pin immutable evidence fields, enum serialization, identifier/address-type semantics, and raw digest retention.
- [ ] Run the targeted test and verify RED because the model does not exist.
- [ ] Implement the model/DAO and v11→v12 migration creating `observations` plus timestamp/session/identifier/digest indexes.
- [ ] Add the entity/DAO to `FlockYouDatabase`, generate schema 12, and verify no destructive upgrade path is introduced.
- [ ] Run targeted model tests and Room compilation; verify GREEN.
- [ ] Commit as `feat: add immutable observation evidence ledger`.

### Task 3: Write raw BLE/Wi-Fi evidence before classification

**Files:**
- Create: `app/src/main/java/com/flockyou/evidence/ObservationFactory.kt`
- Create: `app/src/main/java/com/flockyou/evidence/ObservationRecorder.kt`
- Modify: `app/src/main/java/com/flockyou/detection/handler/BleDetectionHandler.kt`
- Modify: `app/src/main/java/com/flockyou/service/DetectionProcessor.kt`
- Modify the canonical Wi-Fi ingress path identified during implementation.
- Create: `app/src/test/java/com/flockyou/evidence/ObservationFactoryTest.kt`

**Interfaces:**
- Consumes: Android BLE/Wi-Fi scan results and current privacy-filtered location.
- Produces: normalized evidence + SHA-256 digest before any classification or dedupe; BLE context gains raw evidence reference instead of being the evidence store.

- [ ] Write failing fixtures proving BLE observations retain actual source MAC, full manufacturer data, service data/UUIDs, raw scan bytes, PHY/TX/RSSI/timestamp and address type when API exposes it.
- [ ] Write failing Wi-Fi fixtures proving BSSID/SSID/frequency/channel width/information elements are retained independently of vendor classification.
- [ ] Implement deterministic normalization/digesting and append-before-classify recording.
- [ ] Verify a persistence failure is surfaced as evidence-health telemetry rather than silently rewriting a `Detection`.
- [ ] Run targeted tests and scanner compile gates; commit as `feat: persist raw radio observations before inference`.

### Task 4: Remove weak identity merges and add resolver decisions

**Files:**
- Create: `app/src/main/java/com/flockyou/evidence/IdentityResolver.kt`
- Create: `app/src/main/java/com/flockyou/data/model/IdentityLink.kt`
- Create: `app/src/main/java/com/flockyou/data/repository/IdentityLinkDao.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/DetectionRepository.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/DetectionDeduplicator.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/Database.kt`
- Modify: `app/src/test/java/com/flockyou/data/repository/DetectionDeduplicatorTest.kt`

**Interfaces:**
- Produces: `IdentityDecision` with `MATCH`, `POSSIBLY_RELATED`, or `DISTINCT`, plus rule/evidence/rejected-alternative metadata.
- Exact public/global MAC/BSSID may be strong identity evidence; service UUID, OUI/manufacturer, SSID, RSSI, type, method or advertising shape alone may not merge rows.

- [ ] Add RED tests demonstrating two different MACs with same type/name/manufacturer/RSSI/service UUID do not merge.
- [ ] Add RED tests demonstrating service UUID lookup is never a canonical identity merge and throttle keys do not collapse unknown devices by type/manufacturer alone.
- [ ] Implement resolver decisions and remove service-UUID/composite canonical merge paths from repository persistence.
- [ ] Persist identity links/assertions separately from compatibility `Detection` summaries.
- [ ] Run dedupe/resolver tests and repository compile; commit as `fix: prevent weak radio identity merges`.

### Task 5: Strict Apple/Samsung tracker parsers

**Files:**
- Create: `app/src/main/java/com/flockyou/detection/protocol/AppleBleParser.kt`
- Create: `app/src/main/java/com/flockyou/detection/protocol/SamsungBleParser.kt`
- Modify: `app/src/main/java/com/flockyou/detection/handler/BleDetectionHandler.kt`
- Create: `app/src/test/java/com/flockyou/detection/protocol/AppleBleParserTest.kt`
- Create: `app/src/test/java/com/flockyou/detection/protocol/SamsungBleParserTest.kt`

**Interfaces:**
- Produces protocol assertions such as `APPLE_FIND_MY`, `APPLE_PROXIMITY_PAIRING`, `SAMSUNG_MANUFACTURER_ADVERTISEMENT`, and `SAMSUNG_SMARTTAG` only when signature-specific evidence exists.

- [ ] Add RED Sep-5 regressions: Apple `0x07` alone is not AirTag; Samsung company ID `0x0075` / `42 04 01 80...` alone is not SmartTag.
- [ ] Add positive fixtures for independently supported Find My and SmartTag service/signature evidence.
- [ ] Implement strict parsers and route tracker classification through them.
- [ ] Verify ambiguous frames remain neutral protocol assertions, not tracker detections.
- [ ] Commit as `fix: make tracker classification protocol specific`.

### Task 6: Technique-level BLE burst events

**Files:**
- Create: `app/src/main/java/com/flockyou/detection/behavior/BleBurstAnalyzer.kt`
- Create: `app/src/main/java/com/flockyou/data/model/TechniqueEvent.kt`
- Create: `app/src/main/java/com/flockyou/data/repository/TechniqueEventDao.kt`
- Modify: `app/src/main/java/com/flockyou/detection/handler/BleDetectionHandler.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/Database.kt`
- Create: `app/src/test/java/com/flockyou/detection/behavior/BleBurstAnalyzerTest.kt`

**Interfaces:**
- Produces technique events such as `APPLE_ACCESSORY_ADVERTISEMENT_BURST`, `FAST_PAIR_ADVERTISEMENT_BURST`, and `DEVICE_IDENTITY_CHURN`; events reference contributing observation IDs.
- Hardware attribution is nullable and cannot default to Flipper.

- [ ] Add RED tests reproducing the Sep-5 152/9 and 160/7 burst shapes and proving the output is technique-level with `hardwareAttribution = null`.
- [ ] Add RED regression proving a Quectel/Espressif packet processed at promotion time cannot become the burst's source identity.
- [ ] Implement rate, unique-address, payload-diversity/churn and duplicate-ratio metrics over observation windows.
- [ ] Replace `FLIPPER_ZERO_SPAM` promotion for aggregate Apple/Fast Pair traffic with neutral technique events while retaining independently evidenced Flipper device detection.
- [ ] Commit as `fix: separate BLE burst technique from hardware attribution`.

### Task 7: Layered fingerprints and evidence-graded co-travel

**Files:**
- Create: `app/src/main/java/com/flockyou/evidence/FingerprintStack.kt`
- Create: `app/src/main/java/com/flockyou/detection/behavior/CoTravelAnalyzer.kt`
- Create: `app/src/test/java/com/flockyou/evidence/FingerprintStackTest.kt`
- Create: `app/src/test/java/com/flockyou/detection/behavior/CoTravelAnalyzerTest.kt`
- Modify existing co-traveler integration points under `app/src/main/java/com/flockyou/adversarial/`.

**Interfaces:**
- Produces L0 raw, L1 hardware, L2 protocol, L3 payload, L4 behavioral and L5 spatial-temporal fingerprints plus confidence/proof boundaries.
- Produces `MULTI_LOCATION`, `CO_MOVEMENT_CONSISTENT`, or `FOLLOWING_LIKE`; none implies ownership or intent.

- [ ] Add RED tests that advertisement-shape collisions remain `POSSIBLY_RELATED` and never identity matches.
- [ ] Add RED Cradlepoint-style fixture for exact globally assigned BSSID observed at separated locations yielding multi-location evidence only.
- [ ] Add movement continuity, impossible-speed, static-home-baseline and independent-episode controls.
- [ ] Implement layered fingerprinting and conservative co-travel states.
- [ ] Commit as `feat: add evidence-graded radio fingerprint and co-travel analysis`.

### Task 8: Canonical ASTM/OpenDroneID decoding

**Files:**
- Create: `app/src/main/java/com/flockyou/detection/protocol/OpenDroneIdParser.kt`
- Create: `app/src/main/java/com/flockyou/data/model/DroneRemoteId.kt`
- Modify drone/RF handler integration points under `app/src/main/java/com/flockyou/detection/handler/` and `app/src/main/java/com/flockyou/service/`.
- Create: `app/src/test/java/com/flockyou/detection/protocol/OpenDroneIdParserTest.kt`

**Interfaces:**
- Produces canonical Remote ID assertions for Basic ID, Location/Vector, System and Operator messages over BLE/Wi-Fi inputs.
- Existing Skydio/Autel/BRINC/DJI name/SSID matches remain supplemental evidence only.

- [ ] Add RED fixtures for valid/invalid ASTM/OpenDroneID Basic ID, location/vector and multi-message cases.
- [ ] Implement bounded parsers with explicit malformed/unsupported outcomes.
- [ ] Route all Remote-ID-capable scanner paths to the single parser and expose parsed evidence in observations/assertions.
- [ ] Verify no SSID/vendor-name-only input becomes a Remote ID identity.
- [ ] Commit as `feat: add canonical remote id evidence parser`.

### Task 9: Evidence-backed surveillance vendor catalog

**Files:**
- Create: `app/src/main/java/com/flockyou/data/model/SurveillanceVendorCatalog.kt`
- Create: `app/src/test/java/com/flockyou/data/model/SurveillanceVendorCatalogTest.kt`
- Modify: `app/src/main/java/com/flockyou/data/model/CameraSignatures.kt`
- Modify: `app/src/main/java/com/flockyou/data/model/SsidPatterns.kt`
- Reconcile commit: `c25c431`

**Interfaces:**
- Each production signature requires vendor, product family, observable type/value, corroboration requirements, exclusions, confidence tier, source URI/date and signature version.
- Component vendors are never promoted to surveillance ownership from OUI alone.

- [ ] Add RED catalog-schema validation rejecting entries without source/version/corroboration/exclusion metadata.
- [ ] Reconcile verified Flock, Vigilant/Motorola, Axon, Verkada, Skydio X10, Autel, BRINC, COBAN plus additional ALPR/body-camera/video/drone/public-safety families from the approved spec.
- [ ] Add strong negative controls for Apple/Huawei Verkada false OUIs and generic aliases (`Windows`, `Iris`, `Gantry`, `Site`, `X10`).
- [ ] Keep unresolved aliases as research metadata, not production detection rules.
- [ ] Run catalog and camera/signature tests; commit as `feat: version surveillance signature evidence catalog`.

### Task 10: Converge branches, full local gates, Moto proof, merge

**Files:**
- Reconcile unique first-party commits from R7/R8/R9/adversarial worktrees and `c25c431` into the convergence branch.
- Update release/verification documentation with exact commands, commit hashes and APK hashes.

- [ ] Inventory each divergent first-party branch by unique commit and capability; cherry-pick/reimplement only non-superseded behavior.
- [ ] Run sideload/OEM/system unit suites, Room migration/instrumented gates, applicable native/RTL-SDR tests, branding gate, Sep-5 fixture replay and `git diff --check`.
- [ ] Assemble supported debug artifacts and record SHA-256 hashes.
- [ ] Install exact convergence build on rooted Moto; verify new BLE observation stores actual source identity/raw payload digest before classification and burst events reference contributors.
- [ ] Create one convergence PR, review exact diff and merge only after the exact commit is green.
- [ ] Verify `origin/main` contains the merge and no open first-party PR contains unique unmerged product behavior.