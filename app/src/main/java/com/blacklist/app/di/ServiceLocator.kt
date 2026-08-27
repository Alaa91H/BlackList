package com.blacklist.app.di

import android.content.Context
import androidx.room.Room
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.repository.BlackListRepositoryImpl
import com.blacklist.app.domain.analytics.StatisticsEngine
import com.blacklist.app.domain.diagnostics.DiagnosticsService
import com.blacklist.app.domain.enforcement.*
import com.blacklist.app.domain.engine.*
import com.blacklist.app.domain.notification.NotificationManager
import com.blacklist.app.domain.notification.NotificationManagerImpl
import com.blacklist.app.domain.permission.PermissionManager
import com.blacklist.app.domain.permission.PermissionManagerImpl
import com.blacklist.app.domain.capability.CapabilityManager
import com.blacklist.app.domain.capability.CapabilityManagerImpl
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.domain.repository.BlackListRepository
import java.util.Locale
import com.blacklist.app.util.ContactUtils

object ServiceLocator {
    @Volatile private var db: BlackListDatabase? = null
    @Volatile private var repo: BlackListRepository? = null
    @Volatile private var contactUtils: ContactUtils? = null
    @Volatile private var normalizer: PhoneNumberNormalizer? = null
    @Volatile private var blacklistEngine: BlacklistEngine? = null
    @Volatile private var riskEngine: RiskScoringEngine? = null
    @Volatile private var reputationEngine: ReputationEngine? = null
    @Volatile private var behaviorEngine: BehaviorEngine? = null
    @Volatile private var floodProtector: CallFloodProtector? = null
    @Volatile private var policySnapshotStore: PolicySnapshotStore? = null
    @Volatile private var firewallEngine: CallFirewallEngine? = null
    @Volatile private var enforcementResolver: EnforcementResolver? = null
    @Volatile private var permissionManager: com.blacklist.app.domain.permission.PermissionManager? = null
    @Volatile private var capabilityManager: CapabilityManager? = null
    @Volatile private var notificationManager: NotificationManager? = null

    fun provideDatabase(context: Context): BlackListDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, BlackListDatabase::class.java, "blacklist.db")
                .addMigrations(
                    BlackListDatabase.MIGRATION_3_4,
                    BlackListDatabase.MIGRATION_4_5,
                    BlackListDatabase.MIGRATION_5_6,
                    BlackListDatabase.MIGRATION_6_7,
                    BlackListDatabase.MIGRATION_7_8,
                    BlackListDatabase.MIGRATION_8_9,
                    BlackListDatabase.MIGRATION_9_10,
                    BlackListDatabase.MIGRATION_10_11
                )
                // Spec §48: never wipe user data on schema change.
                // Destructive fallback is allowed ONLY on downgrade (e.g. installing an older build).
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { db = it }
        }

    fun provideContactUtils(context: Context): ContactUtils =
        contactUtils ?: synchronized(this) {
            contactUtils ?: ContactUtils(context.applicationContext).also { contactUtils = it }
        }

    fun provideNormalizer(context: Context): PhoneNumberNormalizer =
        normalizer ?: synchronized(this) {
            normalizer ?: PhoneNumberNormalizer(defaultRegion = deviceRegion(context)).also { normalizer = it }
        }

    private fun deviceRegion(context: Context): String {
        val configuredRegion = runCatching {
            context.resources.configuration.locales.get(0).country
        }.getOrNull()
        return configuredRegion
            ?.uppercase()
            ?.takeIf { it.length == 2 }
            ?: Locale.getDefault().country.uppercase().takeIf { it.length == 2 }
            ?: "DE"
    }

    fun provideBlacklistEngine(context: Context): BlacklistEngine =
        blacklistEngine ?: synchronized(this) {
            blacklistEngine ?: BlacklistEngine(provideNormalizer(context)).also { blacklistEngine = it }
        }

    fun provideRiskEngine(): RiskScoringEngine =
        riskEngine ?: synchronized(this) {
            riskEngine ?: RiskScoringEngine().also { riskEngine = it }
        }

    fun provideReputationEngine(context: Context): ReputationEngine =
        reputationEngine ?: synchronized(this) {
            reputationEngine ?: ReputationEngine(provideDatabase(context).callerReputationDao()).also { reputationEngine = it }
        }

    fun provideBehaviorEngine(context: Context): BehaviorEngine =
        behaviorEngine ?: synchronized(this) {
            behaviorEngine ?: BehaviorEngine().also { behaviorEngine = it }
        }

    fun provideFloodProtector(): CallFloodProtector =
        floodProtector ?: synchronized(this) {
            floodProtector ?: CallFloodProtector().also { floodProtector = it }
        }

    fun providePolicySnapshotStore(context: Context): PolicySnapshotStore =
        policySnapshotStore ?: synchronized(this) {
            policySnapshotStore ?: PolicySnapshotStore(
                database = provideDatabase(context),
                normalizer = provideNormalizer(context),
                contactUtils = provideContactUtils(context)
            ).also { policySnapshotStore = it }
        }

    fun provideFirewallEngine(context: Context): CallFirewallEngine =
        firewallEngine ?: synchronized(this) {
            firewallEngine ?: CallFirewallEngine(
                policySnapshots = providePolicySnapshotStore(context),
                normalizer = provideNormalizer(context),
                blacklistEngine = provideBlacklistEngine(context),
                riskEngine = provideRiskEngine(),
                behaviorEngine = provideBehaviorEngine(context)
            ).also { firewallEngine = it }
        }

    fun provideEnforcementResolver(context: Context): EnforcementResolver =
        enforcementResolver ?: synchronized(this) {
            enforcementResolver ?: EnforcementResolver(
                listOf(
                    AndroidCallScreeningBackend(context.applicationContext),
                    TelecomBackend(context.applicationContext)
                )
            ).also { enforcementResolver = it }
        }

    fun providePermissionManager(context: Context): PermissionManager =
        permissionManager ?: synchronized(this) {
            permissionManager ?: PermissionManagerImpl(context).also { permissionManager = it }
        }

    fun provideCapabilityManager(context: Context): CapabilityManager =
        capabilityManager ?: synchronized(this) {
            capabilityManager ?: CapabilityManagerImpl(
                context,
                providePermissionManager(context),
                provideDatabase(context)
            ).also { capabilityManager = it }
        }

    fun provideNotificationManager(context: Context): NotificationManager =
        notificationManager ?: synchronized(this) {
            notificationManager ?: NotificationManagerImpl(context).also { notificationManager = it }
        }

    fun provideStatisticsEngine(context: Context): StatisticsEngine =
        StatisticsEngine(provideDatabase(context).blockedCallLogDao(), provideDatabase(context).callerReputationDao())

    fun provideDiagnosticsService(context: Context): DiagnosticsService =
        DiagnosticsService(context.applicationContext, provideDatabase(context))

    fun provideRepository(context: Context): BlackListRepository =
        repo ?: synchronized(this) {
            repo ?: BlackListRepositoryImpl(
                provideDatabase(context),
                provideNormalizer(context)
            ).also { repo = it }
        }

    fun clearForTest() {
        db = null; repo = null; contactUtils = null; normalizer = null; blacklistEngine = null
        riskEngine = null; reputationEngine = null; behaviorEngine = null; floodProtector = null
        policySnapshotStore = null; firewallEngine = null; enforcementResolver = null; permissionManager = null
        capabilityManager = null; notificationManager = null
    }
}
