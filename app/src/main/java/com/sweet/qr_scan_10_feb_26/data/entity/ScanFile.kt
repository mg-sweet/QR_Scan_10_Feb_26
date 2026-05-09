package com.sweet.qr_scan_10_feb_26.data.entity

import androidx.room.*

@Entity(
    tableName = "scan_files",
    foreignKeys = [ForeignKey(entity = ScanFolder::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")]
)
data class ScanFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long?,
    val fileName: String,
    val createdDate: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedDate: Long? = null,
    val restoredDate: Long? = null,
    val originalFolderName: String? = null
)

data class FileWithStats(
    val id: Long, val folderId: Long, val fileName: String, val createdDate: Long,
    val distinctCount: Int, val totalCount: Int,
    val restoredDate: Long? = null
)