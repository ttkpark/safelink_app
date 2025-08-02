package com.example.safelink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safelink.MainActivity
import com.example.safelink.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * SafeLink Firebase Cloud Messaging 서비스
 * 푸시 알림을 처리하고 관리자로부터의 메시지를 수신
 */
class SafeLinkFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "SafeLinkFCMService"
        private const val CHANNEL_ID = "SafeLink_Alerts_Channel"
        private const val CHANNEL_NAME = "SafeLink 알림"
        private const val CHANNEL_DESCRIPTION = "SafeLink 시스템 알림"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "새로운 FCM 토큰: $token")
        
        // 토큰을 Firebase Database에 저장
        FirebaseService.getFCMToken { currentToken ->
            if (currentToken != null) {
                // 사용자 정보에 FCM 토큰 저장
                val userInfo = mapOf(
                    "fcmToken" to currentToken,
                    "lastUpdated" to System.currentTimeMillis()
                )
                FirebaseService.saveUserInfo("worker_001", userInfo)
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "메시지 수신: ${remoteMessage.from}")
        
        // 알림 데이터 확인
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "알림 제목: ${notification.title}")
            Log.d(TAG, "알림 내용: ${notification.body}")
            
            // 로컬 알림 생성
            sendNotification(notification.title, notification.body)
        }
        
        // 데이터 페이로드 확인
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "데이터 페이로드: ${remoteMessage.data}")
            
            // 데이터 기반 알림 생성
            val title = remoteMessage.data["title"] ?: "SafeLink 알림"
            val body = remoteMessage.data["body"] ?: "새로운 알림이 있습니다"
            val alertType = remoteMessage.data["alertType"] ?: "INFO"
            
            sendNotification(title, body, alertType)
        }
    }
    
    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        Log.d(TAG, "메시지 전송 완료: $msgId")
    }
    
    override fun onSendError(msgId: String, exception: Exception) {
        super.onSendError(msgId, exception)
        Log.e(TAG, "메시지 전송 실패: $msgId, 오류: ${exception.message}")
    }
    
    /**
     * 로컬 알림 생성 및 전송
     */
    private fun sendNotification(title: String?, body: String?, alertType: String = "INFO") {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alertType", alertType)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 알림 소리 설정
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // 알림 우선순위 설정
        val priority = when (alertType) {
            "EMERGENCY" -> NotificationCompat.PRIORITY_HIGH
            "WARNING" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        
        // 알림 아이콘 설정
        val icon = when (alertType) {
            "EMERGENCY" -> R.drawable.ic_launcher_foreground // 실제로는 적절한 아이콘 사용
            "WARNING" -> R.drawable.ic_launcher_foreground
            else -> R.drawable.ic_launcher_foreground
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
        
        // 긴급 상황인 경우 진동 추가
        if (alertType == "EMERGENCY") {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 고유한 알림 ID 생성
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "로컬 알림 전송: $title - $body")
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 