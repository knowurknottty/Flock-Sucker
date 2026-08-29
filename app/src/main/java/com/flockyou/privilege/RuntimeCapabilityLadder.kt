package com.flockyou.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.flockyou.shannon.ShannonCapabilityDetector
import com.topjohnwu.superuser.Shell

enum class RuntimeCapabilityTier(val rank: Int) {
    SIDELOAD(0),
    ROOT_LIBSU(1),
    MAGISK_COMPANION(2),
    SYSTEM_PRIVAPP(3),
    OEM_PLATFORM(4)
}

enum class RootGrantStatus {
    GRANTED,
    DENIED,
    UNKNOWN
}

enum class MagiskCompanionStatus {
    AVAILABLE,
    MAGISK_PRESENT_COMPANION_MISSING,
    UNAVAILABLE
}

data class RuntimeCapabilityProfile(
    val tier: RuntimeCapabilityTier,
    val rootGrant: RootGrantStatus,
    val magiskCompanion: MagiskCompanionStatus,
    val shannonStatus: ShannonCapabilityDetector.ShannonStatus,
    val exactSilentSmsAvailable: Boolean,
    val exactSensorPath: String?,
    val mediaTekCcciTransportAvailable: Boolean,
    val rawDiagnosticTransport: String?,
    val notes: List<String>
)
object RuntimeCapabilityLadder {
    private const val READ_PRIVILEGED_PHONE_STATE =
        "android.permission.READ_PRIVILEGED_PHONE_STATE"

    fun detect(context: Context): RuntimeCapabilityProfile {
        val rootGrant = when (Shell.isAppGrantedRoot()) {
            true -> RootGrantStatus.GRANTED
            false -> RootGrantStatus.DENIED
            null -> RootGrantStatus.UNKNOWN
        }
        val platformSigned = PrivilegeModeDetector.isPlatformSigned(context)
        val privilegedApp = PrivilegeModeDetector.isPrivilegedApp(context)
        val privilegedPhone = context.checkSelfPermission(READ_PRIVILEGED_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val companionCaps = MagiskDiagnosticCompanion.probeCapabilities()
        val companion = detectCompanion(rootGrant, companionCaps.companionAvailable)
        val shannon = ShannonCapabilityDetector.detect()

        val tier = resolveTier(
            platformSigned = platformSigned,
            privilegedApp = privilegedApp,
            privilegedPhone = privilegedPhone,
            companion = companion,
            rootGrant = rootGrant
        )
        val exactAvailable = shannon == ShannonCapabilityDetector.ShannonStatus.AVAILABLE
        val notes = buildList {
            if (rootGrant == RootGrantStatus.UNKNOWN) {
                add("Root grant not yet determined; no prompt was triggered")
            }
            if (companion == MagiskCompanionStatus.MAGISK_PRESENT_COMPANION_MISSING) {
                add("Magisk/root present but Flock-Sucker companion socket is not installed")
            }
            if (companionCaps.mediaTekCcciStreamAvailable) {
                add("MediaTek CCCI raw diagnostics available via authenticated companion; raw CCCI is transport evidence only and is not parsed as Silent-SMS proof")
            }
            if (!exactAvailable) {
                add("Exact Type-0 evidence unavailable; use stock indirect detector only")
            }
        }
        return RuntimeCapabilityProfile(
            tier = tier,
            rootGrant = rootGrant,
            magiskCompanion = companion,
            shannonStatus = shannon,
            exactSilentSmsAvailable = exactAvailable,
            exactSensorPath = if (exactAvailable) "/dev/umts_dm0" else null,
            mediaTekCcciTransportAvailable = companionCaps.mediaTekCcciStreamAvailable,
            rawDiagnosticTransport = when {
                exactAvailable -> "SHANNON_SDM"
                companionCaps.mediaTekCcciStreamAvailable -> "MEDIATEK_CCCI_RAW"
                else -> null
            },
            notes = notes
        )
    }

    internal fun resolveTier(
        platformSigned: Boolean,
        privilegedApp: Boolean,
        privilegedPhone: Boolean,
        companion: MagiskCompanionStatus,
        rootGrant: RootGrantStatus
    ): RuntimeCapabilityTier = when {
        platformSigned && privilegedPhone -> RuntimeCapabilityTier.OEM_PLATFORM
        privilegedApp && privilegedPhone -> RuntimeCapabilityTier.SYSTEM_PRIVAPP
        companion == MagiskCompanionStatus.AVAILABLE -> RuntimeCapabilityTier.MAGISK_COMPANION
        rootGrant == RootGrantStatus.GRANTED -> RuntimeCapabilityTier.ROOT_LIBSU
        else -> RuntimeCapabilityTier.SIDELOAD
    }

    /**
     * Explicit root probe. This may trigger the user's root-manager authorization UI,
     * so it must never be called as a passive background capability check.
     */
    fun requestRootProbe(): Boolean = try {
        Shell.getShell().isRoot
    } catch (_: Throwable) {
        false
    }


    private fun detectCompanion(rootGrant: RootGrantStatus, companionAvailable: Boolean): MagiskCompanionStatus {
        if (companionAvailable) return MagiskCompanionStatus.AVAILABLE
        if (rootGrant == RootGrantStatus.GRANTED && rootCommandExists("magisk")) {
            return MagiskCompanionStatus.MAGISK_PRESENT_COMPANION_MISSING
        }
        return MagiskCompanionStatus.UNAVAILABLE
    }

    private fun rootCommandExists(binary: String): Boolean {
        if (Shell.isAppGrantedRoot() != true || binary != "magisk") return false
        return try {
            Shell.cmd("command", "-v", binary).exec().isSuccess
        } catch (_: Throwable) {
            false
        }
    }
}
