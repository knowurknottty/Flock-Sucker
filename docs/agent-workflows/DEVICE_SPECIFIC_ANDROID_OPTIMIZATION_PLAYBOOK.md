# Device-Specific Android Optimization Playbook

## Purpose

This playbook defines a reproducible, evidence-driven method for optimizing an Android application and its local `llama.cpp` inference runtime for one specific physical device without contaminating the general product branch.

It is intentionally explicit enough that a capable coding agent can execute it with minimal interpretation. The agent must treat every unmeasured assumption as a hypothesis, every numerical improvement as a claim requiring evidence, and every device-specific change as isolated until independently reviewed.

This document is written for constrained or older Android hardware, but the workflow applies to almost any Android phone or tablet with ADB access.

## Core operating contract

The agent MUST optimize against a real physical target when making CPU, memory, thermal, battery, radio-throughput, or native-inference claims.

The agent MUST NOT merge the device-specific optimization branch into the general product branch unless a human explicitly decides the specialization is broadly safe.

The agent MUST preserve correctness, evidence integrity, privacy/security guarantees, scanner coverage, and user-visible truth while optimizing.

The agent MUST NOT manufacture speed by disabling scanners, reducing evidence capture, weakening encryption, hiding errors, lowering model quality without disclosure, or changing the benchmark workload.

The agent MUST measure before and after under comparable conditions. Source inspection may justify "less work" but not a numerical performance claim.
## Required terminology

**Baseline** means the exact repository SHA, build flavor, settings, model artifact, device state, and test scenario used before an optimization.

**Candidate** means one proposed optimization measured against that baseline.

**Recursion** means one complete cycle of: measure → identify dominant cost → change one coherent thing → test → benchmark → keep or revert → establish the new baseline.

**Keep gate** means the candidate improved the target metric without violating correctness or guardrails.

**Revert gate** means the candidate failed to improve meaningfully, introduced regressions, or produced ambiguous evidence.

**Capability truth** means the app reports what the device and subsystem are actually doing, not merely what was configured or requested.

**Device specialization** means a branch whose behavior may intentionally differ from the general application because it is tuned for one hardware profile.

## Required directory convention

Use an isolated worktree outside the primary checkout. Recommended form:

```text
~/.<project>-worktrees/<device-or-purpose>/
```

Use an explicitly device-specific branch name, for example:

```text
perf/<device>-llama-repo-r5
```

The primary checkout MUST remain clean and untouched while specialization work proceeds.
## Phase 0 — Establish isolation before profiling

### Goal

Create a trustworthy starting point that can be reproduced, compared, abandoned, or reviewed without ambiguity.

### Mandatory sequence

1. Identify the authoritative repository checkout.
2. Run `git status --short --branch` and record the output.
3. Record `git rev-parse HEAD` as the baseline SHA.
4. STOP if the baseline checkout contains unexplained local changes.
5. Create a new branch from the explicitly chosen baseline SHA.
6. Create an isolated worktree for that branch.
7. Copy only machine-local configuration required to build, such as `local.properties`; never copy build outputs or caches into version control.
8. Record the worktree path, branch name, and baseline SHA in the evidence log.

Example:

```bash
repo=/path/to/project
baseline_sha="$(git -C "$repo" rev-parse HEAD)"
branch=perf/example-device-llama-repo-r5
worktree="$HOME/.project-worktrees/example-device-r5"
mkdir -p "$(dirname "$worktree")"
git -C "$repo" worktree add -b "$branch" "$worktree" "$baseline_sha"
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > "$worktree/local.properties"
```
### Phase 0 PASS criteria

PASS only when all of the following are true:

- the authoritative checkout is identified;
- the baseline SHA is recorded;
- the specialization worktree is on its own branch;
- the primary checkout remains clean;
- no unrelated changes were imported into the worktree;
- the Android SDK/NDK configuration is known;
- the branch is explicitly intended to remain unmerged until reviewed.

REVERT or STOP if the baseline is uncertain. Performance work on an uncertain baseline is not interpretable.

## Phase 1 — Fingerprint the physical device

### Goal

Convert "old Android phone" into a concrete hardware capability profile. Never tune compiler flags, thread counts, memory limits, GPU paths, or native kernels from the marketing model name alone.

### Device selection

Run:

```bash
adb devices -l
```

If more than one device is connected, select one serial explicitly and use `adb -s "$SERIAL"` for every command. Never allow the host to choose an arbitrary first device during comparative measurements.

Record the device serial in the private evidence bundle, but do not publish unique serials unless required.
### Identity and OS fingerprint

Record at minimum:

