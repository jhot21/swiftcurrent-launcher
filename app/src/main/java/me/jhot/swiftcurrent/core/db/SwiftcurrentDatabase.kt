package me.jhot.swiftcurrent.core.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SelectionRow::class, LaunchCountRow::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SwiftcurrentDatabase : RoomDatabase() {
    abstract fun usage(): UsageDao

    companion object {
        fun open(context: Context): SwiftcurrentDatabase =
            Room.databaseBuilder(context.applicationContext, SwiftcurrentDatabase::class.java, "swiftcurrent.db").build()
    }
}
