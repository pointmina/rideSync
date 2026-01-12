package com.hanto.ridesync.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hanto.ridesync.R
import com.hanto.ridesync.databinding.ActivityScanBinding
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
            viewModel.startScan()
        } else {
            Toast.makeText(this, "BLE permissions are required to scan devices", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Edge-to-Edge 활성화 (가장 먼저 호출)
        enableEdgeToEdge()

        // 2. ViewBinding 초기화
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. 시스템 바(상태바/네비바) 인셋 적용
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            Toast.makeText(this, "Selected: ${device.name}", Toast.LENGTH_SHORT).show()
            // 추후 연결 로직 구현
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
    }

    private fun checkPermissionsAndScan() {
        // Android 12 (S) 이상 대응
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION // 일부 기기 호환성 위해
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
            viewModel.startScan()
        }
    }
}