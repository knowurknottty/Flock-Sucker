# Round 09 — Lazy AI Bootstrap Live Receipt

Date: 2026-08-30
Target: Tonga / Android 12 / privileged systemDebug Flock-Sucker
R9 branch: `inversion-labs/r9-lazy-ai-bootstrap`

## R8 before-state

The accepted R8 scanner process was still live before any R9 Kotlin change or redeploy.

Read-only process evidence:
- main process and isolated `:scanning` process were both resident;
- scanner `dumpsys meminfo`: TOTAL PSS 206,707 kB, TOTAL RSS 85,572 kB, TOTAL SWAP PSS 174,770 kB;
- scanner `/proc/<pid>/smaps_rollup`: RSS 101,660 kB, PSS 43,361 kB, Swap 190,696 kB, SwapPSS 174,266 kB;
- scanner `/proc/<pid>/maps` directly mapped `/data/data/com.flockyou.debug/app_ai_models/gemma-flock-q8-0.gguf`;
- mapped GGUF file size: 291,545,376 bytes (~291.5 MB decimal / ~278.0 MiB binary).

This is direct evidence that the heavyweight model was resident/mapped inside the radio hot process before R9.

## R9 source and verification

R9 design goal: keep heavyweight AI out of the radio hot process and out of normal main-process startup. The selected model is initialized only when an analysis actually requires it.

Durable source commits:
- `d40be221b6c4a9397b6b5bb66eaf2ac3c0864bc5` — lazy AI bootstrap outside the scanning process.
- `728a5e7df4162c568262c8672df4fcc6ecf4f672` — recognize manually provisioned GGUF artifacts without claiming runtime readiness.

Final local source gate before deployment:
- systemDebug JVM tests: 820/820;
- failures/errors/skips: 0/0/0;
- `assembleSystemDebug`: BUILD SUCCESSFUL;
- `git diff --check`: PASS;
- APK SHA-256: `f588330ec2389beca456328f6bdc1d47fe9840d9373597396540bb508a5deaf2`;
- AGP 9.1.1 / Gradle 9.3.1 / Java 17 remained pinned.

Android Studio executed `:app:assembleSystemDebug`, hit the expected privileged-app `run-as` denial, logged `Falling back to standard full install`, installed the APK, and activated `com.flockyou.MainActivity`.

## Provisioning-state correction

The manually installed `gemma-flock-q8-0.gguf` was physically present and loadable, but the AI UI reported `Not downloaded` and disabled `Run Test Analysis`.

Root cause:
- `DetectionAnalyzer.getDownloadedModelIds()` inventoried TASK/BIN artifacts only;
- GGUF artifacts were omitted from that inventory;
- `AiModelStatus` correctly remained `NotDownloaded` until runtime initialization;
- the UI incorrectly equated runtime readiness with artifact availability.

R9 keeps those concepts separate. `ModelArtifactInventory` now recognizes GGUF plus the existing TASK/BIN formats. The UI reports an installed-but-cold model as `Installed • loads on first analysis`, offers `Load now`, and enables test analysis when the selected artifact exists. `AiModelStatus.Ready` still means the inference runtime is genuinely initialized.

Focused RED/GREEN coverage verifies:
- manual GGUF artifact inventory;
- missing GGUF rejection;
- existing TASK inventory compatibility;
- tiny/incomplete TASK rejection;
- test-analysis eligibility for Ready, installed-but-cold, and genuinely absent states.

## Live lazy-load acceptance

Fresh deployed process identities during final acceptance:
- main: PID 22590;
- isolated scanner: PID 22847.

Before the AI trigger, root read-only `/proc` inspection found no GGUF/Gemma/llama mappings in either process. The scanner independently logged `role=SECONDARY` and skipped package-global bootstrap.

Scanning was then started through the visible device mirror. While both processes were still model-cold, the scanner remained fully active with:
- Flock Boost `aggressive=true`;
- `reportDelayMs=0`;
- aggressive matching and max advertisement matches;
- extended advertisements requested;
- `phyRequest=ALL_SUPPORTED`;
- structured Wi-Fi evidence active;
- raw GNSS callback READY with carrier frequency, baseband C/N0, AGC, code lock and valid ADR evidence.

The AI screen visibly reported `Installed • loads on first analysis`. `Run Test Analysis` was then triggered through the real UI.

Main-process logs then proved the intended lazy path:
- `=== testAnalysis START ===`;
- `=== analyzeDetection START ===`;
- `Model not loaded, attempting lazy initialization for: Flock Fine-Tuned Gemma Q8_0`;
- `=== initializeModel START ===`;
- engine preference `llama-cpp` / active engine `LLAMA_CPP`;
- llama.cpp loaded `/data/user/0/com.flockyou.debug/app_ai_models/gemma-flock-q8-0.gguf` as GGUF V3;
- `llama.cpp model ready: gemma-flock-q8-0.gguf`;
- `Model initialized successfully via LlmEngineManager: LLAMA_CPP`;
- final ViewModel result: `success=true, model=gemma-flock-q8-0, error=null`.

The visible AI result card populated after completion.

Post-analysis root read-only maps showed the GGUF mapped in main and zero GGUF/Gemma/llama mappings in `:scanning`. Final memory snapshot: main TOTAL PSS 541,684 kB / SwapPSS 216 kB; scanner TOTAL PSS 130,428 kB / SwapPSS 236 kB. These absolute PSS values are phase-sensitive; the strong architectural proof is the process-specific mmap boundary and scanner SwapPSS collapse relative to R8.

Scanner acquisition remained live after main loaded the model. GNSS delivery reached at least 400 callbacks with READY status, and the aggressive BLE plan continued unchanged.

## R9 disposition

PASS. Heavyweight inference is no longer part of package-global or scanner bootstrap. Normal startup and radio acquisition remain model-cold; a real user analysis lazily loads the manually provisioned GGUF in the main process only. No AI IPC subsystem was required.
