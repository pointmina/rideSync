// 경로: ridesync/service/RideSyncService.kt
package com.hanto.ridesync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hanto.ridesync.R
import com.hanto.ridesync.ble.client.BleClientManager
import com.hanto.ridesync.ble.scanner.BleScanManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RideSyncService : Service() {

    @Inject
    lateinit var bleScanManager: BleScanManager
    @Inject
    lateinit var bleClientManager: BleClientManager

    companion object {
        const val CHANNEL_ID = "RideSync_Channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 서비스가 시작되면 즉시 알림을 띄워 '포그라운드' 상태로 전환
        startForeground(NOTIFICATION_ID, createNotification())

        // START_STICKY: 시스템이 강제로 종료시켜도, 메모리가 확보되면 다시 살려냄
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RideSync Active")
            .setContentText("태그리스 모드가 실행 중입니다.")
            .setSmallIcon(R.mipmap.ic_launcher) // 아이콘이 없다면 기본 아이콘 사용
            .setPriority(NotificationCompat.PRIORITY_LOW) // 중요도 낮음 (소리 X)
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