# SafeLink ESP32C6 센서 모니터링 앱

## 프로젝트 개요

SafeLink는 ESP32C6 센서 디바이스를 활용한 실시간 생체 신호 및 환경 모니터링 시스템입니다. 작업자와 관리자 모드를 지원하여 안전한 작업 환경을 제공합니다.

## 주요 기능

### 🏭 작업자 모드
- **실시간 센서 모니터링**: ESP32C6 센서를 통한 생체 신호 및 환경 데이터 수집
- **연결 관리**: 포그라운드 서비스를 통한 안정적인 블루투스 연결 유지
- **위험 상황 감지**: 심박수, 체온, 습도 등을 기반한 위험도 평가
- **긴급 알림**: 위험 상황 발생 시 즉시 알림 전송
- **데이터 로그**: 실시간 센서 데이터 기록 및 표시

### 👨‍💼 관리자 모드
- **작업자 모니터링**: 전체 작업자의 실시간 상태 확인
- **데이터 분석**: 개별 작업자의 센서 데이터 히스토리 조회
- **통계 대시보드**: 전체 작업장의 안전 상태 통계
- **알림 관리**: 위험 상황 발생 시 관리자 알림

## ESP32C6 센서 디바이스 지원

### 지원하는 BLE 서비스
- **Heart Rate Service (0x180D)**: 심박수 측정
- **Health Thermometer Service (0x1809)**: 체온 측정
- **Environmental Sensing Service (0x181A)**: 습도, 압력 측정
- **Custom Sensor Service (0x1810)**: 통합 센서 데이터

### 데이터 형식
- **통합 센서 데이터**: 8바이트 (심박수, 온도, 습도)
- **건강 상태**: 문자열 기반 상태 정보
- **실시간 업데이트**: 1초 간격 데이터 수신

### 디바이스 인식 방법
- **디바이스 이름**: "ESP32C6_Sensor"
- **자동 스캔**: BLE 스캔을 통한 자동 디바이스 발견
- **연결 관리**: 포그라운드 서비스를 통한 안정적 연결

## 작업자 화면 기능

### 실시간 데이터 표시
- **생체 신호**: 심박수, 체온, 배터리 상태
- **환경 센서**: 주변 온도, 습도, 소음, WBGT
- **위험도 평가**: 실시간 위험도 계산 및 표시
- **상태 메시지**: 현재 건강 상태 설명

### 연결 관리
- **ESP32C6 연결**: 원클릭 센서 디바이스 연결
- **연결 해제**: 작업 종료 시 명시적 연결 해제
- **연결 상태**: 실시간 연결 상태 표시
- **자동 재연결**: 연결 끊김 시 자동 재연결 시도

### 위험 모니터링
- **위험도 레벨**: 안전(0) → 주의(1) → 위험(2) → 긴급(3)
- **임계값 설정**: 심박수, 체온, 습도 기반 위험 판정
- **색상 표시**: 위험도에 따른 UI 색상 변경
- **알림 시스템**: 위험 상황 발생 시 즉시 알림

### 긴급 알림
- **긴급 버튼**: 언제든지 긴급 상황 신고 가능
- **센서 데이터 포함**: 현재 센서 상태와 함께 알림 전송
- **관리자 연동**: 관리자에게 즉시 알림 전달

### 데이터 로그
- **실시간 로그**: 센서 데이터 수신 시마다 로그 기록
- **타임스탬프**: 정확한 시간 정보 포함
- **스크롤 뷰**: 최신 데이터부터 표시
- **자동 정리**: 로그 길이 제한으로 메모리 관리

## 서비스 아키텍처

### BluetoothService (Foreground Service)
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  Service Layer  │    │  Data Layer     │
│                 │    │                 │    │                 │
│ SensorMonitor   │◄──►│ BluetoothService│◄──►│ ESP32C6 Sensor  │
│ Fragment        │    │ (Foreground)    │    │ (BLE Device)    │
│                 │    │                 │    │                 │
│ DeviceDiscovery │◄──►│ BleManager      │◄──►│ ProtocolParser  │
│ Fragment        │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 데이터 흐름
1. **ESP32C6 센서** → **BLE 연결** → **BluetoothService**
2. **BluetoothService** → **StateFlow** → **UI 업데이트**
3. **사용자 액션** → **UI** → **BluetoothService** → **센서 제어**

### 서비스 생명주기
- **시작**: 앱 실행 시 자동 시작
- **연결**: 사용자가 ESP32C6 연결 시도
- **모니터링**: 실시간 데이터 수신 및 처리
- **종료**: 사용자가 연결 해제 또는 앱 종료

## 사용 방법

### 작업자 모드 시작
1. 앱 실행 후 "작업자" 선택
2. 로그인 또는 회원가입
3. 작업자 화면에서 "ESP32C6 연결" 버튼 클릭
4. 디바이스 발견 화면에서 ESP32C6 센서 선택
5. 연결 성공 시 실시간 데이터 확인

