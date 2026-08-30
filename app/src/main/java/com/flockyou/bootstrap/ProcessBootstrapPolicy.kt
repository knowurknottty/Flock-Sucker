package com.flockyou.bootstrap

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

internal enum class ProcessRole {
    MAIN,
    SECONDARY,
    UNKNOWN
}

internal object ProcessBootstrapPolicy {
    fun classify(currentProcessName: String?, mainProcessName: String?): ProcessRole = when {
        currentProcessName.isNullOrBlank() || mainProcessName.isNullOrBlank() -> ProcessRole.UNKNOWN
        currentProcessName == mainProcessName -> ProcessRole.MAIN
        else -> ProcessRole.SECONDARY
    }
}

internal object ProcessNameResolver {
    fun currentProcessName(context: Context): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val currentPid = Process.myPid()
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == currentPid }
                ?.processName
        }
    } catch (_: Exception) {
        null
    }
}