```bash
adb -s "$SERIAL" shell getprop ro.product.manufacturer
adb -s "$SERIAL" shell getprop ro.product.model
adb -s "$SERIAL" shell getprop ro.product.device
adb -s "$SERIAL" shell getprop ro.build.version.release
adb -s "$SERIAL" shell getprop ro.build.version.sdk
adb -s "$SERIAL" shell getprop ro.product.cpu.abilist
adb -s "$SERIAL" shell getprop ro.hardware
adb -s "$SERIAL" shell getprop ro.board.platform
```

Interpretation rules:

- `ro.product.model` identifies the marketed device but is not sufficient for compiler tuning.
- `ro.product.device` helps distinguish hardware variants sold under similar marketing names.
- Android API level constrains native library packaging, permissions, background execution, and available profiling APIs.
- `ro.product.cpu.abilist` determines which ABI can execute; a device-specific build should normally package only the required ABI.
- `ro.hardware` / board platform may reveal the SoC family and should be corroborated with `/proc/cpuinfo`.

Do not infer CPU extensions from an SoC name when the kernel exposes the actual instruction feature list.
### CPU feature fingerprint

Read the kernel-advertised CPU feature set:

```bash
adb -s "$SERIAL" shell cat /proc/cpuinfo
```

Record, per processor where exposed:

- implementer;
- architecture;
- variant;
- part number;
- revision;
- `Features` line;
- hardware / SoC identifier.

For ARM64 llama.cpp work, explicitly record whether the feature list contains `asimd`, `dotprod`, `i8mm`, `sve`, `sve2`, `fp16`, or other relevant extensions.

MUST NOT enable an ISA extension merely because a newer CPU in the same product family supports it. Use the target device's actual advertised features as the compile/runtime authority.
### CPU topology and frequency fingerprint

Read each CPU's min/max/current frequency where the kernel exposes cpufreq:

```bash
adb -s "$SERIAL" shell '
for c in 0 1 2 3 4 5 6 7; do
  p=/sys/devices/system/cpu/cpu$c/cpufreq
  printf "cpu%s " "$c"
  cat "$p/cpuinfo_min_freq" 2>/dev/null | tr "\n" " "
  cat "$p/cpuinfo_max_freq" 2>/dev/null | tr "\n" " "
  cat "$p/scaling_cur_freq" 2>/dev/null | tr "\n" " "
  echo
done'
```

Do not assume CPU index ordering means "little first" or "big first". Group cores by maximum frequency and corroborate with CPU-part information when available.

Thread-count hypotheses MUST be derived from measured throughput and interference. A device with eight logical CPUs does not imply eight llama.cpp inference threads are optimal.
### RAM, storage, thermal, and battery fingerprint

Record:

```bash
adb -s "$SERIAL" shell cat /proc/meminfo
adb -s "$SERIAL" shell df -h /data /sdcard
adb -s "$SERIAL" shell dumpsys thermalservice
adb -s "$SERIAL" shell dumpsys battery
```

Capture at minimum total RAM, available RAM, swap/zram where visible, free app-storage space, battery level, charging state, voltage, temperature, and current thermal status.

For repeatable benchmarks, document whether the device is charging. Do not compare a charging baseline with an unplugged candidate and call the difference an optimization.

STOP if available storage is too low to hold both the model and temporary build/runtime artifacts safely. STOP if the device is already thermally throttled before baseline capture.

### Phase 1 required artifact

Create a machine-readable `device-profile.json` plus a human-readable summary. Never put unique user/device secrets in public documentation.
## Phase 2 — Establish the application baseline

### Build truth

Record the exact Gradle task, flavor, build type, version code/name, package ID, and signing mode. Use the same build configuration for every before/after comparison unless the build configuration itself is the variable under test.

Run the narrowest existing unit tests covering the subsystem first, then the broader suite required by the repository.

Example:

```bash
./gradlew :app:testSideloadDebugUnitTest
./gradlew :app:assembleSideloadDebug
```

Do not treat a successful compilation as runtime verification.

### Install truth

Record the APK SHA-256 before installation. Install with ADB, verify the package/version on the physical target, then launch through the normal user-visible entry point.

If upgrading over existing data, document that fact. If using a clean install, document that fact. Never compare a migrated-data baseline to a clean-data candidate without stating the difference.
### Define fixed benchmark scenarios

Each scenario must be short enough to attribute and long enough to stabilize.

At minimum define:

1. cold launch to steady scan;
2. steady scanner acquisition while idle;
3. high-event scanner burst or deterministic fixture;
4. local model cold load;
5. local model warm prompt;
6. local model generation while scanners remain active;
7. history/map interaction with realistic data volume;
8. background/resume and process-recreation behavior.

