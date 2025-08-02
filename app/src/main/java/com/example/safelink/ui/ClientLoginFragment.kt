package com.example.safelink.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safelink.R
import com.example.safelink.databinding.FragmentClientLoginBinding
import com.example.safelink.model.UserMode
import com.example.safelink.service.FirebaseAuthService
import com.example.safelink.util.SessionManager
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback

class ClientLoginFragment : Fragment() {

    private var _binding: FragmentClientLoginBinding? = null
    private val binding get() = _binding!!
    
    private var userMode: UserMode = UserMode.PERSONAL
    
    companion object {
        private const val TAG = "ClientLoginFragment"
        private const val PREF_NAME = "login_preferences"
        private const val KEY_AUTO_LOGIN = "auto_login_enabled"
        private const val KEY_LAST_LOGIN_EMAIL = "last_login_email"
        
        /**
         * 새로운 ClientLoginFragment 인스턴스 생성
         */
        fun newInstance(userMode: UserMode): ClientLoginFragment {
            return ClientLoginFragment().apply {
                arguments = Bundle().apply {
                    putString("user_mode", userMode.name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupClickListeners()
        getUserModeFromArguments()
        checkAutoLogin()
        loadAutoLoginPreference()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 고객 모드일 때는 시작 화면으로, 개인 모드일 때는 개인동의 화면으로 이동
                if (userMode == UserMode.CUSTOMER) {
                    navigateToStartScreen()
                } else {
                    navigateToCustomerConsent()
                }
            }
        })
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            performLogin()
        }
        
        binding.signupLink.setOnClickListener {
            performSignUp()
        }
        
        binding.autoLoginCheckbox.setOnCheckedChangeListener { _, isChecked ->
            saveAutoLoginPreference(isChecked)
        }
    }

    /**
     * 자동 로그인 설정 저장
     */
    private fun saveAutoLoginPreference(enabled: Boolean) {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(KEY_AUTO_LOGIN, enabled).apply()
        Log.d(TAG, "자동 로그인 설정 저장: $enabled")
    }

    /**
     * 자동 로그인 설정 로드
     */
    private fun loadAutoLoginPreference() {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val autoLoginEnabled = sharedPref.getBoolean(KEY_AUTO_LOGIN, false)
        binding.autoLoginCheckbox.isChecked = autoLoginEnabled
        Log.d(TAG, "자동 로그인 설정 로드: $autoLoginEnabled")
    }

