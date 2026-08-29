package com.flockyou.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flockyou.adversarial.BleTailRegistry
import com.flockyou.adversarial.MagneticTrackerSweep
import com.flockyou.adversarial.NirPulseAnalyzer
import com.flockyou.adversarial.RtlSdrAdsBReceiver
import com.flockyou.adversarial.RtlSdrCapability
import com.flockyou.adversarial.RtlSdrUsbDetector
import com.flockyou.adversarial.ZeroCloudGossipManager
import com.flockyou.data.model.DeviceType
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.privilege.RuntimeCapabilityLadder
import com.flockyou.privilege.RuntimeCapabilityProfile
import com.flockyou.telephony.HybridSilentSmsRegistry
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdversarialSensorsScreen(
    detectionRepository: DetectionRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detections by detectionRepository.allDetections.collectAsState(initial = emptyList())
    val magnetic = remember { MagneticTrackerSweep(context.applicationContext) }
    val magneticState by magnetic.state.collectAsState()
    val mesh = remember { ZeroCloudGossipManager(context.applicationContext) }
    val meshState by mesh.state.collectAsState()
    val bleTailAlerts by BleTailRegistry.alerts.collectAsState()
    val silentSmsEvidence by HybridSilentSmsRegistry.evidence.collectAsState()
    val nirAnalyzer = remember { NirPulseAnalyzer() }
    val nirState by nirAnalyzer.state.collectAsState()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val uiScope = rememberCoroutineScope()
    var cameraEnabled by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var rtlCapability by remember { mutableStateOf<RtlSdrCapability?>(null) }
    val adsbReceiver = remember { RtlSdrAdsBReceiver(context.applicationContext) }
    val adsbState by adsbReceiver.state.collectAsState()
    var capabilityProfile by remember { mutableStateOf<RuntimeCapabilityProfile?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraEnabled = granted }
    val meshPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) mesh.start()
    }

    LaunchedEffect(Unit) {
        val (rtl, profile) = withContext(Dispatchers.IO) {
            RtlSdrUsbDetector.detect(context) to RuntimeCapabilityLadder.detect(context)
        }
        rtlCapability = rtl
        capabilityProfile = profile
    }

    LaunchedEffect(detections) {
        val tokens = detections.asSequence()
            .filter { it.latitude != null && it.longitude != null }
            .filter {
                it.deviceType in setOf(
                    DeviceType.FLOCK_SAFETY_CAMERA,
                    DeviceType.SPEED_CAMERA,
                    DeviceType.RED_LIGHT_CAMERA,
                    DeviceType.TRAFFIC_SENSOR,
                    DeviceType.SURVEILLANCE_INFRASTRUCTURE
                )
            }
            .map {
                "v1|${it.deviceType.name}|" +
                    String.format(Locale.US, "%.4f|%.4f", it.latitude, it.longitude)
            }
            .toSet()
        mesh.updateLocalTokens(tokens)
    }

    DisposableEffect(Unit) {
        onDispose {
            magnetic.stop()
            mesh.destroy()
            adsbReceiver.destroy()
            cameraProvider?.unbindAll()
            cameraExecutor.shutdownNow()
        }
    }
    DisposableEffect(cameraEnabled, lifecycleOwner) {
        if (!cameraEnabled) cameraProvider?.unbindAll()
        onDispose { cameraProvider?.unbindAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adversarial Sensors") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val profile = capabilityProfile
                SensorCard(
                    title = "Runtime Capability + Silent-SMS Evidence",
                    status = profile?.let {
                        "${it.tier.name} · root ${it.rootGrant.name} · ${it.rawDiagnosticTransport ?: "public telephony"} · ${if (it.exactSilentSmsAvailable) "exact+indirect" else "indirect only"}"
                    } ?: "Detecting runtime capabilities…",
                    boundary = profile?.notes?.joinToString(" · ")?.ifBlank {
                        "Capability is runtime-derived; build flavor alone never proves privileged sensor access."
                    } ?: "Capability probes run off the UI thread and never prompt for root passively."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            uiScope.launch {
                                capabilityProfile = withContext(Dispatchers.IO) {
                                    RuntimeCapabilityLadder.detect(context)
                                }
                            }
                        }) { Text("Refresh tier") }
                        Button(onClick = {
                            uiScope.launch {
                                capabilityProfile = withContext(Dispatchers.IO) {
                                    RuntimeCapabilityLadder.requestRootProbe()
                                    RuntimeCapabilityLadder.detect(context)
                                }
                            }
                        }) { Text("Probe root") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val latest = silentSmsEvidence.firstOrNull()
                    Text(
                        latest?.let {
                            "${it.label} · ${(it.confidence * 100).toInt()}% · ${it.sensorPath}"
                        } ?: "No Silent-SMS evidence observed"
                    )
                    latest?.let { Text(it.proofBoundary, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                SensorCard(
                    title = "NIR / Optical Pulse Tripwire",
                    status = if (nirState.candidateDetected) {
                        "Candidate ${(nirState.confidence * 100).toInt()}% · ${nirState.estimatedHz ?: 0f} Hz"
                    } else nirState.message,
                    boundary = nirState.proofBoundary
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) cameraEnabled = !cameraEnabled
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text(if (cameraEnabled) "Stop camera" else "Start camera")
                        }
                        OutlinedButton(onClick = nirAnalyzer::reset) { Text("Reset") }
                    }
                    if (cameraEnabled) {
                        Spacer(Modifier.height(8.dp))
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            factory = { ctx ->
                                PreviewView(ctx).also { view ->
                                    val future = ProcessCameraProvider.getInstance(ctx)
                                    future.addListener({
                                        val provider = future.get()
                                        val preview = Preview.Builder().build()
                                        preview.setSurfaceProvider(view.surfaceProvider)
                                        try {
                                            provider.unbindAll()
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                            analysis.setAnalyzer(cameraExecutor, nirAnalyzer)
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                analysis
                                            )
                                            cameraProvider = provider
                                        } catch (_: Exception) { cameraEnabled = false }
                                    }, ContextCompat.getMainExecutor(ctx))
                                }
                            }
                        )
                    }
                }
            }
            item {
                val rtl = rtlCapability
                SensorCard(
                    title = "RTL-SDR / ADS-B 1090 MHz",
                    status = if (adsbState.active) {
                        "LIVE · ${adsbState.aircraft.size} aircraft · ${adsbState.validFrames} CRC-valid frames"
                    } else adsbState.error ?: rtl?.status ?: "Detecting USB SDR capability…",
                    boundary = "Only CRC-valid DF17/DF18 frames demodulated from live 1090 MHz RTL2832U I/Q are counted as aircraft. USB presence or RF energy alone never creates an aircraft detection."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            uiScope.launch {
                                rtlCapability = withContext(Dispatchers.IO) { RtlSdrUsbDetector.detect(context) }
                            }
                        }, enabled = !adsbState.active) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Refresh")
                        }
                        val device = rtl?.device
                        if (device != null && !rtl.permissionGranted) {
                            Button(onClick = { RtlSdrUsbDetector.requestPermission(context, device) }) { Text("USB permission") }
                        } else if (device != null && rtl.permissionGranted && rtl.nativeBackendAvailable) {
                            Button(onClick = { if (adsbState.active) adsbReceiver.stop() else adsbReceiver.start(device) }) {
                                Text(if (adsbState.active) "Stop 1090" else "Start 1090")
                            }
                        }
                    }
                    if (adsbState.active) {
                        Text(
                            "${adsbState.tuner ?: "RTL2832"} · ${adsbState.sampleRate?.toInt() ?: 0} sps · ${"%.1f".format(adsbState.bytesReceived / 1_000_000.0)} MB I/Q",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (adsbState.active) {
                        Text(
                            if (adsbState.observerFixFresh) "Fresh local observer fix · overhead geometry enabled"
                            else "No fresh local observer fix · aircraft decoding only; overhead alerts disabled",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    adsbState.loiterCandidates.take(4).forEach { candidate ->
                        Text(
                            "OVERHEAD LOITER CANDIDATE · ${candidate.icao} · ${(candidate.confidence * 100).toInt()}% · ${"%.1f".format(candidate.medianObserverDistanceMeters / 1000.0)} km median · ${candidate.cumulativeTurnDegrees.toInt()}° turn",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(candidate.proofBoundary, style = MaterialTheme.typography.bodySmall)
                    }
                    adsbState.aircraft.take(8).forEach { ac ->
                        Text(
                            buildString {
                                append(ac.callsign ?: ac.icao).append(" · ").append(ac.icao)
                                ac.altitudeFeet?.let { append(" · ").append(it).append(" ft") }
                                ac.groundSpeedKnots?.let { append(" · ").append(it).append(" kt") }
                                ac.trackDegrees?.let { append(" · ").append(it).append("°") }
                                ac.observerDistanceMeters?.let { append(" · ").append("%.1f km".format(it / 1000.0)) }
                                append(" · ").append(ac.messageCount).append(" msg")
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                SensorCard(
                    title = "Zero-Cloud Local Mesh (Wi-Fi Aware → BLE GATT)",
                    status = "${meshState.status} · peers ${meshState.peersSeen} · encrypted syncs ${meshState.encryptedSyncs}",
                    boundary = "Ephemeral ECDH + AES-GCM Bloom summaries only; no account, device ID, raw coordinates, or central server. Peers are anonymous: encryption limits passive disclosure but does not authenticate against an active MITM."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (meshState.active) {
                                mesh.stop()
                            } else {
                                val missing = requiredMeshPermissions().filter { permission ->
                                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                                }
                                if (missing.isEmpty()) mesh.start() else meshPermissionLauncher.launch(missing.toTypedArray())
                            }
                        }) {
                            Text(if (meshState.active) "Stop mesh" else "Start mesh")
                        }
                    }
                }
            }

            item {
                SensorCard(
                    title = "Magnetometer Vehicle Sweep",
                    status = "${magneticState.message} · Δ ${"%.1f".format(magneticState.deltaMicroTesla)} µT · peak ${"%.1f".format(magneticState.peakDeltaMicroTesla)} µT",
                    boundary = "Magnetic anomaly only; vehicle steel, speakers, motors, and wiring can produce strong fields. Physically inspect before classifying a tracker."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (magneticState.active) magnetic.stop() else magnetic.start() }) {
                            Text(if (magneticState.active) "Stop sweep" else "Start sweep")
                        }
                        OutlinedButton(onClick = magnetic::recalibrate) { Text("Recalibrate") }
                    }
                }
            }
            item {
                SensorCard(
                    title = "BLE Co-Traveler Graph",
                    status = if (bleTailAlerts.isEmpty()) {
                        "No qualifying co-traveler fingerprint"
                    } else {
                        "${bleTailAlerts.size} heuristic fingerprint${if (bleTailAlerts.size == 1) "" else "s"}"
                    },
                    boundary = "Requires ≥3 rotating MACs in one plausible continuous journey, ≥3 locations separated by ≥2 miles, ≥8 minutes of continuity, and rejects implausible travel speed. Similar advertising structure remains correlation, not identity proof."
                ) { }
            }

            items(bleTailAlerts.take(5)) { alert ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Fingerprint ${alert.fingerprint}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${alert.distinctMacs} MACs · ${alert.separatedLocations} separated locations · " +
                                "${(alert.maxSeparationMeters / 1609.344).let { "%.1f".format(it) }} mi max · " +
                                "${alert.journeyDurationMs / 60_000} min · ${(alert.continuityRatio * 100).toInt()}% continuity"
                        )
                        Text(alert.proofBoundary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorCard(
    title: String,
    status: String,
    boundary: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(boundary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun requiredMeshPermissions(): List<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    return permissions
}
