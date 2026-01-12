package com.hanto.ridesync

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RideSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}