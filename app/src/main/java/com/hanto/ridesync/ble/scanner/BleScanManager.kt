package com.hanto.ridesync.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.hanto.ridesync.common.Constants
import com.hanto.ridesync.common.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class BleScanManager @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
) {

    // [기본 스캔 - UI용]
    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<Resource<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            trySend(Resource.Error("Bluetooth is disabled or not available"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val deviceName = result.device.name ?: return
                val scannedDevice = ScannedDevice(
                    device = result.device,
                    name = deviceName,
                    address = result.device.address,
                    rssi = result.rssi
                )
                trySend(Resource.Success(scannedDevice))
            }
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                trySend(Resource.Error("Scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(Constants.HANTO_SERVICE_UUID)).build())

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            trySend(Resource.Error("Scan error: ${e.message}"))
            close(e)
        }

        awaitClose { scanner.stopScan(scanCallback) }
    }

    /**
     * [태그리스 스캔 - 서비스용]
     * 이 함수가 누락되어 있었습니다! 서비스가 이걸 호출해야 자동 연결이 됩니다.
     */
    @SuppressLint("MissingPermission")
    fun startTaglessScan(
        targetDeviceName: String, // 찾을 이름 (예: "Hanto")
        thresholdRssi: Int = -80, // 테스트용으로 -80으로 설정 (원래 -55)
        requiredHits: Int = 3
    ): Flow<Resource<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            trySend(Resource.Error("Bluetooth disabled"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        var approachCount = 0

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val device = result.device
                val deviceName = device.name ?: result.scanRecord?.deviceName ?: return

                // 이름에 "Hanto"가 포함되어 있는지 확인 (대소문자 무시)
                if (deviceName.contains(targetDeviceName, ignoreCase = true)) {
                    val rssi = result.rssi

                    // 신호가 -80dBm보다 강하면 카운트 증가
                    if (rssi >= thresholdRssi) {
                        approachCount++
                        Log.d("TaglessScan", "[$deviceName] Signal: $rssi ($approachCount/$requiredHits)")

                        if (approachCount >= requiredHits) {
                            Log.d("TaglessScan", "Target Found! -> $deviceName")
                            trySend(Resource.Success(ScannedDevice(device, deviceName, device.address, rssi)))
                            close()
                        }
                    } else {
                        if (approachCount > 0) approachCount = 0
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                trySend(Resource.Error("Scan failed: $errorCode"))
                close()
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filters = emptyList<ScanFilter>() // 이름 검색을 위해 필터 없이 모두 스캔

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            trySend(Resource.Error("Start failed: ${e.message}"))
            close(e)
        }

        awaitClose { scanner.stopScan(scanCallback) }
    }
}