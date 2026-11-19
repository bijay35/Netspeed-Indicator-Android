package com.vzk.netspeed.util

object WeatherUtils {

    fun getWeatherEmoji(code: Int): String = when (code) {
        113 -> "\u2600\uFE0F"       // Sunny
        116 -> "\u26C5"              // Partly cloudy
        119, 122 -> "\u2601\uFE0F"  // Cloudy/Overcast
        143, 248, 260 -> "\uD83C\uDF2B\uFE0F" // Fog
        176, 263, 266, 293, 296, 299, 302, 305, 308, 353, 356, 359 -> "\uD83C\uDF27\uFE0F" // Rain
        179, 182, 185, 227, 230, 320, 323, 326, 329, 332, 335, 338, 362, 365, 368, 371, 374, 377 -> "\u2744\uFE0F" // Snow
        200, 386, 389, 392, 395 -> "\u26A1" // Thunder
        else -> "\uD83C\uDF24\uFE0F"
    }

    fun getConditionText(code: Int): String = when (code) {
        113 -> "Sunny"
        116 -> "Partly Cloudy"
        119 -> "Cloudy"
        122 -> "Overcast"
        143, 248, 260 -> "Foggy"
        176, 263, 266, 293, 296 -> "Light Rain"
        299, 302, 305, 308, 353, 356, 359 -> "Rain"
        179, 323, 326 -> "Light Snow"
        227, 230, 329, 332, 335, 338 -> "Snow"
        182, 185, 311, 314, 317, 320, 362, 365, 368, 371, 374, 377 -> "Sleet"
        200, 386, 389, 392, 395 -> "Thunderstorm"
        else -> "Unknown"
    }
}
