# Auditable Sighting History and Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve every accepted observation as auditable evidence, expose unique-device and sighting counters separately, and render a device-specific text/map history.

**Architecture:** Keep `Detection` as the canonical device summary and introduce append-only `Sighting` persistence keyed to `Detection.id`. Repository upsert writes a sighting before/with canonical updates, while UI consumes dedicated sighting flows for detail/history/map without copying full history into global state.

**Tech Stack:** Kotlin, Room/SQLCipher, Coroutines/Flow, Jetpack Compose, existing map stack.

**Spec:** `docs/superpowers/specs/2026-08-28-flock-sucker-evidence-performance-ai-design.md`

## Global Constraints

- Do not fabricate historical sighting points during migration.
- Location is stored only when existing privacy settings permit it.
- Raw evidence and inferred classification remain separate fields.
- `UNIQUE DEVICES` and `SIGHTINGS` are distinct counters.
- Synthetic fixtures must be marked test-only.

---

### Task 1: Add the append-only sighting schema

**Files:**
- Create: `app/src/main/java/com/flockyou/data/model/Sighting.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/Database.kt`
- Create: `app/src/test/java/com/flockyou/data/repository/SightingMigrationTest.kt`

**Interfaces:**
- Produces: `Sighting`, `SightingDisposition`, `SightingDao` with `insert`, `forDetection`, `locatedForDetection`, `countAll`, `countForDetection`.

- [ ] **Step 1: Write the failing migration/entity tests** proving a new database version contains a `sightings` table indexed by `detectionId`, `timestamp`, and `(detectionId,timestamp)`, and that upgrading an existing database does not invent rows.
- [ ] **Step 2: Run** `./gradlew testSideloadDebugUnitTest --tests '*SightingMigrationTest*'` and verify failure because the entity/table is absent.
- [ ] **Step 3: Implement** `Sighting` with fields defined in the spec and add `SightingDao` plus the Room migration. Keep legacy `Detection.seenCount` untouched during migration.
- [ ] **Step 4: Re-run the targeted test** and verify all assertions pass.
- [ ] **Step 5: Commit** `feat(history): add append-only sighting ledger`.

### Task 2: Make repository upsert record repeat evidence

**Files:**
- Modify: `app/src/main/java/com/flockyou/data/repository/DetectionRepository.kt`
- Modify: `app/src/main/java/com/flockyou/data/repository/EphemeralDetectionRepository.kt`
- Create: `app/src/test/java/com/flockyou/data/repository/SightingRepositoryTest.kt`

**Interfaces:**
- Consumes: `SightingDao` from Task 1.
- Produces: `sightingsForDetection(id): Flow<List<Sighting>>`, `locatedSightingsForDetection(id)`, `totalSightingCount: Flow<Long>`.

- [ ] **Step 1: Write failing tests** for first observation, accepted repeat, throttled repeat, location-off privacy, and persistence failure. Accepted repeats must increment sighting count even when no new canonical `Detection` row is created.
- [ ] **Step 2: Run** the repository test class and confirm the current code fails because repeated sightings only mutate the aggregate row.
- [ ] **Step 3: Implement** one transaction boundary that records the accepted sighting and updates/inserts the canonical detection atomically. Throttled/suppressed observations may update funnel diagnostics but must not masquerade as accepted sightings.
- [ ] **Step 4: Re-run tests** and verify canonical `seenCount` equals accepted sighting count for newly-created post-migration records.
- [ ] **Step 5: Commit** `feat(history): persist repeat sightings with provenance`.

### Task 3: Split home counters into devices and sightings

**Files:**
- Modify: `app/src/main/java/com/flockyou/ui/screens/MainViewModel.kt`
- Modify: `app/src/main/java/com/flockyou/ui/screens/MainScreen.kt`
- Modify: `app/src/main/java/com/flockyou/ui/components/StatusComponents.kt`
- Create: `app/src/test/java/com/flockyou/ui/HeadlineCountsTest.kt`

