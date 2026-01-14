# RideSync: BLE-Based Mobility Audio Dashboard

안드로이드 기반 BLE(Bluetooth Low Energy) 통신 및 오디오 멀티태스킹 시뮬레이션 앱


## Tech Stack

* **Language**: Kotlin
* **Architecture**: MVVM, Clean Architecture (Simplified)
* **DI**: Hilt (Dagger)
* **Asynchronous**: Coroutines, Flow
* **Jetpack**: ViewModel, Lifecycle, ViewBinding
* **Media**: Media3 ExoPlayer, TTS (Text-To-Speech)
* **Bluetooth**: Android BLE API (GATT)

---

## Project Structure

```text
com.hanto.ridesync
├── ble
│   ├── client      # BLE GATT Client & Connection Management
│   └── scanner     # Bluetooth Device Scanner (Flow-based)
├── ui
│   ├── scan        # Device Discovery & List View
│   └── dashboard   # Main Control & Audio Engine View
└── data
    └── repository  # Abstraction layer for BLE operations

```

---

## Key Technical Challenges & Solutions

### BLE Connection Stability (GATT 133 Error)

* **Issue**: 기기 스캔과 연결 시도가 동시에 발생할 때 라디오 자원 충돌로 인해 연결이 실패하는 현상 확인.
* **Solution**: 연결 시도 직전 활성화된 스캔 작업을 강제 종료하고, 명시적인 리소스 해제)를 보장하는 라이프사이클 관리 로직 구현.

### Seamless Audio UX (Ducking)

* **Issue**: 단순 볼륨 조절 방식은 타 미디어 앱(유튜브, 티맵 등)과의 오디오 점유권 충돌 유발.
* **Solution**: 안드로이드 표준 AudioFocusRequest를 준수하고, 인터컴 음성 성격에 맞는 USAGE_VOICE_COMMUNICATION 속성을 정의하여 시스템 레벨의 매끄러운 볼륨 감쇄 로직 완성.

### Automated Recovery

* **Issue**: 라이더는 주행 중 스마트폰 조작이 불가능하여 연결 유실 시 치명적인 정보 단절 발생.
* **Solution**: 플래그를 통해 의도적 해제와 돌발 해제를 구분하고, 코루틴을 활용한 자동 재연결(3회 시도) 프로세스 구축.

---

