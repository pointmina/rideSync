package com.hanto.ridesync.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hanto.ridesync.ble.client.ConnectionState
import com.hanto.ridesync.databinding.ActivityScanBinding
import com.hanto.ridesync.service.RideSyncService
import com.hanto.ridesync.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startForegroundService()
            viewModel.startScan()
        } else {
            Toast.makeText(this, "Permissions are required...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        checkPermissionsAndStartServiceOnly()
    }

    private fun checkPermissionsAndStartServiceOnly() {
        // 1. Android 버전에 맞는 권한 목록 정의
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // 2. 권한이 없는 게 있는지 확인
        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            // [A] 모든 권한이 이미 있다면 -> 서비스 즉시 시작
            startForegroundService()
        } else {
            // [B] 권한이 하나라도 없다면 -> 권한 요청 팝업 띄우기 (이 부분이 빠져 있었습니다!)
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { scannedDevice ->
            Toast.makeText(this, "Selected: ${scannedDevice.name}", Toast.LENGTH_SHORT).show()
            viewModel.connectToDevice(scannedDevice)
        }
        binding.rvDevices.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(this@ScanActivity)
        }
    }

    private fun setupListeners() {
        binding.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }
    }

    private fun observeViewModel() {

        // 1. 스캔 상태 관찰
        lifecycleScope.launch {
            viewModel.scanState.collect { state ->
                when (state) {
                    is ScanUiState.Idle -> {
                        binding.progressBar.isVisible = false
                    }

                    is ScanUiState.Scanning -> {
                        binding.progressBar.isVisible = true
                        deviceAdapter.submitList(state.devices)
                    }

                    is ScanUiState.Error -> {
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@ScanActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // 2. 연결 상태 관찰 (신규 추가)
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        // 권한 체크 후 안전하게 이름 가져오기
                        val deviceName = if (ActivityCompat.checkSelfPermission(
                                this@ScanActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            state.device.name ?: "Unknown Device"
                        } else {
                            "Unknown Device"
                        }

                        Toast.makeText(
                            this@ScanActivity,
                            "Connected to $deviceName",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 대시보드 화면으로 이동
                        navigateToDashboard(state.device.address, deviceName)
                    }
                    is ConnectionState.Disconnected -> { /* 처리 로직 */ }
                    is ConnectionState.Connecting -> { /* 로딩 로직 */ }
                    is ConnectionState.Error -> {
                        Toast.makeText(this@ScanActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun navigateToDashboard(deviceAddress: String, deviceName: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            // 다음 화면에서 사용할 기기 정보를 넘겨줍니다.
            putExtra("DEVICE_ADDRESS", deviceAddress)
            putExtra("DEVICE_NAME", deviceName)
        }
        startActivity(intent)
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 11 이하
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // 권한이 없는 게 하나라도 있으면 요청
        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            startForegroundService()
            viewModel.startScan()
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, RideSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}