For each scenario record duration, user actions, enabled scanners, permissions, screen state, dataset/model, and expected outputs.

The candidate run MUST use the same scenario definition. If the scenario changes, create a new baseline instead of comparing unlike runs.
### Required baseline measurements

Capture the strongest evidence available for each scenario:

- wall-clock latency for bounded operations;
- `dumpsys meminfo` for Java/native/PSS memory;
- Perfetto for scheduler, Binder, frequency, main-thread, and lifecycle timing;
- Simpleperf for CPU hotspot attribution when the build is profileable/debuggable;
- `dumpsys gfxinfo ... framestats` for quick UI frame/jank evidence;
- logcat for errors, retries, lifecycle transitions, and scanner failures;
- app-owned counters for observations, drops, queue depth, and scanner rates;
- battery/thermal snapshots before and after longer scenarios.

A single metric is never sufficient for a system-level optimization. Example: higher tokens/sec is not a win if scanner drops rise materially or the device enters severe thermal throttling sooner.

### Stabilization rule

Run a scenario multiple times when noise is material. Prefer medians and p95/p99 distributions over the single best run. Record outliers; do not silently discard them.
## Phase 3 — Model artifact truth and reproducibility

Before integrating or benchmarking a local model, record:

- canonical artifact name;
- source URL or repository;
- exact byte size;
- SHA-256;
- model format;
- quantization;
- tokenizer/chat-template expectations when known;
- licensing/provenance notes required by the project.

Download once to a controlled host location, verify size and SHA-256, then stage to the device and verify the SHA-256 again on-device.

If the device-side hash differs, STOP. Do not benchmark a corrupt or transformed artifact.

A model is NOT `READY` because the file exists. Required readiness sequence is:

`DOWNLOADED → HASH_VERIFIED → FORMAT_PARSED → RUNTIME_LOADED → SELF_TEST_PASSED → INFERENCE_READY`.

Any UI that compresses these states must preserve the underlying truth and expose the failure reason.
## Phase 4 — Integrate llama.cpp as a first-class runtime

### Pin upstream

Use a pinned llama.cpp commit, preferably as a submodule or vendored source with explicit provenance. Never build production/device-specialized behavior from an unrecorded moving `master`/`main` HEAD.

Record:

```bash
git -C third_party/llama.cpp rev-parse HEAD
```

### Native-build principles

For a one-device branch, prefer the narrowest truthful native target:

- package only the required ABI;
- disable unrelated examples/tests/tools in the app build;
- disable GPU/Vulkan/OpenCL paths unless the target and test plan explicitly use them;
- disable multi-architecture CPU variants when the device profile proves one fixed architecture;
- enable only ISA extensions proven by `/proc/cpuinfo` and validated on-device;
- preserve required llama/ggml functionality and error diagnostics.

A smaller native build is not automatically faster. Binary-size reduction and runtime-speed improvement must be reported separately.
### Conservative ARM64 starting point

For an ARMv8-A device that exposes ASIMD/NEON but not DOTPROD, I8MM, or SVE, a conservative starting configuration is conceptually:

```text
ABI: arm64-v8a only
GGML_NATIVE: OFF
GGML_CPU_ALL_VARIANTS: OFF
GGML_CPU_ARM_ARCH: armv8-a
GGML_CPU_KLEIDIAI: OFF initially
GGML_OPENMP: OFF initially
GPU backends: OFF initially
```

These are hypotheses, not universal best settings. The purpose is to establish a safe executable baseline before testing one optimization dimension at a time.

Do not enable `armv8.2-a+dotprod`, `i8mm`, SVE, or another extension when the target does not advertise it. An illegal-instruction crash is not an optimization failure; it is a process failure caused by violating capability truth.

### JNI contract

The native layer must expose explicit lifecycle operations: initialize runtime, load model, configure context, set system prompt where applicable, process user prompt, generate/stream tokens, cancel, benchmark, unload model, and destroy runtime.
### JNI implementation rules

The JNI layer MUST avoid needless copying of large prompt/model buffers. Keep long-lived native handles instead of reconstructing the runtime per token or per UI event.

Token streaming MUST use bounded delivery. Emitting one Kotlin object and one UI recomposition for every token can dominate inference on weak hardware even when native generation is acceptable.

Cancellation MUST propagate to native generation promptly and return the engine to a defined state. A cancelled request must not leave a hidden decode loop consuming CPU.

Unload and destroy are different operations: unload should free model/context resources while preserving a reusable runtime where appropriate; destroy should release all native backends, worker state, and JNI-owned resources.

