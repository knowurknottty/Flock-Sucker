package com.inversionlabs.flocksucker.utils

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Establishes dashboard-ready MainActivity preconditions before ActivityScenario launches. */
class MainActivityReadyRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val pkg = context.packageName
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.READ_PHONE_STATE)
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }
            permissions.forEach { permission ->
                instrumentation.uiAutomation.grantRuntimePermission(pkg, permission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                instrumentation.uiAutomation.grantRuntimePermission(
                    pkg,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
            context.getSharedPreferences("flocksucker_prefs", 0)
                .edit().putBoolean("getting_started_shown", true).commit()
            shell("cmd deviceidle whitelist +$pkg")
            try {
                base.evaluate()
            } finally {
                shell("cmd deviceidle whitelist -$pkg")
            }
        }

        private fun shell(command: String) {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
