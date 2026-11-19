package com.vzk.netspeed.ui

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.vzk.netspeed.R
import com.vzk.netspeed.data.PrefsManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Add timezone management
        findPreference<EditTextPreference>("add_timezone")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val tzId = (newValue as? String)?.trim() ?: return@setOnPreferenceChangeListener false
                if (tzId.isEmpty()) return@setOnPreferenceChangeListener false

                val prefs = PrefsManager(requireContext())
                val locations = prefs.timezoneLocations.toMutableList()
                if (locations.size >= 5 || locations.contains(tzId)) return@setOnPreferenceChangeListener false

                // Validate timezone
                try {
                    java.time.ZoneId.of(tzId)
                } catch (_: Exception) {
                    return@setOnPreferenceChangeListener false
                }

                locations.add(tzId)
                prefs.timezoneLocations = locations
                updateTimezoneSummary()
                text = "" // Clear after adding
                false // Don't persist - we handle it ourselves
            }
        }

        findPreference<androidx.preference.Preference>("clear_timezones")?.apply {
            setOnPreferenceClickListener {
                PrefsManager(requireContext()).timezoneLocations = emptyList()
                updateTimezoneSummary()
                true
            }
        }

        // Set summaries for list preferences
        setupListSummary("overlay_show_mode")
        setupListSummary("overlay_text_size")
        setupListSummary("overlay_download_color")
        setupListSummary("overlay_upload_color")
        setupListSummary("speed_unit")
        setupListSummary("temperature_unit")
        setupListSummary("clock_format")

        updateTimezoneSummary()
        updateOverlayPositionSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateTimezoneSummary()
    }

    private fun setupListSummary(key: String) {
        findPreference<ListPreference>(key)?.apply {
            summary = entry?.toString() ?: summary
            setOnPreferenceChangeListener { pref, newValue ->
                val lp = pref as ListPreference
                val index = lp.findIndexOfValue(newValue.toString())
                if (index >= 0) lp.summary = lp.entries[index]
                true
            }
        }
    }

    private fun updateTimezoneSummary() {
        val prefs = PrefsManager(requireContext())
        val locations = prefs.timezoneLocations
        findPreference<androidx.preference.Preference>("current_timezones")?.summary =
            if (locations.isEmpty()) "No timezones added"
            else locations.joinToString("\n") { it.substringAfterLast('/').replace('_', ' ') + " ($it)" }
    }

    private fun updateOverlayPositionSummaries() {
        val prefs = PrefsManager(requireContext())
        findPreference<androidx.preference.SeekBarPreference>("overlay_x")?.apply {
            summary = "Current: ${prefs.overlayX} dp from left"
        }
        findPreference<androidx.preference.SeekBarPreference>("overlay_y")?.apply {
            summary = "Current: ${prefs.overlayY} dp from top"
        }
    }
}
