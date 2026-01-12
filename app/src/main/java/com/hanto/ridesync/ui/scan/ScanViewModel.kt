package com.hanto.ridesync.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanto.ridesync.ble.scanner.ScannedDevice
import com.hanto.ridesync.common.Resource
import com.hanto.ridesync.data.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: BleRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    // 중복 제거 및 업데이트를 위한 로컬 캐시 (MAC 주소 -> Device)
    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    fun startScan() {
        _scanState.value = ScanUiState.Scanning(emptyList())
        deviceMap.clear()

        viewModelScope.launch {
            repository.scanDevices().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val newDevice = resource.data
                        deviceMap[newDevice.address] = newDevice

                        // RSSI(신호 세기)가 강한 순서대로 정렬하여 UI 업데이트
                        val sortedList = deviceMap.values.sortedByDescending { it.rssi }
                        _scanState.value = ScanUiState.Scanning(sortedList)
                    }
                    is Resource.Error -> {
                        _scanState.value = ScanUiState.Error(resource.message)
                    }
                    is Resource.Loading -> {
                        // 초기 로딩 상태 처리 (필요 시)
                    }
                }
            }
        }
    }
}

// UI 상태 정의 (Sealed Interface)
sealed interface ScanUiState {
    object Idle : ScanUiState
    data class Scanning(val devices: List<ScannedDevice>) : ScanUiState
    data class Error(val message: String) : ScanUiState
}