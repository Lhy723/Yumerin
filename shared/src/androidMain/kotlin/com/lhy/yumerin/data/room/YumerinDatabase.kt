package com.lhy.yumerin.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CharacterEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        GroupEntity::class,
        MemoryEntryEntity::class,
        ModelProfileEntity::class,
        LocalModelArtifactEntity::class,
        AppSettingsEntity::class,
        GroupMembershipWindowEntity::class,
        CompanionScheduleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class YumerinDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun groupDao(): GroupDao
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun modelProfileDao(): ModelProfileDao
    abstract fun settingsDao(): SettingsDao
    abstract fun groupMembershipWindowDao(): GroupMembershipWindowDao
    abstract fun companionScheduleDao(): CompanionScheduleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN adultConfirmed INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN proactiveEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE characters ADD COLUMN proactiveDailyLimit INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE characters ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN responseState TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN errorMessage TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN usedMemoryIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE groups ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN reviewReason TEXT")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN expiresAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE model_profiles ADD COLUMN localBackend TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE model_profiles ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE local_model_artifacts ADD COLUMN deviceRequirements TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_model_artifacts ADD COLUMN licenseAcknowledged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_artifacts ADD COLUMN diagnosticReason TEXT")
                db.execSQL("ALTER TABLE local_model_artifacts ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultModelProfileId TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS group_membership_windows (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "groupId TEXT NOT NULL, " +
                        "characterId TEXT NOT NULL, " +
                        "joinedAt INTEGER NOT NULL, " +
                        "leftAt INTEGER, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS companion_schedules (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "characterId TEXT NOT NULL, " +
                        "chatId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "prompt TEXT NOT NULL, " +
                        "hour INTEGER NOT NULL, " +
                        "minute INTEGER NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "lastTriggeredAt INTEGER, " +
                        "dailyTriggerCount INTEGER NOT NULL, " +
                        "dailyCountStamp TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "deletedAt INTEGER)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reducedMotionEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_model_artifacts ADD COLUMN logicalModelId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN userName TEXT NOT NULL DEFAULT 'Yumerin User'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN userAvatarUri TEXT")
            }
        }
    }
}
