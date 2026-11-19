package com.vzk.netspeed.ui

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.vzk.netspeed.data.NetworkStatsHelper
import com.vzk.netspeed.data.PrefsManager
import com.vzk.netspeed.data.SortMode
import com.vzk.netspeed.data.TimePeriod
import com.vzk.netspeed.databinding.ActivityAnalyticsBinding
import com.vzk.netspeed.util.FormatUtils

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var appAdapter: AppUsageAdapter
    private lateinit var wifiAdapter: WifiUsageAdapter
    private lateinit var statsHelper: NetworkStatsHelper
    private lateinit var prefs: PrefsManager
    private var currentPeriod = TimePeriod.TODAY
    private var currentSort = SortMode.TOTAL
    private var currentTab = 0 // 0=apps, 1=wifi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statsHelper = NetworkStatsHelper(this)
        prefs = PrefsManager(this)
        appAdapter = AppUsageAdapter()
        wifiAdapter = WifiUsageAdapter()

        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = appAdapter

        setupTabs()
        setupPeriodChips()
        setupSortChips()
    }

    override fun onResume() {
        super.onResume()
        if (currentTab == 1) {
            // Wi-Fi tab doesn't need usage stats permission
            binding.layoutPermission.visibility = View.GONE
            binding.layoutContent.visibility = View.VISIBLE
            loadWifiData()
        } else if (hasUsageStatsPermission()) {
            binding.layoutPermission.visibility = View.GONE
            binding.layoutContent.visibility = View.VISIBLE
            loadAppData()
        } else {
            binding.layoutPermission.visibility = View.VISIBLE
            binding.layoutContent.visibility = View.GONE
            binding.buttonGrantPermission.setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentTab = tab.position
                when (tab.position) {
                    0 -> {
                        binding.chipGroupPeriod.visibility = View.VISIBLE
                        binding.chipGroupSort.visibility = View.VISIBLE
                        binding.recyclerApps.adapter = appAdapter
                        if (hasUsageStatsPermission()) {
                            binding.layoutPermission.visibility = View.GONE
                            binding.layoutContent.visibility = View.VISIBLE
                            loadAppData()
                        } else {
                            binding.layoutPermission.visibility = View.VISIBLE
                            binding.layoutContent.visibility = View.GONE
                        }
                    }
                    1 -> {
                        binding.chipGroupPeriod.visibility = View.GONE
                        binding.chipGroupSort.visibility = View.GONE
                        binding.layoutPermission.visibility = View.GONE
                        binding.layoutContent.visibility = View.VISIBLE
                        binding.recyclerApps.adapter = wifiAdapter
                        loadWifiData()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupPeriodChips() {
        binding.chipToday.setOnClickListener { selectPeriod(TimePeriod.TODAY) }
        binding.chip7days.setOnClickListener { selectPeriod(TimePeriod.LAST_7_DAYS) }
        binding.chip30days.setOnClickListener { selectPeriod(TimePeriod.LAST_30_DAYS) }
        binding.chipThisMonth.setOnClickListener { selectPeriod(TimePeriod.THIS_MONTH) }
        binding.chipToday.isChecked = true
    }

    private fun setupSortChips() {
        binding.chipSortTotal.setOnClickListener { selectSort(SortMode.TOTAL) }
        binding.chipSortDownload.setOnClickListener { selectSort(SortMode.DOWNLOAD) }
        binding.chipSortUpload.setOnClickListener { selectSort(SortMode.UPLOAD) }
        binding.chipSortTotal.isChecked = true
    }

    private fun selectPeriod(period: TimePeriod) {
        currentPeriod = period
        loadAppData()
    }

    private fun selectSort(sort: SortMode) {
        currentSort = sort
        loadAppData()
    }

    private fun loadAppData() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.recyclerApps.visibility = View.GONE

        Thread {
            val apps = statsHelper.queryPerAppUsage(currentPeriod)

            val sorted = when (currentSort) {
                SortMode.TOTAL -> apps.sortedByDescending { it.totalBytes }
                SortMode.DOWNLOAD -> apps.sortedByDescending { it.rxBytes }
                SortMode.UPLOAD -> apps.sortedByDescending { it.txBytes }
            }

            val totalRx = apps.sumOf { it.rxBytes }
            val totalTx = apps.sumOf { it.txBytes }

            runOnUiThread {
                appAdapter.submitList(sorted)
                binding.progressLoading.visibility = View.GONE
                binding.recyclerApps.visibility = View.VISIBLE

                binding.textSummaryTotal.text = "Total: ${FormatUtils.formatBytes(totalRx + totalTx)}"
                binding.textSummaryDown.text = "\u2193 ${FormatUtils.formatBytes(totalRx)}"
                binding.textSummaryUp.text = "\u2191 ${FormatUtils.formatBytes(totalTx)}"
                binding.textAppCount.text = "${sorted.size} apps"
            }
        }.start()
    }

    private fun loadWifiData() {
        val wifiMap = prefs.getWifiUsageMap()
        val items = wifiMap.map { (ssid, usage) ->
            WifiUsageItem(ssid, usage.first, usage.second)
        }.sortedByDescending { it.totalBytes }

        wifiAdapter.submitList(items)

        val totalRx = items.sumOf { it.rxBytes }
        val totalTx = items.sumOf { it.txBytes }

        binding.textSummaryTotal.text = "Total: ${FormatUtils.formatBytes(totalRx + totalTx)}"
        binding.textSummaryDown.text = "\u2193 ${FormatUtils.formatBytes(totalRx)}"
        binding.textSummaryUp.text = "\u2191 ${FormatUtils.formatBytes(totalTx)}"
        binding.textAppCount.text = "${items.size} networks"

        binding.progressLoading.visibility = View.GONE
        binding.recyclerApps.visibility = View.VISIBLE
    }
}
