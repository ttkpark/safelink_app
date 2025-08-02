package com.example.safelink.service

import android.util.Log
import com.example.safelink.model.SensorData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Firebase 서비스
 * Realtime Database와 Cloud Messaging을 관리
 */
object FirebaseService {
    
    private const val TAG = "FirebaseService"
    
    private val database = FirebaseDatabase.getInstance()
    private val messaging = FirebaseMessaging.getInstance()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 데이터베이스 참조
    private val sensorDataRef = database.getReference("sensor_data")
    private val alertsRef = database.getReference("alerts")
    private val usersRef = database.getReference("users")
    private val devicesRef = database.getReference("devices")
    
    // 현재 사용자 ID (실제 앱에서는 로그인 시스템에서 가져옴)
    private var currentUserId: String = "worker_001"
    
    /**
     * 센서 데이터를 Firebase에 저장
     */
    fun saveSensorData(sensorData: SensorData) {
        serviceScope.launch {
            try {
                val dataWithUserId = sensorData.copy(userId = currentUserId)
                
                // 실시간 데이터 저장
                val realtimeData = dataWithUserId.copy(
                    timestamp = System.currentTimeMillis()
                )
                
                // 사용자별 센서 데이터 저장
                val userSensorRef = sensorDataRef.child(currentUserId)
                    .child(sensorData.deviceType)
                    .child("latest")
                
                userSensorRef.setValue(realtimeData)
                    .addOnSuccessListener {
                        Log.d(TAG, "센서 데이터 저장 성공: ${sensorData.deviceId}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "센서 데이터 저장 실패: ${e.message}")
                    }
                
                // 히스토리 데이터 저장 (시간별)
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH", Locale.getDefault())
                val timeKey = dateFormat.format(Date(timestamp))
                
                val historyRef = sensorDataRef.child(currentUserId)
                    .child(sensorData.deviceType)
                    .child("history")
                    .child(timeKey)
                    .child(timestamp.toString())
                
                historyRef.setValue(realtimeData)
                
                // 디바이스 상태 업데이트
                updateDeviceStatus(sensorData)
                
            } catch (e: Exception) {
                Log.e(TAG, "센서 데이터 저장 중 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 디바이스 상태 업데이트
     */
    private fun updateDeviceStatus(sensorData: SensorData) {
        val deviceStatus = mapOf(
            "deviceId" to sensorData.deviceId,
            "deviceType" to sensorData.deviceType,
            "userId" to currentUserId,
            "lastSeen" to System.currentTimeMillis(),
            "isConnected" to sensorData.isConnected,
            "batteryLevel" to sensorData.batteryLevel,
            "riskLevel" to sensorData.getRiskLevel()
        )
        
        devicesRef.child(sensorData.deviceId).setValue(deviceStatus)
    }
    
    /**
     * 알림 전송
     */
    fun sendAlert(sensorData: SensorData, alertType: String) {
        serviceScope.launch {
            try {
                val alert = mapOf(
                    "userId" to currentUserId,
                    "deviceId" to sensorData.deviceId,
                    "deviceType" to sensorData.deviceType,
                    "alertType" to alertType,
                    "timestamp" to System.currentTimeMillis(),
                    "riskLevel" to sensorData.getRiskLevel(),
                    "riskMessage" to sensorData.getRiskMessage(),
                    "sensorData" to mapOf(
                        "heartRate" to sensorData.heartRate,
                        "bodyTemperature" to sensorData.bodyTemperature,
                        "ambientTemperature" to sensorData.ambientTemperature,
                        "humidity" to sensorData.humidity,
                        "noiseLevel" to sensorData.noiseLevel,
                        "wbgt" to sensorData.wbgt
                    ),
                    "isRead" to false
                )
                
                // 알림 저장
                val alertRef = alertsRef.push()
                alertRef.setValue(alert)
                    .addOnSuccessListener {
                        Log.d(TAG, "알림 저장 성공: $alertType")
                        
                        // 관리자에게 푸시 알림 전송
                        sendPushNotificationToManager(alert)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "알림 저장 실패: ${e.message}")
                    }
                
            } catch (e: Exception) {
                Log.e(TAG, "알림 전송 중 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 관리자에게 푸시 알림 전송
     */
    private fun sendPushNotificationToManager(alert: Map<String, Any>) {
        // FCM 토큰을 통해 관리자에게 알림 전송
        // 실제 구현에서는 관리자의 FCM 토큰을 데이터베이스에서 가져와야 함
        val managerToken = "manager_fcm_token_here" // 실제 토큰으로 교체 필요
        
        val message = mapOf(
            "to" to managerToken,
            "notification" to mapOf(
                "title" to when (alert["alertType"]) {
                    "EMERGENCY" -> "🚨 긴급 상황 발생!"
                    "WARNING" -> "⚠️ 위험 상황 감지"
                    else -> "ℹ️ 주의 상황"
                },
                "body" to alert["riskMessage"] as String,
                "sound" to "default"
            ),
            "data" to mapOf(
                "alertId" to alert["timestamp"].toString(),
                "userId" to alert["userId"] as String,
                "deviceId" to alert["deviceId"] as String,
                "alertType" to alert["alertType"] as String
            )
        )
        
        // FCM 서버로 메시지 전송 (실제 구현에서는 서버를 통해 전송)
        Log.d(TAG, "관리자에게 푸시 알림 전송: ${alert["alertType"]}")
    }
    
    /**
     * 사용자 정보 저장
     */
    fun saveUserInfo(userId: String, userInfo: Map<String, Any>) {
        usersRef.child(userId).setValue(userInfo)
            .addOnSuccessListener {
                Log.d(TAG, "사용자 정보 저장 성공: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "사용자 정보 저장 실패: ${e.message}")
            }
    }
    
    /**
     * 실시간 데이터 리스너 설정
     */
    fun setupRealtimeDataListener(userId: String, callback: (Map<String, SensorData>) -> Unit) {
        val userSensorRef = sensorDataRef.child(userId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sensorDataMap = mutableMapOf<String, SensorData>()
                
                for (deviceSnapshot in snapshot.children) {
                    val deviceType = deviceSnapshot.key ?: continue
                    val latestSnapshot = deviceSnapshot.child("latest")
                    
                    if (latestSnapshot.exists()) {
                        val sensorData = latestSnapshot.getValue(SensorData::class.java)
                        if (sensorData != null) {
                            sensorDataMap[deviceType] = sensorData
                        }
                    }
                }
                
                callback(sensorDataMap)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "실시간 데이터 리스너 오류: ${error.message}")
            }
        }
        
        userSensorRef.addValueEventListener(listener)
    }
    
    /**
     * 알림 리스너 설정
     */
    fun setupAlertListener(userId: String, callback: (List<Map<String, Any>>) -> Unit) {
        val userAlertsRef = alertsRef.orderByChild("userId").equalTo(userId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<Map<String, Any>>()
                
                for (alertSnapshot in snapshot.children) {
                    val alert = alertSnapshot.getValue(object : com.google.firebase.database.GenericTypeIndicator<Map<String, Any>>() {})
                    if (alert != null) {
                        alerts.add(alert + ("alertId" to alertSnapshot.key!!))
                    }
                }
                
                // 최신 알림부터 정렬
                alerts.sortByDescending { it["timestamp"] as Long }
                callback(alerts)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "알림 리스너 오류: ${error.message}")
            }
        }
        
        userAlertsRef.addValueEventListener(listener)
    }
    
    /**
     * FCM 토큰 가져오기
     */
    fun getFCMToken(callback: (String?) -> Unit) {
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "FCM 토큰: $token")
                callback(token)
            } else {
                Log.e(TAG, "FCM 토큰 가져오기 실패: ${task.exception?.message}")
                callback(null)
            }
        }
    }
    
    /**
     * 알림 읽음 처리
     */
    fun markAlertAsRead(alertId: String) {
        alertsRef.child(alertId).child("isRead").setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "알림 읽음 처리 완료: $alertId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "알림 읽음 처리 실패: ${e.message}")
            }
    }
    
    /**
     * 사용자 ID 설정
     */
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }
    
    /**
     * 데이터베이스 연결 테스트
     */
    fun testConnection(callback: (Boolean) -> Unit) {
        val testRef = database.getReference(".info/connected")
        testRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                callback(connected)
                testRef.removeEventListener(this)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(false)
                testRef.removeEventListener(this)
            }
        })
    }
} 