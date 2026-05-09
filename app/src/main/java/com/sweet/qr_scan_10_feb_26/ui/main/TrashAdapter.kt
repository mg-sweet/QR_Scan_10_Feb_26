package com.sweet.qr_scan_10_feb_26.ui.main

import android.graphics.Color
import android.view.*
import androidx.recyclerview.widget.*
import com.google.android.material.card.MaterialCardView
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.data.entity.TrashItem
import com.sweet.qr_scan_10_feb_26.databinding.ItemTrashBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

class TrashAdapter(
    private val onRestore: (TrashItem) -> Unit,
    private val onDelete: (TrashItem) -> Unit
) : ListAdapter<TrashItem, TrashAdapter.TrashViewHolder>(DiffCallback()) {

    // ✅ Selection Variables
    var isSelectionMode = false
    val selectedItems = mutableSetOf<TrashItem>()

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        TrashViewHolder(ItemTrashBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: TrashViewHolder, pos: Int) = h.bind(getItem(pos))

    // ✅ Selection Methods
    fun toggleSelectionMode(active: Boolean) {
        isSelectionMode = active
        if (!active) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun toggleSelectAll() {
        if (selectedItems.size == currentList.size) {
            selectedItems.clear()
        } else {
            selectedItems.clear()
            selectedItems.addAll(currentList)
        }
        notifyDataSetChanged()
    }

    inner class TrashViewHolder(val binding: ItemTrashBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TrashItem) {
            binding.apply {
                tvItemName.text = item.name

                // ✅ 1. Setting ကို လှမ်းဖတ်မည်
                val prefs = PreferencesManager(root.context)
                // ✅ Setting ကို လှမ်းဖတ်မည်
                val is24Hour = PreferencesManager(binding.root.context).use24HourFormat
                val pattern = if (is24Hour) "dd MMM, HH:mm" else "dd MMM, hh:mm a"
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())

                // ✅ 2. Setting ပေါ်မူတည်၍ 12hr လား 24hr လား Date Format ပြောင်းမည်
                //val pattern = if (is24Hour) "dd MMM, HH:mm" else "dd MMM, hh:mm a"
                tvDeletedDate.text = "Deleted: ${sdf.format(Date(item.deletedDate))}"

                //tvDeletedDate.text = "Deleted: ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.deletedDate))}"
                ivTypeIcon.setImageResource(if (item.isFolder) R.drawable.ic_folder else R.drawable.ic_file)

                // Normal Click Listeners
                btnRestore.setOnClickListener { onRestore(item) }
                btnDeletePermanent.setOnClickListener { onDelete(item) }

                // ✅ 1. Visibility Logic (Selection Mode ဝင်ပါက ခလုတ်များကိုဖျောက်ပြီး Checkbox ပြမည်)
                checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                actionButtonsContainer.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

                // ✅ 2. Checkbox State & Card UI (Select ဖြစ်နေလျှင် Card ဘောင်ကို အပြာရောင်ပြမည်)
                val isSelected = selectedItems.contains(item)
                checkbox.isChecked = isSelected

                val cardView = root as MaterialCardView
                cardView.strokeWidth = if (isSelected) 3 else 0
                cardView.strokeColor = if (isSelected) Color.parseColor("#4F46E5") else Color.TRANSPARENT

                // ✅ 3. Long Click: Selection Mode ထဲ အလိုလိုဝင်မည်
                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        val activity = root.context as? RecentlyDeletedActivity
                        activity?.findViewById<View>(R.id.btnEnterSelection)?.performClick()
                        toggleItemSelection(item)
                    }
                    true
                }

                // ✅ 4. Single Clicks: Selection Mode ထဲရောက်နေလျှင် Select/Deselect လုပ်မည်
                root.setOnClickListener {
                    if (isSelectionMode) toggleItemSelection(item)
                }

                checkbox.setOnClickListener {
                    toggleItemSelection(item)
                }
            }
        }

        // Helper function for selection toggle
        private fun toggleItemSelection(item: TrashItem) {
            if (selectedItems.contains(item)) {
                selectedItems.remove(item)
            } else {
                selectedItems.add(item)
            }
            notifyItemChanged(adapterPosition)
            // Activity မှ UI (0 Selected, 1 Selected) ကို အလိုလို Update လုပ်ခိုင်းမည်
            (binding.root.context as? RecentlyDeletedActivity)?.updateSelectionUI()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrashItem>() {
        override fun areItemsTheSame(o: TrashItem, n: TrashItem) = o.id == n.id && o.isFolder == n.isFolder
        override fun areContentsTheSame(o: TrashItem, n: TrashItem) = o == n
    }
}