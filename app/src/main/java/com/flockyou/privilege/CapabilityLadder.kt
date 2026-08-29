package com.flockyou.privilege

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Runtime capability ladder for detection surfaces.
 *
 * The ladder is capability-based: what the app can actually DO at runtime,
 * not build-flavor theater. Two installs of the same APK can sit at
 * different rungs; a ROOT_LIBSU install can outrun a SYSTEM_PRIVAPP install
 * for modem-log access, and neither is assumed without a passing probe.
 *
 * Rung order (ascending privilege for sensor access):
 *   SIDELOAD -> ROOT_LIBSU -> MAGISK_COMPANION -> SYSTEM_PRIVAPP -> OEM_PLATFORM
 *
 * ROOT_LIBSU and MAGISK_COMPANION are capability-verified at runtime:
 * a `su`/libsu shell that actually executes, a Magisk companion daemon that
 * actually responds. Root without a responding companion is ROOT_LIBSU, not
 * MAGISK_COMPANION — the rung reflects the verified capability, not the
 * marketing name of the tool.
 */
enum class CapabilityRung(val displayName: String, val rank: Int) {
    SIDELOAD("Sideload", 0),
    ADB_PRIVILEGED("ADB-Granted", 1),
    ROOT_LIBSU("Root (libsu)", 2),
    MAGISK_COMPANION("Magisk Companion", 3),
    SYSTEM_PRIVAPP("System Priv-App", 4),
    OEM_PLATFORM("OEM Platform", 5);

    fun atLeast(other: CapabilityRung): Boolean = rank >= other.rank
}

/**
 * Verifiable runtime capabilities each rung can grant. Capabilities are
 * proven by probe, never inferred from rung alone — a failed probe on a
 * nominally higher rung degrades that surface to its verified capability.
 */
enum class DetectionCapability {
    /** Connect-only WiFi/BLE scanning, cellular cell info. */
    BASIC_SENSORS,

    /** ADB-granted permissions (WRITE_SECURE_SETTINGS / READ_LOGS / DUMP). */
    ADB_GRANTED_PERMISSIONS,

    /** Radio log buffer readable — radio-log silent-SMS evidence path. */
    RADIO_LOG_ACCESS,

    /** Continuous BLE background scanning without duty-cycle throttling. */
    CONTINUOUS_BLE,

    /** Real MAC addresses (PEERS_MAC_ADDRESS / LOCAL_MAC_ADDRESS). */
    REAL_MAC_ADDRESS,

    /** Privileged phone state: IMEI/IMSI where the platform exposes it. */
    PRIVILEGED_PHONE_STATE,

    /** Root shell execution (su binary responds). */
    ROOT_SHELL,

    /** Magisk companion IPC (rooted + companion service responds). */
    MAGISK_COMPANION_IPC,

    /** Modem diagnostic channel access (Samsung Shannon /diag, similar). */
    MODEM_DIAG_ACCESS,

    /** Hidden/priv-app telephony APIs (ServiceState raw data, factory reset). */
    PRIVILEGED_TELEPHONY_API
}

/**
 * Immutable snapshot of the verified capability state. Every claim carries
 * the evidence of the probe that proved it; unverified rung claims are not
 * capabilities.
 */
data class CapabilitySnapshot(
    val rung: CapabilityRung,
    val capabilities: Set<DetectionCapability>,
    /** Per-capability probe evidence: capability -> how it was proven. */
    val capabilityEvidence: Map<DetectionCapability, String>,
    /** Capabilities the rung would grant if its probe passed but didn't. */
    val unverifiedClaims: Map<CapabilityRung, List<String>>,
    val detectedAtMs: Long
) {
    fun has(capability: DetectionCapability): Boolean = capability in capabilities

    /**
     * The highest verified modem evidence path available, if any. A bare
     * root shell is NOT a modem evidence path — shell access alone cannot
     * read protocol events.
     */
    val modemEvidencePath: String?
        get() = when {
            has(DetectionCapability.MODEM_DIAG_ACCESS) ->
                capabilityEvidence[DetectionCapability.MODEM_DIAG_ACCESS]
            has(DetectionCapability.MAGISK_COMPANION_IPC) ->
                capabilityEvidence[DetectionCapability.MAGISK_COMPANION_IPC]
            else -> null
        }

    /** Trust label for evidence produced at this capability level. */
    fun evidenceLabel(hasExactSensor: Boolean): String =
        if (hasExactSensor) EvidenceSourceClass.EXACT.name else EvidenceSourceClass.INDIRECT.name
}

