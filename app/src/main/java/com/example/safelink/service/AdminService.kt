package com.example.safelink.service

import android.util.Log
import com.example.safelink.model.UserSummary
import com.example.safelink.model.UserStatus
import com.example.safelink.model.RiskLevel
import com.example.safelink.model.DashboardStats
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object AdminService {
    private const val TAG = "AdminService"
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    /**
     * 관리자 권한 확인
     */
    suspend fun checkAdminPrivileges(): Boolean {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "현재 로그인된 사용자가 없습니다.")
            return false
        }
        
        return try {
            // Firebase Database에서 사용자의 userMode 확인
            val userRef = database.getReference("users").child(currentUser.uid)
            val userData = userRef.get().await()
            val userMode = userData.child("userMode").getValue(String::class.java)
            
            Log.d(TAG, "현재 사용자 userMode: $userMode")
            
            // ADMIN인 경우에만 true 반환
            userMode == "ADMIN"
            
        } catch (e: Exception) {
            Log.e(TAG, "관리자 권한 확인 중 오류 발생", e)
            false
        }
    }

    /**
     * 관리자 이메일 확인 (임시 사용)
     */
    private fun isAdminEmail(email: String): Boolean {
        return email.endsWith("@admin.com") || email.endsWith("@safelink.com")
    }

    /**
     * 모든 사용자 목록 조회
     */
    fun loadAllUsers(callback: (List<UserSummary>) -> Unit) {
        serviceScope.launch {
            try {
                val usersRef = database.getReference("users")
                
                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val userSummaries = mutableListOf<UserSummary>()
                        
                        for (childSnapshot in snapshot.children) {
                            try {
                                val userUid = childSnapshot.key ?: continue
                                
                                // 기본 사용자 정보 읽기
                                val email = childSnapshot.child("email").getValue(String::class.java) ?: ""
                                val displayName = childSnapshot.child("displayName").getValue(String::class.java) ?: "알 수 없음"
                                val userMode = childSnapshot.child("userMode").getValue(String::class.java) ?: ""
                                val createdAt = childSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                                
                                // CUSTOMER 사용자만 표시 (관리자는 제외)
                                if (userMode == "CUSTOMER") {
                                    // 현재 센서 데이터 확인
                                    val currentSnapshot = childSnapshot.child("current")
                                    val sensorsSnapshot = childSnapshot.child("sensors")
                                    
                                    val isConnected = currentSnapshot.child("isConnected").getValue(Boolean::class.java) ?: false
                                    val lastSeen = currentSnapshot.child("lastSeen").getValue(Long::class.java) ?: createdAt
                                    
                                    // 센서 데이터 읽기
                                    val heartRate = sensorsSnapshot.child("heartRate").getValue(Int::class.java) ?: 0
                                    val bodyTemperature = sensorsSnapshot.child("bodyTemperature").getValue(Float::class.java) ?: 0f
                                    val wbgt = sensorsSnapshot.child("wbgt").getValue(Float::class.java) ?: 0f
                                    
                                    // 위험도 계산
                                    val riskLevel = calculateRiskLevel(heartRate, bodyTemperature, wbgt)
                                    
                                    // 사용자 상태 결정
                                    val status = when {
                                        !isConnected -> UserStatus.OFFLINE
                                        riskLevel == RiskLevel.EMERGENCY -> UserStatus.DANGER
                                        riskLevel == RiskLevel.DANGER -> UserStatus.DANGER
                                        riskLevel == RiskLevel.WARNING -> UserStatus.WARNING
                                        else -> UserStatus.ONLINE
                                    }
                                    
                                    val userSummary = UserSummary(
                                        uid = userUid,
                                        name = displayName,
                                        email = email,
                                        department = "", // 아직 구현되지 않음
                                        position = "", // 아직 구현되지 않음
                                        riskLevel = riskLevel,
                                        status = status,
                                        isOnline = isConnected,
                                        lastSeen = lastSeen
                                    )
                                    
                                    userSummaries.add(userSummary)
                                }
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "사용자 데이터 파싱 실패", e)
                            }
                        }
                        
                        // 마지막 연결 시간순 정렬 (최신순)
                        userSummaries.sortByDescending { it.lastSeen }
                        
                        Log.d(TAG, "로드된 사용자 수: ${userSummaries.size}")
                        callback(userSummaries)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "사용자 목록 조회 실패", error.toException())
                        callback(emptyList())
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "사용자 목록 조회 중 오류", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 대시보드 통계 계산
     */
    fun calculateDashboardStats(userSummaries: List<UserSummary>): DashboardStats {
        val totalUsers = userSummaries.size
        val onlineUsers = userSummaries.count { it.isOnline }
        val warningUsers = userSummaries.count { it.riskLevel == RiskLevel.WARNING }
        val dangerUsers = userSummaries.count { it.riskLevel == RiskLevel.DANGER || it.riskLevel == RiskLevel.EMERGENCY }
        
        return DashboardStats(
            totalUsers = totalUsers,
            onlineUsers = onlineUsers,
            warningUsers = warningUsers,
            dangerUsers = dangerUsers
        )
    }

    /**
     * 사용자 검색
     */
    fun searchUsers(userSummaries: List<UserSummary>, query: String): List<UserSummary> {
        if (query.isBlank()) return userSummaries
        
        return userSummaries.filter { user ->
            user.name.contains(query, ignoreCase = true) ||
            user.email.contains(query, ignoreCase = true) ||
            user.department.contains(query, ignoreCase = true) ||
            user.position.contains(query, ignoreCase = true)
        }
    }

    /**
     * 사용자 삭제
     */
    fun deleteUser(userUid: String, callback: (Boolean, String?) -> Unit) {
        serviceScope.launch {
            try {
                val userRef = database.getReference("users").child(userUid)
                
                userRef.removeValue().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "사용자 삭제 성공: $userUid")
                        callback(true, null)
                    } else {
                        Log.e(TAG, "사용자 삭제 실패", task.exception)
                        callback(false, task.exception?.message)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "사용자 삭제 중 오류", e)
                callback(false, e.message)
            }
        }
    }

    /**
     * 사용자 추가
     */
    fun addUser(
        email: String,
        password: String,
        name: String,
        department: String,
        position: String,
        callback: (Boolean, String?) -> Unit
    ) {
        serviceScope.launch {
            try {
                // Firebase Auth로 사용자 계정 생성
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            if (user != null) {
                                // 사용자 프로필 정보 저장
                                val userRef = database.getReference("users").child(user.uid)
                                val profileData = mapOf(
                                    "name" to name,
                                    "email" to email,
                                    "department" to department,
                                    "position" to position,
                                    "createdAt" to System.currentTimeMillis()
                                )
                                
                                userRef.child("profile").setValue(profileData)
                                    .addOnCompleteListener { profileTask ->
                                        if (profileTask.isSuccessful) {
                                            Log.d(TAG, "사용자 추가 성공: ${user.uid}")
                                            callback(true, null)
                                        } else {
                                            Log.e(TAG, "프로필 저장 실패", profileTask.exception)
                                            callback(false, profileTask.exception?.message)
                                        }
                                    }
                            } else {
                                callback(false, "사용자 계정 생성 실패")
                            }
                        } else {
                            Log.e(TAG, "사용자 계정 생성 실패", task.exception)
                            callback(false, task.exception?.message)
                        }
                    }
                
            } catch (e: Exception) {
                Log.e(TAG, "사용자 추가 중 오류", e)
                callback(false, e.message)
            }
        }
    }

    /**
     * 사용자 정보 업데이트
     */
    fun updateUser(
        userUid: String,
        name: String,
        department: String,
        position: String,
        callback: (Boolean, String?) -> Unit
    ) {
        serviceScope.launch {
            try {
                val userRef = database.getReference("users").child(userUid).child("profile")
                val updateData = mapOf(
                    "name" to name,
                    "department" to department,
                    "position" to position,
                    "updatedAt" to System.currentTimeMillis()
                )
                
                userRef.updateChildren(updateData)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "사용자 정보 업데이트 성공: $userUid")
                            callback(true, null)
                        } else {
                            Log.e(TAG, "사용자 정보 업데이트 실패", task.exception)
                            callback(false, task.exception?.message)
                        }
                    }
                
            } catch (e: Exception) {
                Log.e(TAG, "사용자 정보 업데이트 중 오류", e)
                callback(false, e.message)
            }
        }
    }

    /**
     * 위험도 계산
     */
    private fun calculateRiskLevel(heartRate: Int, bodyTemperature: Float, wbgt: Float): RiskLevel {
        return when {
            heartRate > 140 || bodyTemperature > 39.0f || wbgt > 30.0f -> RiskLevel.EMERGENCY
            heartRate > 120 || bodyTemperature > 38.5f || wbgt > 28.0f -> RiskLevel.DANGER
            heartRate > 100 || bodyTemperature > 37.5f || wbgt > 26.0f -> RiskLevel.WARNING
            else -> RiskLevel.SAFE
        }
    }

    /**
     * 마지막 연결 시간 포맷팅
     */
    fun formatLastSeen(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "방금 전" // 1분 미만
            diff < 3600000 -> "${diff / 60000}분 전" // 1시간 미만
            diff < 86400000 -> "${diff / 3600000}시간 전" // 1일 미만
            else -> dateFormat.format(Date(timestamp))
        }
    }
} 