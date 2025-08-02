package com.example.safelink.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    /**
     * 블루투스 관련 권한 목록 반환 (Android 버전별)
     */
    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 11 이하
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    /**
     * 모든 필요한 권한 목록 반환
     */
    fun getAllRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // 블루투스 권한
        permissions.addAll(getBluetoothPermissions().toList())
        
        // 알림 권한 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return permissions.toTypedArray()
    }
    
    /**
     * 특정 권한이 허용되었는지 확인
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 모든 블루투스 권한이 허용되었는지 확인
     */
    fun hasAllBluetoothPermissions(context: Context): Boolean {
        return getBluetoothPermissions().all { hasPermission(context, it) }
    }
    
    /**
     * 모든 필요한 권한이 허용되었는지 확인
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getAllRequiredPermissions().all { hasPermission(context, it) }
    }
    
    /**
     * 권한 설명이 필요한지 확인
     */
    fun shouldShowPermissionRationale(context: Context, permission: String): Boolean {
        return if (context is android.app.Activity) {
            context.shouldShowRequestPermissionRationale(permission)
        } else {
            false
        }
    }
    
    /**
     * 권한 설명 메시지 반환
     */
    fun getPermissionRationaleMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.BLUETOOTH_CONNECT -> 
                "SafeLink 장치와 연결하기 위해 블루투스 연결 권한이 필요합니다."
            Manifest.permission.BLUETOOTH_SCAN -> 
                "주변 SafeLink 장치를 찾기 위해 블루투스 검색 권한이 필요합니다."
            Manifest.permission.ACCESS_FINE_LOCATION -> 
                "블루투스 장치 검색을 위해 위치 권한이 필요합니다."
            Manifest.permission.POST_NOTIFICATIONS -> 
                "위험 상황 알림을 받기 위해 알림 권한이 필요합니다."
            else -> "이 기능을 사용하기 위해 권한이 필요합니다."
        }
    }
} 