Every native error must cross the JNI boundary as a concrete runtime status or exception. Never convert native failure into a generic `READY` or rules-only success state.

### First runtime acceptance gate

Before optimizing, prove one real prompt produces tokens from the exact verified GGUF on the physical device. Capture model-load logs, system info, first token, completion/cancellation behavior, and memory before/after unload.
## Phase 5 — llama.cpp recursion R1: architecture and thread topology

### Question

What is the fastest stable CPU configuration that does not starve the app's primary sensor/evidence work?

### Variables to test independently

- decode thread count;
- prompt/batch thread count;
- affinity strategy if safely controllable;
- architecture/ISA flags proven by the device;
- OpenMP on/off only if supported and measurable;
- one fixed CPU backend versus runtime multi-variant dispatch.

Start with conservative thread counts such as 1, 2, 4, and the number of performance-cluster cores. Do not jump directly to all logical CPUs.

### Metrics

Measure prompt processing tokens/sec, generation tokens/sec, TTFT, CPU utilization, scanner event rate/drop counters, UI responsiveness, and thermal change.

KEEP the fastest setting that preserves scanner/evidence guardrails. REVERT a faster llama setting if it materially degrades acquisition or causes thermal collapse during the required workload.
## Phase 6 — llama.cpp recursion R2: memory and model loading

### Question

How little memory can the runtime use without creating load churn, cache misses, quality regressions, or scanner interference?

### Variables

- context size;
- KV-cache type/quantization where supported;
- mmap behavior;
- mlock usage only when justified and permitted;
- batch and micro-batch sizes;
- whether to keep the model/context warm between requests;
- prompt/system-prefix reuse where semantically safe.

Measure cold-load time, warm-load/reuse time, native heap/PSS, peak memory, allocation growth, page faults when observable, TTFT, and scanner continuity during load.

On low-RAM devices, a slightly slower model configuration that avoids memory pressure, LMK risk, or repeated reloads may be the superior operating point.

Do not reduce context below the product's required workload merely to improve memory numbers. If a smaller device-specific context is an intentional product tradeoff, expose and document it explicitly.
## Phase 7 — llama.cpp recursion R3: scheduler, batch, and latency

### Question

How should prefill and decode be scheduled so interactive latency is acceptable without monopolizing the phone?

### Variables

- `n_batch` / batch size;
- micro-batch size;
- prompt-processing threads versus decode threads;
- maximum generated tokens for the product scenario;
- prompt compression only when semantic quality is tested;
- inference priority relative to sensor acquisition and UI work;
- cancellation polling frequency.

Benchmark prefill and decode separately. TTFT is often dominated by prompt processing while sustained generation is dominated by decode; one configuration may not optimize both.

The app's evidence/sensor path has priority over convenience AI. Under contention, throttle/pause generation before dropping raw observations.

KEEP only configurations that improve the defined latency/throughput objective and remain inside scanner-drop, thermal, memory, and quality guardrails.
## Phase 8 — llama.cpp recursion R4: JNI and token-delivery hot path

### Question

Is Java/Kotlin/JNI/UI overhead wasting a meaningful fraction of the native inference gain?

### Inspect

- JNI calls per generated token;
- temporary strings/byte arrays;
- UTF-8 conversion frequency;
- coroutine/context switches;
- Flow emissions;
- main-thread work;
- Compose recompositions caused by streaming;
- logging in the generation loop.

Prefer bounded token chunks or timed coalescing for UI delivery while preserving responsive cancellation and useful streaming. Keep native generation state long-lived and avoid rebuilding samplers/context unnecessarily.

Profile before changing the protocol. A theoretically cleaner JNI API that does not improve measured CPU/latency is not automatically worth the maintenance cost.

Regression tests MUST cover correct Unicode reconstruction, complete output, cancellation, engine reuse, unload/reload, and error propagation.
## Phase 9 — llama.cpp recursion R5: bounded device autotuning

### Question

Can the device choose a validated operating point instead of hard-coding one guessed configuration forever?

Build a small bounded search matrix from settings already proven safe in R1–R4. Example dimensions may include decode threads, batch threads, context size, batch size, micro-batch size, and KV-cache mode.

Do NOT run an unbounded combinatorial benchmark on a weak phone. Pre-eliminate configurations that violate ISA, RAM, product-context, or thermal constraints.

For each candidate record TTFT, prompt tok/s, generation tok/s, peak PSS, thermal delta, and scanner/event guardrails. Score only candidates that pass correctness and evidence-preservation gates.

Persist the winner against a fingerprint containing at minimum device model/device codename, Android/API, app build SHA/version, llama.cpp SHA, model SHA-256, and relevant runtime version.

