package com.example.safelink.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safelink.R
import com.example.safelink.databinding.FragmentSignupBinding
import com.example.safelink.model.UserMode
import com.example.safelink.service.FirebaseAuthService
import kotlinx.coroutines.launch

class SignupFragment : Fragment() {
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.signupButton.setOnClickListener {
            performSignup()
        }
        
        binding.loginLinkText.setOnClickListener {
            // 로그인 화면으로 이동 (Fragment Transaction 사용)
            parentFragmentManager.popBackStack()
        }
    }
    
    /**
     * 회원가입 수행
     */
    private fun performSignup() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()
        val name = binding.nameEditText.text.toString().trim()
        val userType = when (binding.userTypeRadioGroup.checkedRadioButtonId) {
            R.id.employeeRadioButton -> UserMode.CUSTOMER
            R.id.managerRadioButton -> UserMode.ADMIN
            else -> UserMode.CUSTOMER
        }
        val termsAccepted = binding.termsCheckBox.isChecked
        
        Log.d(TAG, "회원가입 입력값: email=$email, name=$name, userType=$userType, termsAccepted=$termsAccepted")
        
        // 입력 검증
        if (!validateInputs(email, password, confirmPassword, name, termsAccepted)) {
            return
        }
        
        // 로딩 상태 표시
        binding.signupButton.isEnabled = false
        binding.signupButton.text = "회원가입 중..."
        
        // 회원가입 진행
        lifecycleScope.launch {
            try {
                Log.d(TAG, "FirebaseAuthService.signUp 호출")
                val result = FirebaseAuthService.signUp(email, password, name, userType)
                Log.d(TAG, "회원가입 결과: success=${result.success}, message=${result.message}")
                
                if (result.success) {
                    Log.d(TAG, "회원가입 성공, 로그인 화면으로 이동")
                    showMessage("회원가입이 완료되었습니다.")
                    parentFragmentManager.popBackStack()
                } else {
                    Log.e(TAG, "회원가입 실패: ${result.message}")
                    showError("회원가입 실패: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "회원가입 중 예외 발생", e)
                showError("회원가입 중 오류가 발생했습니다: ${e.message}")
            } finally {
                binding.signupButton.isEnabled = true
                binding.signupButton.text = "회원가입"
            }
        }
    }
    
    private fun validateInputs(
        email: String,
        password: String,
        confirmPassword: String,
        name: String,
        termsAccepted: Boolean
    ): Boolean {
        // 이메일 검증
        if (email.isEmpty()) {
            binding.emailLayout.error = "이메일을 입력해주세요"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "올바른 이메일 형식을 입력해주세요"
            return false
        }
        binding.emailLayout.error = null
        
        // 비밀번호 검증
        if (password.isEmpty()) {
            binding.passwordLayout.error = "비밀번호를 입력해주세요"
            return false
        }
        if (password.length < 6) {
            binding.passwordLayout.error = "비밀번호는 6자 이상이어야 합니다"
            return false
        }
        binding.passwordLayout.error = null
        
        // 비밀번호 확인 검증
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.error = "비밀번호 확인을 입력해주세요"
            return false
        }
        if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = "비밀번호가 일치하지 않습니다"
            return false
        }
        binding.confirmPasswordLayout.error = null
        
        // 이름 검증
        if (name.isEmpty()) {
            binding.nameLayout.error = "이름을 입력해주세요"
            return false
        }
        binding.nameLayout.error = null
        
        // 약관 동의 검증
        if (!termsAccepted) {
            Toast.makeText(requireContext(), "이용약관에 동의해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
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
        private const val TAG = "SignupFragment"
    }
} 