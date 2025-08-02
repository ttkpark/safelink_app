# 질문
```text
작업자 모드에서 어떻게 하면 기기 연결 관리를 할 수 있을까? 기기 연결하는 서비스의 lifecycle는 어떻게 되야 하고, 센서데이터는 어느 경로를 통해 화면에 보여지며, 그 데이터는 어느 lifecycle를 가지며 언제까지 유효할까?
서비스 흐름을 설계해줘
```

# 답
작업자 모드에서의 기기 연결 관리와 데이터 흐름을 설계해드리겠습니다. 현재 프로젝트 구조를 분석하여 최적의 서비스 아키텍처를 제안하겠습니다.

## �� 작업자 모드 서비스 흐름 설계

### 📱 **1. 전체 아키텍처 개요**

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  Service Layer  │    │  Data Layer     │
│                 │    │                 │    │                 │
│ SensorMonitor   │◄──►│ BluetoothService│◄──►│ ESP32C6 Device  │
│ Fragment        │    │ (Foreground)    │    │                 │
│                 │    │                 │    │                 │
│ DeviceDiscovery │◄──►│ BleManager      │◄──►│ Firebase DB     │
│ Fragment        │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### �� **2. 서비스 Lifecycle 설계**

#### **BluetoothService (Foreground Service)**
```kotlin
class BluetoothService : Service() {
    
    // Lifecycle: onCreate → onStartCommand → onDestroy
    // 지속 시간: 앱이 실행되는 동안 또는 명시적 중지까지
    
    override fun onCreate() {
        super.onCreate()
        // 1. 알림 채널 생성
        // 2. Foreground Service 시작
        // 3. BleManager 초기화
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 연결 요청 처리
        // 2. 데이터 수신 시작
        // 3. 백그라운드에서 계속 실행
        return START_STICKY // 서비스가 죽어도 재시작
    }
    
    override fun onDestroy() {
        // 1. 모든 연결 해제
        // 2. 리소스 정리
        super.onDestroy()
    }
}
```

#### **BleManager (BLE 통신 관리)**
```kotlin
class BleManager(private val context: Context) {
    
    // Lifecycle: initialize → connect → dataReceive → disconnect
    // 지속 시간: 연결 요청부터 명시적 해제까지
    
    fun initialize(): Boolean {
        // BLE 어댑터 초기화
        // 권한 확인
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        // GATT 연결
        // 서비스 발견
        // 특성 구독
    }
    
    fun disconnect() {
        // 연결 해제
        // 리소스 정리
    }
}
```

### �� **3. 데이터 흐름 경로**

#### **센서 데이터 수신 경로**
```
ESP32C6 Device
    ↓ (BLE GATT)
BleManager.onCharacteristicChanged()
    ↓ (콜백)
BluetoothService.onSensorDataReceived()
    ↓ (StateFlow)
SensorMonitorFragment.sensorDataFlow
    ↓ (UI 업데이트)
RecyclerView/TextView 업데이트
```

#### **연결 상태 관리 경로**
```
BleManager.onConnectionStateChange()
    ↓ (콜백)
BluetoothService.onConnectionStatusChanged()
    ↓ (StateFlow)
SensorMonitorFragment.connectionStateFlow
    ↓ (UI 업데이트)
연결 상태 표시 업데이트
```

### ⏰ **4. 데이터 Lifecycle 및 유효성**

#### **실시간 센서 데이터**
```kotlin
data class SensorData(
    val deviceId: String,
    val timestamp: Long,        // 생성 시간
    val heartRate: Int,
    val bodyTemperature: Float,
    val humidity: Float,
    // ...
) {
    // 유효 기간: 30초 (센서 데이터 갱신 주기)
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > 30000
    }
    
    // 위험도 판정
    fun getRiskLevel(): RiskLevel {
        return when {
            heartRate > 100 || heartRate < 60 -> RiskLevel.WARNING
            bodyTemperature > 37.5f || bodyTemperature < 35.0f -> RiskLevel.DANGER
            humidity < 30.0f || humidity > 70.0f -> RiskLevel.WARNING
            else -> RiskLevel.SAFE
        }
    }
}
```

#### **연결 상태 데이터**
```kotlin
data class ConnectionStatus(
    val isConnected: Boolean,
    val deviceName: String?,
    val deviceAddress: String?,
    val lastConnectedTime: Long,
    val signalStrength: Int
) {
    // 유효 기간: 연결이 유지되는 동안
    // 연결 해제 시 즉시 무효화
}
```

### 🔄 **5. 구체적인 서비스 흐름**

