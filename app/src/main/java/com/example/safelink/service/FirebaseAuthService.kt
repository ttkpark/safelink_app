package com.example.safelink.service

import android.util.Log
import com.example.safelink.model.LoginResponse
import com.example.safelink.model.FirebaseUser
import com.example.safelink.model.UserMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication 서비스
 */
object FirebaseAuthService {
    private const val TAG = "FirebaseAuthService"
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    /**
     * 로그인 수행
     */
    suspend fun signIn(
        email: String,
        password: String,
        userMode: UserMode
    ): LoginResponse {
        return try {
            Log.d(TAG, "로그인 시도: email=$email, userMode=$userMode")
            
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                Log.d(TAG, "Firebase Auth 로그인 성공: ${user.uid}")
                
                // Firebase Database에서 사용자의 실제 userMode 확인
                val userData = database.reference.child("users").child(user.uid).get().await()
                val actualUserMode = userData.child("userMode").getValue(String::class.java)
                
                Log.d(TAG, "요청된 userMode: $userMode, 실제 userMode: $actualUserMode")
                Log.d(TAG, "전체 사용자 데이터: ${userData.value}")
                
                // 사용자 모드 검증
                if (userMode == UserMode.ADMIN) {
                    if (actualUserMode != UserMode.ADMIN.name) {
                        Log.w(TAG, "관리자 권한 없음: 요청=$userMode, 실제=$actualUserMode")
                        auth.signOut()
                        return LoginResponse(
                            success = false,
                            message = "관리자 권한이 없습니다. 관리자 계정으로 로그인해주세요.",
                            user = null
                        )
                    }
                } else if (userMode == UserMode.CUSTOMER) {
                    if (actualUserMode != UserMode.CUSTOMER.name) {
                        Log.w(TAG, "작업자 권한 없음: 요청=$userMode, 실제=$actualUserMode")
                        auth.signOut()
                        return LoginResponse(
                            success = false,
                            message = "작업자 권한이 없습니다. 작업자 계정으로 로그인해주세요.",
                            user = null
                        )
                    }
                }

                val firebaseUser = FirebaseUser(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "",
                    isEmailVerified = user.isEmailVerified
                )

                Log.d(TAG, "로그인 성공: ${user.email} (${actualUserMode})")
                LoginResponse(
                    success = true,
                    message = "로그인 성공",
                    user = firebaseUser
                )
            } else {
                Log.e(TAG, "Firebase Auth 로그인 실패: user is null")
                LoginResponse(
                    success = false,
                    message = "로그인 실패",
                    user = null
                )
            }
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.e(TAG, "존재하지 않는 사용자", e)
            LoginResponse(
                success = false,
                message = "존재하지 않는 사용자입니다.",
                user = null
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(TAG, "잘못된 비밀번호", e)
            LoginResponse(
                success = false,
                message = "비밀번호가 올바르지 않습니다.",
                user = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "로그인 중 오류 발생", e)
            LoginResponse(
                success = false,
                message = "로그인 중 오류가 발생했습니다: ${e.message}",
                user = null
            )
        }
    }

    /**
     * 회원가입 수행
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        userMode: UserMode
    ): LoginResponse {
        return try {
            Log.d(TAG, "회원가입 시도: email=$email, displayName=$displayName, userMode=$userMode")
            
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                Log.d(TAG, "Firebase Auth 회원가입 성공: ${user.uid}")
                
                // 사용자 프로필 업데이트
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates).await()

                // Firebase Database에 사용자 정보 저장
                val userData = mapOf(
                    "uid" to user.uid,
                    "email" to email,
                    "displayName" to displayName,
                    "userMode" to userMode.name,
                    "createdAt" to System.currentTimeMillis()
                )
                
                Log.d(TAG, "Firebase Database에 저장할 데이터: $userData")
                database.reference.child("users").child(user.uid).setValue(userData).await()
                Log.d(TAG, "Firebase Database 저장 완료")

                val firebaseUser = FirebaseUser(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = displayName,
                    isEmailVerified = user.isEmailVerified
                )

                Log.d(TAG, "회원가입 성공: ${user.email} (${userMode.name})")
                LoginResponse(
                    success = true,
                    message = "회원가입 성공",
                    user = firebaseUser
                )
            } else {
                Log.e(TAG, "Firebase Auth 회원가입 실패: user is null")
                LoginResponse(
                    success = false,
                    message = "회원가입 실패",
                    user = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "회원가입 중 오류 발생", e)
            LoginResponse(
                success = false,
                message = "회원가입 중 오류가 발생했습니다: ${e.message}",
                user = null
            )
        }
    }

    /**
     * 현재 로그인된 사용자 정보 가져오기
     */
    fun getCurrentUser(): FirebaseUser? {
        val user = auth.currentUser
        return user?.let {
            FirebaseUser(
                uid = it.uid,
                email = it.email ?: "",
                displayName = it.displayName ?: "",
                isEmailVerified = it.isEmailVerified
            )
        }
    }

    /**
     * 로그아웃
     */
    fun signOut() {
        auth.signOut()
        Log.d(TAG, "로그아웃 완료")
    }

    /**
     * 현재 로그인 상태 확인
     */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
} 