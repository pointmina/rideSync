package com.hanto.ridesync.data.repository

import android.bluetooth.BluetoothDevice
import com.hanto.ridesync.ble.client.BleClientManager
import com.hanto.ridesync.ble.client.ConnectionState
import com.hanto.ridesync.ble.scanner.BleScanManager
import com.hanto.ridesync.ble.scanner.ScannedDevice
import com.hanto.ridesync.common.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// 인터페이스 없이 구현체만 작성 (시간 단축)
// 실무에서는 interface BleRepository -> class BleRepositoryImpl 구조가 정석
class BleRepository @Inject constructor(
    private val bleScanManager: BleScanManager,
    private val bleClientManager: BleClientManager
) {
    fun scanDevices(): Flow<Resource<ScannedDevice>> {
        return bleScanManager.startScanning()
    }

    val connectionState: Flow<ConnectionState> = bleClientManager.connectionState

    fun connect(device: BluetoothDevice) {
        bleClientManager.connect(device)
    }

    fun disconnect() {
        bleClientManager.disconnect()
    }
}