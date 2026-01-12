package com.hanto.ridesync.ble.client

import android.annotation.SuppressLint
import android.bluetooth.*
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var isUserInitiatedDisconnect = false // 사용자 의도 확인 플래그

    // 재연결을 위한 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isUserInitiatedDisconnect = false
                reconnectJob?.cancel() // 연결 성공 시 재연결 작업 중단
                lastConnectedDevice = gatt.device
                _connectionState.value = ConnectionState.Connected(gatt.device)
                gatt.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected

                // [핵심] 사용자가 직접 끊은 게 아니라면 재연결 시도
                if (!isUserInitiatedDisconnect) {
                    Log.d("BleClient", "Unexpected disconnection. Attempting to reconnect...")
                    startReconnectionProcess()
                } else {
                    close()
                }
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
}