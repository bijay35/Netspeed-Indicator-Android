# Net Speed Indicator for Android

A real-time network speed monitor for Android that displays download/upload speed directly on your status bar — inspired by the [GNOME Shell netspeed extension](https://extensions.gnome.org/extension/104/netspeed/).

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" />
  <img src="https://img.shields.io/badge/Material%20Design-3-blue?logo=material-design" />
</p>

## Features

- **Status Bar Speed Overlay** — Live download/upload speed displayed as text next to the clock, just like the GNOME extension
- **Per-App Data Usage** — See which apps consume the most data with detailed download/upload breakdown
- **Wi-Fi Network Tracking** — Track data usage per Wi-Fi network (SSID)
- **Data Usage Dashboard** — Cumulative download/upload totals with monthly auto-reset
- **Weather Widget** — Current weather conditions via [wttr.in](https://wttr.in) (no API key needed)
- **World Clock** — Up to 5 IANA timezones displayed on the main screen
- **Persistent Monitoring** — Runs as a foreground service, survives reboots

## Screenshots

| Main Screen | Analytics | Settings |
|:-----------:|:---------:|:--------:|
| Speed cards, data usage, weather | Per-app usage with sort/filter | Customize units, intervals |

## How It Works

1. A **foreground service** reads Android's `TrafficStats` byte counters at configurable intervals
2. Speed is calculated from byte deltas and displayed via a **floating overlay** positioned on the status bar
3. Per-app data is queried through `NetworkStatsManager` (requires Usage Access permission)
4. Wi-Fi SSID detection tracks which networks you use the most

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw speed text overlay on status bar |
| `PACKAGE_USAGE_STATS` | Query per-app data usage |
| `QUERY_ALL_PACKAGES` | Resolve app names and icons from UIDs |
| `FOREGROUND_SERVICE` | Keep speed monitoring running |
| `POST_NOTIFICATIONS` | Required for foreground service on Android 13+ |
| `INTERNET` | Fetch weather data |
| `ACCESS_WIFI_STATE` | Detect connected Wi-Fi network name |
| `RECEIVE_BOOT_COMPLETED` | Auto-start monitoring after reboot |

## Build

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

**Requirements:** Android SDK 26+ (Android 8.0), Gradle 8.5, Kotlin 1.9

## Tech Stack

- **Language:** Kotlin
- **UI:** Material Design 3, View Binding
- **Networking:** OkHttp (weather), Android TrafficStats (speed)
- **Architecture:** Service + BroadcastReceiver + SharedPreferences
- **Min SDK:** 26 (Android 8.0 Oreo)

## License

MIT
