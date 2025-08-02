package com.example.safelink.model

data class LoginRequest(
    val email: String,
    val password: String,
    val userMode: UserMode
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: FirebaseUser?
)

data class FirebaseUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val isEmailVerified: Boolean
) 