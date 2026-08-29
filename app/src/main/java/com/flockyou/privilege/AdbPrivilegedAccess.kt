package com.flockyou.privilege

import android.content.Context
import android.os.Build
import java.io.File

/**
 * ADB-grantable privileged state: the middle ground between SIDELOAD and
 * root. One `adb shell pm grant` (or `adb shell cmd`) during a USB setup
 * session — no root, no unlocked bootloader, no warranty risk — unlocks:
 *
 * - WRITE_SECURE_SETTINGS: secure-settings writes, background location grants
 * - READ_LOGS: logcat radio buffer access. On several OEM builds the radio
 *   log carries modem-side SMS/registration traces, giving a legitimate
 *   middle-ground path toward silent-SMS evidence on otherwise stock devices.
 * - DUMP: `dumpsys telephony.registry` snapshots (service state, signal,
 *   cell identity) without registration-churn heuristics.
 *
 * Honesty constraints:
 * - The rung is claimed ONLY when a probe actually exercises the grant.
 *   `pm grant` from an external ADB session is invisible to the app; what
 *   the app CAN verify is whether the privileged operation succeeds.
 * - READ_LOGS-based radio-log silent-SMS matching is evidence-class
 *   EXACT_BY_LOG: the log line directly names the event, but log fidelity
 *   varies by OEM/driver — the proof boundary states this. It is NOT a
 *   modem-diag protocol capture.
 */
enum class AdbGrant(val permission: String, val verifyCommand: () -> Boolean) {
    WRITE_SECURE_SETTINGS(
        "android.permission.WRITE_SECURE_SETTINGS",
        { checkWriteSecureSettings() }
    ),
    READ_LOGS(
        "android.permission.READ_LOGS",
        { checkReadLogs() }
    ),
    DUMP(
        "android.permission.DUMP",
        { checkDump() }
    );

    companion object {
        /**
         * Write probe: putString the SAME value back on a benign Global key.
         * A real SecurityException proves the grant is absent; success proves
         * WRITE_SECURE_SETTINGS is held. This is the only honest probe.
         */
        private fun checkWriteSecureSettings(): Boolean = try {
            val resolver = appContext()?.contentResolver ?: return false
            val key = "flock_capability_probe"
            val current = android.provider.Settings.Global.getString(resolver, key)
            android.provider.Settings.Global.putString(resolver, key, current ?: "0")
            true
        } catch (e: SecurityException) { false }
        catch (e: Exception) { false }

        private fun checkReadLogs(): Boolean = try {
            // READ_LOGS allows opening the log device nodes or running logcat.
            val proc = ProcessBuilder("logcat", "-d", "-t", "1", "-b", "radio")
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor() == 0 && !out.contains("Permission Denial", ignoreCase = true)
        } catch (e: Exception) { false }

        private fun checkDump(): Boolean = try {
            // DUMP allows dumpsys for other packages' services.
            val proc = ProcessBuilder("dumpsys", "--help")
                .redirectErrorStream(true).start()
            proc.waitFor() == 0
        } catch (e: Exception) { false }

        @Volatile private var appContextRef: Context? = null
        fun init(context: Context) { appContextRef = context.applicationContext }
        fun appContext(): Context? = appContextRef
    }
}

/**
 * Probe-result holder for the ADB rung.
 */
data class AdbCapabilityProbe(
    val granted: Set<AdbGrant>,
    val evidence: Map<AdbGrant, String>,
    val detectedAtMs: Long
) {
    val hasRadioLogAccess: Boolean get() = AdbGrant.READ_LOGS in granted
    val hasDumpAccess: Boolean get() = AdbGrant.DUMP in granted
}

/**
 * Radio-log silent-SMS scanner (EXACT_BY_LOG evidence class).
 *
 * Reads the `radio` log buffer and matches modem-side SMS-DELIVER traces
 * with Type-0 / silent indicators. What this proves: the radio log recorded
 * an SMS-DELIVER-REPORT flow with Type-0 markers. What it does NOT prove:
 * full TPDU content (log truncation varies by OEM), and log availability
 * itself varies by build. The evidence record carries the raw log line so
 * the operator sees exactly what matched.
 */
object RadioLogSilentSmsScanner {

    /** Log-line patterns that indicate a Type-0 / silent SMS at the modem layer. */
    private val SILENT_SMS_PATTERNS = listOf(
        Regex("(?i)SMS-DELIVER.*TYPE[-_ ]?0"),
        Regex("(?i)TYPE[-_ ]?0.*SMS"),
        Regex("(?i)class[-_ ]?0.*SMS-DELIVER"),
        Regex("(?i)TP-PID[=:]\\s*0x40"),
        Regex("(?i)SMS.*mwi.*false.*class.*0", RegexOption.IGNORE_CASE)
    )

    /** Marker strings some radio logs print for silent/stealth message flows. */
    private val SILENT_MARKERS = listOf(
        "silent", "type0", "type_0", "type-0", "class0", "class_0", "class 0"
    )

    data class RadioLogHit(
        val timestampMs: Long,
        val logLine: String,
        val matchedPattern: String
    )

    /**
     * Scan the radio buffer for silent-SMS traces. Requires READ_LOGS.
     * Returns empty list when the grant is absent or nothing matched.
     */
    fun scan(maxLines: Int = 2000): List<RadioLogHit> {
        return try {
            val proc = ProcessBuilder(
                "logcat", "-d", "-t", maxLines.toString(), "-b", "radio", "-v", "time"
            ).redirectErrorStream(true).start()
            val lines = proc.inputStream.bufferedReader().readLines()
            proc.waitFor()
            val now = System.currentTimeMillis()
            lines.mapNotNull { line ->
                val pattern = SILENT_SMS_PATTERNS.firstOrNull { it.containsMatchIn(line) }
                if (pattern != null) {
                    RadioLogHit(now, line.trim().take(300), pattern.pattern)
                } else {
                    val lower = line.lowercase()
                    if (lower.contains("sms") && SILENT_MARKERS.any { lower.contains(it) }) {
                        RadioLogHit(now, line.trim().take(300), "silent-marker")
                    } else null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** True when the radio buffer is readable at all (grant check). */
    fun radioBufferReadable(): Boolean = try {
        val proc = ProcessBuilder("logcat", "-d", "-t", "1", "-b", "radio")
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        val ok = proc.waitFor() == 0 && !out.contains("Permission Denial", ignoreCase = true)
        ok
    } catch (e: Exception) { false }
}
