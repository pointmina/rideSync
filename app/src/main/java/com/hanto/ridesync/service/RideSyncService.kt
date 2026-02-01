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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class RideSyncService : Service() {

    @Inject
    lateinit var bleScanManager: BleScanManager
    @Inject
    lateinit var bleClientManager: BleClientManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    companion object {
        const val CHANNEL_ID = "RideSync_Channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "RideSyncService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeConnectionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("태그리스 모드 대기 중..."))

        // 기존에 연결이 안 되어 있다면 즉시 스캔 시작
        if (bleClientManager.connectionState.value is ConnectionState.Disconnected) {
            Log.d(TAG, "서비스 시작됨: 즉시 태그리스 스캔 시작")
            startTaglessScan()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext.cancelChildren()
        bleClientManager.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun observeConnectionState() {
        bleClientManager.connectionState.onEach { state ->
            when (state) {
                is ConnectionState.Disconnected -> {
                    Log.d(TAG, "연결 끊김 -> 태그리스 스캔 시작")
                    updateNotification("연결 대기 중... (Tagless Scan)")
                    startTaglessScan()
                }

                is ConnectionState.Connecting -> {
                    updateNotification("연결 시도 중...")
                    stopTaglessScan()
                }

                is ConnectionState.Connected -> {
                    val name = state.device.name ?: "Unknown"
                    Log.d(TAG, "연결 성공: $name")
                    updateNotification("RideSync 연결됨: $name")
                    stopTaglessScan()
                }

                is ConnectionState.Error -> startTaglessScan()
            }
        }.launchIn(serviceScope)
    }

    private fun startTaglessScan() {
        // 이미 스캔 중이면 중복 실행 방지
        if (scanJob?.isActive == true) return

        scanJob = serviceScope.launch {
            try {
                withTimeout(30_000L) {
                    bleScanManager.startTaglessScan("Hanto", thresholdRssi = -80)
                        .collect { resource ->
                            if (resource is Resource.Success) {
                                Log.d(TAG, "기기 발견! 자동 연결 시도: ${resource.data.name}")
                                bleClientManager.connect(resource.data.device)
                            } else if (resource is Resource.Error) {
                                Log.e(TAG, "스캔 중 에러: ${resource.message}")
                            }
                        }
                }
            } catch (e: TimeoutCancellationException) {
                updateNotification("기기 탐색 실패 (대기 모드)")
                stopTaglessScan()
            } catch (e: Exception) {
                Log.e(TAG, "스캔 중 알 수 없는 오류", e)
                stopTaglessScan()
            }
        }
    }

    private fun stopTaglessScan() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
            scanJob = null
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RideSync Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RideSync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}