**Interfaces:**
- Produces: `MainUiState.totalCount` as unique devices and new `MainUiState.totalSightings: Long`.

- [ ] **Step 1: Write failing tests** showing ten observations of one device produce `UNIQUE DEVICES=1`, `SIGHTINGS=10`, while two devices/ten sightings produce `2/10`.
- [ ] **Step 2: Run targeted tests** and confirm the current UI has no sightings counter.
- [ ] **Step 3: Collect** `repository.totalSightingCount` independently from Room unique row count and render `UNIQUE DEVICES`, `SIGHTINGS`, and `HIGH THREAT` in `StatusCard`.
- [ ] **Step 4: Add accessibility semantics** that read all three values unambiguously.
- [ ] **Step 5: Run unit + Compose tests** and commit `feat(ui): distinguish sightings from unique devices`.

### Task 4: Add evidence-history detail surface

**Files:**
- Modify the existing detection-detail composable under `app/src/main/java/com/flockyou/ui/`
- Create: `app/src/main/java/com/flockyou/ui/components/SightingHistory.kt`
- Create: `app/src/test/java/com/flockyou/ui/SightingPresentationPolicyTest.kt`

**Interfaces:**
- Produces: `SightingPresentationPolicy` and an `EVIDENCE HISTORY` section driven by a detection ID.

- [ ] **Step 1: Write failing presentation tests** for chronological ordering, first/last labels, signal trend, missing-location handling, and default-expanded technical evidence for HIGH/CRITICAL detections.
- [ ] **Step 2: Run tests** and verify failure because no event-level history exists.
- [ ] **Step 3: Implement** compact rows with timestamp/source/signal/location plus expandable raw evidence, rule IDs, and classification reasons. Never merge inferred reason text into raw metadata.
- [ ] **Step 4: Add a `MAP HISTORY` action** only when at least one sighting has location.
- [ ] **Step 5: Run tests** and commit `feat(history): expose auditable device evidence timeline`.

### Task 5: Add device-scoped map history

**Files:**
- Modify: `app/src/main/java/com/flockyou/ui/screens/MapViewModel.kt`
- Modify the existing map screen composable under `app/src/main/java/com/flockyou/ui/screens/`
- Create: `app/src/test/java/com/flockyou/ui/DeviceHistoryMapPolicyTest.kt`

**Interfaces:**
- Produces: `MapScope.AllDetections | MapScope.DeviceHistory(detectionId)`.

- [ ] **Step 1: Write failing tests** proving device scope includes only that device's located sightings, preserves timestamp order, and never creates points for null coordinates.
- [ ] **Step 2: Run targeted tests** and verify the current map only consumes latest canonical locations.
- [ ] **Step 3: Implement** numbered chronological markers, first/last badges, optional path line, visible time/signal legend, and marker tap → exact sighting drawer.
- [ ] **Step 4: Stress with deterministic 1,000+ detection fixture** and assert device-scoped history does not materialize unrelated histories.
- [ ] **Step 5: Run map/unit tests** and commit `feat(map): add device sighting history overlay`.

### Task 6: Android acceptance

**Files:**
- Add/modify Android tests under `app/src/androidTest/java/com/flockyou/`

- [ ] **Step 1:** Build `sideloadDebug`, install on connected phone/tablet, grant only normal runtime permissions through UI/ADB test harness.
- [ ] **Step 2:** Start scanning and prove raw scanner counters rise.
- [ ] **Step 3:** Re-observe a real device and prove `SIGHTINGS` rises while `UNIQUE DEVICES` remains stable.
- [ ] **Step 4:** Open that device, verify timestamped evidence rows, then open `MAP HISTORY` and verify map pins correspond to recorded coordinates.
- [ ] **Step 5:** Capture screenshots/UI tree/logcat and commit `test(android): prove sighting history and map flow`.