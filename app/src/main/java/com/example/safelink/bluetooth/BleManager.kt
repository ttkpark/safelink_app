package com.example.safelink.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.safelink.model.SensorData
import java.util.UUID

class BleManager(private val context: Context) {
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // 발견된 디바이스 목록 관리
    private val discoveredDevices = mutableSetOf<String>() // MAC 주소로 중복 체크
    
    // 상태 관리
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
    private val _isScanning = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = _isScanning
    
    // 연결된 디바이스 정보
    private var connectedDeviceAddress: String? = null
    private var connectedDeviceName: String? = null
    
    // 콜백 함수들
    var onConnectedFragment: (() -> Unit)? = null
    var onDisconnectedFragment: (() -> Unit)? = null
    var onHeartRateReceived: ((Int) -> Unit)? = null
    var onTempHumidityReceived: ((Float, Float) -> Unit)? = null
    var onHealthStatusReceived: ((String) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null
    private var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    private var onSensorDataReceived: ((SensorData) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT = 10000L // 10초 후 자동 스캔 중지
        
        // 비트 연산 유틸리티 함수들
        private fun Byte.toUnsignedInt(): Int = this.toInt() and 0xFF
        private fun Byte.toUnsignedLong(): Long = this.toLong() and 0xFFL
    }

    fun setOnError(callback:((String) -> Unit)?){onError = callback}
    fun setOnConnected(callback:(() -> Unit)?){onConnected = callback}
    fun setOnDisconnected(callback:(() -> Unit)?){onDisconnected = callback}
    fun setOnDeviceFound(callback:((BluetoothDevice) -> Unit)?){onDeviceFound = callback}
    fun setOnSensorDataReceived(callback:((SensorData) -> Unit)?){onSensorDataReceived = callback}
    /**
     * BLE 초기화
     */
    fun initialize(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "블루투스가 지원되지 않는 기기입니다.")
            onError?.invoke("블루투스가 지원되지 않는 기기입니다.")
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "블루투스가 비활성화되어 있습니다.")
            onError?.invoke("블루투스가 비활성화되어 있습니다.")
            return false
        }
        
        Log.d(TAG, "BLE 초기화 성공")
        return true
    }
    
    /**
     * BLE 스캔 시작 - ESP32C6 센서 디바이스 찾기
     */
    fun startScan(onscanStopped:(() -> Unit)) {
        if (isScanning) {
            Log.w(TAG, "이미 스캔 중입니다.")
            return
        }
        
        // 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_SCAN 권한이 필요합니다.")
                onError?.invoke("BLUETOOTH_SCAN 권한이 필요합니다.")
                onscanStopped.invoke()
                return
            }
        }
        
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ACCESS_FINE_LOCATION 권한이 필요합니다.")
            onError?.invoke("ACCESS_FINE_LOCATION 권한이 필요합니다.")
            onscanStopped.invoke()
            return
        }
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE 스캐너를 사용할 수 없습니다.")
            onError?.invoke("BLE 스캐너를 사용할 수 없습니다.")
            onscanStopped.invoke()
            return
        }
        
        // 발견된 디바이스 목록 초기화
        discoveredDevices.clear()
        
        // ESP32C6 센서 디바이스 찾기 위한 스캔 필터 설정
        val scanFilter = ScanFilter.Builder()
            .setDeviceName(BleConstants.DEVICE_NAME) // ESP32C6_Sensor 디바이스만 찾기
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name
                val deviceAddress = device.address
                
                // 중복 디바이스 체크
                if (discoveredDevices.contains(deviceAddress)) {
                    return
                }
                
                Log.d(TAG, "디바이스 발견: ${deviceName ?: "Unknown"} (${deviceAddress})")
                
                // ESP32C6 센서 디바이스인지 확인
                if (deviceName == BleConstants.DEVICE_NAME || 
                    deviceName?.contains("ESP32") == true ||
                    deviceName?.contains("Sensor") == true) {
                    
                    Log.i(TAG, "ESP32C6 센서 디바이스 발견: $deviceName")
                    discoveredDevices.add(deviceAddress)
                    onDeviceFound?.invoke(device)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "스캔 실패: $errorCode")
                isScanning = false
                _isScanning.value = false
                onError?.invoke("스캔 실패: $errorCode")
                onscanStopped.invoke()
            }
        }
        
        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            _isScanning.value = true
            Log.d(TAG, "BLE 스캔 시작 - ESP32C6 센서 디바이스 찾는 중...")
            
            // 10초 후 자동 스캔 중지
            handler.postDelayed({
                if (isScanning) {
                    stopScan()
                    onscanStopped.invoke()
                }
            }, SCAN_TIMEOUT)
            
        } catch (e: Exception) {
            Log.e(TAG, "스캔 시작 실패", e)
            onError?.invoke("스캔 시작 실패: ${e.message}")
        }
    }
    
    /**
     * BLE 스캔 중지
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (!isScanning) {
            return
        }
        
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            _isScanning.value = false
            Log.d(TAG, "BLE 스캔 중지")
        } catch (e: Exception) {
            Log.e(TAG, "스캔 중지 실패", e)
        }
    }
    
    /**
     * 디바이스에 연결
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        if (_connectionState.value) {
            Log.w(TAG, "이미 연결된 상태입니다.")
            return
        }
        
        Log.d(TAG, "디바이스 연결 시도: ${device.name} (${device.address})")
        
        // 연결 시도 중 상태 업데이트
        connectedDeviceAddress = device.address
        connectedDeviceName = device.name
        
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "GATT 서버에 연결됨")
                        _connectionState.value = true
                        onConnected?.invoke()
                        onConnectedFragment?.invoke()
                        
                        // 서비스 발견 시작
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "GATT 서버에서 연결 해제됨")
                        _connectionState.value = false
                        connectedDeviceAddress = null
                        connectedDeviceName = null
                        onDisconnected?.invoke()
                        onDisconnectedFragment?.invoke()
                    }
                }
            }
            
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "서비스 발견 성공")
                    
                    // 모든 서비스 로깅
                    gatt.services.forEach { service ->
                        Log.d(TAG, "서비스 발견: ${service.uuid}")
                        service.characteristics.forEach { characteristic ->
                            Log.d(TAG, "  - 특성: ${characteristic.uuid}")
                        }
                    }
                    
                    // 표준 BLE 서비스들 구독 설정
                    subscribeToStandardServices(gatt)
                    
                } else {
                    Log.e(TAG, "서비스 발견 실패: $status")
                    onError?.invoke("서비스 발견 실패: $status")
                    gatt.disconnect()
                }
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val data = characteristic.value
                Log.d(TAG, "데이터 수신: ${characteristic.uuid} - ${data.size} 바이트")
                
                // 특성별 데이터 처리
                when (characteristic.uuid) {
                    // 표준 서비스들
                    BleConstants.HEART_RATE_MEASUREMENT_UUID -> {
                        processHeartRateData(data)
                    }
                    BleConstants.TEMPERATURE_MEASUREMENT_UUID -> {
                        processTemperatureData(data)
                    }
                    BleConstants.HUMIDITY_UUID -> {
                        processHumidityData(data)
                    }
                    BleConstants.PRESSURE_UUID -> {
                        processPressureData(data)
                    }
                    // ⭐ 통합 센서 데이터 처리
                    BleConstants.SENSOR_DATA_UUID -> {
                        processIntegratedSensorData(data)
                    }
                    // 건강 상태 문자열
                    BleConstants.HEALTH_STATUS_UUID -> {
                        processHealthStatus(data)
                    }
                }
            }
            
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val data = characteristic.value
                    Log.d(TAG, "특성 읽기 성공: ${characteristic.uuid} - ${data.size} 바이트")
                    
                    // 특성별 데이터 처리
                    when (characteristic.uuid) {
                        BleConstants.HEART_RATE_MEASUREMENT_UUID -> {
                            processHeartRateData(data)
                        }
                        BleConstants.TEMPERATURE_MEASUREMENT_UUID -> {
                            processTemperatureData(data)
                        }
                        BleConstants.HUMIDITY_UUID -> {
                            processHumidityData(data)
                        }
                        BleConstants.PRESSURE_UUID -> {
                            processPressureData(data)
                        }
                        BleConstants.SENSOR_DATA_UUID -> {
                            processIntegratedSensorData(data)
                        }
                        BleConstants.HEALTH_STATUS_UUID -> {
                            processHealthStatus(data)
                        }
                    }
                } else {
                    Log.e(TAG, "특성 읽기 실패: $status")
                }
            }
        })
    }
    
    /**
     * 표준 BLE 서비스들 구독 설정
     */
    @SuppressLint("MissingPermission")
    private fun subscribeToStandardServices(gatt: BluetoothGatt) {
        try {
            val characteristicsToSubscribe = mutableListOf<BluetoothGattCharacteristic>()
            
            // Heart Rate Service
            val heartRateService = gatt.getService(BleConstants.HEART_RATE_SERVICE_UUID)
            val heartRateChar = heartRateService?.getCharacteristic(BleConstants.HEART_RATE_MEASUREMENT_UUID)
            if (heartRateChar != null) {
                characteristicsToSubscribe.add(heartRateChar)
                Log.d(TAG, "Heart Rate 특성 발견")
            }
            
            // Temperature Service
            val tempService = gatt.getService(BleConstants.TEMPERATURE_SERVICE_UUID)
            val tempChar = tempService?.getCharacteristic(BleConstants.TEMPERATURE_MEASUREMENT_UUID)
            if (tempChar != null) {
                characteristicsToSubscribe.add(tempChar)
                Log.d(TAG, "Temperature 특성 발견")
            }
            
            // Environmental Service
            val envService = gatt.getService(BleConstants.ENVIRONMENTAL_SERVICE_UUID)
            val humidityChar = envService?.getCharacteristic(BleConstants.HUMIDITY_UUID)
            val pressureChar = envService?.getCharacteristic(BleConstants.PRESSURE_UUID)
            if (humidityChar != null) {
                characteristicsToSubscribe.add(humidityChar)
                Log.d(TAG, "Humidity 특성 발견")
            }
            if (pressureChar != null) {
                characteristicsToSubscribe.add(pressureChar)
                Log.d(TAG, "Pressure 특성 발견")
            }
            
            // ⭐ Custom Sensor Service (통합 데이터)
            val customService = gatt.getService(BleConstants.CUSTOM_SENSOR_SERVICE_UUID)
            val sensorDataChar = customService?.getCharacteristic(BleConstants.SENSOR_DATA_UUID)
            val healthStatusChar = customService?.getCharacteristic(BleConstants.HEALTH_STATUS_UUID)
            if (sensorDataChar != null) {
                characteristicsToSubscribe.add(sensorDataChar)
                Log.d(TAG, "통합 센서 데이터 특성 발견")
            }
            if (healthStatusChar != null) {
                characteristicsToSubscribe.add(healthStatusChar)
                Log.d(TAG, "건강 상태 특성 발견")
            }
            
            // 모든 특성에 대해 알림 활성화
            characteristicsToSubscribe.forEach { characteristic ->
                enableNotifications(gatt, characteristic)
            }
            
            Log.i(TAG, "총 ${characteristicsToSubscribe.size}개 특성에 알림 설정 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "서비스 구독 설정 실패", e)
            onError?.invoke("서비스 구독 설정 실패: ${e.message}")
        }
    }
    
    /**
     * 알림 활성화
     * TODO: 왜 무슨 기준으로 알림을 어디로 보내는가?
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            // 특성 알림 활성화
            gatt.setCharacteristicNotification(characteristic, true)
            
            // 클라이언트 특성 설정 디스크립터 설정
            val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                gatt.writeDescriptor(descriptor)
                Log.d(TAG, "알림 활성화 완료: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "클라이언트 특성 설정 디스크립터를 찾을 수 없습니다: ${characteristic.uuid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "알림 활성화 실패: ${characteristic.uuid}", e)
            onError?.invoke("알림 활성화 실패: ${e.message}")
        }
    }
    
    /**
     * ⭐ 통합 센서 데이터 처리 (8바이트)
     */
    private fun processIntegratedSensorData(data: ByteArray) {
        if (data.size < BleConstants.INTEGRATED_DATA_SIZE) {
            Log.w(TAG, "통합 센서 데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        // 연결 상태 확인 - 연결되지 않은 상태에서는 데이터 처리하지 않음
        if (!_connectionState.value) {
            Log.w(TAG, "⚠️ 연결되지 않은 상태에서 통합 센서 데이터 파싱 시도")
            return
        }
        
        try {
            // 가이드 문서 기준으로 데이터 파싱
            val heartRate = (data[BleConstants.HEART_RATE_OFFSET + 1].toUnsignedInt() shl 8) or 
                           data[BleConstants.HEART_RATE_OFFSET].toUnsignedInt()
            
            val tempRaw = (data[BleConstants.TEMPERATURE_OFFSET + 1].toUnsignedInt() shl 8) or 
                          data[BleConstants.TEMPERATURE_OFFSET].toUnsignedInt()
            val temperature = tempRaw / 10.0f // 0.1°C 단위를 실제 온도로 변환
            
            val humidityRaw = (data[BleConstants.HUMIDITY_OFFSET + 1].toUnsignedInt() shl 8) or 
                             data[BleConstants.HUMIDITY_OFFSET].toUnsignedInt()
            val humidity = humidityRaw / 10.0f // 0.1% 단위를 실제 습도로 변환
            
            Log.i(TAG, "통합 센서 데이터 - 심박수: ${heartRate} BPM, 온도: ${temperature}°C, 습도: ${humidity}%")
            
            // 콜백 호출
            onHeartRateReceived?.invoke(heartRate)
            onTempHumidityReceived?.invoke(temperature, humidity)
            
            // SensorData 객체 생성 및 콜백
            val sensorData = SensorData(
                deviceId = "ESP32C6_Sensor",
                timestamp = System.currentTimeMillis(),
                heartRate = heartRate,
                bodyTemperature = temperature,
                ambientTemperature = temperature,
                humidity = humidity,
                noiseLevel = 0f, // 현재 지원하지 않음
                wbgt = 0f, // 현재 지원하지 않음
                isConnected = true
            )
            onSensorDataReceived?.invoke(sensorData)
            
        } catch (e: Exception) {
            Log.e(TAG, "통합 센서 데이터 파싱 실패", e)
            onError?.invoke("통합 센서 데이터 파싱 실패: ${e.message}")
        }
    }
    
    /**
     * 심박수 데이터 처리 (표준 Heart Rate Service)
     */
    private fun processHeartRateData(data: ByteArray) {
        if (data.size < 2) {
            Log.w(TAG, "심박수 데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        try {
            val flags = data[0]
            val heartRate = if ((flags.toInt() and 0x01) == 0) {
                // 8비트 심박수
                data[1].toUnsignedInt()
            } else {
                // 16비트 심박수
                (data[2].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
            }
            
            Log.i(TAG, "심박수 데이터: ${heartRate} BPM")
            onHeartRateReceived?.invoke(heartRate)
            
        } catch (e: Exception) {
            Log.e(TAG, "심박수 데이터 파싱 실패", e)
        }
    }
    
    /**
     * 온도 데이터 처리 (표준 Health Thermometer Service)
     */
    private fun processTemperatureData(data: ByteArray) {
        if (data.size < 5) {
            Log.w(TAG, "온도 데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        try {
            val flags = data[0]
            val tempRaw = (data[4].toUnsignedInt() shl 24) or (data[3].toUnsignedInt() shl 16) or 
                         (data[2].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
            val temperature = tempRaw / 10.0f // 0.1°C 단위를 실제 온도로 변환
            
            Log.i(TAG, "온도 데이터: ${temperature}°C")
            onTempHumidityReceived?.invoke(temperature, 0f) // 습도는 0으로 설정
            
        } catch (e: Exception) {
            Log.e(TAG, "온도 데이터 파싱 실패", e)
        }
    }
    
    /**
     * 습도 데이터 처리 (표준 Environmental Service)
     */
    private fun processHumidityData(data: ByteArray) {
        if (data.size < 5) {
            Log.w(TAG, "습도 데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        try {
            val flags = data[0]
            val humidityRaw = (data[4].toUnsignedInt() shl 24) or (data[3].toUnsignedInt() shl 16) or 
                            (data[2].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
            val humidity = humidityRaw / 10.0f // 0.1% 단위를 실제 습도로 변환
            
            Log.i(TAG, "습도 데이터: ${humidity}%")
            onTempHumidityReceived?.invoke(0f, humidity) // 온도는 0으로 설정
            
        } catch (e: Exception) {
            Log.e(TAG, "습도 데이터 파싱 실패", e)
        }
    }
    
    /**
     * 압력 데이터 처리 (표준 Environmental Service)
     */
    private fun processPressureData(data: ByteArray) {
        if (data.size < 5) {
            Log.w(TAG, "압력 데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        try {
            val flags = data[0]
            val pressureRaw = (data[4].toUnsignedInt() shl 24) or (data[3].toUnsignedInt() shl 16) or 
                            (data[2].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
            val pressure = pressureRaw / 10.0f // 0.1 Pa 단위를 실제 압력으로 변환
            
            Log.i(TAG, "압력 데이터: ${pressure} Pa")
            
        } catch (e: Exception) {
            Log.e(TAG, "압력 데이터 파싱 실패", e)
        }
    }
    
    /**
     * 건강 상태 문자열 처리
     */
    private fun processHealthStatus(data: ByteArray) {
        try {
            val status = String(data, Charsets.UTF_8)
            Log.i(TAG, "건강 상태: $status")
            
            onHealthStatusReceived?.invoke(status)
            
        } catch (e: Exception) {
            Log.e(TAG, "건강 상태 데이터 파싱 실패", e)
        }
    }
    
    /**
     * 기존 호환성을 위한 센서 데이터 파싱
     */
    private fun parseSensorData(data: ByteArray) {
        if (data.size < BleConstants.DATA_SIZE) {
            Log.w(TAG, "데이터 크기가 부족합니다: ${data.size} 바이트")
            return
        }
        
        try {
            // Little Endian으로 데이터 파싱
            val heartRate = data[BleConstants.HEART_RATE_OFFSET].toUnsignedInt() or 
                           (data[BleConstants.HEART_RATE_OFFSET + 1].toUnsignedInt() shl 8)
            
            val temperatureRaw = data[BleConstants.TEMPERATURE_OFFSET].toUnsignedInt() or 
                                (data[BleConstants.TEMPERATURE_OFFSET + 1].toUnsignedInt() shl 8)
            val temperature = temperatureRaw / 10.0f
            
            val humidityRaw = data[BleConstants.HUMIDITY_OFFSET].toUnsignedInt() or 
                             (data[BleConstants.HUMIDITY_OFFSET + 1].toUnsignedInt() shl 8)
            val humidity = humidityRaw / 10.0f
            
            val timestamp = data[BleConstants.TIMESTAMP_OFFSET].toUnsignedLong() or 
                           (data[BleConstants.TIMESTAMP_OFFSET + 1].toUnsignedLong() shl 8) or
                           (data[BleConstants.TIMESTAMP_OFFSET + 2].toUnsignedLong() shl 16) or
                           (data[BleConstants.TIMESTAMP_OFFSET + 3].toUnsignedLong() shl 24)
            
            Log.i(TAG, "센서 데이터 파싱: HR=$heartRate BPM, Temp=${temperature}°C, Humidity=${humidity}%")
            
            // 연결 상태 확인 - 연결되지 않은 상태에서는 데이터 처리하지 않음
            if (!_connectionState.value) {
                Log.w(TAG, "⚠️ 연결되지 않은 상태에서 센서 데이터 파싱 시도")
                return
            }
            
            // 콜백 호출
            onHeartRateReceived?.invoke(heartRate)
            onTempHumidityReceived?.invoke(temperature, humidity)
            
            // SensorData 객체 생성 및 콜백
            val sensorData = SensorData(
                deviceId = "ESP32C6_Sensor",
                timestamp = timestamp,
                heartRate = heartRate,
                bodyTemperature = temperature,
                ambientTemperature = temperature,
                humidity = humidity,
                noiseLevel = 0f, // 현재 지원하지 않음
                wbgt = 0f, // 현재 지원하지 않음
                isConnected = true
            )
            onSensorDataReceived?.invoke(sensorData)
            
        } catch (e: Exception) {
            Log.e(TAG, "센서 데이터 파싱 실패", e)
            onError?.invoke("센서 데이터 파싱 실패: ${e.message}")
        }
    }
    
    /**
     * 연결 해제
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        try {
            Log.d(TAG, "BLE 연결 해제 시작")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            _connectionState.value = false
            connectedDeviceAddress = null
            connectedDeviceName = null
            Log.d(TAG, "BLE 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "연결 해제 실패", e)
        }
    }
    
    /**
     * GATT Server 지원 여부 확인
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun checkGattServerSupport(device: BluetoothDevice, callback: (Boolean) -> Unit) {
        Log.d(TAG, "GATT Server 지원 여부 확인: ${device.name}")
        
        // 임시 GATT 연결을 시도하여 서비스 발견 가능 여부 확인
        val tempGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "임시 연결 성공 - 서비스 발견 시작")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "임시 연결 해제")
                        gatt.close()
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services
                    Log.d(TAG, "서비스 발견: ${services.size}개 서비스")
                    
                    // ESP32C6 센서 관련 서비스 확인
                    val hasEsp32Services = services.any { service ->
                        service.uuid == BleConstants.HEART_RATE_SERVICE_UUID ||
                        service.uuid == BleConstants.TEMPERATURE_SERVICE_UUID ||
                        service.uuid == BleConstants.ENVIRONMENTAL_SERVICE_UUID ||
                        service.uuid == BleConstants.CUSTOM_SENSOR_SERVICE_UUID
                    }
                    
                    callback(hasEsp32Services)
                    
                    // 임시 연결 해제
                    gatt.disconnect()
                    gatt.close()
                } else {
                    Log.d(TAG, "서비스 발견 실패: $status")
                    callback(false)
                    gatt.close()
                }
            }
        })
    }
    
    /**
     * 리소스 정리
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun cleanup() {
        stopScan()
        disconnect()
    }
    
    /**
     * 현재 연결 상태 확인
     */
    fun isConnected(): Boolean {
        return _connectionState.value
    }
    
    /**
     * 연결된 디바이스 주소 반환
     */
    fun getConnectedDeviceAddress(): String? {
        return connectedDeviceAddress
    }
    
    /**
     * 연결된 디바이스 이름 반환
     */
    fun getConnectedDeviceName(): String? {
        return connectedDeviceName
    }
} 