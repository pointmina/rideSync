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

    }
}