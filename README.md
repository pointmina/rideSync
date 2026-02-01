# RideSync: BLE-Based Mobility Audio Dashboard

별도의 조작 없이 바이크 시동을 걸면(부팅 시) 자동으로 인터컴 기기를 탐색하여 연결하고, 주행 중 음악 감상 시 인터컴 음성이 들리면 자동으로 음악 볼륨을 줄여주는(Audio Ducking) 안전 편의 기능을 제공합니다.

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=flat&logo=kotlin) ![Android](https://img.shields.io/badge/Android-MinSdk%2026%2B-green?style=flat&logo=android) ![Hilt](https://img.shields.io/badge/DI-Hilt-orange) ![Coroutines](https://img.shields.io/badge/Async-Coroutines%20%26%20Flow-blue)

---

## 핵심 기능 (Key Features)

### 1. 태그리스 자동 연결 
* **Boot-to-Connect:** 스마트폰 재부팅 시 `BootReceiver`를 통해 백그라운드 서비스가 자동으로 시작됩니다.
* **Smart Scanning:** 특정 기기명과 신호 강도(RSSI -80dBm 이상)를 감지하여, 라이더가 바이크에 가까이 갔을 때만 연결을 시도합니다.
* **Battery Saver:** 30초 내에 기기를 찾지 못하면 스캔을 자동으로 중단하여 배터리 방전을 방지합니다.

### 2. 오디오 제어
* **Audio Ducking:** 음악 재생 중 인터컴 수신 시, 음악을 멈추지 않고 볼륨만 자연스럽게 줄여줍니다.
* **Focus Management:** 인터컴 종료 시 자동으로 음악 볼륨이 원상 복구됩니다.
* **Legacy Support:** Android 8.0(Oreo) 이상의 `AudioFocusRequest`와 구형 API를 모두 지원합니다.

### 3. 연결 유지
* **Foreground Service:** 안드로이드 시스템에 의해 앱이 강제 종료되는 것을 방지하기 위해 포그라운드 서비스로 동작합니다.
* **Command Queue:** BLE 명령어(Read/Write)를 큐(Queue) 방식으로 순차 처리하여 데이터 손실을 방지합니다.
* **Auto Reconnect:** 연결이 의도치 않게 끊길 경우, 안전한 재연결 시퀀스를 수행합니다.

---

## 기술 스택 (Tech Stack)

* **Language:** Kotlin
* **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture Principles
* **Concurrency:** Coroutines, Flow (StateFlow, SharedFlow)
* **Dependency Injection:** Dagger Hilt
* **Android Components:**
    * `BluetoothLeScanner` & `BluetoothGatt` (BLE)
    * `Foreground Service` & `BroadcastReceiver` (Background Processing)
    * `AudioManager` (Audio Focus & Ducking)
    * `ExoPlayer` & `TextToSpeech` (Media & Simulation)

---

## 프로젝트 구조 (Project Structure)

```text
com.hanto.ridesync
├── ble
│   ├── client      # BLE GATT Client & Connection Management (Singleton)
│   └── scanner     # Bluetooth Device Scanner & Filtering Logic
├── data
│   └── repository  # 데이터 레이어 추상화 (Repository Pattern)
├── di              # Hilt 의존성 주입 모듈 (DI Modules)
├── receiver        # 부팅 감지 리시버 (BootReceiver)
├── service         # 백그라운드 연결 유지 서비스 (RideSyncService)
├── ui
│   ├── dashboard   # 메인 컨트롤 & 오디오 엔진 (Activity, ViewModel)
│   └── scan        # 기기 검색 및 수동 연결 화면
└── util            # 확장 함수 및 유틸리티
