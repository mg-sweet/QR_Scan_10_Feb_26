package com.sweet.qr_scan_10_feb_26.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_items",
    foreignKeys = [
        ForeignKey(
            entity = ScanFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("folderId")]
)
data class ScanItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val scanValue: String,
    val barcodeFormat: String,
    val quantity: Int = 1,
    val firstScannedDate: Long = System.currentTimeMillis(),
    val lastScannedDate: Long = System.currentTimeMillis()
)
