package com.farmassist.app.util

/**
 * Turns raw sensor readings into a plain-language irrigation call.
 * Thresholds below are placeholders — tune with your agronomy/ICAR reference data
 * once available; different crops will need different soil-moisture thresholds.
 */
object IrrigationAdvisor {

    private const val LOW_MOISTURE_THRESHOLD = 30f   // % — below this, irrigate
    private const val HIGH_TEMP_THRESHOLD = 35f       // °C — hot + dry-ish soil = irrigate sooner

    fun adviseFor(soilMoisturePercent: Float, temperatureCelsius: Float, humidityPercent: Float): String {
        return when {
            soilMoisturePercent < LOW_MOISTURE_THRESHOLD -> "Irrigate now"
            soilMoisturePercent < LOW_MOISTURE_THRESHOLD + 10f && temperatureCelsius > HIGH_TEMP_THRESHOLD ->
                "Irrigate soon — high temperature is accelerating soil drying"
            else -> "Hold — soil moisture adequate"
        }
    }
}
