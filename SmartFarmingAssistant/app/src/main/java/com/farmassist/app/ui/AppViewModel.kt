package com.farmassist.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farmassist.app.ble.BleConnectionState
import com.farmassist.app.ble.LiveSensorData
import com.farmassist.app.ble.SensorBleManager
import com.farmassist.app.ble.VirtualFieldCaptureManager
import com.farmassist.app.data.AppDatabase
import com.farmassist.app.data.PredictionRecord
import com.farmassist.app.data.SensorReadingRecord
import com.farmassist.app.ml.ClassificationResult
import com.farmassist.app.ml.CropClassMapping
import com.farmassist.app.ml.DiseaseInfo
import com.farmassist.app.ml.DiseaseInfoRepository
import com.farmassist.app.ml.TFLiteClassifier
import com.farmassist.app.sms.SmsAlertManager
import com.farmassist.app.util.IrrigationAdvisor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PredictionUiState(
    val diseaseResult: ClassificationResult? = null,
    val pestResult: ClassificationResult? = null,
    val diseaseInfo: DiseaseInfo? = null,
    val isRunning: Boolean = false,
    val error: String? = null,
    val usingNpu: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val bleManager = SensorBleManager(application)
    val smsManager = SmsAlertManager(application)
    private val diseaseInfoRepo = DiseaseInfoRepository(application)
    val cropMapping = CropClassMapping(application)

    // ── Virtual field mode ──────────────────────────────────────────────
    val virtualCaptureManager = VirtualFieldCaptureManager()

    private val _virtualFieldModeEnabled = MutableStateFlow(false)
    val virtualFieldModeEnabled: StateFlow<Boolean> = _virtualFieldModeEnabled

    private val _fieldNodeIpAddress = MutableStateFlow("")
    val fieldNodeIpAddress: StateFlow<String> = _fieldNodeIpAddress

    fun setVirtualFieldMode(enabled: Boolean) {
        _virtualFieldModeEnabled.value = enabled
        if (enabled && _fieldNodeIpAddress.value.isNotBlank()) {
            virtualCaptureManager.startPolling("http://${_fieldNodeIpAddress.value}:5000")
        } else {
            virtualCaptureManager.stopPolling()
        }
    }

    fun setFieldNodeIp(ip: String) {
        _fieldNodeIpAddress.value = ip
        // If virtual mode is already on, restart polling with the new IP
        if (_virtualFieldModeEnabled.value && ip.isNotBlank()) {
            virtualCaptureManager.startPolling("http://$ip:5000")
        }
    }
    // ────────────────────────────────────────────────────────────────────

    private var diseaseClassifier: TFLiteClassifier? = null
    private var pestClassifier: TFLiteClassifier? = null

    private val _predictionState = MutableStateFlow(PredictionUiState())
    val predictionState: StateFlow<PredictionUiState> = _predictionState

    val sensorData: StateFlow<LiveSensorData> = bleManager.sensorData
    val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    val allPredictions = db.dao().getAllPredictions()
    val recentSensorReadings = db.dao().getRecentSensorReadings()

    /** Registered farmer phone number for SMS fallback — set from Profile screen. */
    var farmerPhoneNumber: String = ""

    // Per-scan flow state
    var selectedCrop: String? = null
    var capturedBitmap: Bitmap? = null
    var currentAcres: Double? = null

    fun runInference(bitmap: Bitmap, crop: String?, acres: Double?) {
        currentAcres = acres
        viewModelScope.launch {
            _predictionState.value = PredictionUiState(isRunning = true)
            try {
                val allowedIndices = crop?.let { cropMapping.indicesFor(it) }?.takeIf { it.isNotEmpty() }
                val disease = getOrCreateDiseaseClassifier()?.predict(bitmap, allowedIndices)
                // Pest model has no crop mapping data yet — runs unfiltered across all classes.
                val pest = getOrCreatePestClassifier()?.predict(bitmap)

                val best = listOfNotNull(disease, pest).maxByOrNull { it.confidence }
                val info = best?.let { diseaseInfoRepo.infoFor(it.label, crop, it.confidence) }

                _predictionState.value = PredictionUiState(
                    diseaseResult = disease,
                    pestResult = pest,
                    diseaseInfo = info,
                    isRunning = false,
                    usingNpu = diseaseClassifier?.isUsingNnApi() ?: false
                )

                best?.let {
                    val doseText = if (info != null && acres != null) info.doseFor(acres) else null
                    db.dao().insertPrediction(
                        PredictionRecord(
                            timestamp = System.currentTimeMillis(),
                            type = if (it == disease) "disease" else "pest",
                            className = info?.disease?.takeIf { d -> d != "-" } ?: it.label,
                            confidence = it.confidence,
                            recommendation = info?.immediateActions?.joinToString("; ") ?: "",
                            cropSelected = crop,
                            acres = acres,
                            doseText = doseText,
                            imagePath = null
                        )
                    )

                    if (farmerPhoneNumber.isNotBlank() && it.confidence > 0.6f) {
                        val recommendationText = info?.immediateActions?.firstOrNull() ?: "See app for details."
                        smsManager.sendAlert(
                            farmerPhoneNumber,
                            SmsAlertManager.buildDiseaseAlertText(it.label, (it.confidence * 100).toInt(), recommendationText)
                        )
                    }
                }
            } catch (e: Exception) {
                _predictionState.value = PredictionUiState(isRunning = false, error = e.message ?: "Inference failed")
            }
        }
    }

    fun recordSensorReading(data: LiveSensorData) {
        val moisture = data.soilMoisturePercent ?: return
        val temp = data.temperatureCelsius ?: return
        val humidity = data.humidityPercent ?: return
        val advice = IrrigationAdvisor.adviseFor(moisture, temp, humidity)

        viewModelScope.launch {
            db.dao().insertSensorReading(
                SensorReadingRecord(
                    timestamp = System.currentTimeMillis(),
                    soilMoisturePercent = moisture,
                    temperatureCelsius = temp,
                    humidityPercent = humidity,
                    irrigationAdvice = advice
                )
            )
            if (advice == "Irrigate now" && farmerPhoneNumber.isNotBlank()) {
                smsManager.sendAlert(farmerPhoneNumber, SmsAlertManager.buildIrrigationAlertText(advice, moisture))
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            db.dao().clearAllPredictions()
            db.dao().clearAllSensorReadings()
        }
    }

    private fun getOrCreateDiseaseClassifier(): TFLiteClassifier? {
        if (diseaseClassifier == null) {
            diseaseClassifier = try {
                TFLiteClassifier(getApplication(), "crop_model_int8.tflite", "class_names.json")
            } catch (e: Exception) {
                null
            }
        }
        return diseaseClassifier
    }

    private fun getOrCreatePestClassifier(): TFLiteClassifier? {
        if (pestClassifier == null) {
            pestClassifier = try {
                TFLiteClassifier(getApplication(), "pest_model_int8.tflite", "pest_class_names.json")
            } catch (e: Exception) {
                null
            }
        }
        return pestClassifier
    }

    override fun onCleared() {
        super.onCleared()
        diseaseClassifier?.close()
        pestClassifier?.close()
        bleManager.disconnect()
        virtualCaptureManager.destroy()
    }
}
