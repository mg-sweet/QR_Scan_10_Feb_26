package com.sweet.qr_scan_10_feb_26.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats

@Dao
interface ScanFolderDao {
    @Query("""
        SELECT f.id, f.name, f.createdDate, f.lastModified,
        (SELECT COUNT(DISTINCT scanValue) FROM scan_items WHERE folderId = f.id) as distinctCount,
        (SELECT COALESCE(SUM(quantity), 0) FROM scan_items WHERE folderId = f.id) as totalCount
        FROM folders f 
        ORDER BY f.lastModified DESC
    """)
    fun getAllFoldersWithStats(): LiveData<List<FolderWithStats>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): ScanFolder?

    @Insert
    suspend fun insertFolder(folder: ScanFolder): Long

    @Update
    suspend fun updateFolder(folder: ScanFolder)

    @Delete
    suspend fun deleteFolder(folder: ScanFolder)
}

@Dao
interface ScanItemDao {
    @Query("SELECT * FROM scan_items WHERE folderId = :folderId ORDER BY lastScannedDate DESC")
    fun getItemsByFolder(folderId: Long): LiveData<List<ScanItem>>

    @Query("SELECT * FROM scan_items WHERE folderId = :folderId ORDER BY lastScannedDate DESC")
    suspend fun getItemsByFolderSync(folderId: Long): List<ScanItem>

    @Query("SELECT * FROM scan_items WHERE folderId = :folderId AND scanValue = :scanValue LIMIT 1")
    suspend fun getItemByValue(folderId: Long, scanValue: String): ScanItem?

    // --- ဒီ function (၂) ခုကို ပြန်ထည့်ပေးထားပါတယ် ---
    @Query("SELECT COUNT(DISTINCT scanValue) FROM scan_items WHERE folderId = :folderId")
    suspend fun getDistinctCount(folderId: Long): Int

    @Query("SELECT SUM(quantity) FROM scan_items WHERE folderId = :folderId")
    suspend fun getTotalCount(folderId: Long): Int?

    @Insert
    suspend fun insertItem(item: ScanItem): Long

    @Update
    suspend fun updateItem(item: ScanItem)

    @Delete
    suspend fun deleteItem(item: ScanItem)

    @Query("UPDATE scan_items SET quantity = quantity + 1, lastScannedDate = :timestamp WHERE id = :itemId")
    suspend fun incrementQuantity(itemId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE scan_items SET quantity = quantity - 1, lastScannedDate = :timestamp WHERE id = :itemId AND quantity > 1")
    suspend fun decrementQuantity(itemId: Long, timestamp: Long = System.currentTimeMillis()): Int
}