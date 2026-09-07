# ============================================================
# Flock-Sucker ProGuard Rules - COMPREHENSIVE
# ============================================================
# This file ensures all app classes are protected from obfuscation
# that would break functionality (IPC, reflection, serialization)

# ============================================================
# GENERAL KOTLIN RULES
# ============================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class kotlin.Metadata { *; }

# ============================================================
# KOTLIN COROUTINES - CRITICAL FOR STATEFLOW/FLOW
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
# Keep StateFlow and SharedFlow internals
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.channels.** { *; }

# ============================================================
# KOTLIN DATA CLASSES - PRESERVE FIELD NAMES FOR GSON
# ============================================================
# R8 can obfuscate field names which breaks Gson reflection
# Keep all field names in data classes used for serialization
-keepclassmembers class com.inversionlabs.flocksucker.**.* {
    <fields>;
}
# Keep all enum entries
-keepclassmembers enum com.inversionlabs.flocksucker.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
# Keep sealed class subclasses
-keep class * extends com.inversionlabs.flocksucker.service.ScanningService$ScanStatus { *; }
-keep class * extends com.inversionlabs.flocksucker.service.ScanningService$SubsystemStatus { *; }

# ============================================================
# KOTLIN INTRINSICS - Prevent R8 from removing null checks
# ============================================================
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkNotNullParameter(...);
    static void checkParameterIsNotNull(...);
    static void checkNotNullExpressionValue(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkReturnedValueIsNotNull(...);
    static void throwUninitializedPropertyAccessException(...);
}

# ============================================================
# NUCLEAR OPTION - DISABLE OBFUSCATION FOR ENTIRE APP
# ============================================================
# This ensures no class/method/field names are changed, preventing
# any serialization, reflection, or callback registration issues.
# Trade-off: Slightly larger APK, but guaranteed to work.
-keepnames class com.inversionlabs.flocksucker.** { *; }
-keepnames interface com.inversionlabs.flocksucker.** { *; }
-keepclassmembernames class com.inversionlabs.flocksucker.** { *; }

# Keep ALL anonymous inner classes in the app (callbacks, etc.)
-keepclassmembers class com.inversionlabs.flocksucker.** {
    *** $*;
}

# ============================================================
# BLE SCAN CALLBACKS - Anonymous implementations
# ============================================================
# Multiple classes create anonymous ScanCallback objects that
# R8 might strip or rename, breaking BLE scanning
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService {
    private *** bleScanCallback;
    private *** wifiScanReceiver;
}
-keepclassmembers class com.inversionlabs.flocksucker.scanner.standard.StandardBluetoothScanner {
    private *** scanCallbackImpl;
}
-keepclassmembers class com.inversionlabs.flocksucker.scanner.system.SystemBluetoothScanner {
    private *** scanCallbackImpl;
}
-keepclassmembers class com.inversionlabs.flocksucker.scanner.flipper.FlipperBluetoothClient {
    private *** scanCallback;
    private *** gattCallback;
}
-keepclassmembers class com.inversionlabs.flocksucker.ui.screens.FlipperSettingsViewModel {
    private *** scanCallback;
}

# ============================================================
# GNSS/TELEPHONY CALLBACKS - Anonymous implementations
# ============================================================
-keepclassmembers class com.inversionlabs.flocksucker.monitoring.GnssSatelliteMonitor {
    private *** gnssStatusCallback;
    private *** measurementsCallback;
    private *** locationCallback;
}
-keepclassmembers class com.inversionlabs.flocksucker.monitoring.SatelliteMonitor {
    private *** telephonyCallback;
}
-keepclassmembers class com.inversionlabs.flocksucker.service.CellularMonitor {
    private *** telephonyCallback;
    private *** phoneStateListener;
}

# ============================================================
# APPLICATION CORE CLASSES
# ============================================================
-keep class com.inversionlabs.flocksucker.FlockSuckerApplication { *; }
-keep class com.inversionlabs.flocksucker.MainActivity { *; }

