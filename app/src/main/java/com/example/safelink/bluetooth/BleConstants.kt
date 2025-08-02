package com.example.safelink.bluetooth

import java.util.UUID

object BleConstants {
    // 웨어러블 허브 디바이스 정보
    const val DEVICE_NAME = "WearableHub"
    
    // ===== 표준 BLE 서비스 UUID (가이드 문서 기준) =====
    
    // Heart Rate Service (0x180D)
    val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    
    // Health Thermometer Service (0x1809)
    val TEMPERATURE_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
    val TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
    
    // Environmental Sensing Service (0x181A)
    val ENVIRONMENTAL_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
    val HUMIDITY_UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")
    val PRESSURE_UUID = UUID.fromString("00002a6d-0000-1000-8000-00805f9b34fb")
    
    // Custom Sensor Service (0x1810) - 통합 센서 데이터
    val CUSTOM_SENSOR_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    val SENSOR_DATA_UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb") // 통합 센서 데이터
    val HEALTH_STATUS_UUID = UUID.fromString("00002a70-0000-1000-8000-00805f9b34fb") // 건강 상태
    
    // ===== 기존 호환성을 위한 상수들 =====
    
    // Service UUID (기존 호환성)
    val SERVICE_UUID = CUSTOM_SENSOR_SERVICE_UUID
    
    // Characteristic UUID (기존 호환성)
    val CHARACTERISTIC_UUID = SENSOR_DATA_UUID
    
    // Client Characteristic Configuration Descriptor UUID
    val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Sensor Service UUID (service/BleManager.kt에서 사용)
    val SENSOR_SERVICE_UUID = CUSTOM_SENSOR_SERVICE_UUID
    
    // Heart Rate Characteristic UUID
    val HEART_RATE_CHAR_UUID = HEART_RATE_MEASUREMENT_UUID
    
    // Temperature/Humidity Characteristic UUID
    val TEMP_HUMIDITY_CHAR_UUID = TEMPERATURE_MEASUREMENT_UUID
    
    // Control Characteristic UUID
    val CONTROL_CHAR_UUID = UUID.fromString("0000FFE3-0000-1000-8000-00805f9b34fb")
    
    // Control Commands
    const val CMD_START_DATA: Byte = 0x01
    const val CMD_STOP_DATA: Byte = 0x02
    
    // ===== 통합 센서 데이터 형식 (8바이트) =====
    const val INTEGRATED_DATA_SIZE = 8 // 8바이트 통합 데이터
    
    // 통합 데이터 오프셋 (가이드 문서 기준)
    const val HEART_RATE_OFFSET = 0 // 2바이트
    const val TEMPERATURE_OFFSET = 2 // 2바이트 (0.1°C 단위)
    const val HUMIDITY_OFFSET = 4 // 2바이트 (0.1% 단위)
    const val RESERVED_OFFSET = 6 // 2바이트 예약
    
    // ===== 기존 호환성을 위한 상수들 =====
    const val DATA_SIZE = INTEGRATED_DATA_SIZE // 8바이트 데이터
    const val TIMESTAMP_OFFSET = 6 // 기존 호환성
    
    // 데이터 전송 주기 (4초)
    const val DATA_UPDATE_INTERVAL = 4000L
    
    // 스캔 타임아웃
    const val SCAN_TIMEOUT = 10000L // 10초
    
    // 연결 타임아웃
    const val CONNECTION_TIMEOUT = 15000L // 15초
    
    // ===== 건강 상태 문자열 =====
    object HealthStatus {
        const val NORMAL = "Normal"
        const val ELEVATED_HEART_RATE = "Elevated Heart Rate"
        const val TEMPERATURE_ALERT = "Temperature Alert"
        const val HUMIDITY_ALERT = "Humidity Alert"
        const val WARNING = "Warning"
        const val CRITICAL = "Critical"
    }
} 