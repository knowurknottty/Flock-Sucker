package com.flockyou.service

import android.content.Intent

/**
 * Trust boundary for manifest boot receivers.
 *
 * Only Android's protected boot-completion broadcasts are accepted. Vendor-specific
 * QUICKBOOT actions are intentionally excluded because they are not a reliable
 * authenticated signal for security-sensitive state transitions.
 */
object BootActionPolicy {
    private val trustedActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED
    )

    fun isTrustedBootAction(action: String?): Boolean = action in trustedActions
}
