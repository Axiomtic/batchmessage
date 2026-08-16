package com.local.bulksms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
        MessageDraftEntity::class,
        SendTaskEntity::class,
        SendItemEntity::class,
        SendAttemptEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    abstract fun importDao(): ImportDao

    abstract fun draftDao(): DraftDao

    abstract fun sendDao(): SendDao

    companion object {
        const val DATABASE_NAME = "bulk_sms.db"

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }
}