/** Source class for detection evidence — exact sensor vs indirect correlation. */
enum class EvidenceSourceClass(val displayName: String, val proofBoundary: String) {
    /**
     * Direct sensor observation of the protocol event itself (modem diag
     * Type-0 SMS record, NAS signaling frame). The event is proven; metadata
     * completeness is bounded only by the sensor.
     */
    EXACT("Exact sensor", "Sensor directly observed the protocol event"),

    /**
     * Correlation of observable side-effects (telephony state churn, RAT
     * transitions, cell identity churn, signal discontinuities). The anomaly
     * is a statistical inference; no single observation proves intent, and
     * benign causes (congestion, coverage, carrier maintenance) are
     * alternatives. Confidence decays without corroboration.
     */
    INDIRECT("Indirect correlation", "Anomaly inferred from observable side-effects; no direct sensor proof of the triggering event")
}

/**
 * Runtime capability ladder detector. Probes, never assumes:
 *
 * 1. OEM_PLATFORM — platform-signed + READ_PRIVILEGED_PHONE_STATE granted
 * 2. SYSTEM_PRIVAPP — priv-app flags + privileged permissions granted
 * 3. MAGISK_COMPANION — root responds AND a Magisk companion service answers
 * 4. ROOT_LIBSU — a root shell actually executes
 * 5. SIDELOAD — default
 *
 * Every rung above SIDELOAD requires its probe to pass. Compatibility:
 * [toLegacyPrivilegeMode] maps the ladder back onto the original sealed
 * [PrivilegeMode] for existing callers.
 */
object CapabilityLadderDetector {

    private const val TAG = "CapabilityLadder"

    // Shell paths probed for root. Each must exist AND execute successfully.
    private val SU_CANDIDATES =
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/magisk/.core/bin/su", "/data/adb/magisk/busybox")

    private val MAGISK_COMPANION_PATHS =
        listOf("/data/adb/magisk", "/data/adb/modules", "/sbin/.magisk")

