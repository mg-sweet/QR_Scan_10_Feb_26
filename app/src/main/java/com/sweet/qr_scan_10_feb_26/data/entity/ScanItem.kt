package com.sweet.qr_scan_10_feb_26.data.entity
import androidx.room.*

@Entity(
    tableName = "scan_items",
    foreignKeys = [ForeignKey(entity = ScanFile::class, parentColumns = ["id"], childColumns = ["fileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("fileId")]
)
data class ScanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val scanValue: String,
    val barcodeFormat: String,
    val quantity: Int = 1,
    val lastScannedDate: Long = System.currentTimeMillis()
)