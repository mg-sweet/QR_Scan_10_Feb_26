package com.sweet.qr_scan_10_feb_26.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class ScanFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

