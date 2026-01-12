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
            close() // 스트림 종료
            return@callbackFlow
        }

        trySend(Resource.Loading) // 스캔 시작 알림

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                // 이름이 없는 기기는 무시
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

        // 스캔 설정: Low Latency (빠른 검색)
        // 라이딩 중에는 빠르게 연결해야 하므로 배터리를 좀 쓰더라도 Latency를 줄입니다.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 필터: 필요 시 특정 UUID 필터링 추가 (여기선 전체 스캔)
        val filters = listOf<ScanFilter>()

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