Invalidate the cached profile when any fingerprint component that can materially affect performance changes.

The autotuner MUST expose the selected profile and evidence; it must not become an opaque "AI optimization" switch.
## Phase 10 — Whole-repository recursion R1: scanner and observation hot paths

### Goal

Reduce CPU/allocation/latency in acquisition and classification without losing evidence.

Profile scanner callbacks, normalization, signature matching, identity resolution, enrichment dispatch, deduplication, persistence handoff, and event fan-out.

Look for repeated parsing, regex creation, temporary collections, redundant object copies, synchronized sections, coroutine launches per observation, repeated timestamp/location lookups, and work that can safely move after immutable evidence capture.

Critical rule: never optimize by dropping the raw observation ledger. Throttle duplicate alerts, expensive enrichment, or repeated classification if product rules allow it; preserve auditable sightings.

Measure observations/sec, accepted sightings/sec, dropped/error counts, callback CPU, allocation/GC behavior, end-to-end observation-to-persistence latency, and concurrent UI responsiveness.

KEEP only changes that preserve deterministic detection fixtures and physical-scanner acceptance results.
## Phase 11 — Whole-repository recursion R2: Room and evidence persistence

### Goal

Make durable evidence cheap enough to preserve every required sighting on constrained hardware.

Profile encrypted database open, sighting insert/update transactions, device-summary projection updates, recent-history queries, device-history queries, geospatial queries, map projections, retention/compaction, and export snapshots.

Inspect indexes against actual query plans and dataset sizes. Avoid adding indexes reflexively: each index speeds some reads while increasing write cost and storage.

Prefer bounded projections over loading full entities when the UI needs only a few fields. Use paging/windowing for long history. Batch logically related writes when doing so does not destroy event ordering or crash durability requirements.

Never compare encrypted baseline persistence with unencrypted candidate persistence. Never remove provenance fields to make rows smaller without an explicit schema/product decision.

Measure transaction latency distributions, query latency, DB size, WAL behavior, CPU, allocations, and observation-to-durable-ledger delay under scanner load.
## Phase 12 — Whole-repository recursion R3: process transmission and IPC

### Goal

Reduce cross-process serialization, Binder/Messenger chatter, stale-state risk, and redundant fan-out without weakening authoritative state.

Map the entire path for each major payload:

`producer → service state → serialization → Binder/Messenger → client decode → repository/ViewModel → UI`.

Record payload size, frequency, encoding cost, queue depth/backpressure, sequence/order semantics, retry behavior, and full-snapshot frequency.

Prefer revisioned/delta updates for high-frequency state only after an authoritative snapshot path exists. Include `sessionEpoch` plus monotonic sequence/revision so clients can detect gaps and request recovery instead of displaying silently stale state.

Coalesce presentation updates, not forensic evidence. A UI does not need 100 redraws/sec merely because a sensor emitted 100 observations/sec.

Measure Binder/IPC activity with Perfetto, serialization CPU/allocations, bytes/sec, client lag, gap/recovery behavior, and UI update latency.
## Phase 13 — Whole-repository recursion R4: UI, Compose, history, and map

### Goal

Prevent the presentation layer from becoming the bottleneck after acquisition/persistence are fixed.

Profile high-frequency StateFlows, list diffing, sorting/filtering, Compose recompositions, map marker/overlay rebuilds, long evidence timelines, image/icon work, and expensive formatting performed during composition.

Use stable immutable UI models, bounded/paged history windows, cached derived values where correctness allows, and incremental map updates. Do not render thousands of individual markers when clustering or viewport-bounded queries preserve the required information more effectively.

Streaming LLM text should be coalesced so native token throughput does not trigger one expensive recomposition per token.

Measure total/janky frames, frame percentiles, main-thread CPU, recomposition indicators available to the environment, allocation/GC pressure, map interaction latency, and memory after repeated navigation.

KEEP visual optimizations only when the same evidence remains accessible and inspectable. Hiding verbose evidence is not a performance optimization.
## Phase 14 — Whole-repository recursion R5: thermal, energy, and lifecycle stability

### Goal

Make the optimized build sustainable for long field sessions rather than merely fast for a short benchmark.

Run multi-minute and multi-hour scenarios appropriate to the product. Record scanner throughput, local inference activity, screen state, charging state, CPU frequencies, thermal status, battery change, process/service restarts, wakeups, memory growth, and error counts.

Introduce adaptive workload budgets only from measured evidence. Examples include lowering AI generation priority, shortening nonessential analysis, delaying enrichment, or reducing UI refresh frequency under thermal pressure.

