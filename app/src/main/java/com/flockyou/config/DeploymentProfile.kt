package com.flockyou.config

import com.flockyou.BuildConfig

/**
 * Compile-time deployment intent for distributable APKs.
 *
 * This is tuning/identity only. Runtime capability probes remain authoritative:
 * TARGET_ROOTED never implies that root, Magisk, modem diagnostics, or system
 * privileges are actually available on the installed device.
 */
object DeploymentProfile {
    val id: String get() = BuildConfig.DEPLOYMENT_PROFILE
    val rootTargeted: Boolean get() = BuildConfig.TARGET_ROOTED
    val tongaTargeted: Boolean get() = BuildConfig.TARGET_TONGA
    val forceConstrainedDefaults: Boolean get() = BuildConfig.FORCE_CONSTRAINED_DEFAULTS
    val defaultFlockBoost: Boolean get() = BuildConfig.DEFAULT_FLOCK_BOOST
}
