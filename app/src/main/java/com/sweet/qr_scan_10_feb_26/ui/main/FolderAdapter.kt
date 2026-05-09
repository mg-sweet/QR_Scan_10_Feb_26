package com.sweet.qr_scan_10_feb_26.ui.main

import android.annotation.SuppressLint
import android.view.*
import androidx.recyclerview.widget.*
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats
import com.sweet.qr_scan_10_feb_26.databinding.ItemFolderBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

class FolderAdapter(
    private val onFolderClick: (FolderWithStats) -> Unit,
    private val onLongClick: (FolderWithStats) -> Unit, // ✅ Long Press Callback
    private val onMenuClick: (FolderWithStats, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit

) : ListAdapter<FolderWithStats, FolderAdapter.FolderViewHolder>(DiffCallback()) {

    var isSelectionMode = false
    val selectedIds = mutableSetOf<Long>()

    //private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    inner class FolderViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: FolderWithStats) {
            binding.apply {
                tvFolderName.text = item.name
                tvTotalCount.text = "${item.totalCount} Files"

                // Selection Mode UI logic
                cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                btnMenu.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = selectedIds.contains(item.id)

                // ==========================================
                // ✅ Time Format Logic အတိအကျ
                // ==========================================
                val is24Hour = PreferencesManager(binding.root.context).use24HourFormat

                // Locale.US ကို အသုံးပြုခြင်းဖြင့် ဖုန်း၏ Default Language မည်သို့ပင်ဖြစ်စေ Format မှန်ကန်စွာ ထွက်မည်
                val pattern = if (is24Hour) "MMM dd, yyyy HH:mm" else "MMM dd, yyyy hh:mm a"
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())

                val createdStr = "Created: ${sdf.format(Date(item.createdDate))}"

                if (item.restoredDate != null) {
                    // ✅ Restore လုပ်ထားလျှင် နှစ်ခုလုံးပြမည်
                    val restoredStr = "↺ ${sdf.format(Date(item.restoredDate))}"
                    tvCreatedDate.text = "$restoredStr\n$createdStr"
                } else {
                    // ပုံမှန်ဆိုလျှင် Created တစ်ခုတည်းသာပြမည်
                    tvCreatedDate.text = createdStr
                }

                // ==========================================
                // Click Listeners
                // ==========================================
                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(item.id)
                    } else {
                        onFolderClick(item)
                    }
                }

                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        onLongClick(item)
                        true
                    } else {
                        false
                    }
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
    }

    // fun selectAll(allFolders: List<FolderWithStats>) {
    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun toggleSelectAll() {
        // အကယ်၍ ရွေးထားတဲ့အရေအတွက်နဲ့ ရှိသမျှစာရင်း အရေအတွက် တူနေရင် (အကုန်လုံး Select ဖြစ်နေရင်)
        if (selectedIds.size == currentList.size) {
            selectedIds.clear() // အကုန်လုံးကို ပြန်ဖြုတ်လိုက်မယ်
        } else {
            // အကုန်လုံး မရွေးရသေးရင် အကုန်လုံးကို ရွေးလိုက်မယ်
            selectedIds.clear()
            selectedIds.addAll(currentList.map { it.id })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FolderViewHolder(ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<FolderWithStats>() {
        override fun areItemsTheSame(o: FolderWithStats, n: FolderWithStats) = o.id == n.id
        override fun areContentsTheSame(o: FolderWithStats, n: FolderWithStats) = o == n
    }
}