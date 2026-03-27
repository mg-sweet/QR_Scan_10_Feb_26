package com.sweet.qr_scan_10_feb_26.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sweet.qr_scan_10_feb_26.data.dao.ScanFolderDao
import com.sweet.qr_scan_10_feb_26.data.dao.ScanItemDao
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem

@Database(
    entities = [ScanFolder::class, ScanItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun scanItemDao(): ScanItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "advanced_qr_scanner.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
