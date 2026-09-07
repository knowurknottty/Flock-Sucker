package com.inversionlabs.flocksucker.di

import android.content.Context
import com.inversionlabs.flocksucker.data.NetworkSettingsRepository
import com.inversionlabs.flocksucker.data.NukeSettingsRepository
import com.inversionlabs.flocksucker.data.OuiSettingsRepository
import com.inversionlabs.flocksucker.data.SecuritySettingsRepository
import com.inversionlabs.flocksucker.service.ScanningServiceConnection
import com.inversionlabs.flocksucker.data.oui.OuiDownloader
import com.inversionlabs.flocksucker.data.oui.OuiLookupService
import com.inversionlabs.flocksucker.data.repository.DetectionDao
import com.inversionlabs.flocksucker.data.repository.DetectionDeduplicator
import com.inversionlabs.flocksucker.data.repository.DetectionRepository
import com.inversionlabs.flocksucker.data.repository.FlockSuckerDatabase
import com.inversionlabs.flocksucker.data.repository.OuiDao
import com.inversionlabs.flocksucker.data.repository.ObservationDao
import com.inversionlabs.flocksucker.data.repository.IdentityLinkDao
import com.inversionlabs.flocksucker.data.repository.SightingDao
import com.inversionlabs.flocksucker.data.repository.OuiRepository
import com.inversionlabs.flocksucker.evidence.IdentityResolver
import com.inversionlabs.flocksucker.network.OrbotHelper
import com.inversionlabs.flocksucker.network.TorAwareHttpClient
import com.inversionlabs.flocksucker.privilege.PrivilegeMode
import com.inversionlabs.flocksucker.privilege.PrivilegeModeDetector
import com.inversionlabs.flocksucker.scanner.IBluetoothScanner
import com.inversionlabs.flocksucker.scanner.ICellularScanner
import com.inversionlabs.flocksucker.scanner.IWifiScanner
import com.inversionlabs.flocksucker.scanner.ScannerBundle
import com.inversionlabs.flocksucker.scanner.ScannerCapabilities
import com.inversionlabs.flocksucker.scanner.ScannerFactory
import com.inversionlabs.flocksucker.scanner.ScannerModeHelper
import com.inversionlabs.flocksucker.security.AppLockManager
import com.inversionlabs.flocksucker.security.DuressAuthenticator
import com.inversionlabs.flocksucker.security.FailedAuthWatcher
import com.inversionlabs.flocksucker.security.NukeManager
import com.inversionlabs.flocksucker.security.SecureKeyManager
import com.inversionlabs.flocksucker.service.nuke.GeofenceWatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ================================================================
    // Privilege Mode & Scanner Factory
    // ================================================================

    @Provides
    @Singleton
    fun providePrivilegeMode(@ApplicationContext context: Context): PrivilegeMode {
        return PrivilegeModeDetector.detect(context)
    }

    @Provides
    @Singleton
    fun provideScannerFactory(@ApplicationContext context: Context): ScannerFactory {
        return ScannerFactory.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideScannerCapabilities(scannerFactory: ScannerFactory): ScannerCapabilities {
        return scannerFactory.getCapabilities()
    }

    @Provides
    @Singleton
    fun provideWifiScanner(scannerFactory: ScannerFactory): IWifiScanner {
        return scannerFactory.createWifiScanner()
    }

    @Provides
    @Singleton
    fun provideBluetoothScanner(scannerFactory: ScannerFactory): IBluetoothScanner {
        return scannerFactory.createBluetoothScanner()
    }

    @Provides
    @Singleton
    fun provideCellularScanner(scannerFactory: ScannerFactory): ICellularScanner {
        return scannerFactory.createCellularScanner()
    }

    @Provides
    @Singleton
    fun provideScannerBundle(
        wifiScanner: IWifiScanner,
        bluetoothScanner: IBluetoothScanner,
        cellularScanner: ICellularScanner,
        scannerFactory: ScannerFactory
    ): ScannerBundle {
        return ScannerBundle(wifiScanner, bluetoothScanner, cellularScanner, scannerFactory)
    }

    @Provides
    @Singleton
    fun provideScannerModeHelper(@ApplicationContext context: Context): ScannerModeHelper {
        return ScannerModeHelper(context)
    }

    // ================================================================
    // Database & Repositories
    // ================================================================

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlockSuckerDatabase {
        return FlockSuckerDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideDetectionDao(database: FlockSuckerDatabase): DetectionDao {
        return database.detectionDao()
    }

    @Provides
    @Singleton
    fun provideDetectionRepository(
        database: FlockSuckerDatabase,
        detectionDao: DetectionDao,
        sightingDao: SightingDao,
        identityLinkDao: IdentityLinkDao,
        deduplicator: DetectionDeduplicator,
        identityResolver: IdentityResolver
    ): DetectionRepository {
        return DetectionRepository(
            database,
            detectionDao,
            sightingDao,
            identityLinkDao,
            deduplicator,
            identityResolver
        )
    }

    @Provides
    @Singleton
    fun provideSightingDao(database: FlockSuckerDatabase): SightingDao {
        return database.sightingDao()
    }

    @Provides
    @Singleton
    fun provideObservationDao(database: FlockSuckerDatabase): ObservationDao {
        return database.observationDao()
    }

    @Provides
    @Singleton
    fun provideIdentityLinkDao(database: FlockSuckerDatabase): IdentityLinkDao {
        return database.identityLinkDao()
    }

    @Provides
    @Singleton
    fun provideOuiDao(database: FlockSuckerDatabase): OuiDao {
        return database.ouiDao()
    }

    @Provides
    @Singleton
    fun provideOuiRepository(ouiDao: OuiDao): OuiRepository {
        return OuiRepository(ouiDao)
    }

    @Provides
    @Singleton
    fun provideOuiSettingsRepository(@ApplicationContext context: Context): OuiSettingsRepository {
        return OuiSettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideOuiLookupService(ouiRepository: OuiRepository): OuiLookupService {
        return OuiLookupService(ouiRepository)
    }

    // ================================================================
    // Security
    // ================================================================

    @Provides
    @Singleton
    fun provideSecureKeyManager(@ApplicationContext context: Context): SecureKeyManager {
        return SecureKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideSecuritySettingsRepository(@ApplicationContext context: Context): SecuritySettingsRepository {
        return SecuritySettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideAppLockManager(
        @ApplicationContext context: Context,
        securitySettingsRepository: SecuritySettingsRepository,
        secureKeyManager: SecureKeyManager,
        duressAuthenticator: DuressAuthenticator,
        failedAuthWatcher: FailedAuthWatcher
    ): AppLockManager {
        return AppLockManager(context, securitySettingsRepository, secureKeyManager, duressAuthenticator, failedAuthWatcher)
    }

    // Network Settings
    @Provides
    @Singleton
    fun provideNetworkSettingsRepository(@ApplicationContext context: Context): NetworkSettingsRepository {
        return NetworkSettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideOrbotHelper(@ApplicationContext context: Context): OrbotHelper {
        return OrbotHelper(context)
    }

    @Provides
    @Singleton
    fun provideTorAwareHttpClient(
        networkSettingsRepository: NetworkSettingsRepository,
        orbotHelper: OrbotHelper
    ): TorAwareHttpClient {
        return TorAwareHttpClient(networkSettingsRepository, orbotHelper)
    }

    @Provides
    @Singleton
    fun provideOuiDownloader(
        @ApplicationContext context: Context,
        torAwareHttpClient: TorAwareHttpClient
    ): OuiDownloader {
        return OuiDownloader(context, torAwareHttpClient)
    }

    // ================================================================
    // Nuke/Emergency Wipe System
    // ================================================================

    @Provides
    @Singleton
    fun provideNukeSettingsRepository(@ApplicationContext context: Context): NukeSettingsRepository {
        return NukeSettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideNukeManager(
        @ApplicationContext context: Context,
        nukeSettingsRepository: NukeSettingsRepository
    ): NukeManager {
        return NukeManager(context, nukeSettingsRepository)
    }

    @Provides
    @Singleton
    fun provideFailedAuthWatcher(
        @ApplicationContext context: Context,
        nukeSettingsRepository: NukeSettingsRepository,
        nukeManager: NukeManager
    ): FailedAuthWatcher {
        return FailedAuthWatcher(context, nukeSettingsRepository, nukeManager)
    }

    @Provides
    @Singleton
    fun provideDuressAuthenticator(
        @ApplicationContext context: Context,
        nukeSettingsRepository: NukeSettingsRepository,
        nukeManager: NukeManager
    ): DuressAuthenticator {
        return DuressAuthenticator(context, nukeSettingsRepository, nukeManager)
    }

    @Provides
    @Singleton
    fun provideGeofenceWatcher(
        @ApplicationContext context: Context,
        nukeSettingsRepository: NukeSettingsRepository,
        nukeManager: NukeManager
    ): GeofenceWatcher {
        return GeofenceWatcher(context, nukeSettingsRepository, nukeManager)
    }

    // ================================================================
    // IPC Service Connection
    // ================================================================

    @Provides
    @Singleton
    fun provideScanningServiceConnection(
        @ApplicationContext context: Context
    ): ScanningServiceConnection {
        return ScanningServiceConnection(context).also {
            // Auto-bind when the connection is created
            it.bind()
        }
    }

}
