package com.example.safelink.model

data class HistoryEntry(
    val timestamp: Long,
    val sensorData: SensorData,
    val riskLevel: RiskLevel,
    val location: String?
)

data class HistoryStats(
    val totalEntries: Int,
    val averageHeartRate: Float,
    val maxTemperature: Float,
    val riskEvents: Int,
    val averageWBGT: Float,
    val maxNoiseLevel: Float,
    val deviceConnectedTime: Long
) 