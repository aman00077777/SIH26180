package com.farmassist.app.ble

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the Virtual Hardware Agent's camera module endpoint over WiFi and
 * delivers captured images as Bitmaps.
 *
 * This replaces the phone's CameraX capture when "virtual field mode" is active,
 * but the image is still fed into the SAME on-device TFLite inference pipeline.
 * No AI happens here or on the Python agent side — only image fetching.
 *
 * Requires the phone and laptop to be on the same WiFi network.
 * BLE sensor data is separate and works over Bluetooth independently.
 */
class VirtualFieldCaptureManager {

    private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
    val latestBitmap: StateFlow<Bitmap?> = _latestBitmap

    private val _cropHint = MutableStateFlow<String?>(null)
    val cropHint: StateFlow<String?> = _cropHint

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start polling the virtual camera endpoint every 15 seconds.
     * @param baseUrl e.g. "http://192.168.1.42:5000"
     */
    fun startPolling(baseUrl: String) {
        stopPolling()
        _isPolling.value = true
        _lastError.value = null

        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val metaJson = fetchText("$baseUrl/latest-capture")
                    val meta = JSONObject(metaJson)
                    val imageUrl = meta.optString("image_url", "")
                    val crop = meta.optString("crop_hint", "")
                    val capturedAt = meta.optLong("captured_at", 0)

                    if (imageUrl.isNotEmpty() && capturedAt > 0) {
                        val fullImageUrl = "$baseUrl$imageUrl"
                        val bitmap = fetchBitmap(fullImageUrl)
                        if (bitmap != null) {
                            _latestBitmap.value = bitmap
                            _cropHint.value = crop
                            _lastError.value = null
                        }
                    }
                } catch (e: Exception) {
                    _lastError.value = e.message ?: "Connection failed"
                }
                delay(15_000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    fun destroy() {
        stopPolling()
        scope.cancel()
    }

    private fun fetchText(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBitmap(urlString: String): Bitmap? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 10000
            requestMethod = "GET"
        }
        return try {
            val stream: InputStream = connection.inputStream
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
