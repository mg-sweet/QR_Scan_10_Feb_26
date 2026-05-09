package com.sweet.qr_scan_10_feb_26.ui.scanner


import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Color.*
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import com.sweet.qr_scan_10_feb_26.databinding.ItemScanResultBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ScanResultAdapter(
    private val onItemClick: (ScanItem) -> Unit,
    private val onPlusClick: (ScanItem) -> Unit,
    private val onMinusClick: (ScanItem) -> Unit,
    private val onDeleteClick: (ScanItem) -> Unit
) : ListAdapter<ScanItem, ScanResultAdapter.ScanResultViewHolder>(ScanItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val binding = ItemScanResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScanResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScanResultViewHolder(
        private val binding: ItemScanResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanItem) {
            binding.apply {
                tvScanValue.text = item.scanValue
                tvFormat.text = item.barcodeFormat
                tvQuantity.text = item.quantity.toString()

                // ✅ Setting ကို လှမ်းဖတ်မည်
                val is24Hour = PreferencesManager(binding.root.context).use24HourFormat
                // ဒီနေရာမှာတော့ အချိန်လေးကိုပဲ (Time Only) ပြတာ ပိုလှပါလိမ့်မယ်
                val pattern = if (is24Hour) "dd MMM, yyyy HH:mm" else "dd MMM, yyyy hh:mm a"
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())

                tvLastScanned.text = "${sdf.format(Date(item.lastScannedDate))}"

                // ✅ Auto-Fade Highlight Logic
                val diff = System.currentTimeMillis() - item.lastScannedDate

                // Scan ဖတ်လိုက်တာ ၈၀၀ မီလီစက္ကန့် (၀.၈ စက္ကန့်) ထက် နည်းရင် Animation စမယ်
                if (diff < 800) {
                    val colorFrom = parseColor("#E8F5E9") // အစိမ်းဖျော့ (Highlight)
                    val colorTo = TRANSPARENT // ပုံမှန်အရောင် (နောက်ခံနှင့် တစ်သားတည်း)

                    val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
                    colorAnimation.duration = 2000 // 2 စက္ကန့်အတွင်း မှိန်သွားမည်
                    colorAnimation.addUpdateListener { animator ->
                        root.setCardBackgroundColor(animator.animatedValue as Int)
                    }
                    colorAnimation.start()
                } else {
                    // ဟောင်းနေတဲ့ Item တွေအတွက် ပုံမှန်အတိုင်း ထားမည်
                    root.setCardBackgroundColor(TRANSPARENT)
                }

                // Click on card to show QR code
                root.setOnClickListener { onItemClick(item) }

                btnPlus.setOnClickListener { onPlusClick(item) }
                btnMinus.setOnClickListener { onMinusClick(item) }
                btnDelete.setOnClickListener { onDeleteClick(item) }
            }
        }

//        private fun getTimeAgo(timestamp: Long): String {
//            val now = System.currentTimeMillis()
//            val diff = now - timestamp
//
//            return when {
//                diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
//                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
//                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hr ago"
//                else -> {
//                    val sdf = SimpleDateFormat("dd MM yyyy, hh:mm a", Locale.getDefault())
//                    sdf.format(Date(timestamp))
//                }
//            }
//        }
    }

    class ScanItemDiffCallback : DiffUtil.ItemCallback<ScanItem>() {
        override fun areItemsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            return oldItem == newItem
        }
    }
}
