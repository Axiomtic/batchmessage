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
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    abstract fun importDao(): ImportDao

    abstract fun workspaceDao(): WorkspaceDao

    abstract fun draftDao(): DraftDao

    abstract fun sendDao(): SendDao

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

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()
    }
}
