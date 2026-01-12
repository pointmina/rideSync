package com.hanto.ridesync.common

object Constants {
    // 디바이스 시뮬레이션을 위한 가상의 Service UUID
    // 실제로는 장비의 특정 UUID를 넣어야 필터링이 됩니다.
    const val HANTO_SERVICE_UUID = "00001800-0000-1000-8000-00805f9b34fb" // Generic Access Service 예시

    // 스캔 타임아웃 (10초)
    const val SCAN_PERIOD = 10000L
}