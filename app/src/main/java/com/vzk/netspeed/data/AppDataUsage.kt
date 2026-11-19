package com.vzk.netspeed.data

import android.graphics.drawable.Drawable

data class AppDataUsage(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
