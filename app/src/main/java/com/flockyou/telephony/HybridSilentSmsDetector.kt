package com.flockyou.telephony

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SilentSmsEvidenceClass { EXACT, INDIRECT }

enum class TelephonyObservable {
    SERVICE_STATE_CHURN,
    CELL_RESELECTION,
    RAT_CHANGE,
    DATA_REGISTRATION_CHANGE,
    SIGNAL_DISCONTINUITY,
    CELL_INFO_PROBE_CHANGE
}
data class TelephonyObservation(
    val type: TelephonyObservable,
    val timestampMs: Long = System.currentTimeMillis(),
    val detail: String
)

data class SilentSmsEvidence(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val evidenceClass: SilentSmsEvidenceClass,
    val confidence: Float,
    val sensorPath: String,
    val contributingFactors: List<String>,
    val label: String,
    val proofBoundary: String
)

class SilentSmsIndirectCorrelator(
    private val windowMs: Long = 15_000L,
    private val cooldownMs: Long = 5 * 60_000L
) {
    private val observations = ArrayDeque<TelephonyObservation>()
    private var lastAlertMs = 0L

    @Synchronized
    fun observe(observation: TelephonyObservation): SilentSmsEvidence? {
        observations.addLast(observation)
        val cutoff = observation.timestampMs - windowMs
        while (observations.isNotEmpty() && observations.first().timestampMs < cutoff) {
            observations.removeFirst()
        }
        if (observation.timestampMs - lastAlertMs < cooldownMs) return null

        val unique = observations.map { it.type }.toSet()
        val hasNetworkTransition = unique.any {
            it == TelephonyObservable.CELL_RESELECTION ||
                it == TelephonyObservable.RAT_CHANGE ||
                it == TelephonyObservable.SERVICE_STATE_CHURN
        }
        val hasSecondary = unique.any {
            it == TelephonyObservable.DATA_REGISTRATION_CHANGE ||
                it == TelephonyObservable.SIGNAL_DISCONTINUITY ||
                it == TelephonyObservable.CELL_INFO_PROBE_CHANGE
        }
        if (unique.size < 4 || !hasNetworkTransition || !hasSecondary) return null
        lastAlertMs = observation.timestampMs
        val confidence = (0.38f + unique.size * 0.06f).coerceAtMost(0.68f)
        return SilentSmsEvidence(
            timestampMs = observation.timestampMs,
            evidenceClass = SilentSmsEvidenceClass.INDIRECT,
            confidence = confidence,
            sensorPath = "Android public Telephony APIs",
            contributingFactors = observations.map { "${it.type.name}:${it.detail}" }.distinct(),
            label = "INDIRECT / NOT PROOF OF SILENT SMS",
            proofBoundary = "Correlated modem-visible state changes can have benign carrier, mobility, coverage, SIM, or power-management causes. No Type-0 payload was observed."
        )
    }

    fun reset() {
        observations.clear()
        lastAlertMs = 0L
    }
}

object HybridSilentSmsRegistry {
    private val _evidence = MutableStateFlow<List<SilentSmsEvidence>>(emptyList())
    val evidence: StateFlow<List<SilentSmsEvidence>> = _evidence.asStateFlow()

    fun record(item: SilentSmsEvidence) {
        _evidence.value = (listOf(item) + _evidence.value).take(100)
    }

