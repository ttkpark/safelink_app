package com.example.safelink

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.example.safelink.util.SessionManager

class SafeLinkApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // SessionManager 초기화
        SessionManager.initialize(this)
        
        // Firebase 초기화
        try {
            FirebaseApp.initializeApp(this)
            
            // Realtime Database 오프라인 지원 활성화
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            
            Log.d(TAG, "Firebase 초기화 성공")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase 초기화 실패", e)
        }
        
        // 세션 정보 로그 출력
        SessionManager.logSessionInfo()
    }
    
    companion object {
        private const val TAG = "SafeLinkApplication"
    }
} 