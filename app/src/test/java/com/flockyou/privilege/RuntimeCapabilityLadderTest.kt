package com.flockyou.privilege

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeCapabilityLadderTest {
    @Test fun resolvesHighestProvenRuntimeTier() {
        assertEquals(RuntimeCapabilityTier.SIDELOAD, tier())
        assertEquals(RuntimeCapabilityTier.ROOT_LIBSU, tier(root = RootGrantStatus.GRANTED))
        assertEquals(RuntimeCapabilityTier.MAGISK_COMPANION, tier(root = RootGrantStatus.GRANTED, companion = MagiskCompanionStatus.AVAILABLE))
        assertEquals(RuntimeCapabilityTier.SYSTEM_PRIVAPP, tier(privilegedApp = true, privilegedPhone = true, root = RootGrantStatus.GRANTED))
        assertEquals(RuntimeCapabilityTier.OEM_PLATFORM, tier(platformSigned = true, privilegedPhone = true, privilegedApp = true))
    }

    private fun tier(
        platformSigned: Boolean = false,
        privilegedApp: Boolean = false,
        privilegedPhone: Boolean = false,
        companion: MagiskCompanionStatus = MagiskCompanionStatus.UNAVAILABLE,
        root: RootGrantStatus = RootGrantStatus.DENIED
    ) = RuntimeCapabilityLadder.resolveTier(platformSigned, privilegedApp, privilegedPhone, companion, root)
}