    /**
     * 마지막 로그인 이메일 저장
     */
    private fun saveLastLoginEmail(email: String) {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_LAST_LOGIN_EMAIL, email).apply()
        Log.d(TAG, "마지막 로그인 이메일 저장: $email")
    }

    /**
     * 인자에서 사용자 모드 가져오기
     */
    private fun getUserModeFromArguments() {
        userMode = arguments?.getString("user_mode")?.let { modeName ->
            try {
                UserMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                UserMode.PERSONAL
            }
        } ?: UserMode.PERSONAL
        
        updateUITitle()
    }

    /**
     * UI 제목 업데이트
     */
    private fun updateUITitle() {
        val title = when (userMode) {
            UserMode.PERSONAL -> "개인 모드 로그인"
            UserMode.CUSTOMER -> "고객 모드 로그인"
            else -> "로그인"
        }
        binding.toolbar.title = title
    }

    /**
     * 자동 로그인 확인
     */
    private fun checkAutoLogin() {
        // 개인 사용 모드일 때는 바로 작업자 메인 화면으로 이동
        if (userMode == UserMode.PERSONAL) {
            Log.d(TAG, "개인 사용 모드: 바로 작업자 메인 화면으로 이동")
            navigateToWorkerMain()
            return
        }
        
        val currentUser = FirebaseAuthService.getCurrentUser()
        if (currentUser != null) {
            // 자동 로그인 설정 확인
            val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val autoLoginEnabled = sharedPref.getBoolean(KEY_AUTO_LOGIN, false)
            
            if (autoLoginEnabled) {
                // 자동 로그인 확인 다이얼로그 표시
                showAutoLoginDialog(currentUser)
            }
        }
        
        // Firebase 연결 상태 확인
        testFirebaseConnection()
    }
    
    /**
     * 자동 로그인 확인 다이얼로그 표시
     */
    private fun showAutoLoginDialog(user: com.example.safelink.model.FirebaseUser) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("자동 로그인")
            .setMessage("이전에 로그인한 계정(${user.email})으로 자동 로그인하시겠습니까?")
            .setPositiveButton("로그인") { _, _ ->
                // 자동 로그인 진행
                performAutoLogin(user)
            }
            .setNegativeButton("취소") { _, _ ->
                // 자동 로그인 취소 - 현재 화면 유지
                Log.d(TAG, "자동 로그인 취소됨")
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 자동 로그인 수행
     */
    private fun performAutoLogin(user: com.example.safelink.model.FirebaseUser) {
        Log.d(TAG, "자동 로그인 진행: ${user.email}")
        showMessage("자동 로그인되었습니다.")
        
        // 세션 저장
        SessionManager.saveLoginSession(user, userMode, true)
        
        // 개인 모드일 때는 작업자 화면으로, 고객 모드일 때는 클라이언트 메인으로
        if (userMode == UserMode.PERSONAL) {
            navigateToWorkerMain()
        } else {
            navigateToClientMain()
        }
    }
    
    /**
     * Firebase 연결 상태 확인
     */
    private fun testFirebaseConnection() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Firebase 연결 상태 확인 중...")
                // 간단한 연결 테스트
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                Log.d(TAG, "Firebase Auth 초기화 성공")
                
                // 데이터베이스 연결 테스트 (선택사항)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                Log.d(TAG, "Firebase Database 초기화 성공")
                
            } catch (e: Exception) {
                Log.e(TAG, "Firebase 연결 실패", e)
                showError("Firebase 연결에 실패했습니다. 인터넷 연결을 확인해주세요.")
            }
        }
    }

    /**
     * 로그인 수행
     */
    private fun performLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val autoLogin = binding.autoLoginCheckbox.isChecked
        
        Log.d(TAG, "로그인 시도: email=$email, userMode=$userMode, autoLogin=$autoLogin")
        
        if (email.isEmpty() || password.isEmpty()) {
            showError("이메일과 비밀번호를 입력해주세요.")
            return
        }
        
        // 로딩 상태 표시
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "FirebaseAuthService.signIn 호출")
                val result = FirebaseAuthService.signIn(email, password, userMode)
                Log.d(TAG, "로그인 결과: success=${result.success}, message=${result.message}")
                
                if (result.success) {
                    Log.d(TAG, "로그인 성공, 세션 저장")
                    
                    // 세션 저장
                    val currentUser = FirebaseAuthService.getCurrentUser()
                    if (currentUser != null) {
                        SessionManager.saveLoginSession(currentUser, userMode, autoLogin)
                    }
                    
                    // 개인 모드일 때는 작업자 화면으로, 고객 모드일 때는 클라이언트 메인으로
                    if (userMode == UserMode.PERSONAL) {
                        navigateToWorkerMain()
                    } else {
                        navigateToClientMain()
                    }
                } else {
                    Log.e(TAG, "로그인 실패: ${result.message}")
                    showError(result.message ?: "로그인에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 중 예외 발생", e)
                showError("로그인 중 오류가 발생했습니다: ${e.message}")
            } finally {
                binding.loadingIndicator.visibility = View.GONE
                binding.loginButton.isEnabled = true
            }
        }
    }

    /**
     * 회원가입 수행
     */
    private fun performSignUp() {
        // 회원가입 화면으로 이동 (Fragment Transaction 사용)
        val signupFragment = SignupFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, signupFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * 입력 검증
     */
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.emailLayout.error = "이메일을 입력하세요"
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "올바른 이메일 형식을 입력하세요"
            return false
        }
        
        if (password.isEmpty()) {
            binding.passwordLayout.error = "비밀번호를 입력하세요"
            return false
        }
        
        if (password.length < 6) {
            binding.passwordLayout.error = "비밀번호는 6자 이상이어야 합니다"
            return false
        }
        
        return true
    }

    /**
     * 작업자 메인 화면으로 이동 (SensorMonitorFragment)
     */
    private fun navigateToWorkerMain() {
        val fragment = SensorMonitorFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "작업자 메인 화면으로 이동")
    }

    /**
     * 클라이언트 메인 화면으로 이동
     */
    private fun navigateToClientMain() {
        val fragment = ClientMainFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "클라이언트 메인 화면으로 이동")
    }

    /**
     * 개인동의 화면으로 이동
     */
    private fun navigateToCustomerConsent() {
        val fragment = CustomerConsentFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "개인동의 화면으로 이동")
    }

    /**
     * 시작 화면으로 이동
     */
    private fun navigateToStartScreen() {
        // MainActivity의 시작 화면으로 이동
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    /**
     * 로딩 상태 표시
     */
    private fun showLoading(show: Boolean) {
        binding.loginButton.isEnabled = !show
        binding.loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * 에러 메시지 표시
     */
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 일반 메시지 표시
     */
    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 