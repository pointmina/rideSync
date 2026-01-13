package com.hanto.ridesync.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanto.ridesync.ble.client.ConnectionState
import com.hanto.ridesync.ble.scanner.ScannedDevice
import com.hanto.ridesync.common.Resource
import com.hanto.ridesync.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: BleRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()
    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    // 연결 상태 노출
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private var scanJob: Job? = null

    fun startScan() {
        // 1. 상태 초기화
        _scanState.value = ScanUiState.Scanning(emptyList())
        deviceMap.clear()

        // 2. 기존 스캔 작업이 있다면 취소
        scanJob?.cancel()

        // 3. 새 스캔 작업 시작
        scanJob = viewModelScope.launch {

            launch {
                repository.scanDevices().collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val newDevice = resource.data
                            deviceMap[newDevice.address] = newDevice
                        }
                        is Resource.Error -> {
                            _scanState.value = ScanUiState.Error(resource.message)
                        }
                        is Resource.Loading -> {
                            // 로딩 상태
                        }
                    }
                }
            }

            while (isActive) {
                if (deviceMap.isNotEmpty()) {
                    // RSSI(신호 세기)가 강한 순서대로 정렬하여 UI 업데이트
                    val sortedList = deviceMap.values.sortedByDescending { it.rssi }
                    _scanState.value = ScanUiState.Scanning(sortedList)
                }
                delay(500L)
            }
        }
    }

    fun connectToDevice(device: ScannedDevice) {
        stopScan()
        repository.connect(device.device)
    }

    fun disconnectDevice() {
        repository.disconnect()
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanUiState.Idle
    }
}

sealed interface ScanUiState {
    object Idle : ScanUiState
    data class Scanning(val devices: List<ScannedDevice>) : ScanUiState
    data class Error(val message: String) : ScanUiState
}