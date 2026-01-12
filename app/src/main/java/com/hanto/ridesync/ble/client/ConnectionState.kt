package com.hanto.ridesync.ble.client

import android.bluetooth.BluetoothDevice

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    data class Connected(val device: BluetoothDevice) : ConnectionState
    data class Error(val message: String) : ConnectionState
}