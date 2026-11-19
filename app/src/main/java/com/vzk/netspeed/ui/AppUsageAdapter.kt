package com.vzk.netspeed.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vzk.netspeed.R
import com.vzk.netspeed.data.AppDataUsage
import com.vzk.netspeed.databinding.ItemAppUsageBinding
import com.vzk.netspeed.util.FormatUtils

class AppUsageAdapter : ListAdapter<AppDataUsage, AppUsageAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.textAppName.text = item.appName
        b.textTotalUsage.text = FormatUtils.formatBytes(item.totalBytes)
        b.textDownload.text = "\u2193 ${FormatUtils.formatBytes(item.rxBytes)}"
        b.textUpload.text = "\u2191 ${FormatUtils.formatBytes(item.txBytes)}"
        b.textRank.text = "${position + 1}"

        if (item.appIcon != null) {
            b.imageAppIcon.setImageDrawable(item.appIcon)
        } else {
            b.imageAppIcon.setImageResource(R.drawable.ic_speed)
        }

        // Progress bar shows proportion relative to top app
        val maxBytes = getItem(0).totalBytes
        b.progressUsage.max = 100
        b.progressUsage.progress = if (maxBytes > 0) {
            ((item.totalBytes * 100) / maxBytes).toInt()
        } else 0
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppDataUsage>() {
            override fun areItemsTheSame(a: AppDataUsage, b: AppDataUsage) = a.uid == b.uid
            override fun areContentsTheSame(a: AppDataUsage, b: AppDataUsage) =
                a.rxBytes == b.rxBytes && a.txBytes == b.txBytes
        }
    }
}
