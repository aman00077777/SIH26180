package com.farmassist.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predictions")
data class PredictionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,          // "disease" or "pest"
    val className: String,
    val confidence: Float,
    val recommendation: String,
    val cropSelected: String? = null,
    val acres: Double? = null,
    val doseText: String? = null,
    val imagePath: String?     // local file path to the captured leaf photo, nullable
)

@Entity(tableName = "sensor_readings")
data class SensorReadingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val soilMoisturePercent: Float,
    val temperatureCelsius: Float,
    val humidityPercent: Float,
    val irrigationAdvice: String   // "Irrigate now" / "Hold"
)
