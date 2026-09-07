# Flock-Sucker Evidence-Core Hardening Design

Date: 2026-09-05
Status: Design approved in chat; written specification pending user review; implementation not started
Branch: `arch/evidence-core-hardening-r1`
Base: `origin/main` at `956d97d`

## 1. Purpose

Flock-Sucker will become an evidence-grade counter-surveillance and wireless situational-awareness application in which raw observations, classifications, identity resolution, behavioral inference, and operator intent remain distinct layers.

The primary failure mode being corrected is false certainty created by lossy persistence and heuristic identity merging. The Sep-5 field investigation demonstrated that the current build can:

- classify generic Samsung manufacturer traffic as SmartTag;
- classify ambiguous Apple traffic as AirTag;
- count Find My-like traffic toward a Flipper-spam threshold;
- attach an aggregate BLE-spam verdict to an unrelated packet that happened to trigger promotion;
- merge unrelated devices through service-UUID and composite heuristics;
- discard the per-observation source identifier and raw evidence needed to reconstruct a trajectory;
- produce convincing multi-location histories that cannot be proven to represent one physical device.

The redesign preserves all existing scanner capability while making every inference auditable, replayable, and explicitly confidence-bounded.
## 2. Non-negotiable invariants

1. **Observed evidence is immutable.** Classification or resolver changes may create new assertions or entity links but may not rewrite the captured observation.
2. **No weak signal establishes physical identity alone.** Generic service UUIDs, vendor OUIs, RSSI similarity, device type, manufacturer, SSID, or advertisement shape cannot individually merge devices.
3. **Technique detection is separate from hardware attribution.** A burst, spoofing pattern, jammer indicator, or Remote-ID frame is classified independently from the hardware believed to emit it.
4. **Every identity decision is explainable.** Resolver version, rule ID, evidence inputs, score, decision, and rejected alternatives are retained.
5. **Every following/co-travel conclusion is evidence graded.** Reappearance and motion correlation may be reported; ownership, operator identity, and intent are never inferred without independent evidence.
6. **Legacy uncertainty is preserved.** Historical rows missing source identifiers or payloads are labeled `identity_unverifiable`; missing evidence is never reconstructed by assumption.
7. **Flock-Sucker is the permanent product name.** Compatibility identifiers may retain `flockyou`, but visible/source branding cannot regress to Flock You/Flock-You.
8. **Local CI is the promotion authority.** GitHub CI is supplemental unless a gate can only execute remotely.

## 3. Repository convergence boundary

Implementation will occur in an isolated convergence worktree. The dirty primary checkout is never reset, rebased, cleaned, or force-updated.

Before implementation merges, all first-party divergent work is inventoried by commit and semantic capability. Unique behavior is reconciled; duplicate or superseded code is not blindly stacked.

Known reconciliation inputs at design time include:
- `origin/main` at `956d97d`;
- vendor hardening commit `c25c431`;
- open R7 hot-code branch/PR with six unique commits relative to current main;
- local adversarial-sensor worktree with substantial uncommitted scanner/native/Magisk work;
- later R8/R9 documentation/acceptance branches;
- dependency PRs, which are evaluated individually and are not considered product-feature convergence by default.
## 4. Evidence-core data model

The current `Detection` object remains a compatibility/UI projection during migration. It is no longer the authoritative raw record.

New authoritative entities:

### `Observation`
One scanner ingress event before identity inference.

Required fields include:
- observation UUID, scan/session UUID, wall-clock timestamp, monotonic timestamp;
- protocol and source scanner/lane;
- scanner capability/health generation;
- observed MAC/BSSID and BLE address type when available;
- advertised name/SSID;
- RSSI, TX power, PHY, channel/frequency and channel width when exposed;
- complete bounded manufacturer-data map;
- complete bounded service-UUID set and service-data map;
- Wi-Fi information-element digest/details when exposed;
- location, altitude and accuracy only when privacy settings permit capture;
- normalized raw-evidence digest and schema/parser version;
- optional bounded raw metadata needed for future replay.

`Observation` rows are append-only. Reprocessing produces new assertions, never edited observations.
### `Assertion`
A versioned statement derived from one or more observations. Examples:
- vendor OUI match;
- Apple Find My frame-family match;
- Apple proximity-pairing frame-family match;
- Samsung manufacturer advertisement;
- validated SmartTag protocol match;
- Remote ID Basic ID/location message;
- ALPR/body-camera/drone vendor signature match;
- RF burst or following-like behavior.

