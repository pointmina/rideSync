package com.hanto.ridesync.ble.scanner

import android.bluetooth.BluetoothDevice

data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int // 신호 강도 (거리에 따라 UI 정렬할 때 필수)
)