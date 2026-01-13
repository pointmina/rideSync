package com.hanto.ridesync.ble.client

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 * BLE 명령 Sealed Class
 * (읽기, 쓰기, 디스크립터 쓰기 등)
 */
sealed class BleCommand {
    // 1. 특성 읽기 (Read)
    data class Read(
        val characteristic: BluetoothGattCharacteristic
    ) : BleCommand()

    // 2. 특성 쓰기 (Write)
    data class Write(
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        val writeType: Int
    ) : BleCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Write
            if (characteristic != other.characteristic) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = characteristic.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    // 3. 디스크립터 쓰기 (Notification 활성화 시 사용)
    data class WriteDescriptor(
        val descriptor: BluetoothGattDescriptor,
        val data: ByteArray
    ) : BleCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WriteDescriptor
            if (descriptor != other.descriptor) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = descriptor.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}