Each assertion stores `ruleId`, `ruleVersion`, `classifierVersion`, evidence observation IDs, confidence, corroboration level, exclusion checks, and human-readable proof boundary.

### `Entity`
A hypothesized physical or logical device assembled from observations only when identity evidence permits it. Entity IDs are internal and stable across resolver replays.

### `IdentityLink`
A versioned resolver edge from observation to entity. It stores the resolver version, matching evidence, score, decision class (`strong`, `probable`, `possible`, `rejected`), and reasons.

### Compatibility projection
Existing `Detection` and `Sighting` UI/history surfaces are populated from the evidence core during transition. New sightings must include the actual observed identifier, source scanner, raw-evidence digest, resolver decision/provenance, and observation ID.
## 5. Identity resolver

Identity resolution is protocol-aware and conservative. It is not a generic fuzzy matcher.

Strong evidence may include:
- exact globally administered Wi-Fi BSSID where the protocol semantics make it stable;
- exact non-randomized hardware address with corroborating protocol evidence;
- validated Remote ID UAS identifier/serial;
- protocol-defined stable public-key material or other documented stable identifier;
- exact payload-level identifiers whose semantics are vendor/protocol documented.

Weak evidence may contribute to similarity but can never independently merge entities:
- OUI/manufacturer;
- device type;
- SSID or advertised-name similarity;
- RSSI proximity;
- generic/common service UUIDs;
- advertisement length/shape;
- same detection method.

Randomized-address devices are linked only through documented stable payload features plus temporal/spatial continuity. If no stable feature exists, observations remain separate or `possibly_related`; the resolver does not invent continuity.
## 6. Fingerprint stack

Fingerprints are independent evidence products, not a single identity hash.

- **L0 Raw fingerprint:** SHA-256 of canonicalized exact packet/frame evidence.
- **L1 Hardware fingerprint:** stable address/OUI/radio capability evidence when exposed.
- **L2 Protocol fingerprint:** manufacturer IDs, service UUID/service-data schemas, Wi-Fi IEs, beacon/Remote-ID families, DHCP/mDNS/classic-BT features.
- **L3 Payload fingerprint:** stable and rotating byte regions, counters, serial/UAS IDs, key-derived fragments and model-specific fields.
- **L4 Behavioral fingerprint:** advertisement cadence distribution, channel use, address-rotation interval, payload rotation, RSSI statistics and burst behavior.
- **L5 Spatial-temporal fingerprint:** independent location clusters, reappearance intervals, plausible-speed continuity and home/static baselines.
- **L6 Corroborated entity fingerprint:** resolver output composed only from protocol-appropriate evidence.

Each layer has its own schema/version and may be compared independently. Matching L2/L4 shape alone means `similar_behavior`, not `same_device`.

The existing BLE co-traveler hash based on field lengths, UUIDs, TX power and name shape is retained only as a low-confidence similarity feature. It cannot create an entity or following alert without stronger evidence.

## 7. Tracker protocol parsing

Tracker classification must use protocol semantics rather than company ID alone.

Apple manufacturer data is parsed into distinct frame families. Find My/offline-finding, proximity pairing/accessory setup, iBeacon and other recognized Apple families become separate assertions. Ambiguous frames remain `APPLE_BLE_ACCESSORY`/`APPLE_FIND_MY_LIKE` until the required subtype evidence exists.

Samsung company ID `0x0075` becomes `SAMSUNG_BLE_MANUFACTURER_DATA` by default. SmartTag promotion requires a validated SmartTag service/payload signature, with negative fixtures for Samsung TVs/phones/other consumer hardware.
Tile, Chipolo, Pebblebee and other major Find My/Google Find Hub-compatible trackers may be added only where the broadcast protocol provides defensible observable distinctions. Brand compatibility with a tracker network is not itself a radio signature.

Every tracker rule must include ordinary-device negative controls to prevent company-ID or generic-service overclassification.

## 8. BLE/RF burst analysis

The existing hardware-specific `FLIPPER_ZERO_SPAM` promotion is replaced by technique-first events.

Examples:
- `APPLE_ACCESSORY_ADVERTISEMENT_BURST`;
- `FAST_PAIR_ADVERTISEMENT_BURST`;
- `BLE_ADDRESS_CHURN_BURST`;
- `BLE_PAYLOAD_REPLAY_PATTERN`;
- `BLE_SYNTHETIC_ADVERTISEMENT_SUSPECTED`.

