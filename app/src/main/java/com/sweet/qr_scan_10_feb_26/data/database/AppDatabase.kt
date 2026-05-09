package com.sweet.qr_scan_10_feb_26.data.database
import android.content.Context
import androidx.room.*
import com.sweet.qr_scan_10_feb_26.data.dao.*
import com.sweet.qr_scan_10_feb_26.data.entity.*

@Database(entities = [ScanFolder::class, ScanFile::class, ScanItem::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun scanFileDao(): ScanFileDao
    abstract fun scanItemDao(): ScanItemDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "scanner_pro_db")
                    .fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}