package com.vzk.netspeed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.vzk.netspeed.R
import com.vzk.netspeed.data.PrefsManager
import com.vzk.netspeed.ui.MainActivity
import com.vzk.netspeed.util.FormatUtils

class NetSpeedService : Service() {

    companion object {
        const val CHANNEL_ID = "netspeed_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_SPEED_UPDATE = "com.vzk.netspeed.SPEED_UPDATE"
        const val EXTRA_RX_SPEED = "rx_speed"
        const val EXTRA_TX_SPEED = "tx_speed"
        const val EXTRA_TOTAL_RX = "total_rx"
        const val EXTRA_TOTAL_TX = "total_tx"
    }

    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var prevRxBytes = 0L
    private var prevTxBytes = 0L
    private var prevTime = 0L

    // Overlay views
    private var windowManager: WindowManager? = null
    private var overlayLayout: LinearLayout? = null
    private var overlayDownText: TextView? = null
    private var overlayUpText: TextView? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, prefs.refreshInterval * 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs.initTrackingDateIfNeeded()
        prefs.checkMonthlyReset()

        prevRxBytes = TrafficStats.getTotalRxBytes()
        prevTxBytes = TrafficStats.getTotalTxBytes()
        prevTime = System.nanoTime()

        startForeground(NOTIFICATION_ID, buildNotification("--- / ---"))
        createOverlay()
        handler.post(updateRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        removeOverlay()
        super.onDestroy()
    }

    private fun createOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create container layout — horizontal, two speed values side by side
        overlayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(160, 0, 0, 0))  // semi-transparent black
            setPadding(dpToPx(6), dpToPx(1), dpToPx(6), dpToPx(1))
        }

        val textSize = 11f  // sp — compact but readable

        overlayDownText = TextView(this).apply {
            text = "\u2193 ---"
            setTextColor(Color.parseColor("#4FC3F7"))  // light blue for download
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dpToPx(6), 0)
        }

        overlayUpText = TextView(this).apply {
            text = "\u2191 ---"
            setTextColor(Color.parseColor("#AED581"))  // light green for upload
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            typeface = Typeface.DEFAULT_BOLD
        }

        overlayLayout!!.addView(overlayDownText)
        overlayLayout!!.addView(overlayUpText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(52)  // offset past the clock area
            y = dpToPx(10)
        }

        try {
            windowManager?.addView(overlayLayout, params)
        } catch (e: Exception) {
            // Permission revoked or other issue
            overlayLayout = null
        }
    }

    private fun removeOverlay() {
        overlayLayout?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayLayout = null
        overlayDownText = null
        overlayUpText = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun update() {
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        val now = System.nanoTime()

        val elapsed = (now - prevTime) / 1_000_000_000.0
        if (elapsed <= 0) return

        var rxSpeed = ((rxBytes - prevRxBytes) / elapsed).toLong()
        var txSpeed = ((txBytes - prevTxBytes) / elapsed).toLong()
        if (rxSpeed < 0) rxSpeed = 0
        if (txSpeed < 0) txSpeed = 0

        // Update data usage
        val rxDelta = rxBytes - prevRxBytes
        val txDelta = txBytes - prevTxBytes
        if (rxDelta > 0) prefs.totalBytesDownloaded += rxDelta
        if (txDelta > 0) prefs.totalBytesUploaded += txDelta

        // Track Wi-Fi SSID usage
        if (rxDelta > 0 || txDelta > 0) {
            val ssid = getCurrentWifiSsid()
            if (ssid != null) {
                prefs.addWifiUsage(ssid, rxDelta.coerceAtLeast(0), txDelta.coerceAtLeast(0))
            }
        }

        prevRxBytes = rxBytes
        prevTxBytes = txBytes
        prevTime = now

        val unit = FormatUtils.parseSpeedUnit(prefs.speedUnit)
        val downStr = FormatUtils.formatSpeed(rxSpeed, unit)
        val upStr = FormatUtils.formatSpeed(txSpeed, unit)

        val parts = mutableListOf<String>()
        if (prefs.showDownload) parts.add("\u2193$downStr")
        if (prefs.showUpload) parts.add("\u2191$upStr")
        val text = parts.joinToString("  ").ifEmpty { "---" }

        updateNotification(text)

        // Update overlay
        handler.post {
            overlayDownText?.text = "\u2193 $downStr"
            overlayUpText?.text = "\u2191 $upStr"
        }

        // Broadcast for UI updates
        sendBroadcast(Intent(ACTION_SPEED_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_RX_SPEED, rxSpeed)
            putExtra(EXTRA_TX_SPEED, txSpeed)
            putExtra(EXTRA_TOTAL_RX, prefs.totalBytesDownloaded)
            putExtra(EXTRA_TOTAL_TX, prefs.totalBytesUploaded)
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network Speed",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows real-time network speed"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Net Speed")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_transparent)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    @Suppress("deprecation")
    private fun getCurrentWifiSsid(): String? {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        if (ssid == "<unknown ssid>" || ssid == "0x") return null
        return ssid.removeSurrounding("\"")
    }
}
