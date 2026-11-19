package com.vzk.netspeed.ui

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
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

        updateTimezoneSummary()
    }

    override fun onResume() {
        super.onResume()
        updateTimezoneSummary()
    }

    private fun updateTimezoneSummary() {
        val prefs = PrefsManager(requireContext())
        val locations = prefs.timezoneLocations
        findPreference<androidx.preference.Preference>("current_timezones")?.summary =
            if (locations.isEmpty()) "No timezones added"
            else locations.joinToString("\n") { it.substringAfterLast('/').replace('_', ' ') + " ($it)" }
    }
}
