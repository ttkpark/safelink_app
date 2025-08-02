package com.example.safelink.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.safelink.model.HistoryEntry
import com.example.safelink.model.RiskLevel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object ExportService {
    private const val TAG = "ExportService"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * CSV 파일로 데이터 내보내기
     */
    fun exportToCSV(
        context: Context,
        historyEntries: List<HistoryEntry>,
        userId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        try {
            val timestamp = fileDateFormat.format(Date())
            val fileName = "safelink_history_${userId}_$timestamp.csv"
            
            // 앱의 캐시 디렉토리에 파일 생성
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)
            
            // CSV 헤더 작성
            writer.append("타임스탬프,심박수(BPM),체온(°C),주변온도(°C),습도(%),소음(dB),WBGT(°C),배터리(%),위험도,위치\n")
            
            // 데이터 작성
            historyEntries.forEach { entry ->
                val sensorData = entry.sensorData
                val riskLevelText = when (entry.riskLevel) {
                    RiskLevel.SAFE -> "안전"
                    RiskLevel.WARNING -> "주의"
                    RiskLevel.DANGER -> "위험"
                    RiskLevel.EMERGENCY -> "긴급"
                }
                
                val location = entry.location ?: "알 수 없음"
                
                writer.append("${dateFormat.format(Date(entry.timestamp))},")
                writer.append("${sensorData.heartRate},")
                writer.append("${sensorData.bodyTemperature},")
                writer.append("${sensorData.ambientTemperature},")
                writer.append("${sensorData.humidity},")
                writer.append("${sensorData.noiseLevel},")
                writer.append("${sensorData.wbgt},")
                writer.append("${sensorData.batteryLevel},")
                writer.append("$riskLevelText,")
                writer.append("$location\n")
            }
            
            writer.flush()
            writer.close()
            
            Log.d(TAG, "CSV 파일 생성 완료: ${file.absolutePath}")
            callback(true, file.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "CSV 내보내기 실패", e)
            callback(false, e.message)
        }
    }

    /**
     * 파일 공유 인텐트 생성
     */
    fun createShareIntent(context: Context, filePath: String): Intent {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SafeLink 히스토리 데이터")
            putExtra(Intent.EXTRA_TEXT, "SafeLink 사용자 히스토리 데이터입니다.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 파일 삭제
     */
    fun deleteExportedFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "내보낸 파일 삭제 완료: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "파일 삭제 실패", e)
        }
    }

    /**
     * 파일 크기 포맷팅
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
} 