    fun recordExact(timestampMs: Long, sensorPath: String, details: List<String>, parserConfidence: Float) {
        record(
            SilentSmsEvidence(
                timestampMs = timestampMs,
                evidenceClass = SilentSmsEvidenceClass.EXACT,
                confidence = parserConfidence.coerceIn(0.5f, 0.85f),
                sensorPath = sensorPath,
                contributingFactors = details,
                label = "EXACT MODEM SIGNALING: TYPE-0 EVENT",
                proofBoundary = "The modem diagnostic parser observed Type-0 signaling. The numeric parser confidence is heuristic, not empirically calibrated. Sender identity, legal authority, intent, and whether location was derived are not established by this event."
            )
        )
    }
}
class HybridSilentSmsDetector(private val context: Context) {
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: Executor = Executor { command -> mainHandler.post(command) }
    private val correlator = SilentSmsIndirectCorrelator()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var callback31: Any? = null
    private var legacyListener: PhoneStateListener? = null
    private var probeJob: Job? = null
    private var lastServiceState: Int? = null
    private var lastRat: Int? = null
    private var lastDataState: Int? = null
    private var lastSignalDbm: Int? = null
    private var lastCellHash: String? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startApi31() else startLegacy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && probeJob?.isActive != true) {
            probeJob = scope.launch {
                while (isActive) {
                    requestCellInfoProbe()
                    delay(15_000L)
                }
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopApi31()
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        callback31 = null
        legacyListener = null
        probeJob?.cancel()
        probeJob = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startApi31() {
        if (callback31 != null) return
        val callback = object : TelephonyCallback(),
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.DataConnectionStateListener {
            override fun onServiceStateChanged(serviceState: ServiceState) {
                ingestServiceState(serviceState.state)
            }

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val dbm = strongestDbm(signalStrength)
                if (dbm != null) ingestSignal(dbm)
            }

            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                ingestCellInfo(cellInfo, TelephonyObservable.CELL_RESELECTION)
            }

            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                ingestDataState(state)
                ingestRat(networkType)
            }
        }
        try {
            telephony.registerTelephonyCallback(executor, callback)
            callback31 = callback
        } catch (_: SecurityException) {
            callback31 = null
        }
    }

    @Suppress("DEPRECATION")
    private fun startLegacy() {
        if (legacyListener != null) return
        val listener = object : PhoneStateListener() {
            override fun onServiceStateChanged(serviceState: ServiceState?) {
                serviceState?.let { ingestServiceState(it.state) }
            }

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                val dbm = signalStrength?.let(::strongestDbm)
                if (dbm != null) ingestSignal(dbm)
            }

            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                cellInfo?.let { ingestCellInfo(it, TelephonyObservable.CELL_RESELECTION) }
            }

            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                ingestDataState(state)
                ingestRat(networkType)
            }
        }
        try {
            telephony.listen(
                listener,
                PhoneStateListener.LISTEN_SERVICE_STATE or
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    PhoneStateListener.LISTEN_CELL_INFO or
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            )
            legacyListener = listener
        } catch (_: SecurityException) {
            legacyListener = null
        }
    }
    private fun ingestServiceState(state: Int) {
        val previous = lastServiceState
        lastServiceState = state
        if (previous != null && previous != state) {
            emit(TelephonyObservable.SERVICE_STATE_CHURN, "$previous->$state")
        }
    }

    private fun ingestRat(networkType: Int) {
        val previous = lastRat
        lastRat = networkType
        if (previous != null && previous != networkType) {
            emit(TelephonyObservable.RAT_CHANGE, "$previous->$networkType")
        }
    }

    private fun ingestDataState(state: Int) {
        val previous = lastDataState
        lastDataState = state
        if (previous != null && previous != state) {
            emit(TelephonyObservable.DATA_REGISTRATION_CHANGE, "$previous->$state")
        }
    }

    private fun ingestSignal(dbm: Int) {
        val previous = lastSignalDbm
        lastSignalDbm = dbm
        if (previous != null && kotlin.math.abs(dbm - previous) >= 18) {
            emit(TelephonyObservable.SIGNAL_DISCONTINUITY, "$previous->$dbm dBm")
        }
    }

    private fun ingestCellInfo(cellInfo: List<CellInfo>, type: TelephonyObservable) {
        val registered = cellInfo.firstOrNull { it.isRegistered } ?: return
        val digest = sha256(cellIdentityKey(registered)).take(16)
        val previous = lastCellHash
        lastCellHash = digest
        if (previous != null && previous != digest) {
            emit(type, "$previous->$digest")
        }
    }
    private fun strongestDbm(signalStrength: SignalStrength): Int? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            strongestDbmApi29(signalStrength)
        } else {
            // Public pre-29 SignalStrength does not expose a RAT-neutral dBm list.
            // Omit this factor rather than infer dBm from coarse level/ASU values.
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun strongestDbmApi29(signalStrength: SignalStrength): Int? =
        signalStrength.cellSignalStrengths.maxOfOrNull { it.dbm }

    private fun cellIdentityKey(info: CellInfo): String = when (info) {
        is CellInfoGsm -> "gsm:${info.cellIdentity}"
        is CellInfoLte -> "lte:${info.cellIdentity}"
        is CellInfoWcdma -> "wcdma:${info.cellIdentity}"
        is CellInfoCdma -> "cdma:${info.cellIdentity}"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cellIdentityKeyApi29(info)
        } else {
            info.javaClass.name
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cellIdentityKeyApi29(info: CellInfo): String = when (info) {
        is CellInfoNr -> "nr:${info.cellIdentity}"
        is CellInfoTdscdma -> "tdscdma:${info.cellIdentity}"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cellIdentityKeyApi30(info)
        } else {
            info.javaClass.name
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun cellIdentityKeyApi30(info: CellInfo): String =
        "${info.javaClass.simpleName}:${info.cellIdentity}"

    @RequiresApi(Build.VERSION_CODES.S)
    private fun stopApi31() {
        (callback31 as? TelephonyCallback)?.let(telephony::unregisterTelephonyCallback)
    }

    private fun emit(type: TelephonyObservable, detail: String) {
        correlator.observe(
            TelephonyObservation(type = type, detail = detail)
        )?.let(HybridSilentSmsRegistry::record)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestCellInfoProbe() {
        try {
            telephony.requestCellInfoUpdate(
                executor,
                object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        ingestCellInfo(cellInfo, TelephonyObservable.CELL_INFO_PROBE_CHANGE)
                    }
                }
            )
        } catch (_: SecurityException) {
            // Capability absent. Public telephony path remains available at lower fidelity.
        } catch (_: UnsupportedOperationException) {
            // Device/vendor radio interface does not implement active cell-info refresh.
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}