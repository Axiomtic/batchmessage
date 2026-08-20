package com.local.bulksms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.local.bulksms.model.SendStatus
import org.json.JSONArray

class AppTypeConverters {
    @TypeConverter
    fun statusToString(status: SendStatus?): String? = status?.name

    @TypeConverter
    fun stringToStatus(value: String?): SendStatus? = value?.let(SendStatus::valueOf)

    @TypeConverter
    fun stringsToJson(values: List<String>): String = JSONArray(values).toString()

    @TypeConverter
    fun jsonToStrings(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { index -> array.getString(index) }
    }
}

@Database(
    entities = [
        TemplateEntity::class,
        ImportTaskEntity::class,
        WorkspaceEntity::class,
        MessageDraftEntity::class,
        SendTaskEntity::class,
        SendItemEntity::class,
        SendAttemptEntity::class,
        SendHistoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    abstract fun importDao(): ImportDao

    abstract fun workspaceDao(): WorkspaceDao

    abstract fun draftDao(): DraftDao

    abstract fun sendDao(): SendDao

    abstract fun historyDao(): HistoryDao

    companion object {
        const val DATABASE_NAME = "bulk_sms.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workspace` (
                        `id` TEXT NOT NULL,
                        `importId` TEXT NOT NULL,
                        `rawRowsJson` TEXT NOT NULL,
                        `detectedHeader` INTEGER NOT NULL,
                        `firstRowIsHeader` INTEGER NOT NULL,
                        `phoneColumnIndex` INTEGER,
                        `selectedTemplateId` TEXT,
                        `selectedTemplateName` TEXT NOT NULL,
                        `selectedTemplateBody` TEXT NOT NULL,
                        `selectedSubscriptionId` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Phone columns are now bound to the workspace (session) instead of
                // living inside the template; a backup phone column was added.
                db.execSQL("ALTER TABLE `workspace` ADD COLUMN `backupPhoneColumnIndex` INTEGER")
                db.execSQL(
                    "ALTER TABLE `message_drafts` ADD COLUMN `backupPhoneNumber` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL("ALTER TABLE `message_drafts` ADD COLUMN `backupPhoneColumnIndex` INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `send_history` (
                        `id` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `simLabel` TEXT NOT NULL,
                        `total` INTEGER NOT NULL,
                        `succeeded` INTEGER NOT NULL,
                        `failed` INTEGER NOT NULL,
                        `headerNamesJson` TEXT NOT NULL,
                        `firstRowIsHeader` INTEGER NOT NULL,
                        `phoneColumnIndex` INTEGER,
                        `backupPhoneColumnIndex` INTEGER,
                        `rawRowsJson` TEXT NOT NULL,
                        `sentNumbersJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_send_history_completedAt` ON `send_history` (`completedAt`)")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