# ============================================================
# ALL SERVICES (Manifest-declared, must keep names)
# ============================================================
-keep class com.inversionlabs.flocksucker.service.ScanningService { *; }
-keep class com.inversionlabs.flocksucker.service.ScanningService$* { *; }
-keep class com.inversionlabs.flocksucker.service.ServiceRestartJobService { *; }
-keep class com.inversionlabs.flocksucker.service.QuickWipeTileService { *; }
-keep class com.inversionlabs.flocksucker.auto.FlockSuckerCarAppService { *; }

# ============================================================
# ALL BROADCAST RECEIVERS (Manifest-declared)
# ============================================================
-keep class com.inversionlabs.flocksucker.service.BootReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.ServiceRestartReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.ScreenLockReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.QuickWipeReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.nuke.BootWatcher { *; }
-keep class com.inversionlabs.flocksucker.service.nuke.UsbWatchdogReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.nuke.SimStateReceiver { *; }
-keep class com.inversionlabs.flocksucker.service.nuke.NetworkIsolationReceiver { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperAlertBroadcastReceiver { *; }

# ============================================================
# ALL ACTIVITIES
# ============================================================
-keep class com.inversionlabs.flocksucker.ui.EmergencyAlertActivity { *; }
-keep class com.inversionlabs.flocksucker.service.QuickWipeConfirmationActivity { *; }
-keep class com.inversionlabs.flocksucker.debug.ScreenshotHelperActivity { *; }

# ============================================================
# SERVICE MONITORING CLASSES - CRITICAL
# ============================================================
# These classes handle sensor data collection - if obfuscated,
# no BT/WiFi/GNSS/Cell data will appear in the UI
-keep class com.inversionlabs.flocksucker.service.CellularMonitor { *; }
-keep class com.inversionlabs.flocksucker.service.CellularMonitor$* { *; }
-keep class com.inversionlabs.flocksucker.service.RogueWifiMonitor { *; }
-keep class com.inversionlabs.flocksucker.service.RogueWifiMonitor$* { *; }
-keep class com.inversionlabs.flocksucker.service.RfSignalAnalyzer { *; }
-keep class com.inversionlabs.flocksucker.service.RfSignalAnalyzer$* { *; }
-keep class com.inversionlabs.flocksucker.service.UltrasonicDetector { *; }
-keep class com.inversionlabs.flocksucker.service.UltrasonicDetector$* { *; }

# ============================================================
# MONITORING PACKAGE - GNSS/Satellite detection
# ============================================================
-keep class com.inversionlabs.flocksucker.monitoring.** { *; }

# ============================================================
# SCANNER PACKAGE - All scanner implementations
# ============================================================
# Scanner interfaces and base classes
-keep class com.inversionlabs.flocksucker.scanner.ScannerInterfaces { *; }
-keep class com.inversionlabs.flocksucker.scanner.ScannerInterfaces$* { *; }
-keep class com.inversionlabs.flocksucker.scanner.ScannerFactory { *; }
-keep class com.inversionlabs.flocksucker.scanner.ScannerFactory$* { *; }
-keep class com.inversionlabs.flocksucker.scanner.ScannerModeHelper { *; }

# Standard API scanners
-keep class com.inversionlabs.flocksucker.scanner.standard.** { *; }

# System-level scanners (privileged)
-keep class com.inversionlabs.flocksucker.scanner.system.** { *; }

# ============================================================
# FLIPPER ZERO INTEGRATION - COMPREHENSIVE
# ============================================================
-keep class com.inversionlabs.flocksucker.scanner.flipper.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.scanner.flipper.** { *; }

# Flipper Protocol - Binary parsing requires exact field names
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperProtocol { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperProtocol$* { *; }

# Flipper Models - Data classes for protocol communication
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperWifiScanResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperWifiNetwork { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperSubGhzScanResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperSubGhzDetection { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperSubGhzScanStatus { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperBleScanResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperBleDevice { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperIrScanResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperIrDetection { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperNfcScanResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperNfcDetection { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperStatusResponse { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperWipsAlert { *; }

# Flipper Enums - Must preserve names for protocol parsing
-keepclassmembers enum com.inversionlabs.flocksucker.scanner.flipper.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
-keep enum com.inversionlabs.flocksucker.scanner.flipper.WifiSecurityType { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.SubGhzModulation { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.BleDeviceType { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.IrProtocol { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.NfcType { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.FlipperConnectionState { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.FlipperConnectionPreference { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.FlipperHapticPattern { *; }
-keep enum com.inversionlabs.flocksucker.scanner.flipper.FlipperAlertSound { *; }

# Flipper Settings - DataStore serialization requires field names
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperSettings { *; }
-keepclassmembers class com.inversionlabs.flocksucker.scanner.flipper.FlipperSettings { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperSettingsRepository { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.RecentFlipperDevice { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.AutoReconnectState { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.DiscoveredFlipperDevice { *; }

# Flipper BLE Client - GATT callbacks
-keepclassmembers class com.inversionlabs.flocksucker.scanner.flipper.FlipperBluetoothClient {
    private *** gattCallback;
    private *** scanCallback;
    private *** connectionStateCallback;
}

# Active probes
-keep class com.inversionlabs.flocksucker.scanner.probes.** { *; }

# ============================================================
# DATA MODELS - All serialized via Gson/Room
# ============================================================
-keep class com.inversionlabs.flocksucker.data.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.data.** { *; }

# Ensure enum valueOf works
-keepclassmembers enum com.inversionlabs.flocksucker.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# ============================================================
# DETECTION FRAMEWORK - COMPREHENSIVE
# ============================================================
-keep class com.inversionlabs.flocksucker.detection.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.detection.** { *; }
-keep class com.inversionlabs.flocksucker.detection.handler.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.detection.handler.** { *; }
-keep class com.inversionlabs.flocksucker.detection.framework.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.detection.framework.** { *; }
-keep class com.inversionlabs.flocksucker.detection.config.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.detection.config.** { *; }
-keep class com.inversionlabs.flocksucker.detection.profile.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.detection.profile.** { *; }

# Detection Handler inner classes and callbacks
-keep class com.inversionlabs.flocksucker.detection.handler.*$* { *; }
-keep class com.inversionlabs.flocksucker.detection.framework.*$* { *; }
-keep class com.inversionlabs.flocksucker.detection.config.DetectionConfig { *; }
-keep class com.inversionlabs.flocksucker.detection.config.DetectionConfig$* { *; }

# Detection enums
-keepclassmembers enum com.inversionlabs.flocksucker.detection.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# ============================================================
# ============================================================
# AI/LLM CLASSES - COMPREHENSIVE
# ============================================================
# MediaPipe LLM inference requires all classes preserved
-keep class com.inversionlabs.flocksucker.ai.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.ai.** { *; }

# AI Correlation Analysis subpackage
-keep class com.inversionlabs.flocksucker.ai.correlation.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.ai.correlation.** { *; }
-keep class com.inversionlabs.flocksucker.ai.correlation.CorrelatedThreatAnalysis$* { *; }
-keep class com.inversionlabs.flocksucker.ai.correlation.CrossDomainAnalyzer$* { *; }

# AI sealed classes and their subclasses
-keep class com.inversionlabs.flocksucker.ai.ProgressiveAnalysisResult { *; }
-keep class com.inversionlabs.flocksucker.ai.ProgressiveAnalysisResult$* { *; }
-keep class com.inversionlabs.flocksucker.ai.AiAnalysisResult { *; }
-keep class com.inversionlabs.flocksucker.ai.AiAnalysisResult$* { *; }
-keep class com.inversionlabs.flocksucker.ai.LlmEngineManager$* { *; }
-keep class com.inversionlabs.flocksucker.ai.MediaPipeLlmClient$* { *; }
-keep class com.inversionlabs.flocksucker.ai.GeminiNanoClient$* { *; }
-keep class com.inversionlabs.flocksucker.ai.DetectionAnalyzer$* { *; }
-keep class com.inversionlabs.flocksucker.ai.RuleBasedAnalyzer$* { *; }
-keep class com.inversionlabs.flocksucker.ai.LlmOutputParser$* { *; }

# AI enums
-keepclassmembers enum com.inversionlabs.flocksucker.ai.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# AI/LLM CLASSES
# ============================================================
-keep class com.inversionlabs.flocksucker.ai.** { *; }

# ============================================================
# SECURITY/NUKE CLASSES
# ============================================================
-keep class com.inversionlabs.flocksucker.security.** { *; }
-keep class com.inversionlabs.flocksucker.service.nuke.** { *; }

# ============================================================
# NETWORK CLASSES (Tor support)
# ============================================================
-keep class com.inversionlabs.flocksucker.network.** { *; }

# ============================================================
# PRIVILEGE/SYSTEM INTEGRATION
# ============================================================
-keep class com.inversionlabs.flocksucker.privilege.** { *; }

# ============================================================
# ============================================================
# ANDROID AUTO - COMPREHENSIVE
# ============================================================
-keep class com.inversionlabs.flocksucker.auto.** { *; }
-keepclassmembers class com.inversionlabs.flocksucker.auto.** { *; }

# Car App Screens and Sessions
-keep class com.inversionlabs.flocksucker.auto.FlockSuckerCarAppService { *; }
-keep class com.inversionlabs.flocksucker.auto.FlockSuckerSession { *; }
-keep class com.inversionlabs.flocksucker.auto.*Screen { *; }
-keep class com.inversionlabs.flocksucker.auto.*Screen$* { *; }

# Android Auto data models
-keep class com.inversionlabs.flocksucker.auto.*State { *; }
-keep class com.inversionlabs.flocksucker.auto.*Data { *; }
-keep class com.inversionlabs.flocksucker.auto.*Model { *; }

# ============================================================
# TEST MODE (Debug/Testing support)
# ============================================================
-keep class com.inversionlabs.flocksucker.testmode.** { *; }

# ============================================================
# CONFIG CLASSES
# ============================================================
-keep class com.inversionlabs.flocksucker.config.** { *; }

# ============================================================
# DOMAIN/USE CASES
# ============================================================
-keep class com.inversionlabs.flocksucker.domain.** { *; }

# ============================================================
# UTILITY CLASSES
# ============================================================
-keep class com.inversionlabs.flocksucker.util.** { *; }

# ============================================================
# UI VIEWMODELS - Hilt injection requires names
# ============================================================
-keep class com.inversionlabs.flocksucker.ui.screens.**ViewModel { *; }
-keep class com.inversionlabs.flocksucker.ui.screens.**UiState { *; }
-keep class com.inversionlabs.flocksucker.ui.components.**ViewModel { *; }

# ============================================================
# WORKER CLASSES (WorkManager)
# ============================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class com.inversionlabs.flocksucker.worker.** { *; }

# ============================================================
# IPC/MESSENGER - Critical for inter-process communication
# ============================================================
-keep class com.inversionlabs.flocksucker.service.ScanningServiceIpc { *; }
-keep class com.inversionlabs.flocksucker.service.ScanningServiceIpc$* { *; }
-keep class com.inversionlabs.flocksucker.service.ScanningServiceConnection { *; }
-keep class com.inversionlabs.flocksucker.service.ScanningServiceConnection$* { *; }

# Keep ALL members of classes used in IPC serialization (Gson needs field names)
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$SeenDevice { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$ScanConfig { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$ScanStatistics { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$ScanError { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$DetectorHealthStatus { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningService$LearnedSignature { *; }

# Monitoring package data classes - CRITICAL for GNSS/Satellite/Cellular data
-keepclassmembers class com.inversionlabs.flocksucker.monitoring.GnssSatelliteMonitor$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.monitoring.SatelliteMonitor$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.monitoring.SatelliteDetectionHeuristics$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.CellularMonitor$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.RogueWifiMonitor$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.UltrasonicDetector$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.RfSignalAnalyzer$* { *; }

# Data model classes - used throughout IPC
-keepclassmembers class com.inversionlabs.flocksucker.data.model.** { *; }

# ============================================================
# CALLBACK INTERFACES
# ============================================================
-keep interface com.inversionlabs.flocksucker.** { *; }
-keepclassmembers class * implements com.inversionlabs.flocksucker.service.ScanningService$DetectorCallback { *; }

# ============================================================
# SEALED CLASSES - Subclasses must be kept for type checking
# ============================================================
-keep class com.inversionlabs.flocksucker.privilege.PrivilegeMode { *; }
-keep class com.inversionlabs.flocksucker.privilege.PrivilegeMode$* { *; }
-keep class com.inversionlabs.flocksucker.ai.MediaPipeLlmStatus { *; }
-keep class com.inversionlabs.flocksucker.ai.MediaPipeLlmStatus$* { *; }
-keep class com.inversionlabs.flocksucker.ai.GeminiNanoStatus { *; }
-keep class com.inversionlabs.flocksucker.ai.GeminiNanoStatus$* { *; }
-keep class com.inversionlabs.flocksucker.ai.LlmEngineManager$EngineStatus { *; }
-keep class com.inversionlabs.flocksucker.ai.LlmEngineManager$EngineStatus$* { *; }
-keep class com.inversionlabs.flocksucker.data.ProtectionPreset { *; }
-keep class com.inversionlabs.flocksucker.data.ProtectionPreset$* { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperMessage { *; }
-keep class com.inversionlabs.flocksucker.scanner.flipper.FlipperMessage$* { *; }
-keep class com.inversionlabs.flocksucker.testmode.TestScenario { *; }
-keep class com.inversionlabs.flocksucker.testmode.TestScenario$* { *; }
-keep class com.inversionlabs.flocksucker.security.DuressAuthenticator$DuressCheckResult { *; }
-keep class com.inversionlabs.flocksucker.security.DuressAuthenticator$DuressCheckResult$* { *; }
-keep class com.inversionlabs.flocksucker.scanner.probes.ActiveProbeSettings$ProbeAllowedResult { *; }
-keep class com.inversionlabs.flocksucker.scanner.probes.ActiveProbeSettings$ProbeAllowedResult$* { *; }
-keep class com.inversionlabs.flocksucker.ui.screens.ActiveProbesViewModel$ProbeExecutionState { *; }
-keep class com.inversionlabs.flocksucker.ui.screens.ActiveProbesViewModel$ProbeExecutionState$* { *; }

# ============================================================
# HILT DEPENDENCY INJECTION
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keep class com.inversionlabs.flocksucker.di.** { *; }

# ============================================================
# ROOM DATABASE
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Dao interface * { *; }
-keep class com.inversionlabs.flocksucker.data.repository.Converters { *; }
-keep class com.inversionlabs.flocksucker.data.repository.FlockSuckerDatabase { *; }

# ============================================================
# GSON SERIALIZATION
# ============================================================
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# CRITICAL: Keep TypeToken subclasses for Gson reflection
# R8 aggressively strips anonymous TypeToken classes, breaking JSON deserialization
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepattributes EnclosingMethod

# Keep anonymous TypeToken instances used in IPC serialization
# These are created inline in ScanningServiceIpc and ScanningServiceConnection
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningServiceIpc$* { *; }
-keepclassmembers class com.inversionlabs.flocksucker.service.ScanningServiceConnection$* { *; }

# ============================================================
# ANDROID SYSTEM CALLBACKS - CRITICAL FOR RELEASE BUILDS
# ============================================================
# These callbacks are registered via reflection by Android system
# R8 may strip them if it thinks they're unused

# GNSS/GPS Callbacks (for satellite monitoring)
-keep class * extends android.location.GnssStatus$Callback { *; }
-keep class * extends android.location.GnssMeasurementsEvent$Callback { *; }
-keep class * extends android.location.GnssNavigationMessage$Callback { *; }
-keep class * implements android.location.LocationListener { *; }
-keep class android.location.GnssStatus { *; }
-keep class android.location.GnssMeasurement { *; }
-keep class android.location.GnssClock { *; }

# Telephony Callbacks (for cellular monitoring - IMSI catcher detection)
-keep class * extends android.telephony.PhoneStateListener { *; }
-keep class * extends android.telephony.TelephonyCallback { *; }
-keep class * extends android.telephony.TelephonyCallback$* { *; }
-keep class * implements android.telephony.TelephonyCallback$* { *; }

# Telephony data classes (for NTN/Satellite modem monitoring)
-keep class android.telephony.CellInfo { *; }
-keep class android.telephony.CellInfo$* { *; }
-keep class android.telephony.CellInfoNr { *; }
-keep class android.telephony.CellIdentityNr { *; }
-keep class android.telephony.CellSignalStrengthNr { *; }
-keep class android.telephony.ServiceState { *; }
-keep class android.telephony.NetworkRegistrationInfo { *; }
-keep class android.telephony.TelephonyDisplayInfo { *; }
-keep class android.telephony.SignalStrength { *; }

# Android 14+ Satellite Manager API (for NTN satellite modem)
-keep class android.telephony.satellite.** { *; }
-keep class * implements android.telephony.satellite.SatelliteCallback { *; }
-dontwarn android.telephony.satellite.**

# BLE Scan Callbacks (for device detection)
-keep class * extends android.bluetooth.le.ScanCallback { *; }
-keep class android.bluetooth.le.ScanResult { *; }
-keep class android.bluetooth.le.ScanRecord { *; }

# WiFi Scan Callbacks
-keep class * implements android.content.BroadcastReceiver { *; }
-keep class android.net.wifi.ScanResult { *; }

# Audio Recording (for ultrasonic beacon detection)
-keep class android.media.AudioRecord { *; }
-keep class android.media.AudioFormat { *; }
-keep class android.media.AudioFormat$Builder { *; }

# Keep lambda implementations used as callbacks
-keepclassmembers class com.inversionlabs.flocksucker.** {
    private static synthetic void lambda$*(...);
}

# ============================================================
# OKHTTP
# ============================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# SQLCIPHER
# ============================================================
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ============================================================
# PROTOBUF (MediaPipe, ML Kit)
# ============================================================
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ============================================================
# TENSORFLOW LITE
# ============================================================
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }

# ============================================================
# MEDIAPIPE
# ============================================================
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.genai.llminference.jni.proto.** { *; }
-dontwarn com.google.mediapipe.framework.image.**

# ============================================================
# ML KIT
# ============================================================
-keep class com.google.mlkit.genai.** { *; }
-keep class com.google.android.libraries.mlkit.** { *; }

# ============================================================
# GOOGLE MAPS / OSM
# ============================================================
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class org.osmdroid.** { *; }

# ============================================================
# COROUTINES
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.flow.** { *; }

# ============================================================
# ANNOTATION PROCESSING (compile-time only)
# ============================================================
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# ============================================================
# GOOGLE TINK / CRYPTO (ML Kit dependencies)
# ============================================================
# These are optional dependencies used by KeysDownloader that we don't use
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn com.google.crypto.tink.util.KeysDownloader
-dontwarn org.joda.time.**

# ============================================================
# ANDROID AUTO / CAR APP LIBRARY
# ============================================================
-keep class androidx.car.app.** { *; }
-keep class * extends androidx.car.app.Screen { *; }
-keep class * extends androidx.car.app.Session { *; }
-keep class * extends androidx.car.app.CarAppService { *; }

# ============================================================
# USB SERIAL (Flipper Zero)
# ============================================================
-keep class com.hoho.android.usbserial.** { *; }

# ============================================================
# BIOMETRIC
# ============================================================
-keep class androidx.biometric.** { *; }

# ============================================================
# DATASTORE
# ============================================================
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ============================================================
# R8 OPTIMIZATION OVERRIDES - PREVENT AGGRESSIVE OPTIMIZATIONS
# ============================================================
# Prevent R8 from removing "unused" code that's actually used via reflection
-keepclassmembers,allowshrinking class com.inversionlabs.flocksucker.** {
    <methods>;
}

# Prevent R8 from inlining methods that are called via reflection
-keepclassmembers class com.inversionlabs.flocksucker.** {
    public <methods>;
    protected <methods>;
}

# Keep constructors for dependency injection
-keepclassmembers class com.inversionlabs.flocksucker.** {
    public <init>(...);
    @javax.inject.Inject <init>(...);
}

# Prevent R8 from removing companion objects
-keepclassmembers class com.inversionlabs.flocksucker.** {
    public static ** Companion;
    ** INSTANCE;
}

# Keep object singletons
-keepclassmembers class com.inversionlabs.flocksucker.**$Companion {
    *;
}

# ============================================================
# FINAL CATCH-ALL - Keep everything with @Keep annotation
# ============================================================
-keep @androidx.annotation.Keep class * { *; }
-keep class * {
    @androidx.annotation.Keep *;
}
