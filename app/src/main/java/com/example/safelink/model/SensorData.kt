package com.example.safelink.model

import com.google.firebase.database.PropertyName

/**
 * SafeLink System에서 수집되는 센서 데이터 모델
 */
data class SensorData(
    @PropertyName("device_id")
    val deviceId: String = "",
    
    @PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @PropertyName("heart_rate")
    val heartRate: Int = 0, // 심박수 (BPM)
    
    @PropertyName("body_temperature")
    val bodyTemperature: Float = 0f, // 체온 (°C)
    
    @PropertyName("ambient_temperature")
    val ambientTemperature: Float = 0f, // 주변 온도 (°C)
    
    @PropertyName("humidity")
    val humidity: Float = 0f, // 습도 (%)
    
    @PropertyName("noise_level")
    val noiseLevel: Float = 0f, // 소음 레벨 (dB)
    
    @PropertyName("wbgt")
    val wbgt: Float = 0f, // WBGT (Wet Bulb Globe Temperature)
    
    @PropertyName("battery_level")
    val batteryLevel: Int = 0, // 배터리 레벨 (%)
    
    @PropertyName("is_connected")
    val isConnected: Boolean = false, // 연결 상태
    
    @PropertyName("user_id")
    val userId: String = "", // 사용자 ID
    
    @PropertyName("device_type")
    val deviceType: String = "" // "band" 또는 "hub"
) {
    /**
     * 위험도 판단 로직
     * @return 위험도 레벨 (0: 정상, 1: 주의, 2: 위험, 3: 치명적)
     */
    fun getRiskLevel(): Int {
        return when {
            // 체온 위험도
            bodyTemperature >= 38.0f -> 3 // 치명적
            bodyTemperature >= 37.5f -> 2 // 위험
            bodyTemperature >= 37.0f -> 1 // 주의
            
            // 심박수 위험도
            heartRate >= 120 -> 2 // 위험
            heartRate >= 100 -> 1 // 주의
            
            // 소음 위험도
            noiseLevel >= 110f -> 2 // 위험
            noiseLevel >= 95f -> 1 // 주의
            
            // WBGT 위험도
            wbgt >= 30f -> 2 // 위험
            wbgt >= 28f -> 1 // 주의
            
            else -> 0 // 정상
        }
    }
    
    /**
     * 위험도에 따른 메시지 반환
     */
    fun getRiskMessage(): String {
        return when (getRiskLevel()) {
            3 -> "치명적 위험: 즉시 작업 중단 및 의료 조치 필요"
            2 -> "위험: 휴식 및 안전 조치 필요"
            1 -> "주의: 상태 모니터링 필요"
            else -> "정상"
        }
    }
    
    /**
     * WBGT 계산 (간단한 근사치)
     */
    fun calculateWBGT(): Float {
        // WBGT = 0.7 × Tw + 0.2 × Tg + 0.1 × Ta
        // 여기서는 간단히 Ta(건구온도)와 습도를 이용한 근사치 사용
        return ambientTemperature + (humidity / 100f) * 2f
    }
} 