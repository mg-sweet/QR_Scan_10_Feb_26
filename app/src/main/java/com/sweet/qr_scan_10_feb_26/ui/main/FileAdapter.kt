package com.sweet.qr_scan_10_feb_26.ui.main

import android.view.*
import androidx.recyclerview.widget.*
import com.sweet.qr_scan_10_feb_26.data.entity.FileWithStats
import com.sweet.qr_scan_10_feb_26.databinding.ItemFileBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private val onFileClick: (FileWithStats) -> Unit,
    private val onLongClick: (FileWithStats) -> Unit,
    private val onMenuClick: (FileWithStats, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<FileWithStats, FileAdapter.FileViewHolder>(DiffCallback()) {

    var isSelectionMode = false
    val selectedIds = mutableSetOf<Long>()
    //private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    inner class FileViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FileWithStats) {
            binding.apply {

                // ✅ Setting ကို လှမ်းဖတ်မည်
                val is24Hour = PreferencesManager(binding.root.context).use24HourFormat
                val pattern = if (is24Hour) "MMM dd, yyyy  HH:mm" else "MMM dd, yyyy  hh:mm a"
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())

                tvFileName.text = item.fileName
                val createdStr = sdf.format(Date(item.createdDate))
                tvFileDate.text = if (item.restoredDate != null && item.restoredDate > 0)
                    "↺ ${sdf.format(Date(item.restoredDate))} | Created: $createdStr"
                else "Created: $createdStr"

                tvTotalCount.text = "${item.totalCount} Items"
                tvDistinctCount.text = "${item.distinctCount} Uniques"

                // ✅ Selection UI Logic
                cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                btnMenu.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = selectedIds.contains(item.id)

                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(item.id)
                    } else {
                        onFileClick(item)
                    }
                }

                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        onLongClick(item)
                        true
                    } else false
                }

                cbSelect.setOnClickListener { toggleSelection(item.id) }
                btnMenu.setOnClickListener { onMenuClick(item, it) }
            }
        }

        private fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
            notifyItemChanged(adapterPosition)
            onSelectionChanged(selectedIds.size)
        }
    }

    fun toggleSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    // ✅ Select All Logic: လက်ရှိ List ထဲရှိ သမျှ အကုန် Select လုပ်သည်
    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun toggleSelectAll() {
        if (selectedIds.size == currentList.size) {
            selectedIds.clear() // အကုန်လုံး Select ဖြစ်နေရင် ပြန်ဖြုတ်မယ်
        } else {
            selectedIds.clear()
            selectedIds.addAll(currentList.map { it.id }) // အကုန်လုံးကို Select လုပ်မယ်
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FileViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<FileWithStats>() {
        override fun areItemsTheSame(o: FileWithStats, n: FileWithStats) = o.id == n.id
        override fun areContentsTheSame(o: FileWithStats, n: FileWithStats) = o == n
    }
}