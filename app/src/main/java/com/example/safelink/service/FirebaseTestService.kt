package com.example.safelink.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Firebase 연결 테스트 서비스
 */
object FirebaseTestService {
    private const val TAG = "FirebaseTestService"
    
    /**
     * Firebase 연결 상태 확인
     */
    suspend fun testFirebaseConnection(): Boolean {
        return try {
            // Firebase Auth 연결 테스트
            val auth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth 연결 성공")
            
            // Firebase Database 연결 테스트
            val database = Firebase.database
            Log.d(TAG, "Firebase Database 연결 성공")
            
            // 테스트 데이터 쓰기
            val testRef = database.getReference("test_connection")
            testRef.setValue("connected").await()
            Log.d(TAG, "Firebase Database 쓰기 테스트 성공")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase 연결 테스트 실패", e)
            false
        }
    }
    
    /**
     * 테스트 사용자 로그인
     */
    suspend fun testUserLogin(): Boolean {
        return try {
            val auth = FirebaseAuth.getInstance()
            val result = auth.signInWithEmailAndPassword("admin@example.com", "112233").await()
            
            if (result.user != null) {
                Log.d(TAG, "테스트 사용자 로그인 성공: ${result.user?.email}")
                true
            } else {
                Log.e(TAG, "테스트 사용자 로그인 실패")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "테스트 사용자 로그인 중 오류", e)
            false
        }
    }
    
    /**
     * 센서 데이터 저장 테스트
     */
    suspend fun testSensorDataSave(): Boolean {
        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            
            if (user == null) {
                Log.e(TAG, "사용자가 로그인되지 않음")
                return false
            }
            
            val database = Firebase.database
            val sensorDataRef = database.getReference("sensor_data/${user.uid}")
            
            val testData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "heartRate" to 75,
                "bodyTemperature" to 36.5,
                "deviceId" to "test_device"
            )
            
            sensorDataRef.push().setValue(testData).await()
            Log.d(TAG, "센서 데이터 저장 테스트 성공")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "센서 데이터 저장 테스트 실패", e)
            false
        }
    }
} 