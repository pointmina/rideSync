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

    /**
     * [기본 스캔]
     * callbackFlow를 사용하여 콜백 지옥을 없애고,
     * 스트림(Stream) 형태로 스캔 결과를 지속적으로 방출합니다.
     */
    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<Resource<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
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

    /**
     * [2단계: 태그리스 근접 스캔]
     * 타겟 장비(targetDeviceName)가 설정된 RSSI 임계값(thresholdRssi)보다
     * 가까운 거리에서 'requiredHits'회 이상 연속으로 감지되면 성공 신호를 보냅니다.
     */
    @SuppressLint("MissingPermission")
    fun startTaglessScan(
        targetDeviceName: String = "Hanto 50S", // 테스트할 실제 장비 이름으로 변경 필요
        thresholdRssi: Int = -55,              // 이 값보다 커야(가까워야) 감지 인정
        requiredHits: Int = 3                  // 연속 감지 필요 횟수 (신호 튐 방지)
    ): Flow<Resource<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            trySend(Resource.Error("Bluetooth is disabled"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading) // 스캔 시작 알림

        // 연속 감지 횟수 카운터
        var approachCount = 0

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val device = result.device
                // 이름이 없으면 필터링 (ScanFilter를 썼더라도 이중 체크)
                val deviceName = device.name ?: result.scanRecord?.deviceName ?: return

                // 타겟 장비가 맞는지 확인
                if (deviceName == targetDeviceName) {
                    val rssi = result.rssi

                    // 1. RSSI 분석: 설정된 임계값보다 신호가 강한가?
                    if (rssi >= thresholdRssi) {
                        approachCount++
                        Log.d(
                            "TaglessScan",
                            "Signal Detected: $rssi dBm (Count: $approachCount/$requiredHits)"
                        )

                        // 2. 3회 연속 충족 시 '진입(Entry)'으로 판단
                        if (approachCount >= requiredHits) {
                            Log.d("TaglessScan", "Entry Confirmed! Triggering connection.")

                            val scannedDevice = ScannedDevice(
                                device = device,
                                name = deviceName,
                                address = device.address,
                                rssi = rssi
                            )

                            trySend(Resource.Success(scannedDevice))
                            close() // 목표를 달성했으므로 스캔 중단 (배터리 절약)
                        }
                    } else {
                        // 3. 신호가 약해지면 카운트 초기화 (연속성 조건 불만족)
                        if (approachCount > 0) {
                            Log.d("TaglessScan", "Signal dropped to $rssi dBm. Resetting count.")
                            approachCount = 0
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                trySend(Resource.Error("Scan failed code: $errorCode"))
                close()
            }
        }

        // 스캔 설정: 태그리스는 빠른 반응이 중요하므로 LOW_LATENCY 사용
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 필터 설정: 내 장비 이름만 스캔하도록 설정 (CPU/배터리 최적화)
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName(targetDeviceName)
                .build()
        )

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            trySend(Resource.Error("Scan start failed: ${e.message}"))
            close(e)
        }

        awaitClose {
            scanner.stopScan(scanCallback)
            Log.d("TaglessScan", "Scan stopped.")
        }
    }
}