A burst assertion stores packet count, rate distribution, distinct source addresses, address churn/entropy, frame-family distribution, payload diversity, duplicate/replay ratio, RSSI distribution, duration and contributing observation IDs.

Hardware attribution is a separate assertion and defaults to `UNKNOWN`. A packet that happens to cross an aggregate threshold cannot inherit the aggregate event as its device type or manufacturer.

Flock-Sucker may state that behavior is consistent with known BLE-spam tooling only when technique evidence supports that statement; it must not claim Flipper Zero hardware without independent hardware-specific evidence.
## 9. Following and co-travel inference

Following analysis operates on resolved entities and evidence quality, not raw `Detection.seenCount`.

Three explicit result classes are used:

1. `MULTI_LOCATION_REAPPEARANCE` — a high-confidence identity appears at multiple independently separated locations.
2. `CO_MOVEMENT_CONSISTENT` — repeated time/distance/RSSI progression is statistically consistent with handset movement across multiple windows.
3. `FOLLOWING_LIKE_PATTERN` — a stricter threshold requiring departure/reappearance behavior, multiple separated clusters, plausible travel speed, persistence outside a static/home baseline, and false-positive suppression.

Minimum evidence for following-like promotion includes:
- identity confidence above the protocol-specific threshold;
- at least three independent spatial clusters unless a stronger stable identifier permits an explicit exception;
- at least two movement episodes separated in time;
- no impossible-speed transition;
- persistence beyond a short single-site observation window;
- explicit home/neighbor/commute/static-infrastructure baseline tests;
- a confidence score with reasons and competing false-positive explanations.

The UI may say “following-like pattern” or “co-movement consistent.” It may not state that a person, company, agency or operator is intentionally following the user without separate evidence.
## 10. Drone and Remote ID architecture

Drone identification gains a canonical Remote ID parser instead of relying primarily on SSID/model-name regexes.

The parser supports ASTM F3411/OpenDroneID transport evidence exposed through BLE and Wi-Fi. Where present, observations preserve and assertions decode:
- Basic ID / UAS identifier;
- location/vector messages;
- system/operator information;
- self-ID and authentication message classes where available;
- transport, frame version, sequence/timing and raw-evidence digest.

Remote ID identity can seed a strong entity when protocol semantics support stability. Model/vendor inference remains separate unless explicitly encoded or corroborated.

SSID/name signatures for DJI, Skydio, Autel, BRINC, Draganfly, Parrot, Yuneec, Red Cat/Teal, Inspired Flight, Freefly and other platforms remain supplemental evidence, not a replacement for Remote ID decoding.

All drone matchers consume one canonical drone-signature catalog. Duplicated regexes in `RfDetectionHandler`, `RfSignalAnalyzer`, pattern catalogs and future scanners are removed or converted to adapters over that catalog.

## 11. Vendor/signature knowledge base

Production signatures are structured records, not loose regexes. Each signature stores vendor, product family, observable type, exact match rule, corroboration requirements, exclusions/negative rules, confidence tier, source/reference, source date and signature version.
Seed coverage must include, at minimum, these surveillance/public-safety technology families where defensible observables exist:

- **ALPR / vehicle intelligence:** Flock Safety; Motorola Solutions/Vigilant; Genetec AutoVu; Rekor; Leonardo/ELSAG; Neology/PIPS; PlateSmart; LiveView Technologies where product observables are available; Sensys Gatso; All Traffic Solutions.
- **Body/in-car video:** Axon; Motorola WatchGuard/V300/M500/4RE/SVX families; Getac Video; Safe Fleet/COBAN FOCUS; Axis body-worn/in-car; Digital Ally; i-PRO; Utility Associates; Reveal; Wolfcom; Zepcam where observable evidence exists.
- **Fixed/network video:** Verkada; Axis; Avigilon/Motorola; Hanwha Vision; Bosch; i-PRO/Panasonic; Pelco; Teledyne FLIR; Eagle Eye; Rhombus; Genetec appliances.
- **Acoustic/public-safety sensors:** Flock Raven; SoundThinking/ShotSpotter; Shooter Detection Systems and other documented acoustic-sensor products with identifiable radios.
- **Public-safety UAV:** Skydio; Autel Robotics; BRINC; DJI enterprise/public-safety families; Draganfly; Parrot; Yuneec; Red Cat/Teal; Inspired Flight; Freefly.
- **Public-safety/mobile connectivity:** Ericsson Cradlepoint; Semtech/Sierra Wireless AirLink; Peplink; Digi; Teltonika; Inseego. These remain connectivity/context classifications unless product/agency evidence independently supports a surveillance role.
- **Cellular/radio/forensics:** L3Harris and other products only where Android/modem/radio observables can defensibly identify them; Cellebrite/Magnet/Graykey software context is not converted into RF signatures without hardware observables.
- **Component vendors:** Quectel, Nordic, Espressif, TI and similar component manufacturers are identified as components only. Their OUIs/chips never imply surveillance use by themselves.

