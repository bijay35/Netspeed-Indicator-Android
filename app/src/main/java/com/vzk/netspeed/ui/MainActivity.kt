package com.vzk.netspeed.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vzk.netspeed.R
import com.vzk.netspeed.data.PrefsManager
import com.vzk.netspeed.databinding.ActivityMainBinding
import com.vzk.netspeed.service.NetSpeedService
import com.vzk.netspeed.util.FormatUtils
import com.vzk.netspeed.util.WeatherUtils
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private var weatherTimer: Timer? = null
    private var clockTimer: Timer? = null
    private val httpClient = OkHttpClient()

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 200
    }

    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val rxSpeed = intent.getLongExtra(NetSpeedService.EXTRA_RX_SPEED, 0)
            val txSpeed = intent.getLongExtra(NetSpeedService.EXTRA_TX_SPEED, 0)
            val totalRx = intent.getLongExtra(NetSpeedService.EXTRA_TOTAL_RX, 0)
            val totalTx = intent.getLongExtra(NetSpeedService.EXTRA_TOTAL_TX, 0)
            updateSpeedDisplay(rxSpeed, txSpeed, totalRx, totalTx)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PrefsManager(this)

        requestNotificationPermission()
        setupServiceToggle()
        setupAnalyticsCard()
        setupDataUsage()
        setupWeather()
        setupWorldClock()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            speedReceiver,
            IntentFilter(NetSpeedService.ACTION_SPEED_UPDATE),
            Context.RECEIVER_NOT_EXPORTED
        )
        refreshDataUsage()
        refreshWeatherVisibility()
        refreshWorldClockVisibility()
        updateWorldClocks()

        if (prefs.showWeather && prefs.weatherLocation.isNotEmpty()) {
            fetchWeather()
        }

        startClockTimer()

        // If service toggle is on and overlay permission was just granted, restart service
        if (prefs.serviceEnabled && Settings.canDrawOverlays(this)) {
            binding.switchService.isChecked = true
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(speedReceiver)
        weatherTimer?.cancel()
        clockTimer?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_reset_data -> {
                prefs.resetDataUsage()
                refreshDataUsage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                // Permission granted — start the service
                if (prefs.serviceEnabled) {
                    stopService(Intent(this, NetSpeedService::class.java))
                    startForegroundService(Intent(this, NetSpeedService::class.java))
                }
            } else {
                Toast.makeText(this, "Overlay permission denied — speed won't show on status bar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupServiceToggle() {
        binding.switchService.isChecked = prefs.serviceEnabled
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.serviceEnabled = isChecked
            if (isChecked) {
                // Request overlay permission first
                if (!Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                }
                startForegroundService(Intent(this, NetSpeedService::class.java))
            } else {
                stopService(Intent(this, NetSpeedService::class.java))
                binding.textDownloadSpeed.text = "---"
                binding.textUploadSpeed.text = "---"
            }
        }
    }

    private fun updateSpeedDisplay(rxSpeed: Long, txSpeed: Long, totalRx: Long, totalTx: Long) {
        val unit = FormatUtils.parseSpeedUnit(prefs.speedUnit)
        binding.textDownloadSpeed.text = "\u2193 ${FormatUtils.formatSpeed(rxSpeed, unit)}"
        binding.textUploadSpeed.text = "\u2191 ${FormatUtils.formatSpeed(txSpeed, unit)}"

        binding.textTotalDownloaded.text = "Downloaded: ${FormatUtils.formatBytes(totalRx)}"
        binding.textTotalUploaded.text = "Uploaded: ${FormatUtils.formatBytes(totalTx)}"
        binding.textTotalData.text = "Total: ${FormatUtils.formatBytes(totalRx + totalTx)}"
        binding.textTrackingSince.text = "Since: ${prefs.trackingStartDate.ifEmpty { "N/A" }}"
    }

    private fun setupAnalyticsCard() {
        binding.cardAnalytics.setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
    }

    private fun setupDataUsage() {
        refreshDataUsage()
    }

    private fun refreshDataUsage() {
        val totalRx = prefs.totalBytesDownloaded
        val totalTx = prefs.totalBytesUploaded
        binding.textTotalDownloaded.text = "Downloaded: ${FormatUtils.formatBytes(totalRx)}"
        binding.textTotalUploaded.text = "Uploaded: ${FormatUtils.formatBytes(totalTx)}"
        binding.textTotalData.text = "Total: ${FormatUtils.formatBytes(totalRx + totalTx)}"
        binding.textTrackingSince.text = "Since: ${prefs.trackingStartDate.ifEmpty { "N/A" }}"
    }

    private fun setupWeather() {
        binding.buttonRefreshWeather.setOnClickListener { fetchWeather() }
        refreshWeatherVisibility()
    }

    private fun refreshWeatherVisibility() {
        binding.cardWeather.visibility = if (prefs.showWeather) View.VISIBLE else View.GONE
    }

    private fun fetchWeather() {
        val location = prefs.weatherLocation.trim()
        if (location.isEmpty()) {
            binding.textWeatherHeader.text = "Set location in Settings"
            binding.textWeatherTemp.visibility = View.GONE
            binding.textWeatherCondition.visibility = View.GONE
            binding.textWeatherHumidity.visibility = View.GONE
            return
        }

        binding.textWeatherHeader.text = "Loading weather..."

        val encoded = URLEncoder.encode(location, "UTF-8")
        val request = Request.Builder()
            .url("https://wttr.in/$encoded?format=j1")
            .header("User-Agent", "android-netspeed-indicator")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.textWeatherHeader.text = "Weather: Error - ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        runOnUiThread {
                            binding.textWeatherHeader.text = "Weather: HTTP ${resp.code}"
                        }
                        return
                    }

                    val body = resp.body?.string() ?: return
                    val data = JSONObject(body)
                    val current = data.getJSONArray("current_condition").getJSONObject(0)
                    val tempC = current.getString("temp_C")
                    val tempF = current.getString("temp_F")
                    val weatherCode = current.getString("weatherCode").toInt()
                    val humidity = current.getString("humidity")
                    val areaName = data.getJSONArray("nearest_area")
                        .getJSONObject(0)
                        .getJSONArray("areaName")
                        .getJSONObject(0)
                        .getString("value")

                    val emoji = WeatherUtils.getWeatherEmoji(weatherCode)
                    val condition = WeatherUtils.getConditionText(weatherCode)
                    val useCelsius = prefs.temperatureUnit == "celsius"
                    val tempDisplay = if (useCelsius) "${tempC}\u00B0C" else "${tempF}\u00B0F"

                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val refreshTime = timeFormat.format(Date())

                    runOnUiThread {
                        binding.textWeatherHeader.text = "$emoji Weather: $areaName"
                        binding.textWeatherTemp.text = "\uD83C\uDF21\uFE0F Temperature: $tempDisplay"
                        binding.textWeatherCondition.text = "$emoji Condition: $condition"
                        binding.textWeatherHumidity.text = "\uD83D\uDCA7 Humidity: $humidity%"
                        binding.textWeatherLastRefreshed.text = "\uD83D\uDD52 Last refreshed: $refreshTime"
                        binding.textWeatherTemp.visibility = View.VISIBLE
                        binding.textWeatherCondition.visibility = View.VISIBLE
                        binding.textWeatherHumidity.visibility = View.VISIBLE
                        binding.textWeatherLastRefreshed.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    private fun setupWorldClock() {
        refreshWorldClockVisibility()
    }

    private fun refreshWorldClockVisibility() {
        binding.cardWorldClock.visibility = if (prefs.showWorldClock) View.VISIBLE else View.GONE
    }

    private fun updateWorldClocks() {
        if (!prefs.showWorldClock) return

        val locations = prefs.timezoneLocations
        if (locations.isEmpty()) {
            binding.textWorldClocks.text = "No timezones added. Use Settings to add."
            return
        }

        val is12h = prefs.clockFormat == "12h"
        val pattern = if (is12h) "hh:mm a" else "HH:mm"
        val formatter = DateTimeFormatter.ofPattern(pattern)

        val sb = StringBuilder()
        for (tzId in locations) {
            try {
                val zone = ZoneId.of(tzId)
                val now = ZonedDateTime.now(zone)
                val timeStr = now.format(formatter)
                val label = tzId.substringAfterLast('/').replace('_', ' ')
                sb.appendLine("$timeStr  $label")
            } catch (_: Exception) {
                sb.appendLine("--:--  $tzId (invalid)")
            }
        }
        binding.textWorldClocks.text = sb.toString().trimEnd()
    }

    private fun startClockTimer() {
        clockTimer?.cancel()
        clockTimer = Timer().also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { updateWorldClocks() }
                }
            }, 0, 30_000)
        }

        // Weather refresh every 10 minutes
        if (prefs.showWeather) {
            weatherTimer?.cancel()
            weatherTimer = Timer().also {
                it.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        runOnUiThread { fetchWeather() }
                    }
                }, 600_000, 600_000)
            }
        }
    }
}
