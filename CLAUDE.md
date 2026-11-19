# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Check for compilation errors only
./gradlew compileDebugKotlin
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

No test infrastructure exists in this project.

## Architecture

Android app (Kotlin, minSdk 26) that monitors network speed via a foreground service and displays speed, data usage, weather, and world clocks.

**Service-to-UI communication pattern:**
- `NetSpeedService` runs as a sticky foreground service, reads `TrafficStats` byte counters on a timer, computes speed deltas, and updates the persistent notification.
- The service broadcasts `ACTION_SPEED_UPDATE` intents carrying rx/tx speed and cumulative totals.
- `MainActivity` registers a `BroadcastReceiver` for these intents and updates the UI cards.
- `PrefsManager` (SharedPreferences wrapper) is the shared state between service, UI, and settings — both the service and activity read/write it directly.

**Weather:** fetched from `wttr.in` (no API key) using OkHttp, parsed with `org.json`. Auto-refreshes every 10 minutes.

**World Clock:** uses `java.time.ZoneId` / `ZonedDateTime`. Up to 5 IANA timezone IDs stored pipe-delimited in SharedPreferences.

**Threading:** The service uses `Handler(Looper.getMainLooper())` for periodic updates. MainActivity uses `java.util.Timer` for clock/weather refresh. OkHttp callbacks dispatch to `runOnUiThread`.

## Key Conventions

- Package: `com.vzk.netspeed` with sub-packages `service`, `ui`, `data`, `util`
- View Binding enabled (not data binding)
- Material Design 3 theme (`Theme.Material3.DayNight.NoActionBar`)
- Settings use AndroidX Preference library with XML-defined preferences (`res/xml/preferences.xml`)
- Utility classes (`FormatUtils`, `WeatherUtils`) are Kotlin `object` singletons
- Foreground service type is `specialUse` (required for Android 14+)
