package com.example.safelink.bluetooth

import com.example.safelink.model.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

/**
 * BLE 데이터 프로토콜 파서
 * ESP32C6 센서 디바이스와의 통신을 위한 데이터 변환 유틸리티
 */
object ProtocolParser {
    
    private const val TAG = "ProtocolParser"
    
    // 비트 연산 유틸리티 함수들
    private fun Byte.toUnsignedInt(): Int = this.toInt() and 0xFF
    private fun Byte.toUnsignedLong(): Long = this.toLong() and 0xFFL
    
    // SafeLink 프로토콜 상수
    const val START_BYTE = 0xAA.toByte()
    const val END_BYTE = 0x55.toByte()
    const val DEVICE_TYPE_BAND = 0x01.toByte()
    const val DEVICE_TYPE_HUB = 0x02.toByte()
    private const val MIN_PACKET_SIZE = 6 // START + LENGTH + DEVICE_TYPE + CHECKSUM + END
    
    private val buffer = mutableListOf<Byte>()
    
    /**
     * 16비트 값을 실제 값으로 변환
     */
    fun convertTemperature(rawValue: Int): Float {
        return rawValue / 10.0f // 0.1°C 단위
    }
    
    fun convertHumidity(rawValue: Int): Float {
        return rawValue / 10.0f // 0.1% 단위
    }
    
    fun convertPressure(rawValue: Int): Float {
        return rawValue / 10.0f // 0.1 Pa 단위
    }
    
    /**
     * WBGT 계산 (간단한 근사치)
     */
    fun calculateWBGT(temperature: Float, humidity: Float): Float {
        // WBGT = 0.7 × Tw + 0.2 × Tg + 0.1 × Ta
        // 여기서는 간단히 Ta(건구온도)와 습도를 이용한 근사치 사용
        return temperature + (humidity / 100f) * 2f
    }
    
    /**
     * 바이트 배열에서 16비트 값 추출 (Little Endian)
     */
    fun extractUInt16(data: ByteArray, offset: Int): Int {
        if (offset + 1 >= data.size) {
            Log.w(TAG, "데이터 크기가 부족합니다: offset=$offset, size=${data.size}")
            return 0
        }
        return (data[offset + 1].toUnsignedInt() shl 8) or data[offset].toUnsignedInt()
    }
    