User-supplied aliases are normalized where verified (`Skidoo X10` → `Skydio X10`). Ambiguous names such as `Windows Iris`, `Axion 10000` and `Site Gantry` remain research aliases until a vendor/product mapping and observable signature are proven.
## 12. Branding permanence

Visible product identity is exactly `Flock-Sucker` across sideload, system and OEM flavors, Android Auto, notifications, exports, lock screens, test fixtures and user-facing documentation.

Compatibility identifiers may remain unchanged where migration would damage continuity, including package namespace/application IDs, database filename, deep-link schemes, Java/Kotlin package names and migration references containing `flockyou`.

The branding guard is upgraded to:
- scan source/resources/manifests/exporters/Gradle/docs/test-visible strings case-insensitively;
- reject `Flock You`, `Flock-You`, `FLOCK YOU` and other legacy product renderings unless explicitly allowlisted as historical text;
- verify resolved `app_name`, system/OEM names and Android Auto label for every flavor;
- run locally and in CI;
- fail release assembly on regression.

`settings.gradle.kts` project identity becomes `Flock-Sucker`. Export metadata such as GPX/KML creator strings is migrated to `Flock-Sucker` without changing file-format compatibility.

## 13. Database migration

The evidence core requires a Room schema migration from v11 to the next schema version. Migration is additive and non-destructive.

Existing `detections` and `sightings` remain readable. New observation/assertion/entity/link tables are introduced, and new sighting provenance fields are added where needed. Existing rows are not retroactively assigned source MACs or raw payloads that were never stored.

Legacy rows receive a provenance state indicating whether identity can be independently reconstructed. Migration tests must cover a real v11 schema fixture and verify encrypted-database opening without destructive fallback.
## 14. Performance and storage constraints

Evidence fidelity must not make continuous scanning unusable on the target Moto-class hardware.

Implementation requirements:
- canonicalization and hashing remain allocation-bounded on scanner hot paths;
- raw payload storage is bounded per protocol and never stores unbounded log text;
- common parsed fields are indexed; large evidence blobs are not indexed;
- observation writes are batched/transactional where safe without losing per-packet provenance;
- classification and expensive cross-observation inference run off the scanner callback thread;
- resolver replay is incremental and checkpointable;
- retention honors existing privacy/retention settings and can prune observations without leaving dangling assertions/entities;
- UI lists consume projections, not raw observation scans.

Performance gates include scanner ingress/drop telemetry, database write latency, callback latency, memory growth and sustained-scan battery/thermal behavior. Optimization may never silently discard the identity evidence required by this design.

## 15. Privacy and evidence security

The encrypted database remains the default persistent store. Raw evidence is local-first and does not trigger automatic external enrichment.

Location capture continues to obey the user's privacy mode. Exports explicitly identify whether records contain precise location or raw radio identifiers.

Raw evidence shown in the UI must be distinguishable from inference. Evidence digests allow later integrity checking. External lookups such as Shodan/OUI/vendor research remain user-initiated or governed by existing opt-in policy; they do not mutate the original observation.
## 16. UI evidence semantics

Detection detail surfaces expose five independently labeled sections:
1. **Observed:** immutable radio/network facts.
2. **Matched:** signature/classifier assertions with rule IDs and confidence.
3. **Identity:** entity-resolution evidence and confidence.
4. **Behavior:** burst/reappearance/co-movement/following-like analysis.
5. **Unknowns:** material evidence gaps and competing explanations.

Threat scores remain useful for prioritization but are not displayed as probabilities of malicious intent. Hardware/vendor attribution is visually separated from technique alerts.

A user must be able to answer “why did Flock-Sucker call this X?” from the detail screen without reading logcat or source code.

## 17. Sep-5 forensic regression corpus

