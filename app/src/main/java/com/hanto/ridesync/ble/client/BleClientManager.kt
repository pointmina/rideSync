package com.hanto.ridesync.ble.client

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bluetoothGatt: BluetoothGatt? = null

    // UI가 구독할 연결 상태
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BleClient", "Successfully connected to $deviceAddress")
                    _connectionState.value = ConnectionState.Connected(gatt.device)

                    // [중요] 연결 후 반드시 서비스 발견(Discover Services)을 해야 데이터를 읽고 쓸 수 있음
                    // 약간의 딜레이를 주는 것이 안정성에 도움이 됨
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BleClient", "Successfully disconnected from $deviceAddress")
                    _connectionState.value = ConnectionState.Disconnected
                    close()
                }
            } else {
                // 에러 발생 (133번 에러 등)
                Log.e("BleClient", "Error $status encountered for $deviceAddress! Disconnecting...")
                _connectionState.value = ConnectionState.Error("Connection error: $status")
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleClient", "Services discovered for ${gatt.device.address}")
                // 여기서부터 배터리 레벨 읽기 등을 수행할 수 있습니다.
            } else {
                Log.e("BleClient", "Service discovery failed with status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.Connecting

        // 이미 연결된 게 있다면 끊고 시작
        if (bluetoothGatt != null) {
            close()
        }

        // autoConnect = false: 즉시 연결 시도. (사용자가 직접 클릭했을 때 적합)
        // autoConnect = true: 기기가 범위 내에 들어오면 자동 연결. (백그라운드 재연결 시 적합)
        // RideSync는 리스트 클릭이므로 false가 반응이 빠름.
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}