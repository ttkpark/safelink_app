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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safelink.MainActivity
import com.example.safelink.databinding.FragmentClientMainBinding
import com.example.safelink.model.SensorData
import com.example.safelink.service.BluetoothService
import com.example.safelink.service.ConnectionStatus
import com.example.safelink.service.DeviceInfo
import com.example.safelink.service.FirebaseAuthService
import com.example.safelink.util.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.OnBackPressedCallback

/**
 * SafeLink 작업자 모드 센서 모니터링 화면
 * 
 * 서비스 흐름:
 * 1. 화면 시작 → BluetoothService 바인딩
 * 2. 서비스 데이터 관찰 → StateFlow를 통한 실시간 UI 업데이트
 * 3. 사용자 액션 → 서비스 메서드 호출
 * 4. 화면 종료 → 서비스 바인딩 해제
 */
class SensorMonitorFragment : Fragment() {

    private var _binding: FragmentClientMainBinding? = null
    private val binding get() = _binding!!
    
    // BluetoothService 바인딩
    private var bluetoothService: BluetoothService? = null
    private var isServiceBound = false
    
    // 현재 센서 데이터
    private var currentSensorData: SensorData? = null
    private var currentRiskLevel: Int = 0
    
    // 서비스 연결 콜백
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isServiceBound = true
            
            Log.d(TAG, "BluetoothService 연결됨")
            setupServiceCallbacks()
            observeServiceData()
            updateInitialUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isServiceBound = false
            Log.d(TAG, "BluetoothService 연결 해제됨")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupClickListeners()
        setupUI()
        
