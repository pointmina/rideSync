package com.hanto.ridesync

import android.app.Application
import android.content.Intent
import android.os.Build
import com.hanto.ridesync.service.RideSyncService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RideSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 앱 실행 시 백그라운드 서비스 즉시 가동
        startRideSyncService()
    }

    private fun startRideSyncService() {
        val intent = Intent(this, RideSyncService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 오레오(API 26) 이상에서는 startForegroundService 사용 필수
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}