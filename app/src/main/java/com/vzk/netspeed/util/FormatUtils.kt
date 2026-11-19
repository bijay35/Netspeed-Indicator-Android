package com.vzk.netspeed.util

object FormatUtils {

    enum class SpeedUnit { ADAPTIVE, KBPS, MBPS }

    fun formatSpeed(bytesPerSec: Long, unit: SpeedUnit): String {
        val bytes = bytesPerSec.coerceAtLeast(0)
        return when (unit) {
            SpeedUnit.KBPS -> {
                val kbps = bytes / 1024.0
                "%.1f KB/s".format(kbps)
            }
            SpeedUnit.MBPS -> {
                val mbps = bytes / (1024.0 * 1024.0)
                "%.2f MB/s".format(mbps)
            }
            SpeedUnit.ADAPTIVE -> {
                if (bytes < 1024L * 1024L) {
                    val kbps = bytes / 1024.0
                    "%.1f KB/s".format(kbps)
                } else {
                    val mbps = bytes / (1024.0 * 1024.0)
                    "%.2f MB/s".format(mbps)
                }
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024L -> "%.1f KB".format(b / 1024.0)
            b < 1024L * 1024L * 1024L -> "%.1f MB".format(b / (1024.0 * 1024.0))
            else -> "%.2f GB".format(b / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun parseSpeedUnit(value: String): SpeedUnit = when (value) {
        "kbps" -> SpeedUnit.KBPS
        "mbps" -> SpeedUnit.MBPS
        else -> SpeedUnit.ADAPTIVE
    }
}
