package com.hanto.ridesync.ble.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var isUserInitiatedDisconnect = false // 사용자 의도 확인 플래그

    // 재연결을 위한 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isUserInitiatedDisconnect = false
                reconnectJob?.cancel()
                lastConnectedDevice = gatt.device
                _connectionState.value = ConnectionState.Connected(gatt.device)
                gatt.discoverServices() // 서비스 발견 시작
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
                if (!isUserInitiatedDisconnect) {
                    startReconnectionProcess()
                } else {
                    close()
                }
            }
        }

        // 서비스 발견 완료 시 호출
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleClient", "Services Discovered. Reading Battery...")
                readBatteryLevel() // 연결 성공 및 서비스 발견 후 배터리 읽기 시작
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                _batteryLevel.value = level
                Log.d("BleClient", "Battery Level Read: $level%")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                _batteryLevel.value = level
                Log.d("BleClient", "Battery Level Changed: $level%")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startReconnectionProcess() {
        val device = lastConnectedDevice ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting

            // 재연결 시도 (최대 3번, 5초 간격)
            repeat(3) { attempt ->
                Log.d("BleClient", "Reconnection attempt ${attempt + 1}")
                delay(5000) // 5초 대기

                if (bluetoothGatt != null) close()
                bluetoothGatt = device.connectGatt(context, false, gattCallback)

                // 연결 시도 후 잠시 대기하며 상태 변화를 관찰
                delay(2000)
                if (_connectionState.value is ConnectionState.Connected) return@launch
            }

            // 3번 모두 실패 시 에러 상태로 변경
            _connectionState.value = ConnectionState.Error("Automatic reconnection failed.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        isUserInitiatedDisconnect = false
        lastConnectedDevice = device
        _connectionState.value = ConnectionState.Connecting
        if (bluetoothGatt != null) close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isUserInitiatedDisconnect = true // 사용자가 직접 끊었음을 명시
        reconnectJob?.cancel()
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun readBatteryLevel() {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(BATTERY_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)

        if (characteristic != null) {
            // 1. 즉시 한 번 읽기
            gatt.readCharacteristic(characteristic)

            // 2. 값이 변할 때마다 알려달라고 설정
            gatt.setCharacteristicNotification(characteristic, true)

            // CCCD 설정
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
}