MUST NOT silently reduce primary sensor acquisition to make battery graphs look better. If Android or hardware forces degraded acquisition, Capability Truth / mission telemetry must record the degradation and its time window.

Verify background → foreground, screen-off → screen-on, process recreation, model unload/reload, permission changes, and service restart. A fast app that loses its scanners after lifecycle transitions fails this recursion.

Final long-run PASS requires no unbounded thread/coroutine/queue growth, no unexplained memory climb, no hidden scanner death, and no false `READY`/`ACTIVE` state.
## Measurement command reference

### Memory snapshots

```bash
adb -s "$SERIAL" shell dumpsys meminfo "$PACKAGE" > "$ARTIFACT_DIR/meminfo.txt"
```

Capture before launch, after steady state, after the focused stress flow, and after returning to idle. Compare TOTAL PSS, Java heap, native heap, graphics, Views/Activities, and object/binder counts where exposed.

### Quick frame evidence

```bash
adb -s "$SERIAL" shell dumpsys gfxinfo "$PACKAGE" reset
# execute exactly one focused UI flow
adb -s "$SERIAL" shell dumpsys gfxinfo "$PACKAGE" framestats > "$ARTIFACT_DIR/gfxinfo-framestats.txt"
```

Do not report frame improvement if the candidate changed the screen contents or workload materially.
### Simpleperf CPU attribution

Use only when the target build is debuggable or profileable:

```bash
adb -s "$SERIAL" shell simpleperf record \
  --app "$PACKAGE" \
  -o /data/local/tmp/perf.data \
  -e cpu-clock -f 4000 -g \
  --duration 30
adb -s "$SERIAL" pull /data/local/tmp/perf.data "$ARTIFACT_DIR/perf.data"
```

Interpret self-time separately from inclusive/children time. Prefer app-owned/native llama/ggml symbols when identifying code to change.

Simpleperf does not explain suspended coroutine wall time, Binder waits, lock waits, or scheduling gaps. If the flow feels slow but CPU samples are low, capture Perfetto rather than guessing.

### Perfetto timeline evidence

Use a bounded trace around one scenario. Include scheduler/frequency/Binder/gfx/view/dalvik tracks and app trace sections where available. Preserve the `.pftrace` file as evidence rather than relying only on screenshots of the trace viewer.
## Keep / revert decision table

A candidate may be kept only when the primary objective improves and no hard guardrail regresses.

Hard guardrails include:

- deterministic tests remain green;
- real scanner acquisition remains functional;
- observation/sighting loss does not increase outside explicitly accepted OS limits;
- evidence/provenance semantics remain intact;
- no new crash, ANR, native abort, illegal instruction, or lifecycle deadlock;
- no false `READY`, `ACTIVE`, or capability state;
- privacy/encryption are not weakened;
- model output quality remains within the defined acceptance corpus;
- memory and thermal behavior remain inside device-safe limits.

If the primary metric improves but a hard guardrail fails, REVERT or redesign. Do not average correctness and speed into one score.
## Common invalid conclusions

### "Eight cores means eight llama threads"

Invalid. Heterogeneous cores, scheduler behavior, memory bandwidth, thermal limits, and concurrent app work can make fewer threads faster and more stable.

### "arm64-v8a means DOTPROD/I8MM/SVE are safe"

Invalid. ABI identifies the instruction-set family, not every optional extension. Read the actual device features and validate on hardware.

### "The model downloaded, therefore AI works"

Invalid. The artifact must hash-verify, parse, load, pass a self-test, and complete real inference.

### "The scanner says active, therefore it is scanning"

Invalid. Require recent successful acquisition timestamps, event rates, and error/drop counters.

### "One run got faster, therefore the optimization worked"

Invalid. Thermal state, background activity, caches, charging, and radio conditions can dominate one run. Repeat comparable scenarios and report distributions.
### "Shorter prompts prove a faster runtime"

Invalid unless prompt compression is the variable under test and semantic-quality regression is measured. Runtime throughput and workload reduction are different claims.

### "Lower memory is always better"

Invalid. Aggressive unloading, tiny caches, or tiny context may reduce one snapshot while increasing reload time, page churn, battery use, or product failure.

### "Fewer IPC messages means better architecture"

Invalid if coalescing removes state transitions or hides gaps. Preserve authoritative ordering and recovery semantics first; optimize transport second.

### "No detections means no devices were present"

Invalid without coverage evidence. A scientifically meaningful negative result requires proof that the relevant scanners were acquiring successfully during the observation window.

### "Release build is always the right profiler target"

Invalid when method-level attribution requires profileable/debuggable instrumentation. Use the appropriate build for diagnosis, then validate user-facing behavior on the shipping-equivalent build.
## Required evidence-bundle layout

