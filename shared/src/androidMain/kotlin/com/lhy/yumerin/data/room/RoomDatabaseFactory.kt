package com.lhy.yumerin.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.lhy.yumerin.domain.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

fun createAndroidAppGraph(context: Context): AppGraph {
    val appContext = context.applicationContext
    val database = synchronized(DATABASE_BOOTSTRAP_LOCK) {
        System.loadLibrary("sqlcipher")
        val passphrase = AndroidDatabaseKeyStore(appContext).getOrCreatePassphrase()
        val legacyWasPresent = appContext.getDatabasePath(LEGACY_DATABASE_NAME).exists()
        val secureWasPresent = appContext.getDatabasePath(SECURE_DATABASE_NAME).exists()
        val secureDatabase = Room.databaseBuilder(
            appContext,
            YumerinDatabase::class.java,
            SECURE_DATABASE_NAME
        ).openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .addMigrations(*DATABASE_MIGRATIONS)
            .build()
        try {
            migrateLegacyDatabaseIfNeeded(appContext, secureDatabase, legacyWasPresent, secureWasPresent, passphrase)
        } finally {
            passphrase.fill(0)
        }
        secureDatabase
    }
    return RoomAppGraph(database, AndroidCredentialStore(appContext), File(appContext.filesDir, "models"), appContext)
}

private const val LEGACY_DATABASE_NAME = "yumerin.db"
private const val SECURE_DATABASE_NAME = "yumerin.secure.db"
private val DATABASE_BOOTSTRAP_LOCK = Any()
private val DATABASE_MIGRATIONS = arrayOf(
    YumerinDatabase.MIGRATION_1_2,
    YumerinDatabase.MIGRATION_2_3,
    YumerinDatabase.MIGRATION_3_4,
    YumerinDatabase.MIGRATION_4_5,
    YumerinDatabase.MIGRATION_5_6,
    YumerinDatabase.MIGRATION_6_7,
    YumerinDatabase.MIGRATION_7_8,
    YumerinDatabase.MIGRATION_8_9,
    YumerinDatabase.MIGRATION_9_10,
    YumerinDatabase.MIGRATION_10_11
)

/**
 * Copies the pre-encryption database once. The old file is retained if copying fails so a
 * subsequent launch can retry without silently losing local history.
 */
private fun migrateLegacyDatabaseIfNeeded(
    context: Context,
    secureDatabase: YumerinDatabase,
    legacyWasPresent: Boolean,
    secureWasPresent: Boolean,
    passphrase: ByteArray
) {
    val legacyFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
    if (!legacyWasPresent || !legacyFile.exists()) return

    if (secureWasPresent) {
        context.deleteDatabase(LEGACY_DATABASE_NAME)
        return
    }

    val legacyDatabase = Room.databaseBuilder(
        context,
        YumerinDatabase::class.java,
        LEGACY_DATABASE_NAME
    ).addMigrations(*DATABASE_MIGRATIONS).build()

    try {
        runBlocking(Dispatchers.IO) {
            val characters = legacyDatabase.characterDao().getAll()
            val chats = legacyDatabase.chatThreadDao().getAll()
            val messages = legacyDatabase.chatMessageDao().getAll()
            val groups = legacyDatabase.groupDao().getAll()
            val memories = legacyDatabase.memoryEntryDao().getAll()
            val profiles = legacyDatabase.modelProfileDao().getProfiles()
            val artifacts = legacyDatabase.modelProfileDao().getArtifacts()
            val settings = legacyDatabase.settingsDao().getAll()
            val membershipWindows = legacyDatabase.groupMembershipWindowDao().getAll()
            val schedules = legacyDatabase.companionScheduleDao().getAll()
            val runtimeStates = legacyDatabase.characterRuntimeStateDao().getAll()
            val agentGoals = legacyDatabase.scheduledAgentGoalDao().getAll()
            val delegatedGoals = legacyDatabase.delegatedAgentGoalDao().getAll()
            val delegatedGoalSteps = legacyDatabase.delegatedAgentGoalStepDao().getAll()

            secureDatabase.withTransaction {
                characters.forEach { secureDatabase.characterDao().upsert(it) }
                chats.forEach { secureDatabase.chatThreadDao().upsert(it) }
                messages.forEach { secureDatabase.chatMessageDao().upsert(it) }
                groups.forEach { secureDatabase.groupDao().upsert(it) }
                memories.forEach { secureDatabase.memoryEntryDao().upsert(it) }
                profiles.forEach { secureDatabase.modelProfileDao().upsertProfile(it) }
                artifacts.forEach { secureDatabase.modelProfileDao().upsertArtifact(it) }
                settings.forEach { secureDatabase.settingsDao().upsert(it) }
                membershipWindows.forEach { secureDatabase.groupMembershipWindowDao().upsert(it) }
                schedules.forEach { secureDatabase.companionScheduleDao().upsert(it) }
                runtimeStates.forEach { secureDatabase.characterRuntimeStateDao().upsert(it) }
                agentGoals.forEach { secureDatabase.scheduledAgentGoalDao().upsert(it) }
                delegatedGoals.forEach { secureDatabase.delegatedAgentGoalDao().upsert(it) }
                delegatedGoalSteps.forEach { secureDatabase.delegatedAgentGoalStepDao().upsert(it) }
            }
        }
        legacyDatabase.close()
        context.deleteDatabase(LEGACY_DATABASE_NAME)
    } catch (error: Throwable) {
        legacyDatabase.close()
        secureDatabase.close()
        context.deleteDatabase(SECURE_DATABASE_NAME)
        // Force the key to remain referenced until the failure is surfaced; the next launch
        // must reuse it so a partially created database can never be opened with a new key.
        passphrase.fill(0)
        throw error
    }
}