        // 유효한 세션이 있을 때만 서비스 바인딩
        if (SessionManager.isValidSession()) {
            Log.d(TAG, "유효한 세션 발견 - BluetoothService 바인딩")
            bindBluetoothService()
        } else {
            Log.d(TAG, "유효한 세션 없음 - 서비스 바인딩하지 않음")
            showNoSessionMessage()
        }
    }
    
    /**
     * BluetoothService 바인딩 (작업자 서비스 흐름 문서 기반)
     */
    private fun bindBluetoothService() {
        val intent = Intent(requireContext(), BluetoothService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 세션이 없을 때 표시할 메시지
     */
    private fun showNoSessionMessage() {
        binding.bluetoothStatusText.text = "세션 없음"
        binding.bluetoothStatusText.setTextColor(
            requireContext().getColor(android.R.color.holo_red_dark)
        )
        
        binding.riskLevelText.text = "로그인 필요"
        binding.riskLevelText.setTextColor(
            requireContext().getColor(android.R.color.darker_gray)
        )
        
        binding.riskMessageText.text = "작업자 모드로 로그인해주세요."
        
        Toast.makeText(requireContext(), "유효한 세션이 없습니다. 로그인해주세요.", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 서비스 콜백 설정 (작업자 서비스 흐름 문서 기반)
     */
    private fun setupServiceCallbacks() {
        bluetoothService?.let { service ->
            service.onSensorDataReceived = { sensorData ->
                lifecycleScope.launch {
                    // 연결 상태 확인 - 연결되지 않은 상태에서 데이터가 들어오면 로그 출력
                    val currentStatus = service.connectionStateFlow.value
                    if (currentStatus !is ConnectionStatus.Connected) {
                        Log.w(TAG, "⚠️ 연결되지 않은 상태에서 센서 데이터 수신: $currentStatus")
                        Log.w(TAG, "⚠️ 데이터: HR=${sensorData.heartRate}, Temp=${sensorData.bodyTemperature}, DeviceId=${sensorData.deviceId}")
                        return@launch // 연결되지 않은 상태에서는 데이터 처리하지 않음
                    }
                    
                    currentSensorData = sensorData
                    updateSensorDataDisplay(sensorData)
                    Log.i(TAG, "센서 데이터 수신: HR=${sensorData.heartRate}, Temp=${sensorData.bodyTemperature}")
                }
            }
            
            service.onConnectionStatusChanged = { status ->
                lifecycleScope.launch {
                    updateConnectionStatus(status)
                    updateUIForConnection(status)
                    Log.i(TAG, "연결 상태 변경: $status")
                }
            }
            
            service.onRiskLevelChanged = { riskLevel ->
                lifecycleScope.launch {
                    currentRiskLevel = riskLevel
                    updateRiskLevelDisplay(riskLevel)
                    Log.i(TAG, "위험도 변경: $riskLevel")
                }
            }
            
            service.onError = { errorMessage ->
                lifecycleScope.launch {
                    Log.e(TAG, "서비스 오류: $errorMessage")
                    Toast.makeText(requireContext(), "오류: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 서비스 데이터 관찰 (작업자 서비스 흐름 문서 기반)
     */
    private fun observeServiceData() {
        bluetoothService?.let { service ->
            // 센서 데이터 관찰
            lifecycleScope.launch {
                service.sensorDataFlow.collect { sensorData ->
                    sensorData?.let { data ->
                        // 연결 상태 확인 - 연결되지 않은 상태에서 데이터가 들어오면 로그 출력
                        val currentStatus = service.connectionStateFlow.value
                        if (currentStatus !is ConnectionStatus.Connected) {
                            Log.w(TAG, "⚠️ StateFlow: 연결되지 않은 상태에서 센서 데이터 수신: $currentStatus")
                            Log.w(TAG, "⚠️ StateFlow 데이터: HR=${data.heartRate}, Temp=${data.bodyTemperature}, DeviceId=${data.deviceId}")
                            return@collect // 연결되지 않은 상태에서는 데이터 처리하지 않음
                        }
                        
                        currentSensorData = data
                        updateSensorDataDisplay(data)
                    }
                }
            }
            
            // 연결 상태 관찰
            lifecycleScope.launch {
                service.connectionStateFlow.collect { status ->
                    updateConnectionStatus(status)
                    updateUIForConnection(status)
                }
            }
            
            // 위험도 관찰
            lifecycleScope.launch {
                service.riskLevelFlow.collect { riskLevel ->
                    currentRiskLevel = riskLevel
                    updateRiskLevelDisplay(riskLevel)
                }
            }
            
            // 데이터 로그 관찰
            lifecycleScope.launch {
                service.dataLogFlow.collect { logData ->
                    _binding?.let { binding ->
                        binding.dataLogText.text = logData
                    }
                }
            }
        }
    }
    
    /**
     * 초기 UI 설정
     */
    private fun updateInitialUI() {
        // 현재 연결 상태 확인
        val currentStatus = bluetoothService?.connectionStateFlow?.value
        updateConnectionStatus(currentStatus ?: ConnectionStatus.Disconnected)
        updateUIForConnection(currentStatus ?: ConnectionStatus.Disconnected)
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 센서 모니터링 화면에서는 로그인 화면으로 이동
                navigateToClientLogin()
            }
        })
    }

    /**
     * 클릭 리스너 설정 (작업자 서비스 흐름 문서 기반)
     */
    private fun setupClickListeners() {
        _binding?.let { binding ->
            binding.bluetoothSetupButton.setOnClickListener {
                val currentStatus = bluetoothService?.connectionStateFlow?.value
                when (currentStatus) {
                    is ConnectionStatus.Connected -> {
                        // 연결 해제
                        disconnectFromSensor()
                    }
                    else -> {
                        // 연결 시도
                        showBluetoothSetupDialog()
                    }
                }
            }
            
            binding.emergencyButton.setOnClickListener {
                sendEmergencyAlert()
            }
            
            // 로그아웃 버튼이 있다면 설정
            binding.logoutButton?.setOnClickListener {
                performLogout()
            }
        }
    }

    /**
     * UI 초기 설정
     */
    private fun setupUI() {
        // 현재 연결 상태 확인 후 UI 업데이트
        val currentStatus = bluetoothService?.connectionStateFlow?.value
        if (currentStatus != null) {
            updateConnectionStatus(currentStatus)
            updateUIForConnection(currentStatus)
        } else {
            // 연결 상태가 없으면 초기 상태로 설정
            updateConnectionStatus(ConnectionStatus.Disconnected)
            updateUIForConnection(ConnectionStatus.Disconnected)
        }
        
        _binding?.let { binding ->
            // 버튼 텍스트 설정
            binding.bluetoothSetupButton.text = "ESP32C6 연결"
            binding.emergencyButton.text = "긴급 알림"
            
            // 데이터 로그 초기화
            binding.dataLogText.text = "ESP32C6 센서 연결을 기다리는 중..."
        }
    }

    /**
     * 센서 연결 해제 (작업자 서비스 흐름 문서 기반)
     */
    private fun disconnectFromSensor() {
        bluetoothService?.let { service ->
            // 연결된 디바이스 정보 가져오기
            val deviceInfo = service.getConnectedDeviceInfo()
            
            // 연결 해제
            deviceInfo?.let { info ->
                service.disconnectDevice(info.deviceAddress)
            }
            
            // UI 업데이트
            currentSensorData = null
            currentRiskLevel = 0
            updateConnectionStatus(ConnectionStatus.Disconnected)
            updateUIForConnection(ConnectionStatus.Disconnected)
            
            _binding?.let { binding ->
                binding.bluetoothSetupButton.text = "ESP32C6 연결"
            }
            
            Toast.makeText(requireContext(), "ESP32C6 센서 연결을 해제했습니다.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "ESP32C6 센서 연결 해제")
        }
    }

    /**
     * 연결 상태에 따른 UI 업데이트 (작업자 서비스 흐름 문서 기반)
     */
    private fun updateUIForConnection(status: ConnectionStatus) {
        _binding?.let { binding ->
            when (status) {
                is ConnectionStatus.Connected -> {
                    binding.bluetoothSetupButton.text = "연결 해제"
                    binding.bluetoothSetupButton.setBackgroundColor(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                }
                is ConnectionStatus.Connecting -> {
                    binding.bluetoothSetupButton.text = "연결 중..."
                    binding.bluetoothSetupButton.setBackgroundColor(
                        requireContext().getColor(android.R.color.holo_orange_dark)
                    )
                }
                is ConnectionStatus.Failed -> {
                    binding.bluetoothSetupButton.text = "ESP32C6 연결"
                    binding.bluetoothSetupButton.setBackgroundColor(
                        requireContext().getColor(android.R.color.holo_blue_dark)
                    )
                    showDefaultValues()
                }
                ConnectionStatus.Disconnected -> {
                    binding.bluetoothSetupButton.text = "ESP32C6 연결"
                    binding.bluetoothSetupButton.setBackgroundColor(
                        requireContext().getColor(android.R.color.holo_blue_dark)
                    )
                    showDefaultValues()
                }
            }
        }
    }

    /**
     * 기본값 표시 (연결되지 않은 경우)
     */
    private fun showDefaultValues() {
        _binding?.let { binding ->
            binding.heartRateText.text = "-- BPM"
            binding.bodyTemperatureText.text = "--°C"
            binding.ambientTemperatureText.text = "--°C"
            binding.humidityText.text = "--%"
            binding.noiseLevelText.text = "-- dB"
            binding.wbgtText.text = "--°C"
            binding.bandBatteryText.text = "--%"
            binding.riskLevelText.text = "연결 대기"
            binding.riskMessageText.text = "ESP32C6 센서를 연결해주세요"
        }
    }

    /**
     * 센서 데이터 표시 업데이트 (작업자 서비스 흐름 문서 기반)
     */
    private fun updateSensorDataDisplay(sensorData: SensorData) {
        _binding?.let { binding ->
            // ESP32C6 센서 데이터
            binding.heartRateText.text = "${sensorData.heartRate} BPM"
            binding.bodyTemperatureText.text = "${String.format("%.1f", sensorData.bodyTemperature)}°C"
            binding.bandBatteryText.text = "${sensorData.batteryLevel}%"
            
            // ESP32C6 환경 센서 데이터
            binding.ambientTemperatureText.text = "${String.format("%.1f", sensorData.ambientTemperature)}°C"
            binding.humidityText.text = "${String.format("%.1f", sensorData.humidity)}%"
            binding.noiseLevelText.text = "${String.format("%.1f", sensorData.noiseLevel)} dB"
            binding.wbgtText.text = "${String.format("%.1f", sensorData.wbgt)}°C"
        }
    }

    /**
     * 연결 상태 업데이트 (작업자 서비스 흐름 문서 기반)
     */
    private fun updateConnectionStatus(status: ConnectionStatus) {
        _binding?.let { binding ->
            when (status) {
                is ConnectionStatus.Connected -> {
                    binding.bluetoothStatusText.text = "ESP32C6 연결됨 (${status.deviceName})"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_green_dark)
                    )
                }
                is ConnectionStatus.Connecting -> {
                    binding.bluetoothStatusText.text = "ESP32C6 연결 중..."
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_orange_dark)
                    )
                }
                is ConnectionStatus.Failed -> {
                    binding.bluetoothStatusText.text = "ESP32C6 연결 실패: ${status.reason}"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                }
                ConnectionStatus.Disconnected -> {
                    binding.bluetoothStatusText.text = "ESP32C6 연결 끊김"
                    binding.bluetoothStatusText.setTextColor(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                }
            }
        }
    }

    /**
     * 위험도 표시 업데이트 (작업자 서비스 흐름 문서 기반)
     */
    private fun updateRiskLevelDisplay(riskLevel: Int) {
        _binding?.let { binding ->
            val (riskText, riskColor) = when (riskLevel) {
                0 -> "안전" to android.R.color.holo_green_dark
                1 -> "주의" to android.R.color.holo_orange_dark
                2 -> "위험" to android.R.color.holo_red_dark
                else -> "긴급" to android.R.color.holo_red_dark
            }
            
            binding.riskLevelText.text = riskText
            binding.riskLevelText.setTextColor(requireContext().getColor(riskColor))
            
            // 위험도에 따른 메시지 설정
            val riskMessage = when (riskLevel) {
                0 -> "정상 상태입니다."
                1 -> "주의가 필요한 상태입니다."
                2 -> "위험한 상태입니다. 관리자에게 연락하세요."
                else -> "긴급 상황입니다! 즉시 대응이 필요합니다."
            }
            
            binding.riskMessageText.text = riskMessage
        }
    }

    /**
     * 블루투스 설정 다이얼로그 표시 (작업자 서비스 흐름 문서 기반)
     */
    private fun showBluetoothSetupDialog() {
        // 디바이스 발견 화면으로 이동
        val fragment = DeviceDiscoveryFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "디바이스 발견 화면으로 이동")
    }

    /**
     * 긴급 알림 전송 (작업자 서비스 흐름 문서 기반)
     */
    private fun sendEmergencyAlert() {
        val currentData = currentSensorData
        val alertMessage = if (currentData != null && bluetoothService?.isConnected() == true) {
            "긴급 알림 전송 - 심박수: ${currentData.heartRate} BPM, " +
            "체온: ${String.format("%.1f", currentData.bodyTemperature)}°C, " +
            "위험도: $currentRiskLevel"
        } else {
            "긴급 알림 전송"
        }
        
        Toast.makeText(requireContext(), alertMessage, Toast.LENGTH_LONG).show()
        Log.d(TAG, "긴급 알림 전송: $alertMessage")
    }

    /**
     * 클라이언트 로그인 화면으로 이동 (작업자 서비스 흐름 문서 기반)
     */
    private fun navigateToClientLogin() {
        // 연결 해제 확인
        if (bluetoothService?.isConnected() == true) {
            disconnectFromSensor()
        }
        
        val fragment = ClientLoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "클라이언트 로그인 화면으로 이동")
    }
    
    /**
     * 로그아웃 수행
     */
    private fun performLogout() {
        Log.d(TAG, "작업자 모드 로그아웃 수행")
        
        // BluetoothService 연결 해제
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
        
        // Firebase 로그아웃
        FirebaseAuthService.signOut()
        
        // 세션 완전 삭제
        SessionManager.clearLoginSession()
        
        // 시작 화면으로 이동 (백 스택 클리어)
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        
        Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "작업자 모드 로그아웃 완료")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // 서비스 바인딩 해제
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
        
        _binding = null
    }

    companion object {
        private const val TAG = "SensorMonitorFragment"
    }
} 