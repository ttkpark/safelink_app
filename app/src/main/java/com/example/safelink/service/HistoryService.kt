package com.example.safelink.service

import android.util.Log
import com.example.safelink.model.HistoryEntry
import com.example.safelink.model.HistoryStats
import com.example.safelink.model.SensorData
import com.example.safelink.model.RiskLevel
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object HistoryService {
    private const val TAG = "HistoryService"
    private val database = FirebaseDatabase.getInstance()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 사용자의 히스토리 데이터 조회
     */
    fun loadUserHistory(
        userId: String,
        startDate: Long,
        endDate: Long,
        callback: (List<HistoryEntry>) -> Unit
    ) {
        serviceScope.launch {
            try {
                val historyRef = database.getReference("users").child(userId).child("history")
                
                val query = historyRef.orderByChild("timestamp")
                    .startAt(startDate.toDouble())
                    .endAt(endDate.toDouble())
                
                query.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val historyEntries = mutableListOf<HistoryEntry>()
                        
                        for (childSnapshot in snapshot.children) {
                            try {
                                val sensorData = childSnapshot.getValue(SensorData::class.java)
                                if (sensorData != null) {
                                    val riskLevel = sensorData.getRiskLevel()
                                    val historyEntry = HistoryEntry(
                                        timestamp = sensorData.timestamp,
                                        sensorData = sensorData,
                                        riskLevel = when (riskLevel) {
                                            0 -> RiskLevel.SAFE
                                            1 -> RiskLevel.WARNING
                                            2 -> RiskLevel.DANGER
                                            else -> RiskLevel.EMERGENCY
                                        },
                                        location = null // TODO: 위치 정보 추가
                                    )
                                    historyEntries.add(historyEntry)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "히스토리 데이터 파싱 실패", e)
                            }
                        }
                        
                        // 시간순 정렬 (최신순)
                        historyEntries.sortByDescending { it.timestamp }
                        
                        callback(historyEntries)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "히스토리 데이터 조회 실패", error.toException())
                        callback(emptyList())
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "히스토리 데이터 조회 중 오류", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 히스토리 통계 계산
     */
    fun calculateHistoryStats(historyEntries: List<HistoryEntry>): HistoryStats {
        if (historyEntries.isEmpty()) {
            return HistoryStats(
                totalEntries = 0,
                averageHeartRate = 0f,
                maxTemperature = 0f,
                riskEvents = 0,
                averageWBGT = 0f,
                maxNoiseLevel = 0f,
                deviceConnectedTime = 0L
            )
        }

        val heartRates = historyEntries.mapNotNull { it.sensorData.heartRate.takeIf { hr -> hr > 0 } }
        val temperatures = historyEntries.mapNotNull { it.sensorData.bodyTemperature.takeIf { temp -> temp > 0 } }
        val wbgtValues = historyEntries.mapNotNull { it.sensorData.wbgt.takeIf { wbgt -> wbgt > 0 } }
        val noiseLevels = historyEntries.mapNotNull { it.sensorData.noiseLevel.takeIf { noise -> noise > 0 } }
        
        val riskEvents = historyEntries.count { it.riskLevel != RiskLevel.SAFE }
        val connectedEntries = historyEntries.filter { it.sensorData.isConnected }
        val deviceConnectedTime = if (connectedEntries.isNotEmpty()) {
            connectedEntries.size * 5 * 60 * 1000L // 5분 간격으로 가정
        } else 0L

        return HistoryStats(
            totalEntries = historyEntries.size,
            averageHeartRate = if (heartRates.isNotEmpty()) heartRates.average().toFloat() else 0f,
            maxTemperature = if (temperatures.isNotEmpty()) temperatures.maxOrNull() ?: 0f else 0f,
            riskEvents = riskEvents,
            averageWBGT = if (wbgtValues.isNotEmpty()) wbgtValues.average().toFloat() else 0f,
            maxNoiseLevel = if (noiseLevels.isNotEmpty()) noiseLevels.maxOrNull() ?: 0f else 0f,
            deviceConnectedTime = deviceConnectedTime
        )
    }

    /**
     * 더미 히스토리 데이터 생성 (테스트용) - 사용하지 않음
     */
    /*
    fun createDummyHistoryData(userId: String, days: Int = 7): List<HistoryEntry> {
        val historyEntries = mutableListOf<HistoryEntry>()
        val currentTime = System.currentTimeMillis()
        val interval = 5 * 60 * 1000L // 5분 간격
        
        for (i in 0 until (days * 24 * 12)) { // 하루 288개 데이터 (5분 간격)
            val timestamp = currentTime - (i * interval)
            
            // 랜덤 센서 데이터 생성
            val heartRate = Random.nextInt(60, 121)
            val bodyTemperature = Random.nextDouble(35.5, 38.1).toFloat()
            val ambientTemperature = Random.nextDouble(20.0, 35.1).toFloat()
            val humidity = Random.nextDouble(30.0, 80.1).toFloat()
            val noiseLevel = Random.nextDouble(30.0, 70.1).toFloat()
            val wbgt = Random.nextDouble(20.0, 35.1).toFloat()
            val batteryLevel = Random.nextInt(20, 101)
            
            val sensorData = SensorData(
                deviceId = "SafeLink_Band_001",
                timestamp = timestamp,
                heartRate = heartRate,
                bodyTemperature = bodyTemperature,
                ambientTemperature = ambientTemperature,
                humidity = humidity,
                noiseLevel = noiseLevel,
                wbgt = wbgt,
                batteryLevel = batteryLevel,
                isConnected = true,
                userId = userId,
                deviceType = "Band"
            )
            
            val riskLevel = when {
                heartRate > 100 || bodyTemperature > 37.5f -> RiskLevel.WARNING
                heartRate > 120 || bodyTemperature > 38.5f -> RiskLevel.DANGER
                heartRate > 140 || bodyTemperature > 39.0f -> RiskLevel.EMERGENCY
                else -> RiskLevel.SAFE
            }
            
            val historyEntry = HistoryEntry(
                timestamp = timestamp,
                sensorData = sensorData,
                riskLevel = riskLevel,
                location = "사무실 A"
            )
            
            historyEntries.add(historyEntry)
        }
        
        return historyEntries.sortedByDescending { it.timestamp }
    }
    */

    /**
     * 날짜 범위 포맷팅
     */
    fun formatDateRange(startDate: Long, endDate: Long): String {
        val startStr = dateFormat.format(Date(startDate))
        val endStr = dateFormat.format(Date(endDate))
        return "$startStr ~ $endStr"
    }

    /**
     * 시간 포맷팅
     */
    fun formatDuration(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}시간 ${minutes}분"
            minutes > 0 -> "${minutes}분"
            else -> "1분 미만"
        }
    }
} 