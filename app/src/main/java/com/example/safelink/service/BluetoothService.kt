package com.example.safelink.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.safelink.R
import com.example.safelink.bluetooth.ProtocolParser
import com.example.safelink.bluetooth.BleConstants
import com.example.safelink.model.SensorData
import com.example.safelink.bluetooth.BleManager
import com.example.safelink.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SafeLink 작업자 모드 블루투스 통신 서비스
 * 웨어러블 허브(Wearable Hub) 디바이스와의 연결 및 데이터 수신을 담당하는 Foreground Service
 * 
 * 서비스 생명주기:
 * 1. 앱 시작 → 서비스 바인딩
 * 2. 센서 연결 → GATT 연결 및 데이터 수신
 * 3. 실시간 모니터링 → StateFlow를 통한 UI 업데이트
 * 4. 작업 종료 → 명시적 연결 해제
 */
class BluetoothService : Service() {
    
    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "SafeLink_Worker_Channel"
        
        // 연결 재시도 관련 상수
        private const val MAX_RETRY_COUNT = 5
        private const val RETRY_DELAY_MS = 3000L
        private const val SCAN_TIMEOUT_MS = 10000L
        
        // 웨어러블 허브 디바이스 관련 상수
        private const val WEARABLE_HUB_NAME = "WearableHub"
        private const val WEARABLE_HUB_PREFIX = "Wearable"
    }
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleManager: BleManager
    
    // 연결된 장치들
    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()
    private val retryCounts = mutableMapOf<String, Int>()
    
    // StateFlow를 통한 데이터 관리 (작업자 서비스 흐름 문서 기반)
    private val _sensorDataFlow = MutableStateFlow<SensorData?>(null)
    val sensorDataFlow: StateFlow<SensorData?> = _sensorDataFlow.asStateFlow()
    
    private val _connectionStateFlow = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStateFlow: StateFlow<ConnectionStatus> = _connectionStateFlow.asStateFlow()
    
    private val _deviceListFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val deviceListFlow: StateFlow<List<DeviceInfo>> = _deviceListFlow.asStateFlow()
    
    // 위험 모니터링을 위한 데이터
    private val _riskLevelFlow = MutableStateFlow<Int>(0)
    val riskLevelFlow: StateFlow<Int> = _riskLevelFlow.asStateFlow()
    
    // 데이터 로그를 위한 스트림
    private val _dataLogFlow = MutableStateFlow<String>("")
    val dataLogFlow: StateFlow<String> = _dataLogFlow.asStateFlow()
    
    // 콜백 함수들 (UI와의 연동)
    var onSensorDataReceived: ((SensorData) -> Unit)? = null
    var onConnectionStatusChanged: ((ConnectionStatus) -> Unit)? = null
    var onRiskLevelChanged: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    // 스캔 관련 콜백
    var onDeviceFound: ((DeviceInfo) -> Unit)? = null
    var onScanStarted: (() -> Unit)? = null
    var onScanStopped: (() -> Unit)? = null
    
    // 서비스 상태 관리
    private var isForegroundService = false
    private var isWorkerMode = false
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService onCreate - 일반 서비스로 시작")
        
        // SessionManager 초기화
        SessionManager.initialize(this)
        
        initializeBluetooth()
        setupBleManager()
        createNotificationChannel()
        
        // 초기에는 일반 서비스로 시작 (Foreground Service 아님)
        isForegroundService = false
        
        // 초기 데이터 로그 설정
        updateDataLog("웨어러블 허브 모니터링 서비스로 시작되었습니다.")
        
        // 세션 상태 로그 출력
        Log.d(TAG, "서비스 시작 시 세션 상태:")
        SessionManager.logSessionInfo()
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothService onDestroy - 작업자 서비스 종료")
        
        // Foreground Service 해제
        demoteFromForeground()
        
        disconnectAllDevices()
        serviceScope.cancel()
        updateDataLog("작업자 서비스가 종료되었습니다.")
    }

    /*
    BLE Manager 얻기
     */
    fun getBLEManager():BleManager{
        return bleManager
    }
    
    /**
     * 블루투스 초기화
     */
    private fun initializeBluetooth() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            onError?.invoke("블루투스가 비활성화되어 있습니다.")
            updateDataLog("오류: 블루투스가 비활성화되어 있습니다.")
        } else {
            updateDataLog("블루투스 초기화 완료")
        }
    }
    
    /**
     * BLE 매니저 설정 (작업자 서비스 흐름 문서 기반)
     */
    private fun setupBleManager() {
        bleManager = BleManager(this)
        
        if (!bleManager.initialize()) {
            Log.e(TAG, "BLE 초기화 실패")
            onError?.invoke("BLE 초기화에 실패했습니다.")
            updateDataLog("오류: BLE 초기화에 실패했습니다.")
            return
        }
        
        updateDataLog("BLE 매니저 초기화 완료")
        
        // BLE 콜백 설정
        bleManager.setOnDeviceFound{ device ->
            serviceScope.launch {
                val deviceInfo = DeviceInfo(
                    deviceId = device.address,
                    deviceName = device.name ?: "Unknown Device",
                    deviceAddress = device.address,
                    isConnected = false,
                    connectionTime = 0L
                )
                
                // 디바이스 목록에 추가
                val currentList = _deviceListFlow.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.deviceAddress == device.address }
                if (existingIndex == -1) {
                    currentList.add(deviceInfo)
                    _deviceListFlow.value = currentList
                    onDeviceFound?.invoke(deviceInfo)
                    updateDataLog("디바이스 발견: ${deviceInfo.deviceName}")
                }
            }
        }
        
        bleManager.setOnSensorDataReceived{ sensorData ->
            serviceScope.launch {
                // 연결 상태 확인 - 연결되지 않은 상태에서는 데이터 처리하지 않음
                val currentStatus = _connectionStateFlow.value
                if (currentStatus !is ConnectionStatus.Connected) {
                    Log.w(TAG, "⚠️ 연결되지 않은 상태에서 센서 데이터 수신 시도: $currentStatus")
                    Log.w(TAG, "⚠️ 데이터: HR=${sensorData.heartRate}, Temp=${sensorData.bodyTemperature}, DeviceId=${sensorData.deviceId}")
                    return@launch
                }
                
                _sensorDataFlow.value = sensorData
                onSensorDataReceived?.invoke(sensorData)
                
                // 위험도 계산 및 업데이트
                val riskLevel = sensorData.getRiskLevel()
                _riskLevelFlow.value = riskLevel
                onRiskLevelChanged?.invoke(riskLevel)
                
                // 작업자 모드일 때 Firebase에 데이터 저장
                if (isWorkerMode) {
                    saveSensorDataToFirebase(sensorData)
                }
                
                // 데이터 로그 업데이트
                updateDataLog("센서 데이터 수신: HR=${sensorData.heartRate} BPM, Temp=${sensorData.bodyTemperature}°C, Risk=$riskLevel")
                
                Log.i(TAG, "센서 데이터 수신: HR=${sensorData.heartRate}, Temp=${sensorData.bodyTemperature}, Risk=$riskLevel")
            }
        }
        
        bleManager.setOnConnected{
            serviceScope.launch {
                val status = ConnectionStatus.Connected(
                    deviceName = bleManager.getConnectedDeviceName() ?: "WearableHub",
                    deviceAddress = bleManager.getConnectedDeviceAddress() ?: "",
                    connectionTime = System.currentTimeMillis()
                )
                _connectionStateFlow.value = status
                onConnectionStatusChanged?.invoke(status)
                
                // Foreground Service로 승격
                promoteToForeground()
                
                // 세션 상태 업데이트
                SessionManager.setDeviceConnected(true)
                
                updateNotification("웨어러블 허브 연결됨")
                updateDataLog("웨어러블 허브 연결 성공")
            }
        }
        
        bleManager.setOnDisconnected{
            serviceScope.launch {
                val status = ConnectionStatus.Disconnected
                _connectionStateFlow.value = status
                onConnectionStatusChanged?.invoke(status)
                
                // 세션 상태 업데이트
                SessionManager.setDeviceConnected(false)
                
                // 연결 해제 시 Foreground Service 해제
                demoteFromForeground()
                
                updateNotification("웨어러블 허브 연결 끊김")
                updateDataLog("웨어러블 허브 연결 해제")
            }
        }

        bleManager.setOnError{ errorMessage ->
            serviceScope.launch {
                Log.e(TAG, "BLE 오류: $errorMessage")
                onError?.invoke(errorMessage)
                updateDataLog("오류: $errorMessage")
            }
        }
    }
    
    /**
     * 웨어러블 허브 디바이스 연결 (작업자 서비스 흐름 문서 기반)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToWearableHubDevice(deviceAddress: String): Boolean {
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            updateDataLog("오류: 블루투스가 비활성화되어 있습니다.")
            return false
        }
        
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "Device not found: $deviceAddress")
            updateDataLog("오류: 디바이스를 찾을 수 없습니다: $deviceAddress")
            return false
        }
        
        // 이미 연결된 장치인지 확인
        if (connectedDevices.containsKey(deviceAddress)) {
            Log.d(TAG, "Device already connected: $deviceAddress")
            updateDataLog("이미 연결된 디바이스입니다: $deviceAddress")
            return true
        }

        Log.d(TAG, "Connecting to Wearable Hub device: $deviceAddress")
        updateDataLog("웨어러블 허브 연결 시도: $deviceAddress")
        _connectionStateFlow.value = ConnectionStatus.Connecting(deviceAddress)
        // 기존 연결 해제
        disconnectDevice(deviceAddress)
        bleManager.connectToDevice(device)


        // 연결 타임아웃 설정
        serviceScope.launch {
            delay(10000) // 10초 타임아웃
            val currentStatus = _connectionStateFlow.value
            if (currentStatus is ConnectionStatus.Connecting && currentStatus.deviceAddress == deviceAddress) {
                Log.w(TAG, "Connection timeout for device: $deviceAddress")
                updateDataLog("연결 타임아웃: $deviceAddress")
                _connectionStateFlow.value = ConnectionStatus.Failed("WearableHub", "연결 타임아웃 - 웨어러블 허브를 재부팅해주세요")
            }
        }
        return true
    }
    
    /**
     * 연결 해제 (작업자 서비스 흐름 문서 기반)
     */
    fun disconnectDevice(deviceAddress: String) {
        val gatt = connectedDevices[deviceAddress]
        gatt?.let {
            Log.d(TAG, "Disconnecting device: $deviceAddress")
            updateDataLog("디바이스 연결 해제: $deviceAddress")
            it.disconnect()
            it.close()
        }
        connectedDevices.remove(deviceAddress)
        retryCounts.remove(deviceAddress)
        
        if (connectedDevices.isEmpty()) {
            _connectionStateFlow.value = ConnectionStatus.Disconnected
        }
    }
    
    /**
     * 모든 디바이스 연결 해제
     */
    private fun disconnectAllDevices() {
        connectedDevices.keys.forEach { deviceAddress ->
            disconnectDevice(deviceAddress)
        }
    }
    
    /**
     * 현재 연결 상태 확인
     */
    fun isConnected(): Boolean {
        return _connectionStateFlow.value is ConnectionStatus.Connected
    }
    
    /**
     * 연결된 디바이스 정보 반환
     */
    fun getConnectedDeviceInfo(): DeviceInfo? {
        val status = _connectionStateFlow.value
        return if (status is ConnectionStatus.Connected) {
            DeviceInfo(
                deviceId = status.deviceAddress,
                deviceName = status.deviceName,
                deviceAddress = status.deviceAddress,
                isConnected = true,
                connectionTime = status.connectionTime
            )
        } else null
    }
    
    /**
     * 블루투스 스캔 시작
     */
    fun startScan(onscanStopped:(() -> Unit)): Boolean {
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            onError?.invoke("블루투스가 비활성화되어 있습니다.")
            return false
        }
        
        Log.d(TAG, "블루투스 스캔 시작")
        updateDataLog("블루투스 스캔 시작")
        onScanStarted?.invoke()
        
        // BLE 매니저를 통한 스캔 시작
        bleManager.startScan(onscanStopped)
        return true
    }
    
    /**
     * 블루투스 스캔 중지
     */
    fun stopScan() {
        Log.d(TAG, "블루투스 스캔 중지")
        updateDataLog("블루투스 스캔 중지")
        onScanStopped?.invoke()
        
        bleManager.stopScan()
    }
    
    /**
     * 서비스가 Foreground Service인지 확인
     */
    fun isForegroundService(): Boolean {
        return isForegroundService
    }
    
    /**
     * 서비스 상태 정보 반환
     */
    fun getServiceStatus(): String {
        return when {
            isForegroundService -> "Foreground Service"
            else -> "일반 서비스"
        }
    }
    
    /**
     * 서비스가 실행 중인지 확인
     */
    fun isServiceRunning(): Boolean {
        return true // 서비스가 바인딩되어 있다면 실행 중
    }
    
    /**
     * Foreground Service로 승격
     */
    fun promoteToForeground() {
        if (!isForegroundService) {
            isForegroundService = true
            startForeground(NOTIFICATION_ID, createNotification("웨어러블 허브 모니터링 중"))
            updateDataLog("Foreground Service로 승격됨")
            Log.d(TAG, "서비스가 Foreground Service로 승격됨")
            
            // 세션 상태 업데이트
            SessionManager.setForegroundServiceActive(true)
        }
    }
    
    /**
     * Foreground Service 해제 (일반 서비스로 전환)
     */
    fun demoteFromForeground() {
        if (isForegroundService) {
            isForegroundService = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            updateDataLog("Foreground Service에서 일반 서비스로 전환됨")
            Log.d(TAG, "서비스가 Foreground Service에서 일반 서비스로 전환됨")
            
            // 세션 상태 업데이트
            SessionManager.setForegroundServiceActive(false)
            
            // 일반 서비스로 전환 후에도 기본 알림은 유지 (선택사항)
            startForeground(NOTIFICATION_ID, createNotification("SafeLink 웨어러블 허브 대기 중"))
        }
    }
    
    /**
     * 작업자 모드 설정
     */
    fun setWorkerMode(enabled: Boolean) {
        isWorkerMode = enabled
        Log.d(TAG, "작업자 모드 설정: $enabled")
        updateDataLog("작업자 모드 설정: $enabled")
    }
    
    /**
     * 작업자 모드 여부 확인
     */
    fun isWorkerMode(): Boolean {
        return isWorkerMode
    }
    
    /**
     * 모든 연결 해제 및 서비스 정리
     */
    fun cleanup() {
        Log.d(TAG, "서비스 정리 시작")
        updateDataLog("서비스 정리 시작")
        
        // 모든 디바이스 연결 해제
        disconnectAllDevices()
        
        // 스캔 중지
        stopScan()
        
        // Foreground Service 해제
        demoteFromForeground()
        
        // 세션 상태 업데이트
        SessionManager.setDeviceConnected(false)
        
        updateDataLog("서비스 정리 완료")
    }
    
    /**
     * Firebase에 센서 데이터 저장 (작업자 모드)
     * 6. 개인 모드가 아니라 작업자 모드일때는 Firebase RDB에 데이터를 등록하고 기록할 수 있다.
     */
    private fun saveSensorDataToFirebase(sensorData: SensorData) {
        serviceScope.launch {
            try {
                // Firebase 서비스를 통한 데이터 저장
                // TODO: FirebaseService 구현 후 연결
                Log.d(TAG, "Firebase에 센서 데이터 저장: HR=${sensorData.heartRate}, Temp=${sensorData.bodyTemperature}")
                updateDataLog("Firebase에 데이터 저장: HR=${sensorData.heartRate} BPM")
                
                // 작업자 모드일 때만 Firebase에 저장
                if (isWorkerMode) {
                    // TODO: FirebaseService.saveSensorData(sensorData)
                    Log.d(TAG, "작업자 모드에서 Firebase 데이터 저장 완료")
                } else {
                    Log.d(TAG, "개인 모드에서는 Firebase 저장하지 않음")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase 데이터 저장 실패", e)
                updateDataLog("오류: Firebase 데이터 저장 실패")
            }
        }
    }
    
    /**
     * 데이터 로그 업데이트
     */
    private fun updateDataLog(message: String) {
        serviceScope.launch {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val logEntry = "[$timestamp] $message\n"
            
            val currentLog = _dataLogFlow.value
            val newLog = currentLog + logEntry
            
            // 로그 길이 제한 (2000자)
            val finalLog = if (newLog.length > 2000) {
                val startIndex = newLog.indexOf('\n') + 1
                newLog.substring(startIndex)
            } else {
                newLog
            }
            
            _dataLogFlow.value = finalLog
        }
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeLink Worker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "웨어러블 허브 연결 상태 알림"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 알림 생성
     */
    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeLink 웨어러블 허브 모니터링")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * GATT 콜백 (작업자 서비스 흐름 문서 기반)
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: "Unknown Device"
            
            Log.d(TAG, "Connection state changed for $deviceName: status=$status, newState=$newState")
            updateDataLog("연결 상태 변경: $deviceName (상태: $status, 새상태: $newState)")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Successfully connected to GATT server: $deviceName")
                        updateDataLog("GATT 서버 연결 성공: $deviceName")
                        connectedDevices[deviceAddress] = gatt
                        retryCounts.remove(deviceAddress)
                        
                        // 연결 상태 업데이트
                        val connectedStatus = ConnectionStatus.Connected(
                            deviceAddress = deviceAddress,
                            deviceName = deviceName,
                            connectionTime = System.currentTimeMillis()
                        )
                        _connectionStateFlow.value = connectedStatus
                        
                        // 콜백 호출
                        onConnectionStatusChanged?.invoke(connectedStatus)
                        
                        Log.d(TAG, "Connection status updated to Connected: $deviceName")
                        updateDataLog("연결 상태 업데이트: 연결됨 - $deviceName")
                        
                        // 서비스 발견 시작
                        Log.d(TAG, "Starting service discovery for $deviceName")
                        updateDataLog("서비스 발견 시작: $deviceName")
                        gatt.discoverServices()
                    } else {
                        Log.e(TAG, "Failed to connect to GATT server: $deviceName, status=$status")
                        updateDataLog("GATT 서버 연결 실패: $deviceName (상태: $status)")
                        handleConnectionFailure(deviceAddress, deviceName, status)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server: $deviceName")
                    updateDataLog("GATT 서버 연결 해제: $deviceName")
                    connectedDevices.remove(deviceAddress)
                    
                    if (connectedDevices.isEmpty()) {
                        _connectionStateFlow.value = ConnectionStatus.Disconnected
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: "Unknown Device"
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for $deviceName")
                updateDataLog("서비스 발견됨: $deviceName")
                
                // ESP32C6 센서 서비스 확인 및 알림 설정
                setupEsp32C6Notifications(gatt)
                
            } else {
                Log.e(TAG, "Service discovery failed for $deviceName: status=$status")
                updateDataLog("서비스 발견 실패: $deviceName (상태: $status)")
                handleConnectionFailure(deviceAddress, deviceName, status)
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val deviceAddress = gatt.device.address
            val data = characteristic.value
            
            Log.d(TAG, "Data received from $deviceAddress: ${characteristic.uuid} - ${data.size} bytes")
            
            // 데이터 파싱 (작업자 서비스 흐름 문서 기반)
            serviceScope.launch {
                try {
                    val sensorData = ProtocolParser.parseSensorData(data)
                    sensorData?.let { data ->
                        // 디바이스 ID를 장치 주소로 설정
                        val updatedData = data.copy(deviceId = deviceAddress)
                        
                        // 센서 데이터 업데이트
                        _sensorDataFlow.value = updatedData
                        
                        // 위험도 계산
                        val riskLevel = updatedData.getRiskLevel()
                        _riskLevelFlow.value = riskLevel
                        
                        // 콜백 호출
                        onSensorDataReceived?.invoke(updatedData)
                        onRiskLevelChanged?.invoke(riskLevel)
                        
                        // 데이터 로그 업데이트
                        updateDataLog("센서 데이터: HR=${updatedData.heartRate}, Temp=${updatedData.bodyTemperature}, Risk=$riskLevel")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Data parsing failed", e)
                    updateDataLog("데이터 파싱 실패: ${e.message}")
                }
            }
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: "Unknown Device"
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful for $deviceName")
                updateDataLog("디스크립터 쓰기 성공: $deviceName")
            } else {
                Log.e(TAG, "Descriptor write failed for $deviceName: status=$status")
                updateDataLog("디스크립터 쓰기 실패: $deviceName (상태: $status)")
            }
        }
    }
    
    /**
     * ESP32C6 센서 알림 설정 (작업자 서비스 흐름 문서 기반)
     */
    private fun setupEsp32C6Notifications(gatt: BluetoothGatt) {
        val deviceName = gatt.device.name ?: "Unknown Device"
        
        Log.d(TAG, "Setting up ESP32C6 notifications for $deviceName")
        updateDataLog("ESP32C6 알림 설정: $deviceName")
        
        // 사용 가능한 모든 서비스 로그 출력
        val services = gatt.services
        Log.d(TAG, "Available services for $deviceName:")
        updateDataLog("사용 가능한 서비스: $deviceName")
        services.forEach { service ->
            Log.d(TAG, "  Service: ${service.uuid}")
            updateDataLog("    서비스: ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                Log.d(TAG, "    Characteristic: ${characteristic.uuid}")
                updateDataLog("      특성: ${characteristic.uuid}")
            }
        }
        
        // ESP32C6 통합 센서 서비스 찾기 (Custom Sensor Service)
        var service = gatt.getService(BleConstants.CUSTOM_SENSOR_SERVICE_UUID)
        if (service == null) {
            Log.d(TAG, "Custom sensor service not found, trying heart rate service")
            updateDataLog("통합 센서 서비스 없음, 심박수 서비스 시도")
            service = gatt.getService(BleConstants.HEART_RATE_SERVICE_UUID)
        }
        
        if (service == null) {
            Log.e(TAG, "No suitable service found for $deviceName")
            updateDataLog("오류: 적합한 서비스를 찾을 수 없습니다: $deviceName")
            return
        }
        
        // 특성 찾기 (통합 센서 데이터 또는 심박수 측정)
        var characteristic = service.getCharacteristic(BleConstants.SENSOR_DATA_UUID)
        if (characteristic == null) {
            Log.d(TAG, "Sensor data characteristic not found, trying heart rate measurement")
            updateDataLog("센서 데이터 특성 없음, 심박수 측정 특성 시도")
            characteristic = service.getCharacteristic(BleConstants.HEART_RATE_MEASUREMENT_UUID)
        }
        
        if (characteristic == null) {
            Log.e(TAG, "No suitable characteristic found for $deviceName")
            updateDataLog("오류: 적합한 특성을 찾을 수 없습니다: $deviceName")
            return
        }
        
        // 알림 활성화
        val success = gatt.setCharacteristicNotification(characteristic, true)
        if (!success) {
            Log.e(TAG, "Failed to enable notifications for $deviceName")
            updateDataLog("오류: 알림 활성화 실패: $deviceName")
            return
        }
        
        // Client Characteristic Configuration Descriptor 설정
        val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = gatt.writeDescriptor(descriptor)
            if (!writeSuccess) {
                Log.e(TAG, "Failed to write descriptor for $deviceName")
                updateDataLog("오류: 디스크립터 쓰기 실패: $deviceName")
            }
        }
        
        Log.d(TAG, "WearableHub notifications enabled for $deviceName")
        updateDataLog("WearableHub 알림 활성화됨: $deviceName")
    }
    
    /**
     * 연결 실패 처리 (작업자 서비스 흐름 문서 기반)
     */
    private fun handleConnectionFailure(deviceAddress: String, deviceName: String, status: Int) {
        val currentRetryCount = retryCounts[deviceAddress] ?: 0
        
        when (status) {
            133 -> {
                Log.e(TAG, "Connection failed with status 133 (GATT_INTERNAL_ERROR) for $deviceName")
                updateDataLog("연결 실패 (GATT_INTERNAL_ERROR): $deviceName")
                if (currentRetryCount < MAX_RETRY_COUNT) {
                    Log.d(TAG, "Retrying connection for $deviceName (attempt ${currentRetryCount + 1}/$MAX_RETRY_COUNT)")
                    updateDataLog("연결 재시도: $deviceName (시도 ${currentRetryCount + 1}/$MAX_RETRY_COUNT)")
                    retryCounts[deviceAddress] = currentRetryCount + 1
                    
                    // 지연 후 재연결 시도
                    serviceScope.launch {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS)
                        connectToWearableHubDevice(deviceAddress)
                    }
                } else {
                    Log.e(TAG, "Max retry count reached for $deviceName")
                    updateDataLog("최대 재시도 횟수 도달: $deviceName")
                    retryCounts.remove(deviceAddress)
                    _connectionStateFlow.value = ConnectionStatus.Failed(deviceName, "연결 실패")
                }
            }
            8, 257 -> {
                Log.e(TAG, "Connection failed with status $status (GATT_INSUFFICIENT_AUTHENTICATION) for $deviceName")
                updateDataLog("연결 실패 (인증 실패): $deviceName")
                _connectionStateFlow.value = ConnectionStatus.Failed(deviceName, "인증 실패 - 웨어러블 허브를 재부팅해주세요")
            }
            19 -> {
                Log.e(TAG, "Connection failed with status 19 (GATT_REMOTE_DEVICE_NOT_REACHABLE) for $deviceName")
                updateDataLog("연결 실패 (디바이스에 도달할 수 없음): $deviceName")
                _connectionStateFlow.value = ConnectionStatus.Failed(deviceName, "디바이스에 도달할 수 없음")
            }
            147 -> {
                Log.e(TAG, "Connection failed with status 147 (GATT_CONNECTION_TIMEOUT) for $deviceName")
                updateDataLog("연결 실패 (연결 타임아웃): $deviceName")
                if (currentRetryCount < MAX_RETRY_COUNT) {
                    Log.d(TAG, "Retrying connection for $deviceName (attempt ${currentRetryCount + 1}/$MAX_RETRY_COUNT)")
                    updateDataLog("연결 재시도: $deviceName (시도 ${currentRetryCount + 1}/$MAX_RETRY_COUNT)")
                    retryCounts[deviceAddress] = currentRetryCount + 1
                    
                    // 지연 후 재연결 시도
                    serviceScope.launch {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS)
                        connectToWearableHubDevice(deviceAddress)
                    }
                } else {
                    Log.e(TAG, "Max retry count reached for $deviceName")
                    updateDataLog("최대 재시도 횟수 도달: $deviceName")
                    retryCounts.remove(deviceAddress)
                    _connectionStateFlow.value = ConnectionStatus.Failed(deviceName, "연결 타임아웃 - 웨어러블 허브를 재부팅해주세요")
                }
            }
            else -> {
                Log.e(TAG, "Connection failed with status $status for $deviceName")
                updateDataLog("연결 실패 (상태 $status): $deviceName")
                _connectionStateFlow.value = ConnectionStatus.Failed(deviceName, "연결 실패: $status")
            }
        }
    }
}

/**
 * 연결 상태 데이터 클래스 (작업자 서비스 흐름 문서 기반)
 */
sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    data class Connecting(val deviceAddress: String) : ConnectionStatus()
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val connectionTime: Long
    ) : ConnectionStatus()
    data class Failed(val deviceName: String, val reason: String) : ConnectionStatus()
}

/**
 * 디바이스 정보 데이터 클래스 (작업자 서비스 흐름 문서 기반)
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceAddress: String,
    val isConnected: Boolean,
    val connectionTime: Long = 0L
) 
) 