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

data class ManualSensorData(
    val soilMoisture: Float = 45f,
    val temperature: Float = 28f,
    val humidity: Float = 60f,
    val rainfall: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val bleManager = SensorBleManager(application)
    val smsManager = SmsAlertManager(application)
    private val diseaseInfoRepo = DiseaseInfoRepository(application)
    val cropMapping = CropClassMapping(application)

    // ── Global Auto / Manual Data Source Mode ───────────────────────────
    private val _isAutoMode = MutableStateFlow(true)
    val isAutoMode: StateFlow<Boolean> = _isAutoMode

    // Backwards-compatible alias for ProfileScreen / existing observers
    val virtualFieldModeEnabled: StateFlow<Boolean> = _isAutoMode

    private val _manualSensorData = MutableStateFlow(ManualSensorData())
    val manualSensorData: StateFlow<ManualSensorData> = _manualSensorData
    private var hasUserEditedManual = false

    private val _fieldNodeIpAddress = MutableStateFlow("10.0.2.2")
    val fieldNodeIpAddress: StateFlow<String> = _fieldNodeIpAddress

    // ── Virtual field capture manager ───────────────────────────────────
    val virtualCaptureManager = VirtualFieldCaptureManager()

    private var lastKnownLiveData = LiveSensorData()

    fun setAutoMode(enabled: Boolean) {
        _isAutoMode.value = enabled
        if (enabled) {
            if (_fieldNodeIpAddress.value.isNotBlank()) {
                virtualCaptureManager.startPolling(buildNodeUrl(_fieldNodeIpAddress.value))
            }
            _mergedSensorData.value = lastKnownLiveData
            _mergedConnectionState.value = bleManager.connectionState.value
        } else {
            virtualCaptureManager.stopPolling()
            // Pre-fill with last known live values if user hasn't customized manual values yet
            if (!hasUserEditedManual) {
                if (lastKnownLiveData.soilMoisturePercent != null ||
                    lastKnownLiveData.temperatureCelsius != null ||
                    lastKnownLiveData.humidityPercent != null
                ) {
                    _manualSensorData.value = ManualSensorData(
                        soilMoisture = lastKnownLiveData.soilMoisturePercent ?: 45f,
                        temperature = lastKnownLiveData.temperatureCelsius ?: 28f,
                        humidity = lastKnownLiveData.humidityPercent ?: 60f,
                        rainfall = lastKnownLiveData.rainfallFlag
                    )
                }
            }
            publishManualSensorData(_manualSensorData.value)
            _mergedConnectionState.value = BleConnectionState.CONNECTED
        }
    }

    fun setVirtualFieldMode(enabled: Boolean) {
        setAutoMode(enabled)
    }

    /**
     * Builds the base URL for the virtual field node.
     * Supports both local IPs (e.g. "192.168.1.42" → "http://192.168.1.42:5000")
     * and cloud URLs (e.g. "krishitech-field-node.onrender.com" → "https://krishitech-field-node.onrender.com").
     */
    private fun buildNodeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed.trimEnd('/')
            trimmed.contains(".") && !trimmed[0].isDigit() -> "https://$trimmed".trimEnd('/')
            else -> "http://$trimmed:5000"
        }
    }

    fun setFieldNodeIp(ip: String) {
        _fieldNodeIpAddress.value = ip
        if (_isAutoMode.value && ip.isNotBlank()) {
            virtualCaptureManager.startPolling(buildNodeUrl(ip))
        }
    }

    fun updateManualSensorData(data: ManualSensorData) {
        hasUserEditedManual = true
        _manualSensorData.value = data
        if (!_isAutoMode.value) {
            publishManualSensorData(data)
        }
    }

    private fun publishManualSensorData(data: ManualSensorData) {
        _mergedSensorData.value = LiveSensorData(
            soilMoisturePercent = data.soilMoisture,
            temperatureCelsius = data.temperature,
            humidityPercent = data.humidity,
            rainfallFlag = data.rainfall,
            batteryLevelPercent = null,
            connected = true
        )
    }
    // ────────────────────────────────────────────────────────────────────

    private var diseaseClassifier: TFLiteClassifier? = null
    private var pestClassifier: TFLiteClassifier? = null

    private val _predictionState = MutableStateFlow(PredictionUiState())
    val predictionState: StateFlow<PredictionUiState> = _predictionState

    private val _mergedSensorData = MutableStateFlow(LiveSensorData())
    val sensorData: StateFlow<LiveSensorData> = _mergedSensorData

    private val _mergedConnectionState = MutableStateFlow(BleConnectionState.IDLE)
    val bleConnectionState: StateFlow<BleConnectionState> = _mergedConnectionState

    init {
        // Start camera polling if initially in Auto mode and IP available
        if (_isAutoMode.value && _fieldNodeIpAddress.value.isNotBlank()) {
            virtualCaptureManager.startPolling(buildNodeUrl(_fieldNodeIpAddress.value))
        }

        // Collect from BLE manager
        viewModelScope.launch {
            bleManager.sensorData.collect { bleData ->
                lastKnownLiveData = bleData
                if (_isAutoMode.value) {
                    _mergedSensorData.value = bleData
                }
            }
        }
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (_isAutoMode.value) {
                    _mergedConnectionState.value = state
                }
            }
        }
        // Collect from WiFi virtual field mode
        viewModelScope.launch {
            virtualCaptureManager.telemetryData.collect { wifiData ->
                if (wifiData != null) {
                    lastKnownLiveData = wifiData
                    if (_isAutoMode.value) {
                        _mergedSensorData.value = wifiData
                        _mergedConnectionState.value = BleConnectionState.CONNECTED
                    }
                }
            }
        }
    }

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