### 센서 모니터링
- **실시간 데이터**: 화면에 실시간 센서 값 표시
- **위험도 확인**: 위험도 레벨과 상태 메시지 확인
- **데이터 로그**: 하단의 실시간 데이터 로그 확인

### 작업 종료
1. "연결 해제" 버튼 클릭
2. ESP32C6 센서 연결 해제
3. 백 버튼으로 로그인 화면으로 이동

## 프로젝트 구조

```
safelink_app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/safelink/
│   │   │   ├── bluetooth/           # BLE 통신 관련
│   │   │   │   ├── BleConstants.kt  # BLE UUID 상수
│   │   │   │   ├── BleManager.kt    # BLE 연결 관리
│   │   │   │   └── ProtocolParser.kt # 데이터 파싱
│   │   │   ├── model/               # 데이터 모델
│   │   │   │   ├── SensorData.kt    # 센서 데이터
│   │   │   │   ├── UserMode.kt      # 사용자 모드
│   │   │   │   └── RiskLevel.kt     # 위험도 레벨
│   │   │   ├── service/             # 서비스 레이어
│   │   │   │   ├── BluetoothService.kt # 포그라운드 서비스
│   │   │   │   ├── FirebaseService.kt  # Firebase 연동
│   │   │   │   └── AdminService.kt     # 관리자 서비스
│   │   │   └── ui/                  # UI 레이어
│   │   │       ├── SensorMonitorFragment.kt    # 작업자 화면
│   │   │       ├── DeviceDiscoveryFragment.kt  # 디바이스 발견
│   │   │       ├── AdminDashboardFragment.kt   # 관리자 대시보드
│   │   │       └── adapter/         # RecyclerView 어댑터
│   │   └── res/                     # 리소스 파일
│   │       ├── layout/              # 레이아웃 XML
│   │       ├── values/              # 문자열, 색상 등
│   │       └── drawable/            # 아이콘, 이미지
│   └── build.gradle.kts             # 앱 빌드 설정
├── build.gradle.kts                 # 프로젝트 빌드 설정
└── README.md                        # 프로젝트 문서
```

## 기술 스택

### Android 개발
- **언어**: Kotlin
- **아키텍처**: MVVM + Repository Pattern
- **비동기 처리**: Kotlin Coroutines + Flow
- **UI**: Jetpack Compose (예정) + XML Layout
- **네비게이션**: Navigation Component

### 블루투스 통신
- **BLE**: Android Bluetooth Low Energy API
- **GATT**: Generic Attribute Profile
- **UUID**: 표준 및 커스텀 서비스 UUID
- **데이터 파싱**: Little Endian 바이트 처리

### 백엔드 서비스
- **Firebase**: Authentication, Realtime Database, Cloud Messaging
- **실시간 데이터**: Firebase Realtime Database
- **알림**: Firebase Cloud Messaging
- **인증**: Firebase Authentication

### 서비스 관리
- **Foreground Service**: 백그라운드 BLE 연결 유지
- **StateFlow**: 반응형 데이터 스트림
- **Lifecycle**: Android Lifecycle 관리
- **Permission**: 런타임 권한 처리

## 설치 및 실행

### 필수 요구사항
- Android 6.0 (API 23) 이상
- 블루투스 4.0 이상 지원 기기
- ESP32C6 센서 디바이스

### 빌드 방법
```bash
# 프로젝트 클론
git clone [repository-url]
cd safelink_app

# 의존성 설치
./gradlew build

# 디버그 APK 빌드
./gradlew assembleDebug
```

### 권한 설정
앱 실행 시 다음 권한이 필요합니다:
- 블루투스 연결 및 스캔
- 위치 정보 (BLE 스캔용)
- 알림 (Android 13+)
- 포그라운드 서비스

## 개발 가이드

### ESP32C6 센서 연동
1. **BLE 서비스 구현**: 표준 서비스 UUID 사용
2. **데이터 형식**: 8바이트 통합 데이터 구조
3. **연결 관리**: 안정적인 GATT 연결 유지
4. **에러 처리**: 연결 실패 시 재시도 로직

### UI 개발
1. **반응형 디자인**: StateFlow 기반 UI 업데이트
2. **사용자 경험**: 직관적인 연결 및 모니터링 인터페이스
3. **접근성**: 색상 대비 및 텍스트 크기 고려
4. **성능**: 효율적인 데이터 처리 및 메모리 관리

### 서비스 개발
1. **포그라운드 서비스**: 안정적인 백그라운드 실행
2. **데이터 스트림**: StateFlow를 통한 실시간 데이터 전달
3. **에러 핸들링**: 네트워크 및 하드웨어 오류 처리
4. **리소스 관리**: 메모리 누수 방지 및 효율적 리소스 사용

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 연락처

프로젝트 관련 문의사항이 있으시면 이슈를 생성해주세요.
