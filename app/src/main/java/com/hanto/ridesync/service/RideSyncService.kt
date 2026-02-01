package com.hanto.ridesync.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hanto.ridesync.R
import com.hanto.ridesync.ble.client.BleClientManager
import com.hanto.ridesync.ble.client.ConnectionState
import com.hanto.ridesync.ble.scanner.BleScanManager
import com.hanto.ridesync.common.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RideSyncService : Service() {

    @Inject lateinit var bleScanManager: BleScanManager
    @Inject lateinit var bleClientManager: BleClientManager

    // 서비스의 수명주기와 함께할 코루틴 스코프
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 스캔 작업을 제어하기 위한 Job (중복 실행 방지)
    private var scanJob: Job? = null

    companion object {
        const val CHANNEL_ID = "RideSync_Channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "RideSyncService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. 서비스가 켜지자마자 연결 상태 감시 시작
        observeConnectionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("태그리스 모드 대기 중..."))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 서비스 종료 시 모든 작업 취소 및 자원 해제
        serviceScope.coroutineContext.cancelChildren()
        bleClientManager.disconnect()
    }

    /**
     * [핵심 로직] 연결 상태를 실시간으로 관찰하여 행동 결정
     */
    @SuppressLint("MissingPermission") // <--- 이 줄 추가!
    private fun observeConnectionState() {
        bleClientManager.connectionState.onEach { state ->
            when (state) {
                is ConnectionState.Disconnected -> {
                    // 끊어졌을 때: 사용자가 직접 끊은 게 아니라면 '태그리스 스캔' 시작
                    Log.d(TAG, "State: Disconnected")
                    updateNotification("연결 대기 중... (Tagless Scan)")
                    startTaglessScan()
                }
                is ConnectionState.Connecting -> {
                    Log.d(TAG, "State: Connecting")
                    updateNotification("장치와 연결 시도 중...")
                    stopTaglessScan() // 연결 중이면 스캔 중단
                }
                is ConnectionState.Connected -> {
                    val deviceName = state.device.name ?: "Unknown Device"
                    Log.d(TAG, "State: Connected to $deviceName")
                    updateNotification("RideSync 연결됨: $deviceName")
                    stopTaglessScan() // 연결 성공 시 스캔 중단
                }
                is ConnectionState.Error -> {
                    Log.e(TAG, "State: Error - ${state.message}")
                    // 에러 발생 시 잠시 후 다시 스캔 시도 (여기선 바로 시작)
                    startTaglessScan()
                }
            }
        }.launchIn(serviceScope)
    }

    /**
     * [태그리스 스캔 시작]
     * - 이미 스캔 중이면 무시
     * - 3회 연속 감지된 기기가 나오면 즉시 연결 시도
     */
    private fun startTaglessScan() {
        if (scanJob?.isActive == true) return // 이미 스캔 중

        Log.d(TAG, "Starting Tagless Scan...")
        scanJob = serviceScope.launch {
            // 2단계에서 만든 'startTaglessScan' 호출
            // 주의: 실제 장비 이름을 꼭 맞춰야함.
            bleScanManager.startTaglessScan(targetDeviceName = "Hanto 50S")
                .collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val device = resource.data.device
                            Log.d(TAG, "Tagless Target Found! Connecting to ${device.address}")

                            // [Action] 기기 발견 -> 연결 요청
                            bleClientManager.connect(device)

                            // 연결 요청 후 스캔 루프 종료 (observeConnectionState가 상태 변화 감지함)
                            stopTaglessScan()
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Tagless Scan Error: ${resource.message}")
                            // 스캔 에러 시 잠시 대기 후 재시도 로직 등을 추가할 수 있음
                        }
                        is Resource.Loading -> {
                            // 로딩 중 (스캔 진행 중)
                        }
                    }
                }
        }
    }

    private fun stopTaglessScan() {
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Stopping Tagless Scan")
            scanJob?.cancel()
            scanJob = null
        }
    }

    // --- Notification 관련 (상태 메시지 업데이트 기능 추가) ---

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RideSync Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true) // 상태 업데이트 때마다 알림음 울리지 않게 설정
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "RideSync Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}