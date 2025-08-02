package com.example.safelink.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.safelink.R
import com.example.safelink.databinding.FragmentCustomerConsentBinding
import com.example.safelink.model.ConsentData
import com.example.safelink.model.UserMode
import androidx.activity.OnBackPressedCallback

class CustomerConsentFragment : Fragment() {

    private var _binding: FragmentCustomerConsentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerConsentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupClickListeners()
        loadTermsAndConditions()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 개인동의 화면에서는 뒤로가기 시 앱 종료
                requireActivity().finish()
            }
        })
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        binding.btnAccept.setOnClickListener {
            handleAcceptance()
        }
        
        binding.btnDecline.setOnClickListener {
            handleDecline()
        }
    }

    /**
     * 약관 및 조건 로드
     */
    private fun loadTermsAndConditions() {
        val terms = getString(R.string.consent_terms)
        binding.consentText.text = terms
    }

    /**
     * 동의 처리
     */
    private fun handleAcceptance() {
        saveConsent(true)
        navigateToClientLogin()
    }

    /**
     * 거부 처리
     */
    private fun handleDecline() {
        showNonMemberRecommendationDialog()
    }

    /**
     * 비회원 사용 권장 다이얼로그 표시
     */
    private fun showNonMemberRecommendationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("개인정보 수집 동의")
            .setMessage("개인정보 수집에 동의하지 않으시면 비회원으로 서비스를 이용하실 수 있습니다. 비회원으로 계속하시겠습니까?")
            .setPositiveButton("비회원으로 계속") { _, _ ->
                saveConsent(false)
                navigateToClientLogin()
            }
            .setNegativeButton("다시 고려") { _, _ ->
                // 다이얼로그 닫기
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 동의 정보 저장
     */
    private fun saveConsent(isAccepted: Boolean) {
        val consentData = ConsentData(
            isAccepted = isAccepted,
            timestamp = System.currentTimeMillis(),
            userMode = UserMode.CUSTOMER
        )
        
        // TODO: SharedPreferences에 동의 정보 저장
        Log.d(TAG, "동의 정보 저장: $consentData")
    }

    /**
     * 클라이언트 로그인 화면으로 이동
     */
    private fun navigateToClientLogin() {
        val fragment = ClientLoginFragment.newInstance(UserMode.CUSTOMER)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "클라이언트 로그인 화면으로 이동")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CustomerConsentFragment"
    }
} 