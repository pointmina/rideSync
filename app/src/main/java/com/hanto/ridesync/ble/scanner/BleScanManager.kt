package com.hanto.ridesync.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.hanto.ridesync.common.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import android.os.ParcelUuid
import com.hanto.ridesync.common.Constants

class BleScanManager @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
) {

    /**
     * callbackFlow를 사용하여 콜백 지옥을 없애고,
     * 스트림(Stream) 형태로 스캔 결과를 지속적으로 방출합니다.
     */
    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<Resource<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null || !bluetoothAdapter.isEnabled) {
            trySend(Resource.Error("Bluetooth is disabled or not available"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading) // 스캔 시작 알림

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                // 이름이 없는 기기는 무시 (선택 사항)
                val deviceName = result.device.name ?: return

                val scannedDevice = ScannedDevice(
                    device = result.device,
                    name = deviceName,
                    address = result.device.address,
                    rssi = result.rssi
                )

                // 성공적으로 찾은 기기를 방출
                trySend(Resource.Success(scannedDevice))
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                trySend(Resource.Error("Scan failed with error code: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(Constants.HANTO_SERVICE_UUID))
                .build()
        )

        // 스캔 시작
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            trySend(Resource.Error("Permission denied or Scan error: ${e.message}"))
            close(e)
        }

        awaitClose {
            scanner.stopScan(scanCallback)
        }
    }
}