The preserved Sep-5 artifacts become private/local regression fixtures or privacy-scrubbed derived fixtures; precise personal location evidence is not committed publicly.

Required regressions include:
- Samsung `0x0075 / 42 04 01 80...` must not promote to SmartTag without SmartTag-specific corroboration;
- Apple `0x07` alone must not promote to AirTag;
- Apple Find My-like traffic may contribute to a technique-level burst but must not independently prove Flipper hardware;
- aggregate Apple burst promotion may not rewrite a Quectel/Espressif triggering packet into a Flipper device;
- generic BLE fingerprint-shape collisions may not merge physical identities;
- exact Cradlepoint-style BSSID reappearance may produce multi-location evidence while remaining neutral on ownership/intent;
- legacy sightings lacking observed identifiers remain `identity_unverifiable`.
## 18. Test strategy

Implementation is red-first for each semantic defect.

Minimum local gates before promotion:
- unit tests for canonicalization, fingerprint layers, parser semantics, resolver decisions and negative controls;
- Room v11→new-version migration tests and encrypted open/reopen tests;
- sideload, OEM and system debug unit-test suites;
- native/RTL-SDR tests where scanner convergence touches native code;
- branding regression tests plus `scripts/verification/check-product-branding.sh`;
- fixture replay of Sep-5 derived packet/event cases;
- catalog-schema validation that rejects production signatures lacking sources, versions, corroboration or exclusion rules;
- Remote ID parser fixtures covering valid/invalid Basic ID, location/vector and multi-message cases;
- `git diff --check`;
- assemble sideload/system/OEM debug artifacts as supported by the project;
- install on the rooted Moto and prove observation provenance in the live database/export;
- live verification that a new BLE observation stores actual observed MAC/address type/payload digest before dedupe;
- live verification that a burst event references contributing observations rather than impersonating one of them.

A test count is evidence only for the exact commit and exact command that produced it. Prior green counts are never carried forward as proof for a new merge.

## 19. Convergence sequence

Implementation proceeds in this order:
1. preserve/inventory every divergent first-party branch and dirty worktree;
2. land permanent branding guard and cleanup;
3. add evidence-core schema + migration + immutable observation writer;
4. make BLE/Wi-Fi ingress preserve raw identifiers/evidence before classification;
5. replace lossy dedupe with protocol-aware identity resolver and compatibility projections;
6. implement strict Apple/Samsung tracker parsers and burst separation;
7. implement fingerprint stack and evidence-graded co-travel logic;
8. implement canonical drone catalog + Remote ID decoder;
9. migrate vendor signatures into evidence-backed catalog with negative controls;
10. reconcile unique R7/R8/R9/adversarial/vendor work; then run full local gates;
11. create one convergence PR, review exact diff/commit, merge only after green evidence and Moto verification.
## 20. Acceptance criteria

The architecture is complete only when all of the following are true:

- no visible product surface in release artifacts renders a legacy Flock You/Flock-You name;
- a newly captured BLE/Wi-Fi observation can be reconstructed from persisted evidence without consulting logcat;
- no production identity path merges devices solely on service UUID, manufacturer/OUI, RSSI, device type, SSID, or advertisement shape;
- Samsung company ID alone cannot produce SmartTag;
- ambiguous Apple proximity traffic alone cannot produce AirTag;
- Find My traffic and BLE-spam technique scoring are separated from Flipper hardware attribution;
- every burst event references its contributing observations and does not inherit a bystander packet's identity;
- following-like alerts expose identity confidence, movement evidence, false-positive checks and proof boundary;
- Remote ID frames are parsed through one canonical implementation and available to all drone detection paths;
- vendor signatures are versioned, sourced and carry negative/exclusion rules;
- legacy histories with missing identifiers are visibly marked unverifiable rather than silently merged;
- sideload/OEM/system tests, migration gates, branding gates and applicable native tests pass on the exact convergence commit;
- the convergence build installs and records evidence correctly on the Moto before main is merged.

## 21. Explicit non-goals

This project does not attempt to identify a human operator from passive RF evidence, infer malicious intent from vendor presence, bypass access controls, interfere with radios, or turn a component OUI into an accusation.

It also does not require renaming the `com.flockyou` package namespace or encrypted database filename. Those are compatibility identifiers and changing them would create needless migration risk.

The evidence-core work does not broaden active-probing authority. Existing active security-research features remain governed by their current authorization and safety boundaries.