package com.example.safelink.model

data class UserSummary(
    val uid: String,
    val name: String,
    val email: String,
    val department: String,
    val position: String,
    val lastSeen: Long,
    val status: UserStatus,
    val riskLevel: RiskLevel,
    val isOnline: Boolean
)

enum class UserStatus {
    ONLINE, OFFLINE, WARNING, DANGER, EMERGENCY
}

data class DashboardStats(
    val totalUsers: Int,
    val onlineUsers: Int,
    val warningUsers: Int,
    val dangerUsers: Int
) 