#### **A. 앱 시작 시**
```kotlin
// 1. MainActivity에서 사용자 모드 선택
// 2. SensorMonitorFragment 생성
// 3. BluetoothService 시작 (Foreground)
// 4. BleManager 초기화
```

#### **B. 기기 연결 시**
```kotlin
// 1. 사용자가 "ESP32C6 연결" 버튼 클릭
// 2. DeviceDiscoveryFragment로 이동
// 3. BLE 스캔 시작
// 4. ESP32C6 디바이스 발견 및 연결
// 5. 서비스 발견 및 특성 구독
// 6. 실시간 데이터 수신 시작
```

#### **C. 데이터 수신 시**
```kotlin
// 1. ESP32C6에서 센서 데이터 전송
// 2. BleManager.onCharacteristicChanged() 호출
// 3. 데이터 파싱 및 검증
// 4. BluetoothService.sensorDataFlow 업데이트
// 5. SensorMonitorFragment UI 업데이트
// 6. 위험도 판정 및 알림 (필요시)
```

#### **D. 앱 종료 시**
```kotlin
// 1. 사용자가 "연결 해제" 버튼 클릭
// 2. BleManager.disconnect() 호출
// 3. BluetoothService 중지
// 4. 모든 리소스 정리
```

### �� **6. 구현 제안**

#### **BluetoothService 개선**
```kotlin
class BluetoothService : Service() {
    
    private val _sensorDataFlow = MutableStateFlow<SensorData?>(null)
    val sensorDataFlow: StateFlow<SensorData?> = _sensorDataFlow.asStateFlow()
    
    private val _connectionStateFlow = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStateFlow: StateFlow<ConnectionStatus> = _connectionStateFlow.asStateFlow()
    
    private lateinit var bleManager: BleManager
    
    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        setupBleCallbacks()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    private fun setupBleCallbacks() {
        bleManager.onSensorDataReceived = { sensorData ->
            _sensorDataFlow.value = sensorData
        }
        
        bleManager.onConnected = {
            _connectionStateFlow.value = ConnectionStatus.Connected(
                deviceName = bleManager.getConnectedDeviceName(),
                deviceAddress = bleManager.getConnectedDeviceAddress()
            )
        }
        
        bleManager.onDisconnected = {
            _connectionStateFlow.value = ConnectionStatus.Disconnected
        }
    }
    
    fun connectToDevice(deviceAddress: String) {
        // 연결 로직
    }
    
    fun disconnect() {
        bleManager.disconnect()
    }
}
```

#### **SensorMonitorFragment 개선**
```kotlin
class SensorMonitorFragment : Fragment() {
    
    private val bluetoothService: BluetoothService by lazy {
        // 서비스 바인딩 또는 ViewModel을 통한 접근
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 센서 데이터 관찰
        lifecycleScope.launch {
            bluetoothService.sensorDataFlow.collect { sensorData ->
                sensorData?.let { updateSensorUI(it) }
            }
        }
        
        // 연결 상태 관찰
        lifecycleScope.launch {
            bluetoothService.connectionStateFlow.collect { status ->
                updateConnectionUI(status)
            }
        }
    }
    
    private fun connectToSensor() {
        // 디바이스 발견 화면으로 이동
        findNavController().navigate(R.id.action_to_device_discovery)
    }
    
    private fun disconnectFromSensor() {
        bluetoothService.disconnect()
    }
}
```

### 📋 **7. 권장사항**

#### **A. 서비스 지속성**
- **BluetoothService**: Foreground Service로 구현하여 앱이 백그라운드에 있어도 연결 유지
- **BleManager**: 서비스 내부에서 관리하여 연결 상태 유지

#### **B. 데이터 관리**
- **실시간 데이터**: StateFlow를 통한 반응형 UI 업데이트
- **히스토리 데이터**: Firebase에 저장하여 장기 보관
- **로컬 캐시**: SharedPreferences에 최근 연결 정보 저장

#### **C. 에러 처리**
- **연결 실패**: 자동 재연결 시도 (최대 3회)
- **데이터 손실**: 타임아웃 기반 데이터 유효성 검증
- **권한 문제**: 런타임 권한 요청 및 안내

#### **D. 성능 최적화**
- **데이터 필터링**: 유효하지 않은 데이터 필터링
- **UI 업데이트**: 디바운싱을 통한 과도한 UI 업데이트 방지
- **메모리 관리**: 오래된 데이터 자동 정리

이 설계를 통해 작업자 모드에서 안정적이고 효율적인 기기 연결 관리와 데이터 흐름을 구현할 수 있습니다!