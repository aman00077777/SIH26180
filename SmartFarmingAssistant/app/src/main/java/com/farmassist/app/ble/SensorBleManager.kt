package com.farmassist.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * BLE characteristic UUIDs — synchronised with the Virtual Hardware Agent
 * (virtual_hardware_agent.py).  The service uses the BT SIG "Environmental
 * Sensing" base UUID; individual characteristics use standard or custom UUIDs.
 *
 * Byte format: 4-byte little-endian float for all sensor values except
 * rainfall which is a single byte (0 = dry, 1 = raining).
 */
object BleSpec {
    val SERVICE_UUID: UUID = UUID.fromString("0000181A-0000-1000-8000-00805F9B34FB")

    // Sensor array
    val SOIL_MOISTURE_CHAR_UUID: UUID = UUID.fromString("00002A6F-0000-1000-8000-00805F9B34FB")
    val TEMPERATURE_CHAR_UUID: UUID   = UUID.fromString("00002A6E-0000-1000-8000-00805F9B34FB")
    val HUMIDITY_CHAR_UUID: UUID      = UUID.fromString("00002A6D-0000-1000-8000-00805F9B34FB")   // was 2A6F (collision with soil moisture) — fixed
    val RAINFALL_CHAR_UUID: UUID      = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")   // custom — rain flag (0/1 byte)

    // Power system
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")   // BT SIG standard Battery Level

    val DEVICE_NAME_PREFIX = "KrishiTech"   // matches virtual hardware agent advertised name
}

data class LiveSensorData(
    // Sensor array
    val soilMoisturePercent: Float? = null,
    val temperatureCelsius: Float? = null,
    val humidityPercent: Float? = null,
    val rainfallFlag: Boolean = false,
    // Power system
    val batteryLevelPercent: Float? = null,
    // Connection
    val connected: Boolean = false
)

enum class BleConnectionState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

@SuppressLint("MissingPermission") // caller is responsible for requesting BLUETOOTH_SCAN/CONNECT first
class SensorBleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.IDLE)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _sensorData = MutableStateFlow(LiveSensorData())
    val sensorData: StateFlow<LiveSensorData> = _sensorData

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /** Scans for the KrishiTech field node by advertised name prefix and connects on first match. */
    fun startScan() {
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BleConnectionState.ERROR
            return
        }
        scanner = adapter.bluetoothLeScanner
        _connectionState.value = BleConnectionState.SCANNING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = BleConnectionState.IDLE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: return
            if (deviceName.startsWith(BleSpec.DEVICE_NAME_PREFIX)) {
                stopScan()
                connectTo(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.CONNECTED
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    _sensorData.value = _sensorData.value.copy(connected = false)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = g.getService(BleSpec.SERVICE_UUID) ?: return

            // Enable notifications on each characteristic so readings stream in live.
            listOf(
                BleSpec.SOIL_MOISTURE_CHAR_UUID,
                BleSpec.TEMPERATURE_CHAR_UUID,
                BleSpec.HUMIDITY_CHAR_UUID,
                BleSpec.RAINFALL_CHAR_UUID,
                BleSpec.BATTERY_LEVEL_CHAR_UUID
            ).forEach { uuid ->
                service.getCharacteristic(uuid)?.let { char ->
                    g.setCharacteristicNotification(char, true)
                    val cccd = char.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // standard CCCD UUID
                    )
                    cccd?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    cccd?.let { g.writeDescriptor(it) }
                }
            }
            _sensorData.value = _sensorData.value.copy(connected = true)
        }

        @Deprecated("Deprecated in Android 13+, kept for min-SDK 26 compatibility")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicUpdate(characteristic)
        }

        private fun handleCharacteristicUpdate(characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return

            when (characteristic.uuid) {
                BleSpec.RAINFALL_CHAR_UUID -> {
                    // Rainfall is a single byte: 0 = dry, 1 = raining
                    val raining = value.isNotEmpty() && value[0].toInt() != 0
                    _sensorData.value = _sensorData.value.copy(rainfallFlag = raining, connected = true)
                }
                else -> {
                    // All other characteristics are 4-byte little-endian floats
                    val floatVal = try {
                        java.nio.ByteBuffer.wrap(value)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            .float
                    } catch (e: Exception) {
                        return
                    }

                    when (characteristic.uuid) {
                        BleSpec.SOIL_MOISTURE_CHAR_UUID ->
                            _sensorData.value = _sensorData.value.copy(soilMoisturePercent = floatVal, connected = true)
                        BleSpec.TEMPERATURE_CHAR_UUID ->
                            _sensorData.value = _sensorData.value.copy(temperatureCelsius = floatVal, connected = true)
                        BleSpec.HUMIDITY_CHAR_UUID ->
                            _sensorData.value = _sensorData.value.copy(humidityPercent = floatVal, connected = true)
                        BleSpec.BATTERY_LEVEL_CHAR_UUID ->
                            _sensorData.value = _sensorData.value.copy(batteryLevelPercent = floatVal, connected = true)
                    }
                }
            }
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
