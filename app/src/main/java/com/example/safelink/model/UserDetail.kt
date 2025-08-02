package com.example.safelink.model

data class UserDetail(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val department: String,
    val position: String,
    val joinDate: Long,
    val currentSensorData: SensorData,
    val riskEvents: List<RiskEvent>,
    val deviceStatus: DeviceStatus,
    val totalRiskEvents: Int,
    val lastRiskEvent: RiskEvent?
)

data class RiskEvent(
    val id: String,
    val timestamp: Long,
    val riskLevel: RiskLevel,
    val description: String,
    val sensorData: SensorData
)

data class DeviceStatus(
    val bandConnected: Boolean,
    val hubConnected: Boolean,
    val bandBatteryLevel: Int,
    val hubBatteryLevel: Int,
    val lastSeen: Long,
    val signalStrength: Int
) 