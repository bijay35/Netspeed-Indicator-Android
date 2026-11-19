package com.vzk.netspeed.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vzk.netspeed.databinding.ItemWifiUsageBinding
import com.vzk.netspeed.util.FormatUtils

data class WifiUsageItem(
    val ssid: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

class WifiUsageAdapter : ListAdapter<WifiUsageItem, WifiUsageAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemWifiUsageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWifiUsageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.textSsid.text = item.ssid
        b.textWifiTotal.text = FormatUtils.formatBytes(item.totalBytes)
        b.textWifiDownload.text = "\u2193 ${FormatUtils.formatBytes(item.rxBytes)}"
        b.textWifiUpload.text = "\u2191 ${FormatUtils.formatBytes(item.txBytes)}"
        b.textWifiRank.text = "${position + 1}"

        val maxBytes = getItem(0).totalBytes
        b.progressWifiUsage.max = 100
        b.progressWifiUsage.progress = if (maxBytes > 0) {
            ((item.totalBytes * 100) / maxBytes).toInt()
        } else 0
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WifiUsageItem>() {
            override fun areItemsTheSame(a: WifiUsageItem, b: WifiUsageItem) = a.ssid == b.ssid
            override fun areContentsTheSame(a: WifiUsageItem, b: WifiUsageItem) =
                a.rxBytes == b.rxBytes && a.txBytes == b.txBytes
        }
    }
}
