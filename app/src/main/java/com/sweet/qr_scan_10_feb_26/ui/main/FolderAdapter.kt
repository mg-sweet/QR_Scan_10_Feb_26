package com.sweet.qr_scan_10_feb_26.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats
import com.sweet.qr_scan_10_feb_26.databinding.ItemFolderBinding
import java.text.SimpleDateFormat
import java.util.*

class FolderAdapter(
    private val onFolderClick: (FolderWithStats) -> Unit,
    private val onDeleteClick: (FolderWithStats) -> Unit
) : ListAdapter<FolderWithStats, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FolderWithStats) {
            binding.apply {
                tvFolderName.text = item.name
                tvCreatedDate.text = "Created: ${formatDate(item.createdDate)}"

                // Database မှ တစ်ခါတည်းပါလာသော stats များကို တန်းထည့်သည်
                // Coroutine မလိုတော့သောကြောင့် Performance အလွန်ကောင်းသွားသည်
                tvDistinctCount.text = "${item.distinctCount} Distinct"
                tvTotalCount.text = "${item.totalCount} Scans"

                cardFolder.setOnClickListener {
                    onFolderClick(item)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick(item)
                }
            }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class FolderDiffCallback : DiffUtil.ItemCallback<FolderWithStats>() {
        override fun areItemsTheSame(oldItem: FolderWithStats, newItem: FolderWithStats): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FolderWithStats, newItem: FolderWithStats): Boolean {
            return oldItem == newItem
        }
    }
}