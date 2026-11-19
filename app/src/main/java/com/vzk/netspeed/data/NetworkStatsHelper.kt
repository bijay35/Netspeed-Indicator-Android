package com.vzk.netspeed.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.RemoteException
import java.util.Calendar

enum class TimePeriod(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_MONTH("This Month")
}

enum class SortMode(val label: String) {
    TOTAL("Total"),
    DOWNLOAD("Download"),
    UPLOAD("Upload")
}

class NetworkStatsHelper(private val context: Context) {

    private val statsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val pm = context.packageManager

    // Well-known Android system UIDs
    private val systemUidNames = mapOf(
        0 to "Android System (root)",
        1000 to "Android System",
        1001 to "Phone Services",
        1002 to "Bluetooth",
        1010 to "Wi-Fi",
        1013 to "Media Server",
        1014 to "DRM Server",
        1016 to "GPS",
        1017 to "Media DRM",
        1021 to "Cache Storage",
        1023 to "DNS Tethering",
        1037 to "Media Player",
        1041 to "Media Server",
        1047 to "DNS Resolver",
        1051 to "Nearby Sharing",
        9999 to "Misc System",
        -4 to "Tethering",
        -5 to "Removed Apps"
    )

    @Suppress("deprecation")
    fun queryPerAppUsage(period: TimePeriod): List<AppDataUsage> {
        val (startTime, endTime) = getTimeRange(period)
        val usageMap = mutableMapOf<Int, Pair<Long, Long>>()

        queryBucket(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)
        queryBucket(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)

        return usageMap.mapNotNull { (uid, usage) ->
            val (rx, tx) = usage
            if (rx + tx == 0L) return@mapNotNull null
            resolveApp(uid, rx, tx)
        }.sortedByDescending { it.totalBytes }
    }

    private fun resolveApp(uid: Int, rx: Long, tx: Long): AppDataUsage {
        // Try well-known system UIDs first
        systemUidNames[uid]?.let { name ->
            return AppDataUsage(uid, "android", name, null, rx, tx)
        }

        // Try resolving via PackageManager
        val packages = pm.getPackagesForUid(uid)
        if (packages != null && packages.isNotEmpty()) {
            // Try each package — pick the first one that resolves to a real app name
            for (pkg in packages) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val appIcon = pm.getApplicationIcon(appInfo)
                    return AppDataUsage(uid, pkg, appName, appIcon, rx, tx)
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                }
            }
            // All packages failed to resolve — use the first package name
            return AppDataUsage(uid, packages[0], packages[0], null, rx, tx)
        }

        // Try scanning all installed apps for matching UID
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                if (appInfo.uid == uid) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val appIcon = pm.getApplicationIcon(appInfo)
                    return AppDataUsage(uid, appInfo.packageName, appName, appIcon, rx, tx)
                }
            }
        } catch (_: Exception) {}

        // Fallback — categorize by UID range
        val name = when {
            uid in 10000..19999 -> "App (UID $uid)"
            uid in 1000..9999 -> "System (UID $uid)"
            else -> "Unknown (UID $uid)"
        }
        return AppDataUsage(uid, "unknown:$uid", name, null, rx, tx)
    }

    private fun queryBucket(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        usageMap: MutableMap<Int, Pair<Long, Long>>
    ) {
        try {
            val stats: NetworkStats = statsManager.querySummary(
                networkType, null, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val existing = usageMap[uid] ?: Pair(0L, 0L)
                usageMap[uid] = Pair(
                    existing.first + bucket.rxBytes,
                    existing.second + bucket.txBytes
                )
            }
            stats.close()
        } catch (_: RemoteException) {
        } catch (_: SecurityException) {
        }
    }

    private fun getTimeRange(period: TimePeriod): Pair<Long, Long> {
        val endTime = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val startTime = when (period) {
            TimePeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            TimePeriod.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            TimePeriod.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            TimePeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
        return Pair(startTime, endTime)
    }
}