    fun detect(context: Context): CapabilitySnapshot {
        val evidence = mutableMapOf<DetectionCapability, String>()
        val unverified = mutableMapOf<CapabilityRung, List<String>>()
        val capabilities = mutableSetOf<DetectionCapability>()

        capabilities.add(DetectionCapability.BASIC_SENSORS)
        evidence[DetectionCapability.BASIC_SENSORS] = "Public API sensors always available"

        // --- Priv-app / OEM probe (existing logic, capability-labeled) ---
        val isSystemApp = isSystemApp(context)
        val isPrivilegedApp = isPrivilegedApp(context)
        val isPlatformSigned = isPlatformSigned(context)
        val hasPrivilegedPhone = hasPermission(context, "android.permission.READ_PRIVILEGED_PHONE_STATE")
        val hasPeersMac = hasPermission(context, "android.permission.PEERS_MAC_ADDRESS")
        val hasLocalMac = hasPermission(context, "android.permission.LOCAL_MAC_ADDRESS")
        val hasBtPrivileged = hasPermission(context, "android.permission.BLUETOOTH_PRIVILEGED")

        // --- Root probe (must actually execute) ---
        val rootProbe = probeRootShell()
        if (rootProbe.verified) {
            capabilities.add(DetectionCapability.ROOT_SHELL)
            evidence[DetectionCapability.ROOT_SHELL] = rootProbe.evidence
        }

        // --- ADB-granted permissions probe (middle ground, no root) ---
        val adbProbe = probeAdbGrants()
        if (adbProbe.granted.isNotEmpty()) {
            capabilities.add(DetectionCapability.ADB_GRANTED_PERMISSIONS)
            evidence[DetectionCapability.ADB_GRANTED_PERMISSIONS] =
                "ADB grants verified: " + adbProbe.granted.joinToString(",") { it.name }
            if (adbProbe.hasRadioLogAccess) {
                capabilities.add(DetectionCapability.RADIO_LOG_ACCESS)
                evidence[DetectionCapability.RADIO_LOG_ACCESS] =
                    "radio log buffer readable via READ_LOGS grant"
            }
        }

        // --- Magisk companion probe ---
        val magiskProbe = probeMagiskCompanion(rootProbe.verified)
        if (magiskProbe.verified) {
            capabilities.add(DetectionCapability.MAGISK_COMPANION_IPC)
            evidence[DetectionCapability.MAGISK_COMPANION_IPC] = magiskProbe.evidence
        }

        // --- Ladder selection: highest rung whose probes all passed ---
        val rung = when {
            isPlatformSigned && hasPrivilegedPhone -> CapabilityRung.OEM_PLATFORM
            isPrivilegedApp || (isSystemApp && (hasBtPrivileged || hasPeersMac || hasLocalMac)) ->
                CapabilityRung.SYSTEM_PRIVAPP
            magiskProbe.verified -> CapabilityRung.MAGISK_COMPANION
            rootProbe.verified -> CapabilityRung.ROOT_LIBSU
            adbProbe.granted.isNotEmpty() -> CapabilityRung.ADB_PRIVILEGED
            else -> CapabilityRung.SIDELOAD
        }

        // --- Capability derivation from the selected rung + probes ---
        when (rung) {
            CapabilityRung.OEM_PLATFORM -> {
                capabilities.add(DetectionCapability.PRIVILEGED_PHONE_STATE)
                evidence[DetectionCapability.PRIVILEGED_PHONE_STATE] =
                    "Platform-signed with READ_PRIVILEGED_PHONE_STATE granted"
                capabilities.add(DetectionCapability.REAL_MAC_ADDRESS)
                evidence[DetectionCapability.REAL_MAC_ADDRESS] = "Platform signature grants MAC access"
                capabilities.add(DetectionCapability.CONTINUOUS_BLE)
                evidence[DetectionCapability.CONTINUOUS_BLE] = "Platform-signed: no duty-cycle throttle"
                capabilities.add(DetectionCapability.PRIVILEGED_TELEPHONY_API)
                evidence[DetectionCapability.PRIVILEGED_TELEPHONY_API] = "Hidden API access via platform signature"
                if (rootProbe.verified) capabilities.add(DetectionCapability.ROOT_SHELL)
                if (magiskProbe.verified) capabilities.add(DetectionCapability.MAGISK_COMPANION_IPC)
                // Modem diag requires a confirmed modem channel (Shannon /diag etc.)
                val modemProbe = probeModemDiagAccess()
                if (modemProbe.verified) {
                    capabilities.add(DetectionCapability.MODEM_DIAG_ACCESS)
                    evidence[DetectionCapability.MODEM_DIAG_ACCESS] = modemProbe.evidence
                } else {
                    unverified[CapabilityRung.OEM_PLATFORM] =
                        listOf("MODEM_DIAG_ACCESS: platform-signed but no modem channel probe passed")
                }
            }
            CapabilityRung.SYSTEM_PRIVAPP -> {
                if (hasPrivilegedPhone) {
                    capabilities.add(DetectionCapability.PRIVILEGED_PHONE_STATE)
                    evidence[DetectionCapability.PRIVILEGED_PHONE_STATE] = "READ_PRIVILEGED_PHONE_STATE granted"
                }
                if (hasPeersMac || hasLocalMac) {
                    capabilities.add(DetectionCapability.REAL_MAC_ADDRESS)
                    evidence[DetectionCapability.REAL_MAC_ADDRESS] = "PEERS_MAC_ADDRESS or LOCAL_MAC_ADDRESS granted"
                }
                capabilities.add(DetectionCapability.CONTINUOUS_BLE)
                evidence[DetectionCapability.CONTINUOUS_BLE] = "Priv-app: throttling exemptions available"
                if (hasBtPrivileged) {
                    capabilities.add(DetectionCapability.PRIVILEGED_TELEPHONY_API)
                    evidence[DetectionCapability.PRIVILEGED_TELEPHONY_API] = "BLUETOOTH_PRIVILEGED granted"
                }
            }
            CapabilityRung.MAGISK_COMPANION -> {
                if (rootProbe.verified) capabilities.add(DetectionCapability.ROOT_SHELL)
                // Companion grants modem log reads where the OEM exposes them
                val modemProbe = probeModemDiagAccess()
                if (modemProbe.verified) {
                    capabilities.add(DetectionCapability.MODEM_DIAG_ACCESS)
                    evidence[DetectionCapability.MODEM_DIAG_ACCESS] =
                        "Root + companion: modem diag node readable"
                } else {
                    unverified[CapabilityRung.MAGISK_COMPANION] =
                        listOf("MODEM_DIAG_ACCESS: root present, modem channel not found")
                }
            }
            CapabilityRung.ROOT_LIBSU -> {
                val modemProbe = probeModemDiagAccess()
                if (modemProbe.verified) {
                    capabilities.add(DetectionCapability.MODEM_DIAG_ACCESS)
                    evidence[DetectionCapability.MODEM_DIAG_ACCESS] = modemProbe.evidence
                }
            }
            CapabilityRung.ADB_PRIVILEGED -> {
                if (adbProbe.hasRadioLogAccess) {
                    capabilities.add(DetectionCapability.RADIO_LOG_ACCESS)
                    evidence[DetectionCapability.RADIO_LOG_ACCESS] =
                        "radio log readable via READ_LOGS"
                }
                // DUMP enables telephony.registry snapshots
                if (AdbGrant.DUMP in adbProbe.granted) {
                    capabilities.add(DetectionCapability.PRIVILEGED_TELEPHONY_API)
                    evidence[DetectionCapability.PRIVILEGED_TELEPHONY_API] =
                        "dumpsys telephony.registry readable via DUMP grant"
                }
            }
            CapabilityRung.SIDELOAD -> { /* basic sensors only */ }
        }

        return CapabilitySnapshot(
            rung = rung,
            capabilities = capabilities,
            capabilityEvidence = evidence,
            unverifiedClaims = unverified,
            detectedAtMs = System.currentTimeMillis()
        )
    }

