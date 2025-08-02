package com.example.safelink.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.safelink.model.UserMode
import com.example.safelink.model.FirebaseUser

/**
 * 세션 관리 유틸리티 클래스
 * 앱 재시작 시에도 세션을 유지하고, 로그아웃 시에만 완전히 종료
 */
object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREF_NAME = "session_preferences"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_MODE = "user_mode"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_UID = "user_uid"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_SESSION_TIMESTAMP = "session_timestamp"
    private const val KEY_AUTO_LOGIN = "auto_login"
    private const val KEY_FOREGROUND_SERVICE_ACTIVE = "foreground_service_active"
    private const val KEY_DEVICE_CONNECTED = "device_connected"
    
    private lateinit var sharedPreferences: SharedPreferences
    
    /**
     * 초기화
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "SessionManager 초기화 완료")
    }
    
    /**
     * 로그인 세션 저장
     */
    fun saveLoginSession(user: FirebaseUser, userMode: UserMode, autoLogin: Boolean = false) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_MODE, userMode.name)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_UID, user.uid)
            putString(KEY_DISPLAY_NAME, user.displayName)
            putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis())
            putBoolean(KEY_AUTO_LOGIN, autoLogin)
        }.apply()
        
        Log.d(TAG, "로그인 세션 저장: ${user.email} (${userMode.name})")
    }
    
    /**
     * 로그아웃 세션 삭제
     */
    fun clearLoginSession() {
        sharedPreferences.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_MODE)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_UID)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_SESSION_TIMESTAMP)
            putBoolean(KEY_AUTO_LOGIN, false)
            
            // Foreground Service 및 디바이스 연결 상태도 초기화
            putBoolean(KEY_FOREGROUND_SERVICE_ACTIVE, false)
            putBoolean(KEY_DEVICE_CONNECTED, false)
        }.apply()
        
        Log.d(TAG, "로그인 세션 삭제 완료")
    }
    
    /**
     * 로그인 상태 확인
     * Foreground Service가 있고 디바이스가 연결된 상태일 때만 유효한 세션으로 간주
     */
    fun isLoggedIn(): Boolean {
        val basicLogin = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val isValidSession = isValidSession()
        
        Log.d(TAG, "로그인 상태 확인: 기본로그인=$basicLogin, 유효세션=$isValidSession")
        
        // 기본 로그인 상태이면서 유효한 세션이 있을 때만 로그인된 것으로 간주
        return basicLogin && isValidSession
    }
    
    /**
     * 사용자 모드 가져오기
     */
    fun getUserMode(): UserMode? {
        val modeName = sharedPreferences.getString(KEY_USER_MODE, null)
        return modeName?.let { 
            try {
                UserMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
    
    /**
     * 사용자 이메일 가져오기
     */
    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }
    
    /**
     * 사용자 UID 가져오기
     */
    fun getUserUid(): String? {
        return sharedPreferences.getString(KEY_USER_UID, null)
    }
    
    /**
     * 사용자 표시명 가져오기
     */
    fun getDisplayName(): String? {
        return sharedPreferences.getString(KEY_DISPLAY_NAME, null)
    }
    
    /**
     * 세션 타임스탬프 가져오기
     */
    fun getSessionTimestamp(): Long {
        return sharedPreferences.getLong(KEY_SESSION_TIMESTAMP, 0L)
    }
    
    /**
     * 자동 로그인 설정 확인
     */
    fun isAutoLoginEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_LOGIN, false)
    }
    
    /**
     * Foreground Service 활성화 상태 저장
     * 5. 연결 성공해서 Foreground 서비스가 됨과 동시에 세션이 등록된다. Foreground service가 있을때 앱 자동 로그인 세션이 유효한 것이다.
     */
    fun setForegroundServiceActive(active: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FOREGROUND_SERVICE_ACTIVE, active).apply()
        Log.d(TAG, "Foreground Service 상태 저장: $active")
    }
    
    /**
     * Foreground Service 활성화 상태 확인
     */
    fun isForegroundServiceActive(): Boolean {
        return sharedPreferences.getBoolean(KEY_FOREGROUND_SERVICE_ACTIVE, false)
    }
    
    /**
     * 디바이스 연결 상태 저장
     */
    fun setDeviceConnected(connected: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DEVICE_CONNECTED, connected).apply()
        Log.d(TAG, "디바이스 연결 상태 저장: $connected")
    }
    
    /**
     * 디바이스 연결 상태 확인
     */
    fun isDeviceConnected(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEVICE_CONNECTED, false)
    }
    
    /**
     * 유효한 세션인지 확인 (Foreground Service + 디바이스 연결)
     */
    fun isValidSession(): Boolean {
        val isForegroundActive = isForegroundServiceActive()
        val isDeviceConnected = isDeviceConnected()
        val isValid = isForegroundActive && isDeviceConnected
        
        Log.d(TAG, "세션 유효성 확인: Foreground=$isForegroundActive, Connected=$isDeviceConnected, Valid=$isValid")
        return isValid
    }
    
    /**
     * 세션 정보 로그 출력
     */
    fun logSessionInfo() {
        Log.d(TAG, "=== 세션 정보 ===")
        Log.d(TAG, "로그인 상태: ${isLoggedIn()}")
        Log.d(TAG, "사용자 모드: ${getUserMode()}")
        Log.d(TAG, "사용자 이메일: ${getUserEmail()}")
        Log.d(TAG, "사용자 UID: ${getUserUid()}")
        Log.d(TAG, "표시명: ${getDisplayName()}")
        Log.d(TAG, "자동 로그인: ${isAutoLoginEnabled()}")
        Log.d(TAG, "세션 시작: ${getSessionTimestamp()}")
        Log.d(TAG, "Foreground Service: ${isForegroundServiceActive()}")
        Log.d(TAG, "디바이스 연결: ${isDeviceConnected()}")
        Log.d(TAG, "세션 유효: ${isValidSession()}")
    }
} 