Use one timestamped directory per benchmark campaign:

```text
artifacts/device-opt/<timestamp>/
  device-profile.json
  environment.txt
  baseline/
  r1/
  r2/
  r3/
  r4/
  r5/
  repo-r1/
  repo-r2/
  repo-r3/
  repo-r4/
  repo-r5/
  final/
```

Each recursion directory should contain raw command outputs, benchmark summaries, traces, logs, APK/model hashes, and a `decision.md` stating KEEP or REVERT with evidence.

Never overwrite baseline evidence with candidate evidence. Never store only a hand-written summary when the raw artifact is available.
### Required recursion decision record

Use this template after every recursion:

```text
RECURSION:
Baseline SHA:
Candidate SHA:
Device profile ID:
Model SHA-256:
Scenario:
Variable changed:
Everything intentionally held constant:
Primary metric baseline:
Primary metric candidate:
Guardrail measurements:
Correctness/tests:
Raw artifact paths:
Unexpected observations:
Decision: KEEP | REVERT | INCONCLUSIVE
Reason:
New baseline SHA if kept:
```

An `INCONCLUSIVE` result is valid. It is better than inventing a win from noisy data. Resolve the noise or change the experiment before proceeding.
## Worked example — Moto G Power (2022)

This section is an example from one real specialization campaign. These values MUST NOT be copied to another device without re-fingerprinting it.

Observed target summary:

```text
Marketing model: Moto G Power (2022)
Android: 12
API: 31
ABI: arm64-v8a
RAM: ~3.85 GB
Hardware / SoC identifier: MT6765H
CPU count: 8
CPU architecture: ARMv8
Advertised features include: fp, asimd, aes, pmull, sha1, sha2, crc32, cpuid
Advertised features do NOT include: dotprod, i8mm, sve
```

Observed cpufreq grouping showed four cores with a maximum near 2.301 GHz and four cores with a maximum near 1.8 GHz. This justified testing four-thread inference as an early hypothesis, not declaring four threads optimal before measurement.
### Worked model artifact

The campaign's benchmark GGUF was independently verified on host and device:

```text
Name: gemma-mlx-probe-fused-q8_0.gguf
Size: 291,545,376 bytes
SHA-256: 82b323bf05eba698b87a39d1eca8ea31506222aff25b415f6388135069725b57
Format: GGUF
Quantization: Q8_0
```

The device copy MUST be treated as the same benchmark artifact only while the SHA-256 matches exactly.

The specialization pinned llama.cpp to one recorded commit rather than an unspecified upstream HEAD. If llama.cpp is upgraded during the campaign, that upgrade starts a new runtime baseline because kernels, model loading, sampling, defaults, and performance may change.

### Initial safe build hypothesis

Because this device did not advertise DOTPROD/I8MM/SVE, the first native configuration targeted ordinary ARMv8-A/ASIMD and disabled unsupported advanced variants. Later optimizations must be benchmarked against this executable baseline.
## Failure diagnosis tree — native model does not load

1. Verify host and device model hashes match the expected SHA-256.
2. Confirm the app process can read the file path actually passed to JNI.
3. Confirm the packaged native ABI matches the device ABI.
4. Inspect logcat for `UnsatisfiedLinkError`, missing dependent `.so` files, model-parser errors, or unsupported architecture errors.
5. Confirm llama.cpp commit and GGUF version compatibility.
6. Confirm native context/model parameters are within device RAM constraints.
7. If the process aborts with illegal instruction, re-check CPU feature/compile flags before anything else.
8. If load succeeds but preparation fails, isolate model load from context/sampler allocation and measure memory separately.

STOP after the first proven failure. Do not stack speculative fixes across multiple layers before rerunning the smallest reproducer.

## Failure diagnosis tree — inference hangs or is extremely slow

Separate model loading, prompt processing, and token generation timing. Then inspect CPU utilization, thread count, scheduler/frequency traces, thermal status, memory pressure, JNI/token delivery, and logging overhead in that order.
## Failure diagnosis tree — app becomes faster but scanners regress

1. Compare scanner acquisition timestamps/rates before and after.
2. Check CPU saturation and which cores/threads dominate during inference.
3. Inspect scheduling gaps in Perfetto around scanner callbacks and service workers.
4. Check memory pressure, GC, Binder queues, and coroutine/thread pool starvation.
5. Reduce AI priority/threads/batch pressure before changing scanner behavior.
6. Re-run the deterministic scanner fixture and one physical acquisition scenario.
7. REVERT if evidence loss remains unexplained.

The AI subsystem is subordinate to evidence acquisition in an evidence-oriented application.