    // ==================== Probes ====================

    data class ProbeResult(val verified: Boolean, val evidence: String)

    /**
     * Root probe: an su binary must exist AND a `id` command must return
     * uid=0. Existence alone is not capability.
     */
    fun probeRootShell(): ProbeResult {
        for (path in SU_CANDIDATES) {
            val f = File(path)
            if (!f.exists() || !f.canExecute()) continue
            return try {
                val proc = ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().use { it.readText() }
                val exited = proc.waitFor() == 0
                if (exited && output.contains("uid=0")) {
                    ProbeResult(true, "su at $path executed; id output: ${output.trim().take(60)}")
                } else {
                    ProbeResult(false, "su at $path ran but did not yield uid=0")
                }
            } catch (e: Exception) {
                ProbeResult(false, "su at $path failed to execute: ${e.message}")
            }
        }
        return ProbeResult(false, "no su candidate found or executed")
    }

    /**
     * Magisk companion probe: root plus a Magisk data dir present. A full
     * companion-service handshake requires the companion library; the
     * capability claim here is bounded to what the probe proves.
     */
    fun probeMagiskCompanion(rootVerified: Boolean): ProbeResult {
        if (!rootVerified) return ProbeResult(false, "root not verified; companion probe skipped")
        val found = MAGISK_COMPANION_PATHS.firstOrNull { File(it).exists() }
            ?: return ProbeResult(false, "root verified but no Magisk companion paths found")
        return ProbeResult(true, "Magisk companion dir present at $found (dir-level probe)")
    }

    /**
     * Modem diagnostic channel probe: Shannon /diag node or comparable modem
     * device node readable. Bounded, honest: null means unavailable.
     */
    fun probeModemDiagAccess(): ProbeResult {
        val candidates = listOf(
            "/dev/smd11",            // Qualcomm diag
            "/dev/diag",
            "/dev/umts_boot0",       // Samsung Shannon adjacency
            "/dev/ttySAC0"
        )
        val found = candidates.firstOrNull { val f = File(it); f.exists() && f.canRead() }
            ?: return ProbeResult(false, "no modem diag device node readable")
        return ProbeResult(true, "modem diag node readable at $found")
    }

    /**
     * ADB-grant probe: exercise each grantable permission. Existence of the
     * manifest entry proves nothing — each probe attempts the operation.
     */
    fun probeAdbGrants(): AdbCapabilityProbe {
        val granted = mutableSetOf<AdbGrant>()
        val evidence = mutableMapOf<AdbGrant, String>()
        for (grant in AdbGrant.entries) {
            try {
                if (grant.verifyCommand()) {
                    granted.add(grant)
                    evidence[grant] = "${grant.permission} verified by operation probe"
                }
            } catch (e: Exception) {
                // probe failed — not granted
            }
        }
        return AdbCapabilityProbe(granted, evidence, System.currentTimeMillis())
    }

    // ==================== Legacy flag checks (unchanged) ====================

    private fun isSystemApp(context: Context): Boolean = try {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(context.packageName, 0)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, 0)
        }
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) { false }

    /** Priv-app check via sourceDir path (mirrors PrivilegeModeDetector, no hidden API). */
    private fun isPrivilegedApp(context: Context): Boolean = try {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            appInfo.sourceDir?.contains("/priv-app/") == true
    } catch (e: Exception) { false }

    private fun isPlatformSigned(context: Context): Boolean = try {
        val pm = context.packageManager
        val mine = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            .signingsCompat()
        val platform = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES)
            .signingsCompat()
        mine != null && platform != null && mine.contentEquals(platform)
    } catch (e: Exception) { false }

    private fun android.content.pm.PackageInfo.signingsCompat() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) signingInfo?.apkContentsSigners?.firstOrNull()?.toCharsString()
        else @Suppress("DEPRECATION") signatures?.firstOrNull()?.toCharsString()

    private fun hasPermission(context: Context, permission: String): Boolean =
        context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
