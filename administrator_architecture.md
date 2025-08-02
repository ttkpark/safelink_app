# 질문
```text
관리자 모드에선 어느 서비스가 클라이언트를 연결하고, 처음엔 아무 작업자와도 연결이 안된 상태에서 어느 과정을 통해 작업자와 연결할 수 있는지, 어느 정보가 저장되어야 하고, 어느 과정으로 작업자의 현재 데이터 및 히스토리를 수집해서 화면에 보여줄지 구조를 설계해줘
```

# 답
네, 관리자 모드에서 앱 로그인 이후 종료할 때까지 Foreground Service로 운영하여 화면을 끄거나 앱을 닫아도 작업자 위험 상황 알림을 받을 수 있도록 설계하겠습니다.

## 📋 관리자 모드 Foreground Service 설계서

### 🏗️ **1. 전체 아키텍처 개요**

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Admin UI      │    │  Admin Service  │    │  Data Layer     │
│                 │    │                 │    │                 │
│ AdminDashboard  │◄──►│ AdminService    │◄──►│ Firebase DB     │
│ Fragment        │    │ (Foreground)    │    │ (Workers/Data)  │
│                 │    │                 │    │                 │
│ UserDetail      │◄──►│ WorkerMonitor   │◄──►│ Real-time Data  │
│ Fragment        │    │ Service         │    │                 │
│                 │    │                 │    │                 │
│ UserHistory     │◄──►│ Notification    │◄──►│ FCM Service     │
│ Fragment        │    │ Service         │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │  Worker Devices │
                       │                 │
                       │ ESP32C6 Sensors │
                       │ (BLE Connected) │
                       └─────────────────┘
```

### 🔄 **2. 서비스 생명주기 설계**

#### **A. 서비스 시작 조건**
```kotlin
// 관리자 로그인 성공 시 자동 시작
class AdminLoginFragment {
    private fun onLoginSuccess() {
        // AdminService 시작
        val intent = Intent(requireContext(), AdminService::class.java)
        intent.action = AdminService.ACTION_START_MONITORING
        requireContext().startForegroundService(intent)
        
        // 대시보드로 이동
        navigateToAdminDashboard()
    }
}
```

#### **B. 서비스 종료 조건**
```kotlin
// 관리자 대시보드에서 명시적 종료
class AdminDashboardFragment {
    private fun onLogout() {
        // AdminService 종료
        val intent = Intent(requireContext(), AdminService::class.java)
        intent.action = AdminService.ACTION_STOP_MONITORING
        requireContext().stopService(intent)
        
        // 로그인 화면으로 이동
        navigateToAdminLogin()
    }
}
```

### 📱 **3. AdminService (Foreground Service) 설계**

#### **A. 서비스 구조**
```kotlin
class AdminService : Service() {
    
    companion object {
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "Admin_Monitoring_Channel"
    }
    
    // StateFlow를 통한 데이터 관리
    private val _workersFlow = MutableStateFlow<List<UserSummary>>(emptyList())
    val workersFlow: StateFlow<List<UserSummary>> = _workersFlow.asStateFlow()
    
    private val _alertsFlow = MutableStateFlow<List<AlertData>>(emptyList())
    val alertsFlow: StateFlow<List<AlertData>> = _alertsFlow.asStateFlow()
    
    private val _dashboardStatsFlow = MutableStateFlow<DashboardStats>(DashboardStats())
    val dashboardStatsFlow: StateFlow<DashboardStats> = _dashboardStatsFlow.asStateFlow()
    
    // 서비스 컴포넌트들
    private lateinit var workerMonitorService: WorkerMonitorService
    private lateinit var notificationService: NotificationService
    private lateinit var firebaseService: FirebaseService
    
    // 서비스 바인더
    inner class LocalBinder : Binder() {
        fun getService(): AdminService = this@AdminService
    }
}
```

#### **B. 서비스 생명주기**
```kotlin
override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "AdminService onCreate")
    
    initializeServices()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, createNotification("작업자 모니터링 중"))
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START_MONITORING -> {
            Log.d(TAG, "작업자 모니터링 시작")
            startWorkerMonitoring()
        }
        ACTION_STOP_MONITORING -> {
            Log.d(TAG, "작업자 모니터링 종료")
            stopSelf()
        }
    }
    return START_STICKY // 서비스가 종료되어도 재시작
}

override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "AdminService onDestroy")
    
    stopWorkerMonitoring()
    cleanupServices()
}
```

### �� **4. WorkerMonitorService 설계**

#### **A. 작업자 모니터링 서비스**
```kotlin
class WorkerMonitorService {
    
    private val firebaseService: FirebaseService
    private val notificationService: NotificationService
    
