package com.example.safelink.ui

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

class AdminLoginFragment : Fragment() {

    private var _binding: FragmentClientLoginBinding? = null
    private val binding get() = _binding!!

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
        updateUITitle()
        checkAutoLogin()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 관리자 로그인 화면에서는 개인동의 화면으로 이동
                navigateToCustomerConsent()
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
            // TODO: 자동 로그인 설정 저장
        }
    }

    /**
     * UI 제목 업데이트
     */
    private fun updateUITitle() {
        binding.toolbar.title = "관리자 로그인"
        binding.toolbar.subtitle = "클라이언트(작업자) 관리 시스템"
    }

    /**
     * 자동 로그인 확인
     */
    private fun checkAutoLogin() {
        // TODO: 자동 로그인 상태 확인 및 처리
        val currentUser = FirebaseAuthService.getCurrentUser()
        if (currentUser != null) {
            // 관리자로 로그인된 경우에만 자동 로그인
            Log.d(TAG, "자동 로그인 확인: ${currentUser.email}")
            // Firebase Database에서 userMode 확인 후 관리자인 경우에만 자동 로그인
            // TODO: 실제 userMode 확인 로직 구현
        }
    }

    /**
     * 로그인 수행
     */
    private fun performLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        
        Log.d(TAG, "관리자 로그인 시도: email=$email")
        
        // 입력 검증
        if (!validateInput(email, password)) {
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "FirebaseAuthService.signIn 호출 (ADMIN)")
                val response = FirebaseAuthService.signIn(email, password, UserMode.ADMIN)
                Log.d(TAG, "관리자 로그인 결과: success=${response.success}, message=${response.message}")
                
                if (response.success) {
                    Log.d(TAG, "관리자 로그인 성공, 세션 저장")
                    
                    // 세션 저장
                    val currentUser = FirebaseAuthService.getCurrentUser()
                    if (currentUser != null) {
                        SessionManager.saveLoginSession(currentUser, UserMode.ADMIN, false)
                    }
                    
                    showMessage("관리자 로그인 성공")
                    navigateToAdminDashboard()
                } else {
                    Log.e(TAG, "관리자 로그인 실패: ${response.message}")
                    showError(response.message ?: "관리자 로그인에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "관리자 로그인 중 예외 발생", e)
                showError("로그인 중 오류가 발생했습니다: ${e.message}")
            } finally {
                showLoading(false)
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
     * 관리자 대시보드로 이동
     */
    private fun navigateToAdminDashboard() {
        val fragment = AdminDashboardFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "관리자 대시보드로 이동")
    }

    /**
     * 백스택을 클리어하고 관리자 대시보드로 이동 (자동 로그인용)
     */
    private fun navigateToAdminDashboardWithClearStack() {
        val fragment = AdminDashboardFragment()
        // 백스택을 완전히 클리어하고 대시보드로 이동
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
        
        // 백스택 클리어
        parentFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        
        Log.d(TAG, "자동 로그인으로 관리자 대시보드로 이동")
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

    companion object {
        private const val TAG = "AdminLoginFragment"
    }
} 