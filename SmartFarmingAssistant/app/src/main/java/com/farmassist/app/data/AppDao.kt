package com.farmassist.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert
    suspend fun insertPrediction(record: PredictionRecord): Long

    @Query("SELECT * FROM predictions ORDER BY timestamp DESC")
    fun getAllPredictions(): Flow<List<PredictionRecord>>

    @Query("DELETE FROM predictions")
    suspend fun clearAllPredictions()

    @Insert
    suspend fun insertSensorReading(record: SensorReadingRecord): Long

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC")
    fun getAllSensorReadings(): Flow<List<SensorReadingRecord>>

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSensorReadings(): Flow<List<SensorReadingRecord>>

    @Query("DELETE FROM sensor_readings")
    suspend fun clearAllSensorReadings()
}
