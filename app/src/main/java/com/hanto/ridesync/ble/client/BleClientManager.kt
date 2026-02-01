package com.hanto.ridesync.ble.client

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var isUserInitiatedDisconnect = false // 사용자 의도 확인 플래그

    // 재연결을 위한 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    // 명령을 쌓아둘 큐
    private val commandQueue: Queue<BleCommand> = ConcurrentLinkedQueue()

    // 현재 명령이 수행 중인지 확인하는 플래그
    @Volatile
    private var isBusy = false

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isUserInitiatedDisconnect = false
                reconnectJob?.cancel()
                lastConnectedDevice = gatt.device
                _connectionState.value = ConnectionState.Connected(gatt.device)
                gatt.discoverServices() // 서비스 발견 시작
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
                if (!isUserInitiatedDisconnect) {
                    startReconnectionProcess()
                } else {
                    close()
                }
            }
        }

        // 서비스 발견 완료 시 호출
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleClient", "Services Discovered. Reading Battery...")
                // TODO 추후 개발 예정.
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                _batteryLevel.value = level
                Log.d("BleClient", "Battery Level Read: $level%")
            }

            isBusy = false
            processNextCommand()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            isBusy = false
            processNextCommand()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            isBusy = false
            processNextCommand()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                _batteryLevel.value = level
                Log.d("BleClient", "Battery Level Changed: $level%")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startReconnectionProcess() {
        val device = lastConnectedDevice ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting

            // 재연결 시도 (최대 3번, 5초 간격)
            repeat(3) { attempt ->
                Log.d("BleClient", "Reconnection attempt ${attempt + 1}")
                delay(5000) // 5초 대기

                if (bluetoothGatt != null) close()
                bluetoothGatt = device.connectGatt(context, false, gattCallback)

                // 연결 시도 후 잠시 대기하며 상태 변화를 관찰
                delay(2000)
                if (_connectionState.value is ConnectionState.Connected) return@launch
            }

            // 3번 모두 실패 시 에러 상태로 변경
            _connectionState.value = ConnectionState.Error("Automatic reconnection failed.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        isUserInitiatedDisconnect = false
        lastConnectedDevice = device
        _connectionState.value = ConnectionState.Connecting
        if (bluetoothGatt != null) close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isUserInitiatedDisconnect = true
        reconnectJob?.cancel()

        // 이미 연결된 상태라면 disconnect() 호출 후 콜백을 기다림
        if (bluetoothGatt != null && _connectionState.value is ConnectionState.Connected) {
            bluetoothGatt?.disconnect()
        } else {
            // 연결이 안 된 상태라면 바로 리소스 정리
            close()
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun close() {
        Log.d("BleClient", "GATT 리소스 해제 (close)")
        commandQueue.clear()
        isBusy = false
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enqueueCommand(command: BleCommand) {
        commandQueue.add(command)
        processNextCommand()
    }

    // 큐에서 다음 명령을 꺼내서 실행
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Synchronized
    private fun processNextCommand() {
        if (isBusy) {
            Log.d("BleClientManager", "Busy state, waiting for callback...")
            return
        }

        val command = commandQueue.poll() ?: return // 큐가 비었으면 종료
        val gatt = bluetoothGatt ?: run {
            Log.e("BleClientManager", "Gatt is null")
            commandQueue.clear()
            return
        }

        isBusy = true // 실행 시작

        val result = when (command) {
            is BleCommand.Read -> {
                Log.d("BleClientManager", "Processing Read: ${command.characteristic.uuid}")
                gatt.readCharacteristic(command.characteristic)
            }

            is BleCommand.Write -> {
                Log.d("BleClientManager", "Processing Write: ${command.characteristic.uuid}")
                command.characteristic.value = command.data
                command.characteristic.writeType = command.writeType
                gatt.writeCharacteristic(command.characteristic)
            }

            is BleCommand.WriteDescriptor -> {
                Log.d("BleClientManager", "Processing WriteDescriptor: ${command.descriptor.uuid}")
                command.descriptor.value = command.data
                gatt.writeDescriptor(command.descriptor)
            }
        }

        // 명령 실행 자체를 실패했을 경우 (거의 없지만 방어 코드)
        if (!result) {
            Log.e("BleClientManager", "Command execution failed internally")
            isBusy = false
            processNextCommand() // 다음 명령 시도
        }
    }
}