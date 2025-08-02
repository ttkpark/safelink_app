package com.example.safelink.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safelink.databinding.FragmentClientMainBinding
import com.example.safelink.bluetooth.BleManager
import com.example.safelink.model.SensorData
import com.example.safelink.model.UserMode
import com.example.safelink.service.BluetoothService
import com.example.safelink.service.ConnectionStatus
import com.example.safelink.service.FirebaseAuthService
import com.example.safelink.util.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class ClientMainFragment : Fragment() {
    private var _binding: FragmentClientMainBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bleManager: BleManager
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dataLog = StringBuilder()
    private var isDataReceiving = false
    
    // BluetoothService 바인딩 관련 변수들
    private var bluetoothService: BluetoothService? = null
    private var isServiceBound = false
    
    // 서비스 연결을 위한 ServiceConnection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "BluetoothService 연결됨")
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isServiceBound = true
            
            // 서비스 콜백 설정
            setupServiceCallbacks()
            setupBleManager()

            // 서비스가 Foreground Service인지 확인하여 세션 상태 업데이트
            updateSessionStatus()
            
            Toast.makeText(requireContext(), "BluetoothService가 연결되었습니다.", Toast.LENGTH_SHORT).show()


            // 서비스가 바인딩된 후 자동으로 웨어러블 허브 디바이스 연결 시도
            autoConnectToWearableHub()

            // 초기 연결 상태 확인
            updateConnectionStatusFromService()

        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "BluetoothService 연결 해제됨")
            bluetoothService = null
            isServiceBound = false
            Toast.makeText(requireContext(), "BluetoothService 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientMainBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupBluetoothService()
        setupClickListeners()
        setupInitialUI() // 초기 UI 설정
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // 뒤로가기 처리
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 클라이언트 메인 화면에서는 로그인 화면으로 이동
                navigateToClientLogin()
            }
        })
    }
    
    /**
     * BluetoothService 시작 및 바인딩
     * 1. 서비스가 실행되지 않고 있으면 실행하고 Bind한다.
     */
    private fun setupBluetoothService() {
        try {
            // 서비스 시작
            val serviceIntent = Intent(requireContext(), BluetoothService::class.java)
            requireContext().startService(serviceIntent)
            
            // 서비스 바인딩
            val bindIntent = Intent(requireContext(), BluetoothService::class.java)
            requireContext().bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            Log.d(TAG, "BluetoothService 시작 및 바인딩 시도")
        } catch (e: Exception) {
            Log.e(TAG, "BluetoothService 시작 실패", e)
            Toast.makeText(requireContext(), "BluetoothService 시작에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 서비스 콜백 설정
     */
    private fun setupServiceCallbacks() {
        bluetoothService?.let { service ->
            // 센서 데이터 수신 콜백
            service.onSensorDataReceived = { sensorData ->
                lifecycleScope.launch {
                    updateSensorData(sensorData)
                    updateRiskLevel(sensorData)
                    addToDataLog(sensorData)
                }
            }
            
            // 연결 상태 변경 콜백
            service.onConnectionStatusChanged = { status ->
                lifecycleScope.launch {
                    updateConnectionStatusFromService(status)
                    // 연결 상태 변경 시 세션 상태 업데이트
                    updateSessionStatus()
                }
            }
            
            // 위험도 변경 콜백
            service.onRiskLevelChanged = { riskLevel ->
                lifecycleScope.launch {
                    updateRiskLevelFromService(riskLevel)
                }
            }
            
            // 오류 콜백
            service.onError = { errorMessage ->
                lifecycleScope.launch {
                    Log.e(TAG, "서비스 오류: $errorMessage")
                    Toast.makeText(requireContext(), "오류: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
            
            Log.d(TAG, "서비스 콜백 설정 완료")

        }
    }
    
    /**
     * 세션 상태 업데이트
     * Foreground Service가 있을 때 앱 자동 로그인 세션이 유효한 것이다.
     */
    private fun updateSessionStatus() {
        bluetoothService?.let { service ->
            val isForeground = service.isForegroundService()
            val isConnected = service.isConnected()
            val serviceStatus = service.getServiceStatus()
            
            Log.d(TAG, "서비스 상태: $serviceStatus, 연결됨: $isConnected")
            
            if (isForeground && isConnected) {
                // Foreground Service가 있고 연결된 상태면 세션 유효
                Log.d(TAG, "세션 유효: Foreground Service 실행 중, 디바이스 연결됨")
                
                // 세션 상태 저장
                SessionManager.setForegroundServiceActive(true)
                SessionManager.setDeviceConnected(true)
                
                // 세션 정보 로그 출력
                SessionManager.logSessionInfo()
            } else {
                Log.d(TAG, "세션 상태: Foreground=$isForeground, Connected=$isConnected")
                
                // 일반 서비스 상태일 때도 세션 정보 업데이트
                SessionManager.setForegroundServiceActive(isForeground)
                SessionManager.setDeviceConnected(isConnected)
            }
        }
    }
    
    /**
     * 서비스에서 연결 상태 업데이트
     * 2. 서비스의 블루투스 실행 상태를 조회하고, 그에따라 데이터를 가져올 수 있으면 가져온다.
     */
    private fun updateConnectionStatusFromService(status: ConnectionStatus? = null) {
        val currentStatus = status ?: bluetoothService?.connectionStateFlow?.value
        currentStatus?.let { connectionStatus ->
            when (connectionStatus) {
                is ConnectionStatus.Connected -> {
                    binding.bluetoothStatusText.text = "WearableHub 연결됨"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_green_dark)
                    )
                    Toast.makeText(requireContext(), "WearableHub에 연결되었습니다!", Toast.LENGTH_SHORT).show()
                    
                    // 연결된 상태에서 데이터 수신 시작
                    startDataReceiving()
                }
                is ConnectionStatus.Connecting -> {
                    binding.bluetoothStatusText.text = "WearableHub 연결 중..."
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_orange_dark)
                    )
                }
                is ConnectionStatus.Disconnected -> {
                    binding.bluetoothStatusText.text = "WearableHub 연결 끊김"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                    
                    // 연결이 끊어진 상태에서 데이터 수신 중지
                    stopDataReceiving()
                }
                is ConnectionStatus.Failed -> {
                    binding.bluetoothStatusText.text = "WearableHub 연결 실패"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                    Toast.makeText(requireContext(), "연결 실패: ${connectionStatus.reason}", Toast.LENGTH_SHORT).show()
                    
                    // 연결 실패 시 데이터 수신 중지
                    stopDataReceiving()
                }
            }
        }
        
        // 서비스 상태도 함께 업데이트
        updateServiceStatus()
    }
    
    /**
     * 서비스 상태 업데이트
     */
    private fun updateServiceStatus() {
        bluetoothService?.let { service ->
            val serviceStatus = service.getServiceStatus()
            val isRunning = service.isServiceRunning()
            
            Log.d(TAG, "서비스 상태: $serviceStatus, 실행 중: $isRunning")

            // 서비스 상태를 UI에 표시 (선택사항)
            // binding.serviceStatusText.text = "서비스: $serviceStatus"
        }
    }
    
    /**
     * 데이터 수신 시작
     */
    private fun startDataReceiving() {
        if (!isDataReceiving) {
            isDataReceiving = true
            Log.d(TAG, "데이터 수신 시작")
        }
    }
    
    /**
     * 데이터 수신 중지
     */
    private fun stopDataReceiving() {
        if (isDataReceiving) {
            isDataReceiving = false
            Log.d(TAG, "데이터 수신 중지")
        }
    }
    
    /**
     * 서비스에서 위험도 업데이트
     */
    private fun updateRiskLevelFromService(riskLevel: Int) {
        val (riskText, riskColor) = when (riskLevel) {
            0 -> "안전" to android.R.color.holo_green_dark
            1 -> "주의" to android.R.color.holo_orange_dark
            2 -> "위험" to android.R.color.holo_red_dark
            else -> "긴급" to android.R.color.holo_red_dark
        }
        
        binding.riskLevelText.text = riskText
        binding.riskLevelText.setTextColor(requireContext().getColor(riskColor))
    }
    
    /**
     * 웨어러블 허브 디바이스 자동 연결 시도
     */
    private fun autoConnectToWearableHub() {
        // 저장된 웨어러블 허브 디바이스 주소가 있다면 연결 시도
        val savedDeviceAddress = getSavedWearableHubAddress()
        if (savedDeviceAddress != null) {
            Log.d(TAG, "저장된 웨어러블 허브 디바이스 연결 시도: $savedDeviceAddress")
            connectToWearableHubDevice(savedDeviceAddress)
        } else {
            Log.d(TAG, "저장된 웨어러블 허브 디바이스 주소가 없습니다. 디바이스 발견 화면으로 이동하세요.")
            Toast.makeText(requireContext(), "웨어러블 허브를 찾기 위해 '블루투스 설정' 버튼을 눌러주세요.", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 저장된 웨어러블 허브 디바이스 주소 가져오기
     */
    private fun getSavedWearableHubAddress(): String? {
        val sharedPrefs = requireContext().getSharedPreferences("SafeLinkPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("wearable_hub_device_address", null)
    }
    
    /**
     * 웨어러블 허브 디바이스 주소 저장
     */
    private fun saveWearableHubAddress(deviceAddress: String) {
        val sharedPrefs = requireContext().getSharedPreferences("SafeLinkPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("wearable_hub_device_address", deviceAddress).apply()
        Log.d(TAG, "웨어러블 허브 디바이스 주소 저장: $deviceAddress")
    }
    
    /**
     * 웨어러블 허브 디바이스 연결 시도
     */
    private fun connectToWearableHubDevice(deviceAddress: String) {
        bluetoothService?.let { service ->
            val success = service.connectToWearableHubDevice(deviceAddress)
            if (success) {
                Log.d(TAG, "웨어러블 허브 디바이스 연결 시도: $deviceAddress")
                Toast.makeText(requireContext(), "웨어러블 허브 연결을 시도합니다.", Toast.LENGTH_SHORT).show()
                
                // 디바이스 주소 저장
                saveWearableHubAddress(deviceAddress)
            } else {
                Log.e(TAG, "웨어러블 허브 디바이스 연결 시도 실패: $deviceAddress")
                Toast.makeText(requireContext(), "웨어러블 허브 연결 시도에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e(TAG, "BluetoothService가 바인딩되지 않음")
            Toast.makeText(requireContext(), "BluetoothService가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupBleManager() {
        bluetoothService?.let { service ->
            bleManager = service.getBLEManager()

            // BLE 초기화
            if (!bleManager.initialize()) {
                Log.e(TAG, "BLE 초기화 실패")
                Toast.makeText(requireContext(), "BLE 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show()
                return
            }

            bleManager.onConnectedFragment = {
                lifecycleScope.launch {
                    updateConnectionStatus(true)
                    Toast.makeText(requireContext(), "ESP32C6_Sensor에 연결되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }

            bleManager.onDisconnectedFragment = {
                lifecycleScope.launch {
                    updateConnectionStatus(false)
                    Toast.makeText(requireContext(), "ESP32C6_Sensor 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            bleManager.onHeartRateReceived = { heartRate ->
                lifecycleScope.launch {
                    _binding?.let { binding ->
                        binding.heartRateText.text = "심박수: $heartRate BPM"
                    }
                    Log.d(TAG, "심박수 수신: $heartRate BPM")
                }
            }

            bleManager.onTempHumidityReceived = { temperature, humidity ->
                lifecycleScope.launch {
                    _binding?.let { binding ->
                        if(temperature != 0.0f)binding.bodyTemperatureText.text = "체온: ${temperature}°C"
                        if(temperature != 0.0f)binding.ambientTemperatureText.text = "주변온도: ${temperature}°C"
                        if(humidity != 0.0f)binding.humidityText.text = "습도: ${humidity}%"
                    }
                    Log.d(TAG, "온도/습도 수신: ${temperature}°C, ${humidity}%")
                }
            }

            service.onSensorDataReceived = { sensorData ->
                lifecycleScope.launch {
                    updateSensorData(sensorData)
                    updateRiskLevel(sensorData)
                    addToDataLog(sensorData)
                }
            }
        } ?: run {
            Log.e(TAG, "BluetoothService가 바인딩되지 않음")
            Toast.makeText(requireContext(), "BluetoothService가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupClickListeners() {
        binding.bluetoothSetupButton.setOnClickListener {
            showBluetoothSetupDialog()
        }
        
        binding.logoutButton.setOnClickListener {
            performLogout()
        }
        
        // 서비스 상태 확인 버튼 (테스트용)
        // binding.serviceStatusButton.setOnClickListener {
        //     checkServiceStatus()
        // }
        
        // emergency_button은 SensorMonitorFragment에서 처리됨
    }
    
    /**
     * 서비스 상태 확인 (테스트용)
     */
    private fun checkServiceStatus() {
        bluetoothService?.let { service ->
            val status = service.getServiceStatus()
            val isForeground = service.isForegroundService()
            val isRunning = service.isServiceRunning()
            
            val message = "서비스 상태: $status\n" +
                         "Foreground: $isForeground\n" +
                         "실행 중: $isRunning"
            
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            Log.d(TAG, message)
        } ?: run {
            Toast.makeText(requireContext(), "서비스가 바인딩되지 않음", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 더미 데이터 시뮬레이션 제거됨 - 실제 ESP32C6 센서 연결 시에만 데이터 표시
    
    /**
     * 초기 UI 설정 - 연결되지 않은 상태에서 기본값 표시
     */
    private fun setupInitialUI() {
        _binding?.let { binding ->
            // 센서 데이터 기본값 설정
            binding.heartRateText.text = "심박수: -- BPM"
            binding.bodyTemperatureText.text = "체온: -- °C"
            binding.ambientTemperatureText.text = "주변온도: -- °C"
            binding.humidityText.text = "습도: -- %"
            binding.noiseLevelText.text = "소음: -- dB"
            binding.wbgtText.text = "WBGT: -- °C"
            binding.bandBatteryText.text = "밴드 배터리: -- %"
            
            // 위험도 기본값 설정
            binding.riskLevelText.text = "연결 대기"
            binding.riskLevelText.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            binding.riskMessageText.text = "웨어러블 허브 연결을 기다리는 중..."
            
                    // 연결 상태 설정
        binding.bluetoothStatusText.text = "WearableHub 연결 끊김"
        binding.bluetoothStatusText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        
        // 데이터 로그 초기화
        binding.dataLogText.text = "웨어러블 허브 연결을 기다리는 중..."
        }
    }
    
    private fun updateSensorData(sensorData: SensorData) {
        _binding?.let { binding ->
            binding.heartRateText.text = "심박수: ${sensorData.heartRate} BPM"
            binding.bodyTemperatureText.text = "체온: ${sensorData.bodyTemperature}°C"
            binding.ambientTemperatureText.text = "주변온도: ${sensorData.ambientTemperature}°C"
            binding.humidityText.text = "습도: ${sensorData.humidity}%"
            binding.noiseLevelText.text = "소음: ${sensorData.noiseLevel} dB"
            binding.wbgtText.text = "WBGT: ${sensorData.wbgt}°C"
            binding.bandBatteryText.text = "밴드 배터리: 85%"
        }
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        _binding?.let { binding ->
            if (isConnected) {
                binding.bluetoothStatusText.text = "ESP32C6_Sensor 연결됨"
                binding.bluetoothStatusText.setTextColor(
                    requireContext().getColor(android.R.color.holo_green_dark)
                )
            } else {
                binding.bluetoothStatusText.text = "ESP32C6_Sensor 연결 끊김"
                binding.bluetoothStatusText.setTextColor(
                    requireContext().getColor(android.R.color.holo_red_dark)
                )
            }
        }
    }
    
    private fun updateRiskLevel(sensorData: SensorData) {
        _binding?.let { binding ->
            val riskLevel = sensorData.getRiskLevel()
            val (riskText, riskColor) = when (riskLevel) {
                0 -> "안전" to android.R.color.holo_green_dark
                1 -> "주의" to android.R.color.holo_orange_dark
                2 -> "위험" to android.R.color.holo_red_dark
                else -> "긴급" to android.R.color.holo_red_dark
            }
            
            binding.riskLevelText.text = riskText
            binding.riskLevelText.setTextColor(requireContext().getColor(riskColor))
            
            binding.riskMessageText.text = sensorData.getRiskMessage()
        }
    }
    
    private fun addToDataLog(sensorData: SensorData) {
        _binding?.let { binding ->
            val timestamp = dateFormat.format(Date(sensorData.timestamp))
            val logEntry = "[$timestamp] 심박수: ${sensorData.heartRate} BPM, 체온: ${sensorData.bodyTemperature}°C\n"
            
            dataLog.append(logEntry)
            
            // 로그가 너무 길어지면 앞부분 제거
            if (dataLog.length > 2000) {
                val startIndex = dataLog.indexOf('\n') + 1
                dataLog.delete(0, startIndex)
            }
            
            binding.dataLogText.text = dataLog.toString()
        }
    }
    
    private fun showBluetoothSetupDialog() {
        // BLE 디바이스 발견 화면으로 이동
        val fragment = DeviceDiscoveryFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun sendEmergencyAlert() {
        Toast.makeText(requireContext(), "긴급 알림이 전송되었습니다.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "긴급 알림 전송")
        
        // Firebase에 긴급 알림 전송
        // TODO: Firebase 서비스를 통한 긴급 알림 구현
    }
    
    private fun navigateToClientLogin() {
        val fragment = ClientLoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        Log.d(TAG, "클라이언트 로그인 화면으로 이동")
    }
    
    /**
     * 로그아웃 수행
     * 6. 기기에 연결이 성공했다면, 서비스를 Foreground Service로 승격시키고, 데이터를 받는다.
     * 5. 연결 성공해서 Foreground 서비스가 됨과 동시에 세션이 등록된다. Foreground service가 있을때 앱 자동 로그인 세션이 유효한 것이다.
     */
    private fun performLogout() {
        Log.d(TAG, "로그아웃 수행")
        
        // Firebase 로그아웃
        FirebaseAuthService.signOut()
        
        // 세션 삭제
        SessionManager.clearLoginSession()
        
        // Foreground Service 해제
        bluetoothService?.let { service ->
            service.demoteFromForeground()
            Log.d(TAG, "로그아웃 시 Foreground Service 해제")
        }
        
        // 서비스 바인딩 해제
        unbindBluetoothService()
        
        // 시작 화면으로 이동
        requireActivity().onBackPressedDispatcher.onBackPressed()
        
        Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "로그아웃 완료")
    }
    
    /**
     * BluetoothService 바인딩 해제
     */
    private fun unbindBluetoothService() {
        if (isServiceBound) {
            try {
                requireContext().unbindService(serviceConnection)
                isServiceBound = false
                bluetoothService = null
                Log.d(TAG, "BluetoothService 바인딩 해제 완료")
            } catch (e: Exception) {
                Log.e(TAG, "BluetoothService 바인딩 해제 실패", e)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 서비스 바인딩 해제
        unbindBluetoothService()
        _binding = null
    }
    
    companion object {
        private const val TAG = "ClientMainFragment"
    }
} 