## Failure diagnosis tree — benchmark result is noisy

Normalize charging state, starting temperature, background apps, screen state, model cache state, build type, scanner configuration, and scenario duration. Repeat the experiment. If variance remains large, increase sample count or choose a more focused metric. Mark the recursion `INCONCLUSIVE` rather than selecting the best-looking sample.
## Agent execution handoff prompt

Use the following template when assigning this work to another capable agent:

```text
Optimize this Android project for one specific connected physical device using DEVICE_SPECIFIC_ANDROID_OPTIMIZATION_PLAYBOOK.md as a binding execution contract.

Do not merge the device-specific branch. Begin by proving the authoritative baseline SHA is clean, then create an isolated worktree and specialization branch. Fingerprint the physical device from ADB rather than relying on marketing specifications. Record CPU features/topology/frequencies, RAM, ABI, Android/API, thermal and battery state.

Create reproducible baseline evidence before changing code. Pin llama.cpp to an exact commit. Verify the exact GGUF byte size and SHA-256 on both host and device. A downloaded model is not READY until it parses, loads, passes a real inference self-test, streams output, cancels correctly, and unloads/reloads.

Execute five llama.cpp recursions in order: architecture/thread topology; memory/model loading; scheduler/batch/latency; JNI/token delivery; bounded autotuning. After each recursion measure, compare, then KEEP, REVERT, or mark INCONCLUSIVE. Never keep a speed gain that loses scanner/evidence data, weakens privacy/security, breaks correctness, creates thermal instability, or degrades required model quality.
```
```text
Then execute five whole-repository recursions: scanner/observation hot path; Room/evidence persistence; IPC/transmission; UI/history/map; thermal/energy/lifecycle stability.

For every numerical claim preserve raw benchmark artifacts and state exactly what changed and what was held constant. Use Perfetto, Simpleperf, meminfo, gfxinfo, logcat, and app-owned acquisition/drop counters where appropriate. Never infer performance solely from source code.

Do not guess unsupported CPU extensions. Do not use all cores merely because they exist. Do not hide rules-only fallback behind an LLM-ready status. Do not reduce acquisition coverage to win battery or latency benchmarks. Negative field results are valid only when scanner coverage is proven.

Finish with a full device acceptance run and an evidence summary containing baseline SHA, final SHA, device profile, llama.cpp SHA, model SHA, every recursion decision, measured wins/regressions, unresolved limitations, and explicit confirmation that the specialization branch remains unmerged.
```

## Final device acceptance checklist

The campaign is complete only after every applicable item below has direct evidence.
- [ ] Baseline SHA and final SHA recorded.
- [ ] Specialization branch/worktree isolated and unmerged.
- [ ] Device profile captured from the real physical target.
- [ ] CPU ISA assumptions match kernel-advertised features.
- [ ] Exact APK/build flavor/package/version recorded.
- [ ] Exact model name, byte size, host SHA-256, and device SHA-256 recorded.
- [ ] Exact llama.cpp commit recorded.
- [ ] Native library loads on the target without illegal-instruction or linkage errors.
- [ ] GGUF parses and model loads successfully.
- [ ] Real inference self-test produces expected nonempty output.
- [ ] TTFT, prompt throughput, generation throughput, and memory measured.
- [ ] Cancellation returns promptly to a valid engine state.
- [ ] Unload/reload succeeds without runaway memory growth.
- [ ] Scanner acquisition remains operational during inference.
- [ ] Scanner event/drop/coverage telemetry is captured during concurrent workload.
- [ ] Observation/evidence correctness tests remain green.
- [ ] Room/history/map behavior remains correct under realistic data volume.
- [ ] IPC clients recover from lifecycle/reconnect without stale false state.
- [ ] UI remains responsive under scanner and inference load.
- [ ] Thermal status and CPU-frequency behavior captured for sustained workload.
- [ ] Battery/charging conditions documented for long-run comparisons.
- [ ] No optimization depends on weakened encryption/privacy.
- [ ] No optimization hides unsupported/degraded capabilities.
- [ ] Each of five llama recursions has KEEP/REVERT/INCONCLUSIVE evidence.
- [ ] Each of five repository recursions has KEEP/REVERT/INCONCLUSIVE evidence.
- [ ] Raw traces/logs/reports are preserved.
- [ ] Final performance claims state the exact scenario and measurement method.
- [ ] Remaining limitations and unverified claims are explicitly listed.

## Final rule

Optimization is complete when the device-specific build is measurably better for its defined workload **and** more truthful or equally truthful about what the hardware, sensors, runtime, and model are actually doing. Speed without evidence integrity is a regression.
