package com.vzk.netspeed.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // Speed settings
    var speedUnit: String
        get() = prefs.getString("speed_unit", "adaptive") ?: "adaptive"
        set(value) = prefs.edit().putString("speed_unit", value).apply()

    var refreshInterval: Int
        get() = prefs.getInt("refresh_interval", 1)
        set(value) = prefs.edit().putInt("refresh_interval", value).apply()

    var showUpload: Boolean
        get() = prefs.getBoolean("show_upload", true)
        set(value) = prefs.edit().putBoolean("show_upload", value).apply()

    var showDownload: Boolean
        get() = prefs.getBoolean("show_download", true)
        set(value) = prefs.edit().putBoolean("show_download", value).apply()

    // Data usage
    var totalBytesDownloaded: Long
        get() = prefs.getLong("total_bytes_downloaded", 0L)
        set(value) = prefs.edit().putLong("total_bytes_downloaded", value).apply()

    var totalBytesUploaded: Long
        get() = prefs.getLong("total_bytes_uploaded", 0L)
        set(value) = prefs.edit().putLong("total_bytes_uploaded", value).apply()

    var trackingStartDate: String
        get() = prefs.getString("tracking_start_date", "") ?: ""
        set(value) = prefs.edit().putString("tracking_start_date", value).apply()

    var monthlyReset: Boolean
        get() = prefs.getBoolean("monthly_reset", true)
        set(value) = prefs.edit().putBoolean("monthly_reset", value).apply()

    var resetDay: Int
        get() = prefs.getInt("reset_day", 1)
        set(value) = prefs.edit().putInt("reset_day", value).apply()

    // Weather
    var showWeather: Boolean
        get() = prefs.getBoolean("show_weather", false)
        set(value) = prefs.edit().putBoolean("show_weather", value).apply()

    var weatherLocation: String
        get() = prefs.getString("weather_location", "") ?: ""
        set(value) = prefs.edit().putString("weather_location", value).apply()

    var temperatureUnit: String
        get() = prefs.getString("temperature_unit", "celsius") ?: "celsius"
        set(value) = prefs.edit().putString("temperature_unit", value).apply()

    // World clock
    var showWorldClock: Boolean
        get() = prefs.getBoolean("show_world_clock", false)
        set(value) = prefs.edit().putBoolean("show_world_clock", value).apply()

    var timezoneLocations: List<String>
        get() {
            val raw = prefs.getString("timezone_locations", "") ?: ""
            return if (raw.isEmpty()) emptyList() else raw.split("|")
        }
        set(value) = prefs.edit().putString("timezone_locations", value.joinToString("|")).apply()

    var clockFormat: String
        get() = prefs.getString("clock_format", "24h") ?: "24h"
        set(value) = prefs.edit().putString("clock_format", value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    // Wi-Fi usage tracking
    // Stored as "ssid1\trx\ttx\nssid2\trx\ttx\n..."
    fun getWifiUsageMap(): MutableMap<String, Pair<Long, Long>> {
        val raw = prefs.getString("wifi_usage_data", "") ?: ""
        if (raw.isEmpty()) return mutableMapOf()
        val map = mutableMapOf<String, Pair<Long, Long>>()
        for (line in raw.split("\n")) {
            val parts = line.split("\t")
            if (parts.size == 3) {
                map[parts[0]] = Pair(parts[1].toLongOrNull() ?: 0L, parts[2].toLongOrNull() ?: 0L)
            }
        }
        return map
    }

    fun saveWifiUsageMap(map: Map<String, Pair<Long, Long>>) {
        val raw = map.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" }
        prefs.edit().putString("wifi_usage_data", raw).apply()
    }

    fun addWifiUsage(ssid: String, rxDelta: Long, txDelta: Long) {
        if (ssid.isEmpty()) return
        val map = getWifiUsageMap()
        val existing = map[ssid] ?: Pair(0L, 0L)
        map[ssid] = Pair(existing.first + rxDelta, existing.second + txDelta)
        saveWifiUsageMap(map)
    }

    fun clearWifiUsage() {
        prefs.edit().remove("wifi_usage_data").apply()
    }

    fun initTrackingDateIfNeeded() {
        if (trackingStartDate.isEmpty()) {
            trackingStartDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    fun checkMonthlyReset() {
        if (!monthlyReset) return
        val startDateStr = trackingStartDate
        if (startDateStr.isEmpty()) return

        val today = LocalDate.now()
        val startDate = LocalDate.parse(startDateStr)

        if (today.dayOfMonth >= resetDay &&
            (today.monthValue > startDate.monthValue || today.year > startDate.year)) {
            resetDataUsage()
        }
    }

    fun resetDataUsage() {
        totalBytesDownloaded = 0L
        totalBytesUploaded = 0L
        trackingStartDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
