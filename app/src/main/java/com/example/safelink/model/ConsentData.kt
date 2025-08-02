package com.example.safelink.model

/**
 * 개인정보 수집 동의 데이터
 */
data class ConsentData(
    val isAccepted: Boolean,
    val timestamp: Long,
    val userMode: UserMode
) 