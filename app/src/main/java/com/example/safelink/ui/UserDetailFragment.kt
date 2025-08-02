package com.example.safelink.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.safelink.R
import com.example.safelink.databinding.FragmentUserDetailBinding
import com.example.safelink.model.*
import com.example.safelink.service.AdminService
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.OnBackPressedCallback

class UserDetailFragment : Fragment() {

    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!
    
    private var userUid: String? = null
    private var currentUserDetail: UserDetail? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupClickListeners()
        getUserUidFromArguments()
        loadUserDetail()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 사용자 상세 화면에서는 이전 화면으로 이동
                parentFragmentManager.popBackStack()
            }
        })
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        binding.contactButton.setOnClickListener {
            showContactInfo()
        }
        
        binding.historyButton.setOnClickListener {
            navigateToHistory()
        }
    }

    /**
     * 인자에서 사용자 UID 가져오기
     */
    private fun getUserUidFromArguments() {
        userUid = arguments?.getString("user_uid")
        if (userUid == null) {
            showError("사용자 정보를 찾을 수 없습니다.")
            // 에러 발생 시 이전 화면으로 이동
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 사용자 상세 정보 로드
     */
    private fun loadUserDetail() {
        userUid?.let { uid ->
            // TODO: 실제 Firebase에서 사용자 상세 정보 로드
            // 현재는 더미 데이터 사용
            currentUserDetail = createDummyUserDetail(uid)
            updateUserDetailDisplay()
        }
    }

    /**
     * 더미 사용자 상세 정보 생성
     */
    private fun createDummyUserDetail(uid: String): UserDetail {
        val currentTime = System.currentTimeMillis()
        
        // 현재 센서 데이터 (더미)
        val currentSensorData = SensorData(
            deviceId = "SafeLink_Band_001",
            timestamp = currentTime,
            heartRate = 75,
            bodyTemperature = 36.5f,
            ambientTemperature = 28.3f,
            humidity = 65f,
            noiseLevel = 45f,
            wbgt = 29.5f,
            batteryLevel = 85,
            isConnected = true,
            userId = uid,
            deviceType = "Band"
        )
        
        // 위험 이벤트 목록 (더미)
        val riskEvents = listOf(
            RiskEvent(
                id = "event_1",
                timestamp = currentTime - 3600000, // 1시간 전
                riskLevel = RiskLevel.WARNING,
                description = "심박수 상승 (120 BPM)",
                sensorData = currentSensorData
            ),
            RiskEvent(
                id = "event_2",
                timestamp = currentTime - 7200000, // 2시간 전
                riskLevel = RiskLevel.DANGER,
                description = "체온 상승 (38.5°C)",
                sensorData = currentSensorData
            )
        )
        
        // 디바이스 상태 (더미)
        val deviceStatus = DeviceStatus(
            bandConnected = true,
            hubConnected = true,
            bandBatteryLevel = 85,
            hubBatteryLevel = 92,
            lastSeen = currentTime,
            signalStrength = -45
        )
        
        return UserDetail(
            uid = uid,
            name = "홍길동",
            email = "hong@example.com",
            phone = "010-1234-5678",
            department = "개발팀",
            position = "개발자",
            joinDate = currentTime - (365 * 24 * 60 * 60 * 1000L), // 1년 전
            currentSensorData = currentSensorData,
            riskEvents = riskEvents,
            deviceStatus = deviceStatus,
            totalRiskEvents = riskEvents.size,
            lastRiskEvent = riskEvents.firstOrNull()
        )
    }

    /**
     * 사용자 상세 정보 표시 업데이트
     */
    private fun updateUserDetailDisplay() {
        currentUserDetail?.let { user ->
            // 기본 정보
            binding.userNameText.text = user.name
            binding.userEmailText.text = user.email
            binding.userPhoneText.text = user.phone
            
            // 현재 센서 데이터
            updateSensorDataDisplay(user.currentSensorData)
            
            // 위험 정보
            updateRiskInfoDisplay(user)
            
            // 위험 이벤트 목록
            updateRiskEventsDisplay(user.riskEvents)
            
            // 디바이스 상태
            updateDeviceStatusDisplay(user.deviceStatus)
        }
    }

    /**
     * 센서 데이터 표시 업데이트
     */
    private fun updateSensorDataDisplay(sensorData: SensorData) {
        // SafeLink Band 데이터
        binding.heartRateText.text = "${sensorData.heartRate} BPM"
        binding.bodyTemperatureText.text = "${sensorData.bodyTemperature}°C"
        binding.bandBatteryText.text = "${sensorData.batteryLevel}%"
        
        // SafeLink Hub 데이터
        binding.ambientTemperatureText.text = "${sensorData.ambientTemperature}°C"
        binding.humidityText.text = "${sensorData.humidity}%"
        binding.noiseLevelText.text = "${sensorData.noiseLevel} dB"
        binding.wbgtText.text = "${sensorData.wbgt}°C"
        
        // 연결 상태
        updateConnectionStatus(sensorData.isConnected)
    }

    /**
     * 연결 상태 업데이트
     */
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            binding.deviceConnectionStatus.text = "연결됨"
            binding.deviceConnectionStatus.setTextColor(
                requireContext().getColor(com.google.android.material.R.color.design_default_color_primary)
            )
        } else {
            binding.deviceConnectionStatus.text = "연결 끊김"
            binding.deviceConnectionStatus.setTextColor(
                requireContext().getColor(com.google.android.material.R.color.design_default_color_error)
            )
        }
    }

    /**
     * 위험 정보 표시 업데이트
     */
    private fun updateRiskInfoDisplay(user: UserDetail) {
        val riskLevel = user.currentSensorData.getRiskLevel()
        val (riskText, riskColor) = when (riskLevel) {
            0 -> "안전" to com.google.android.material.R.color.design_default_color_primary
            1 -> "주의" to com.google.android.material.R.color.design_default_color_secondary
            2 -> "위험" to com.google.android.material.R.color.design_default_color_error
            else -> "긴급" to com.google.android.material.R.color.design_default_color_error
        }
        
        binding.riskLevelText.text = riskText
        binding.riskLevelText.setTextColor(requireContext().getColor(riskColor))
        
        binding.riskDescriptionText.text = user.currentSensorData.getRiskMessage()
        binding.riskTimestampText.text = "방금 전"
    }

    /**
     * 위험 이벤트 목록 표시 업데이트
     */
    private fun updateRiskEventsDisplay(riskEvents: List<RiskEvent>) {
        if (riskEvents.isEmpty()) {
            binding.noRiskEventsText.visibility = View.VISIBLE
        } else {
            binding.noRiskEventsText.visibility = View.GONE
            
            // 최근 3개 이벤트만 표시
            val recentEvents = riskEvents.take(3)
            val eventTexts = recentEvents.map { event ->
                val riskText = when (event.riskLevel) {
                    RiskLevel.SAFE -> "안전"
                    RiskLevel.WARNING -> "주의"
                    RiskLevel.DANGER -> "위험"
                    RiskLevel.EMERGENCY -> "긴급"
                }
                "${dateFormat.format(Date(event.timestamp))} [$riskText] ${event.description}"
            }
            
            // 이벤트 텍스트를 표시할 TextView가 있다면 업데이트
            // 현재 레이아웃에 해당 TextView가 없으므로 주석 처리
            // binding.riskEvent1Text.text = eventTexts.getOrNull(0) ?: ""
            // binding.riskEvent2Text.text = eventTexts.getOrNull(1) ?: ""
            // binding.riskEvent3Text.text = eventTexts.getOrNull(2) ?: ""
        }
    }

    /**
     * 디바이스 상태 표시 업데이트
     */
    private fun updateDeviceStatusDisplay(deviceStatus: DeviceStatus) {
        // 디바이스 연결 상태
        val connectionStatus = if (deviceStatus.bandConnected) "연결됨" else "연결 안됨"
        val connectionColor = if (deviceStatus.bandConnected) {
            requireContext().getColor(com.google.android.material.R.color.design_default_color_primary)
        } else {
            requireContext().getColor(com.google.android.material.R.color.design_default_color_error)
        }
        
        binding.deviceConnectionStatus.text = connectionStatus
        binding.deviceConnectionStatus.setTextColor(connectionColor)
        
        // 디바이스 이름
        binding.deviceNameText.text = "SafeLink Band"
        
        // 신호 강도
        binding.signalStrengthText.text = "${deviceStatus.signalStrength} dBm"
        
        // 마지막 연결 시간
        binding.lastConnectedText.text = AdminService.formatLastSeen(deviceStatus.lastSeen)
    }

    /**
     * 연락처 정보 표시
     */
    private fun showContactInfo() {
        currentUserDetail?.let { user ->
            val message = """
                이름: ${user.name}
                이메일: ${user.email}
                전화번호: ${user.phone}
                부서: ${user.department}
                직책: ${user.position}
            """.trimIndent()
            
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 히스토리 화면으로 이동
     */
    private fun navigateToHistory() {
        userUid?.let { uid ->
            val fragment = UserHistoryFragment.newInstance(uid)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
            
            Log.d(TAG, "히스토리 화면으로 이동: $uid")
        }
    }

    /**
     * 에러 메시지 표시
     */
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "UserDetailFragment"
        
        /**
         * 새로운 UserDetailFragment 인스턴스 생성
         */
        fun newInstance(userUid: String): UserDetailFragment {
            return UserDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("user_uid", userUid)
                }
            }
        }
    }
} 