    /**
     * 바이트 배열에서 32비트 값 추출 (Little Endian)
     */
    fun extractUInt32(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) {
            Log.w(TAG, "데이터 크기가 부족합니다: offset=$offset, size=${data.size}")
            return 0L
        }
        return (data[offset + 3].toUnsignedLong() shl 24) or
               (data[offset + 2].toUnsignedLong() shl 16) or
               (data[offset + 1].toUnsignedLong() shl 8) or
               data[offset].toUnsignedLong()
    }
    
    /**
     * 센서 데이터 파싱 (public 메서드)
     * BluetoothService에서 호출하는 메서드
     */
    fun parseSensorData(data: ByteArray): SensorData? {
        return try {
            // ESP32C6 통합 센서 데이터 파싱
            val integratedData = parseIntegratedSensorData(data)
            if (integratedData != null) {
                val (heartRate, temperature, humidity) = integratedData
                SensorData(
                    deviceId = "",
                    timestamp = System.currentTimeMillis(),
                    heartRate = heartRate,
                    bodyTemperature = temperature,
                    ambientTemperature = temperature,
                    humidity = humidity,
                    noiseLevel = 0f,
                    wbgt = calculateWBGT(temperature, humidity),
                    batteryLevel = 100,
                    isConnected = true,
                    userId = "",
                    deviceType = "ESP32C6"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "센서 데이터 파싱 실패", e)
            null
        }
    }
    
    /**
     * 통합 센서 데이터 파싱 (8바이트)
     * 가이드 문서 기준: [심박수2바이트][온도2바이트][습도2바이트][예약2바이트]
     */
    fun parseIntegratedSensorData(data: ByteArray): Triple<Int, Float, Float>? {
        if (data.size < BleConstants.INTEGRATED_DATA_SIZE) {
            Log.w(TAG, "통합 센서 데이터 크기가 부족합니다: ${data.size} 바이트")
            return null
        }
        
        try {
            // 심박수 (2바이트)
            val heartRate = extractUInt16(data, BleConstants.HEART_RATE_OFFSET)
            
            // 온도 (2바이트) - 0.1°C 단위
            val tempRaw = extractUInt16(data, BleConstants.TEMPERATURE_OFFSET)
            val temperature = convertTemperature(tempRaw)
            
            // 습도 (2바이트) - 0.1% 단위
            val humidityRaw = extractUInt16(data, BleConstants.HUMIDITY_OFFSET)
            val humidity = convertHumidity(humidityRaw)
            
            Log.d(TAG, "통합 센서 데이터 파싱: HR=$heartRate BPM, Temp=${temperature}°C, Humidity=${humidity}%")
            
            return Triple(heartRate, temperature, humidity)
            
        } catch (e: Exception) {
            Log.e(TAG, "통합 센서 데이터 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 표준 Heart Rate 데이터 파싱
     */
    fun parseHeartRateData(data: ByteArray): Int? {
        if (data.size < 2) {
            Log.w(TAG, "심박수 데이터 크기가 부족합니다: ${data.size} 바이트")
            return null
        }
        
        try {
            val flags = data[0]
            val heartRate = if ((flags.toInt() and 0x01) == 0) {
                // 8비트 심박수
                data[1].toUnsignedInt()
            } else {
                // 16비트 심박수
                if (data.size >= 3) {
                    (data[2].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
                } else {
                    Log.w(TAG, "16비트 심박수 데이터 크기가 부족합니다")
                    return null
                }
            }
            
            Log.d(TAG, "심박수 데이터 파싱: $heartRate BPM")
            return heartRate
            
        } catch (e: Exception) {
            Log.e(TAG, "심박수 데이터 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 표준 Temperature 데이터 파싱 (IEEE 11073-20601)
     */
    fun parseTemperatureData(data: ByteArray): Float? {
        if (data.size < 5) {
            Log.w(TAG, "온도 데이터 크기가 부족합니다: ${data.size} 바이트")
            return null
        }
        
        try {
            val flags = data[0]
            val tempRaw = extractUInt32(data, 1).toInt()
            val temperature = tempRaw / 100.0f // 0.01°C 단위를 실제 온도로 변환
            
            Log.d(TAG, "온도 데이터 파싱: ${temperature}°C")
            return temperature
            
        } catch (e: Exception) {
            Log.e(TAG, "온도 데이터 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 표준 Humidity 데이터 파싱
     */
    fun parseHumidityData(data: ByteArray): Float? {
        if (data.size < 5) {
            Log.w(TAG, "습도 데이터 크기가 부족합니다: ${data.size} 바이트")
            return null
        }
        
        try {
            val flags = data[0]
            val humidityRaw = extractUInt32(data, 1).toInt()
            val humidity = humidityRaw / 100.0f // 0.01% 단위를 실제 습도로 변환
            
            Log.d(TAG, "습도 데이터 파싱: ${humidity}%")
            return humidity
            
        } catch (e: Exception) {
            Log.e(TAG, "습도 데이터 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 표준 Pressure 데이터 파싱
     */
    fun parsePressureData(data: ByteArray): Float? {
        if (data.size < 5) {
            Log.w(TAG, "압력 데이터 크기가 부족합니다: ${data.size} 바이트")
            return null
        }
        
        try {
            val flags = data[0]
            val pressureRaw = extractUInt32(data, 1).toInt()
            val pressure = pressureRaw / 10.0f // 0.1 Pa 단위를 실제 압력으로 변환
            
            Log.d(TAG, "압력 데이터 파싱: ${pressure} Pa")
            return pressure
            
        } catch (e: Exception) {
            Log.e(TAG, "압력 데이터 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 건강 상태 문자열 파싱
     */
    fun parseHealthStatus(data: ByteArray): String? {
        try {
            val status = String(data, Charsets.UTF_8)
            Log.d(TAG, "건강 상태 파싱: $status")
            return status
            
        } catch (e: Exception) {
            Log.e(TAG, "건강 상태 파싱 실패", e)
            return null
        }
    }
    
    /**
     * 건강 상태를 한글로 변환
     */
    fun translateHealthStatus(status: String): String {
        return when (status) {
            BleConstants.HealthStatus.NORMAL -> "정상"
            BleConstants.HealthStatus.ELEVATED_HEART_RATE -> "심박수 상승"
            BleConstants.HealthStatus.TEMPERATURE_ALERT -> "온도 경고"
            BleConstants.HealthStatus.HUMIDITY_ALERT -> "습도 경고"
            BleConstants.HealthStatus.WARNING -> "주의"
            BleConstants.HealthStatus.CRITICAL -> "위험"
            else -> "알 수 없음"
        }
    }
    
    /**
     * 데이터 유효성 검사
     */
    fun validateHeartRate(heartRate: Int): Boolean {
        return heartRate in 30..220 // 일반적인 심박수 범위
    }
    
    fun validateTemperature(temperature: Float): Boolean {
        return temperature in 20.0f..50.0f // 일반적인 체온 범위
    }
    
    fun validateHumidity(humidity: Float): Boolean {
        return humidity in 0.0f..100.0f // 습도 범위
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환 (디버깅용)
     */
    fun bytesToHex(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
    
    /**
     * 16진수 문자열을 바이트 배열로 변환
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
    
    /**
     * 블루투스로부터 받은 바이트 데이터를 파싱하여 SensorData로 변환
     */
    fun parseData(data: ByteArray): Flow<SensorData?> = flow {
        // 버퍼에 데이터 추가
        buffer.addAll(data.toList())
        
        // 완전한 패킷이 있는지 확인하고 파싱
        while (buffer.size >= MIN_PACKET_SIZE) {
            val packet = findAndExtractPacket()
            if (packet != null) {
                val sensorData = parsePacket(packet)
                if (sensorData != null) {
                    emit(sensorData)
                }
            } else {
                break // 완전한 패킷이 없음
            }
        }
    }
    
    /**
     * 버퍼에서 완전한 패킷을 찾아 추출
     */
    private fun findAndExtractPacket(): ByteArray? {
        // START 바이트 찾기
        val startIndex = buffer.indexOf(START_BYTE)
        if (startIndex == -1) {
            buffer.clear() // START 바이트가 없으면 버퍼 클리어
            return null
        }
        
        // 불완전한 데이터 제거
        if (startIndex > 0) {
            buffer.removeAt(0)
            return null
        }
        
        // 최소 패킷 크기 확인
        if (buffer.size < MIN_PACKET_SIZE) {
            return null
        }
        
        // 데이터 길이 확인
        val dataLength = buffer[1].toUnsignedInt()
        val totalPacketSize = MIN_PACKET_SIZE + dataLength
        
        if (buffer.size < totalPacketSize) {
            return null // 완전한 패킷이 아님
        }
        
        // 패킷 추출
        val packet = buffer.take(totalPacketSize).toByteArray()
        buffer.removeAll(packet.toList())
        
        return packet
    }
    
    /**
     * 패킷을 파싱하여 SensorData 생성
     */
    private fun parsePacket(packet: ByteArray): SensorData? {
        try {
            // 패킷 구조 검증
            if (packet[0] != START_BYTE || packet[packet.size - 1] != END_BYTE) {
                return null
            }
            
            val dataLength = packet[1].toUnsignedInt()
            val deviceType = packet[2]
            val dataStart = 3
            val checksumIndex = dataStart + dataLength
            
            // 체크섬 검증
            val calculatedChecksum = calculateChecksum(packet, dataStart, dataLength)
            val receivedChecksum = packet[checksumIndex]
            
            if (calculatedChecksum != receivedChecksum) {
                return null // 체크섬 불일치
            }
            
            // 데이터 파싱
            val data = packet.slice(dataStart until checksumIndex)
            return parseSensorData(data, deviceType)
            
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 센서 데이터 파싱
     */
    private fun parseSensorData(data: List<Byte>, deviceType: Byte): SensorData {
        val buffer = ByteBuffer.wrap(data.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        
        val deviceId = generateDeviceId(deviceType)
        val timestamp = System.currentTimeMillis()
        
        return when (deviceType) {
            DEVICE_TYPE_BAND -> {
                // Band 데이터: 심박수, 체온, 배터리
                val heartRate = buffer.getShort().toInt()
                val bodyTemperature = buffer.getFloat()
                val batteryLevel = buffer.get().toUnsignedInt()
                
                SensorData(
                    deviceId = deviceId,
                    timestamp = timestamp,
                    heartRate = heartRate,
                    bodyTemperature = bodyTemperature,
                    batteryLevel = batteryLevel,
                    isConnected = true,
                    deviceType = "band"
                )
            }
            
            DEVICE_TYPE_HUB -> {
                // Hub 데이터: 주변 온도, 습도, 소음, 배터리
                val ambientTemperature = buffer.getFloat()
                val humidity = buffer.getFloat()
                val noiseLevel = buffer.getFloat()
                val batteryLevel = buffer.get().toUnsignedInt()
                
                val wbgt = ambientTemperature + (humidity / 100f) * 2f
                
                SensorData(
                    deviceId = deviceId,
                    timestamp = timestamp,
                    ambientTemperature = ambientTemperature,
                    humidity = humidity,
                    noiseLevel = noiseLevel,
                    wbgt = wbgt,
                    batteryLevel = batteryLevel,
                    isConnected = true,
                    deviceType = "hub"
                )
            }
            
            else -> {
                // 알 수 없는 디바이스 타입
                SensorData(
                    deviceId = deviceId,
                    timestamp = timestamp,
                    deviceType = "unknown"
                )
            }
        }
    }
    
    /**
     * 체크섬 계산
     */
    private fun calculateChecksum(data: ByteArray, start: Int, length: Int): Byte {
        var checksum: Byte = 0
        for (i in start until start + length) {
            checksum = (checksum.toInt() xor data[i].toInt()).toByte()
        }
        return checksum
    }
    
    /**
     * 디바이스 ID 생성
     */
    private fun generateDeviceId(deviceType: Byte): String {
        val prefix = when (deviceType) {
            DEVICE_TYPE_BAND -> "BAND"
            DEVICE_TYPE_HUB -> "HUB"
            else -> "UNKNOWN"
        }
        return "$prefix-${System.currentTimeMillis()}"
    }
    
    /**
     * 테스트용 더미 데이터 생성
     */
    fun createDummyData(deviceType: Byte): ByteArray {
        val data = mutableListOf<Byte>()
        
        when (deviceType) {
            DEVICE_TYPE_BAND -> {
                // 심박수 (2바이트), 체온 (4바이트), 배터리 (1바이트)
                data.addAll(shortToBytes(75)) // 심박수 75 BPM
                data.addAll(floatToBytes(36.8f)) // 체온 36.8°C
                data.add(85) // 배터리 85%
            }
            
            DEVICE_TYPE_HUB -> {
                // 주변 온도 (4바이트), 습도 (4바이트), 소음 (4바이트), 배터리 (1바이트)
                data.addAll(floatToBytes(28.5f)) // 주변 온도 28.5°C
                data.addAll(floatToBytes(65.0f)) // 습도 65%
                data.addAll(floatToBytes(45.0f)) // 소음 45dB
                data.add(90) // 배터리 90%
            }
        }
        
        val dataLength = data.size.toByte()
        val deviceTypeByte = deviceType
        val checksum = calculateChecksum(data.toByteArray(), 0, data.size)
        
        return byteArrayOf(START_BYTE, dataLength, deviceTypeByte) + data.toByteArray() + byteArrayOf(checksum, END_BYTE)
    }
    
    private fun shortToBytes(value: Short): List<Byte> {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array().toList()
    }
    
    private fun floatToBytes(value: Float): List<Byte> {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array().toList()
    }
} 