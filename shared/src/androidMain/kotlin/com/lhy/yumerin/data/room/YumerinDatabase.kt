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
        CompanionScheduleEntity::class,
        CharacterRuntimeStateEntity::class,
        ScheduledAgentGoalEntity::class,
        DelegatedAgentGoalEntity::class,
        DelegatedAgentGoalStepEntity::class
    ],
    version = 12,
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
    abstract fun characterRuntimeStateDao(): CharacterRuntimeStateDao
    abstract fun scheduledAgentGoalDao(): ScheduledAgentGoalDao
    abstract fun delegatedAgentGoalDao(): DelegatedAgentGoalDao
    abstract fun delegatedAgentGoalStepDao(): DelegatedAgentGoalStepDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN themeColor TEXT NOT NULL DEFAULT 'BLUE'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS character_runtime_states (" +
                        "characterId TEXT NOT NULL PRIMARY KEY, " +
                        "moodValence REAL NOT NULL, " +
                        "moodArousal REAL NOT NULL, " +
                        "trust REAL NOT NULL, " +
                        "closeness REAL NOT NULL, " +
                        "completedInteractions INTEGER NOT NULL, " +
                        "lastInteractionAt INTEGER, " +
                        "sourceChatId TEXT, " +
                        "sourceMessageId TEXT, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scheduled_agent_goals (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "characterId TEXT NOT NULL, " +
                        "chatId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "prompt TEXT NOT NULL, " +
                        "dueAt INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "completedAt INTEGER, " +
                        "resultMessageId TEXT, " +
                        "failureReason TEXT)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS delegated_agent_goals (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "characterId TEXT NOT NULL, " +
                        "chatId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "outcome TEXT NOT NULL, " +
                        "constraints TEXT NOT NULL, " +
                        "replanCount INTEGER NOT NULL DEFAULT 0, " +
                        "status TEXT NOT NULL, " +
                        "latestSummary TEXT, " +
                        "pendingQuestion TEXT, " +
                        "resumeInput TEXT, " +
                        "failureReason TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "completedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS delegated_agent_goal_steps (" +
                        "id TEXT NOT NULL, " +
                        "goalId TEXT NOT NULL, " +
                        "stepOrder INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "summary TEXT, " +
                        "attempts INTEGER NOT NULL, " +
                        "PRIMARY KEY(id, goalId))"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN componentMaterial TEXT NOT NULL DEFAULT 'STANDARD'")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN importance REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN emotionTag TEXT")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN occurredAt INTEGER")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN factSubject TEXT")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN factPredicate TEXT")
                db.execSQL("ALTER TABLE memory_entries ADD COLUMN factValue TEXT")
                db.execSQL("ALTER TABLE character_runtime_states ADD COLUMN formsOfAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE character_runtime_states ADD COLUMN sharedInJokes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE character_runtime_states ADD COLUMN openCommitments TEXT NOT NULL DEFAULT ''")
            }
        }

    }
}
