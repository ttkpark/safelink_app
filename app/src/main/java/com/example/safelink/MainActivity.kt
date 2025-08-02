package com.example.safelink

import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.safelink.databinding.ActivityMainBinding
import com.example.safelink.ui.AdminDashboardFragment
import com.example.safelink.ui.AdminLoginFragment
import com.example.safelink.ui.ClientLoginFragment
import com.example.safelink.ui.ClientMainFragment
import com.example.safelink.ui.CustomerConsentFragment
import com.example.safelink.ui.SensorMonitorFragment
import com.example.safelink.ui.UserDetailFragment
import com.example.safelink.ui.UserHistoryFragment
import com.example.safelink.util.SessionManager
import com.example.safelink.model.UserMode

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        setupBackPressHandler()
        
        // 세션 체크 및 자동 로그인 처리
        checkSessionAndAutoLogin()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupClickListeners() {
        // 개인 사용 카드 클릭 - 개인동의 화면으로 이동
        binding.cardPersonal.setOnClickListener {
            navigateToCustomerConsent()
        }

        // 관리자 사용 카드 클릭
        binding.cardAdmin.setOnClickListener {
            navigateToAdminLogin()
        }

        // 고객 사용 카드 클릭
        binding.cardCustomer.setOnClickListener {
            navigateToClientLogin()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
                    is CustomerConsentFragment -> {
                        // 개인동의 화면에서는 뒤로가기 시 앱 종료
                        finish()
                    }
                    is ClientLoginFragment -> {
                        // 고객 모드 로그인 화면에서는 시작 화면으로 이동
                        showStartScreen()
                    }
                    is AdminLoginFragment -> {
                        // 관리자 로그인 화면에서는 개인동의 화면으로 이동
                        navigateToCustomerConsent()
                    }
                    is ClientMainFragment -> {
                        // 클라이언트 메인 화면에서는 로그인 화면으로 이동
                        navigateToClientLogin()
                    }
                    is com.example.safelink.ui.SensorMonitorFragment -> {
                        // 작업자 메인 화면에서는 시작 화면으로 이동
                        showStartScreen()
                    }
                    is AdminDashboardFragment -> {
                        // 관리자 대시보드에서는 앱 종료
                        finish()
                    }
                    is UserDetailFragment, is UserHistoryFragment -> {
                        // 상세 화면에서는 이전 화면으로 이동
                        if (isEnabled) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                    else -> {
                        // 기본 뒤로가기 동작
                        if (isEnabled) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        })
    }

    private fun navigateToCustomerConsent() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, CustomerConsentFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToClientLogin() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ClientLoginFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToAdminLogin() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AdminLoginFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToWorkerMain() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SensorMonitorFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun showFragmentContainer() {
        binding.startScreenContent.visibility = android.view.View.GONE
        binding.fragmentContainer.visibility = android.view.View.VISIBLE
    }

    private fun showStartScreen() {
        binding.startScreenContent.visibility = android.view.View.VISIBLE
        binding.fragmentContainer.visibility = android.view.View.GONE
    }
    
    /**
     * 세션 체크 및 자동 로그인 처리
     */
    private fun checkSessionAndAutoLogin() {
        // 세션 정보 로그 출력
        SessionManager.logSessionInfo()
        
        if (SessionManager.isLoggedIn()) {
            val userMode = SessionManager.getUserMode()
            val userEmail = SessionManager.getUserEmail()
            
            Log.d(TAG, "유효한 세션 발견: $userEmail (${userMode?.name})")
            
            when (userMode) {
                UserMode.PERSONAL -> {
                    // 개인 모드: 바로 작업자 화면으로 이동
                    Log.d(TAG, "개인 모드 자동 로그인 - 작업자 화면으로 이동")
                    navigateToWorkerMain()
                }
                UserMode.CUSTOMER -> {
                    // 고객 모드: 클라이언트 메인 화면으로 이동
                    Log.d(TAG, "고객 모드 자동 로그인 - 클라이언트 메인 화면으로 이동")
                    navigateToClientMain()
                }
                UserMode.ADMIN -> {
                    // 관리자 모드: 관리자 대시보드로 이동
                    Log.d(TAG, "관리자 모드 자동 로그인 - 관리자 대시보드로 이동")
                    navigateToAdminDashboard()
                }
                else -> {
                    Log.w(TAG, "알 수 없는 사용자 모드: $userMode")
                    showStartScreen()
                }
            }
        } else {
            Log.d(TAG, "유효한 세션 없음 - 시작 화면 표시")
            showStartScreen()
        }
    }
    
    /**
     * 관리자 대시보드로 이동
     */
    private fun navigateToAdminDashboard() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AdminDashboardFragment())
            .addToBackStack(null)
            .commit()
    }
    
    /**
     * 클라이언트 메인 화면으로 이동
     */
    private fun navigateToClientMain() {
        showFragmentContainer()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ClientMainFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}