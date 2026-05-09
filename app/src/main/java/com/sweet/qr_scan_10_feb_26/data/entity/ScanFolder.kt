package com.sweet.qr_scan_10_feb_26.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class ScanFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedDate: Long? = null,
    val restoredDate: Long? = null
)

data class FolderWithStats(
    val id: Long, val name: String, val createdDate: Long, val lastModified: Long,
    val distinctCount: Int, val totalCount: Int,
    val restoredDate: Long? = null
)

data class TrashItem(
    val id: Long,
    val name: String,
    val deletedDate: Long,
    val isFolder: Boolean, // Room can map 1/0 to Boolean automatically
    val subInfo: String? = null
)