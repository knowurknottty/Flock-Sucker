package com.inversionlabs.flocksucker.di

import android.content.Context
import com.inversionlabs.flocksucker.data.DetectionSettingsRepository
import com.inversionlabs.flocksucker.detection.DetectionRegistry
import com.inversionlabs.flocksucker.detection.handler.BleRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.CellularDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.CellularRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.DetectionHandler
import com.inversionlabs.flocksucker.detection.handler.GnssDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.GnssRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.RfDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.RfRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.SatelliteDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.SatelliteRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.UltrasonicDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.UltrasonicRegistryHandler
import com.inversionlabs.flocksucker.detection.handler.WifiDetectionHandler
import com.inversionlabs.flocksucker.detection.handler.WifiRegistryHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt module for detection system dependency injection.
 *
 * This module provides:
 * - Individual detection handlers for each protocol
 * - A Set<DetectionHandler<*>> for the DetectionRegistry
 * - The DetectionRegistry singleton
 *
 * Handlers are bound @IntoSet to allow the registry to receive
 * all handlers via constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object DetectionModule {

    // ================================================================
    // Individual Handler Providers
    // Note: These handlers have @Inject constructors and are provided
    // directly by Hilt. We only need explicit providers for handlers
    // with complex dependencies or for setting up the @IntoSet bindings.
    // ================================================================

    // CellularDetectionHandler - has @Inject constructor (no dependencies)
    // UltrasonicDetectionHandler - has @Inject constructor (no dependencies)
    // GnssDetectionHandler - has @Inject constructor (no dependencies)
    // RfDetectionHandler - has @Inject constructor (no dependencies)
    // SatelliteDetectionHandler - requires DetectionSettingsRepository

    // ================================================================
    // Registry Handler Adapters (bound into Set)
    // These adapters wrap the protocol-specific handlers to conform
    // to the unified DetectionHandler<*> interface for the registry.
    // ================================================================

    /**
     * Provide BLE handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideBleHandler(
        @ApplicationContext context: Context
    ): DetectionHandler<*> {
        return BleRegistryHandler(context)
    }

    /**
     * Provide WiFi handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideWifiHandler(
        @ApplicationContext context: Context,
        wifiDetectionHandler: WifiDetectionHandler
    ): DetectionHandler<*> {
        return WifiRegistryHandler(context, wifiDetectionHandler)
    }

    /**
     * Provide Cellular handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideCellularHandler(
        @ApplicationContext context: Context,
        cellularDetectionHandler: CellularDetectionHandler
    ): DetectionHandler<*> {
        return CellularRegistryHandler(context, cellularDetectionHandler)
    }

    /**
     * Provide Satellite handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideSatelliteHandler(
        @ApplicationContext context: Context,
        satelliteDetectionHandler: SatelliteDetectionHandler
    ): DetectionHandler<*> {
        return SatelliteRegistryHandler(context, satelliteDetectionHandler)
    }

    /**
     * Provide Ultrasonic handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideUltrasonicHandler(
        @ApplicationContext context: Context,
        ultrasonicDetectionHandler: UltrasonicDetectionHandler
    ): DetectionHandler<*> {
        return UltrasonicRegistryHandler(context, ultrasonicDetectionHandler)
    }

    /**
     * Provide GNSS handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideGnssHandler(
        @ApplicationContext context: Context,
        gnssDetectionHandler: GnssDetectionHandler
    ): DetectionHandler<*> {
        return GnssRegistryHandler(context, gnssDetectionHandler)
    }

    /**
     * Provide RF handler into the handler set.
     */
    @Provides
    @Singleton
    @IntoSet
    fun provideRfHandler(
        @ApplicationContext context: Context,
        rfDetectionHandler: RfDetectionHandler
    ): DetectionHandler<*> {
        return RfRegistryHandler(context, rfDetectionHandler)
    }

    // ================================================================
    // Detection Registry
    // ================================================================

    /**
     * Provide the DetectionRegistry singleton.
     *
     * The registry receives all handlers via the Set<DetectionHandler<*>>
     * parameter, which is populated by the @IntoSet bindings above.
     */
    @Provides
    @Singleton
    fun provideDetectionRegistry(
        handlers: Set<@JvmSuppressWildcards DetectionHandler<*>>
    ): DetectionRegistry {
        return DetectionRegistry(handlers)
    }
}
