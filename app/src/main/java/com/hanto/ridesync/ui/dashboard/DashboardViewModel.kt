package com.hanto.ridesync.ui.dashboard

import androidx.lifecycle.ViewModel
import com.hanto.ridesync.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: BleRepository
) : ViewModel() {

    fun disconnectDevice() {
        repository.disconnect()
    }
}