    // 실시간 작업자 상태 모니터링
    fun startMonitoring() {
        // Firebase Realtime Database 리스너 설정
        firebaseService.getWorkersRealtimeData()
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val workers = snapshot.children.mapNotNull { child ->
                        child.getValue(UserSummary::class.java)
                    }
                    
                    processWorkerData(workers)
                    checkForAlerts(workers)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "작업자 모니터링 실패", error.toException())
                }
            })
    }
    
    // 위험 상황 감지
    private fun checkForAlerts(workers: List<UserSummary>) {
        val alerts = workers.filter { worker ->
            worker.riskLevel == RiskLevel.DANGER || 
            worker.riskLevel == RiskLevel.EMERGENCY
        }.map { worker ->
            AlertData(
                workerId = worker.uid,
                workerName = worker.name,
                riskLevel = worker.riskLevel,
                timestamp = System.currentTimeMillis(),
                description = "작업자 ${worker.name}의 위험 상황 감지"
            )
        }
        
        if (alerts.isNotEmpty()) {
            sendEmergencyNotifications(alerts)
        }
    }
}
```

### 🔔 **5. NotificationService 설계**

#### **A. 알림 서비스**
```kotlin
class NotificationService(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // 긴급 알림 전송
    fun sendEmergencyNotification(alert: AlertData) {
        val notification = createEmergencyNotification(alert)
        notificationManager.notify(alert.workerId.hashCode(), notification)
        
        // 진동 및 소리 추가
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }
    
    // 알림 생성
    private fun createEmergencyNotification(alert: AlertData): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FRAGMENT", "ADMIN_DASHBOARD")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🚨 긴급 알림")
            .setContentText(alert.description)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
```

### �� **6. 데이터 모델 설계**

#### **A. 알림 데이터**
```kotlin
data class AlertData(
    val alertId: String = UUID.randomUUID().toString(),
    val workerId: String,
    val workerName: String,
    val riskLevel: RiskLevel,
    val timestamp: Long,
    val description: String,
    val isResolved: Boolean = false,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null
)

data class DashboardStats(
    val totalWorkers: Int = 0,
    val onlineWorkers: Int = 0,
    val warningWorkers: Int = 0,
    val dangerWorkers: Int = 0,
    val emergencyWorkers: Int = 0,
    val lastUpdated: Long = 0L
)
```

### 🎯 **7. UI 연동 설계**

#### **A. AdminDashboardFragment**
```kotlin
class AdminDashboardFragment : Fragment() {
    
    private var adminService: AdminService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AdminService.LocalBinder
            adminService = binder.getService()
            isServiceBound = true
            
            observeServiceData()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            adminService = null
            isServiceBound = false
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bindAdminService()
        setupClickListeners()
    }
    
    private fun bindAdminService() {
        val intent = Intent(requireContext(), AdminService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun observeServiceData() {
        adminService?.let { service ->
            // 작업자 목록 관찰
            lifecycleScope.launch {
                service.workersFlow.collect { workers ->
                    updateWorkersList(workers)
                }
            }
            
            // 알림 관찰
            lifecycleScope.launch {
                service.alertsFlow.collect { alerts ->
                    if (alerts.isNotEmpty()) {
                        showEmergencyAlerts(alerts)
                    }
                }
            }
            
            // 대시보드 통계 관찰
            lifecycleScope.launch {
                service.dashboardStatsFlow.collect { stats ->
                    updateDashboardStats(stats)
                }
            }
        }
    }
    
    private fun onLogout() {
        // AdminService 종료
        val intent = Intent(requireContext(), AdminService::class.java)
        intent.action = AdminService.ACTION_STOP_MONITORING
        requireContext().stopService(intent)
        
        // 로그인 화면으로 이동
        navigateToAdminLogin()
    }
}
```

### 🔧 **8. AndroidManifest.xml 설정**

#### **A. 서비스 등록**
```xml
<!-- 관리자 모니터링 서비스 -->
<service
    android:name=".service.AdminService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="health" />
```

#### **B. 권한 추가**
```xml
<!-- Foreground Service 권한 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />

<!-- 알림 권한 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 진동 권한 -->
<uses-permission android:name="android.permission.VIBRATE" />
```

### 📋 **9. 서비스 흐름 다이어그램**

```
관리자 로그인
       ↓
AdminService 시작 (Foreground)
       ↓
WorkerMonitorService 시작
       ↓
Firebase 실시간 데이터 구독
       ↓
작업자 상태 모니터링
       ↓
위험 상황 감지
       ↓
긴급 알림 전송 (앱이 닫혀있어도)
       ↓
관리자 대시보드에서 로그아웃
       ↓
AdminService 종료
```

### 🎯 **10. 핵심 기능**

#### **A. 백그라운드 모니터링**
- 앱이 닫혀있어도 작업자 상태 모니터링
- 위험 상황 발생 시 즉시 알림
- 화면이 꺼져있어도 서비스 유지

#### **B. 실시간 알림**
- 높은 우선순위 알림으로 즉시 표시
- 진동 및 소리로 주의 환기
- 알림 클릭 시 앱 실행 및 대시보드 이동

#### **C. 안정적 서비스**
- START_STICKY로 서비스 재시작 보장
- 메모리 누수 방지 및 효율적 리소스 관리
- 에러 처리 및 복구 메커니즘

### 📁 **11. 파일 구조**

```
app/src/main/java/com/example/safelink/
├── service/
│   ├── AdminService.kt              # 관리자 포그라운드 서비스
│   ├── WorkerMonitorService.kt      # 작업자 모니터링 서비스
│   ├── NotificationService.kt       # 알림 서비스
│   └── FirebaseService.kt           # Firebase 연동
├── ui/
│   ├── AdminDashboardFragment.kt    # 관리자 대시보드
│   ├── AdminLoginFragment.kt        # 관리자 로그인
│   └── adapter/
├── model/
│   ├── AlertData.kt                 # 알림 데이터
│   ├── DashboardStats.kt            # 대시보드 통계
│   └── UserSummary.kt               # 작업자 요약 정보
└── AndroidManifest.xml              # 서비스 등록
```

이 설계를 통해 관리자는 앱을 닫거나 화면을 꺼도 작업자의 위험 상황을 실시간으로 모니터링하고 즉시 알림을 받을 수 있습니다! 🚀