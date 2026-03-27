package com.sweet.qr_scan_10_feb_26.data.entity

data class FolderWithStats(
    val id: Long,
    val name: String,
    val createdDate: Long,
    val lastModified: Long,
    val distinctCount: Int,
    val totalCount: Int
)