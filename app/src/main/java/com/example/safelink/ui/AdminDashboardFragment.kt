package com.example.safelink.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safelink.R
import com.example.safelink.databinding.FragmentAdminDashboardBinding
import com.example.safelink.model.UserSummary
import com.example.safelink.service.AdminService
import com.example.safelink.service.FirebaseAuthService
import com.example.safelink.util.SessionManager
import com.example.safelink.ui.adapter.UserSummaryAdapter
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding

    private lateinit var userAdapter: UserSummaryAdapter
    private var allUsers: List<UserSummary> = emptyList()
    private var filteredUsers: List<UserSummary> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        checkAdminPrivileges()
        loadUsers()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        binding!!.toolbar.title = "관리자 대시보드"
        binding!!.toolbar.subtitle = "클라이언트(작업자) 관리 및 모니터링"

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 관리자 대시보드에서는 관리자 로그인 화면으로 이동
                navigateToAdminLogin()
            }
        })
    }

    /**
     * RecyclerView 설정
     */
    private fun setupRecyclerView() {
        userAdapter = UserSummaryAdapter(
            onUserClick = { user ->
                navigateToUserDetail(user.uid)
            },
            onUserLongClick = { user ->
                showUserActionDialog(user)
                true
            }
        )

        binding!!.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = userAdapter
        }
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        binding!!.addUserButton.setOnClickListener {
            showAddUserDialog()
        }
        
        binding!!.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    /**
     * 관리자 권한 확인
     */
    private fun checkAdminPrivileges() {
        lifecycleScope.launch {
            if (!AdminService.checkAdminPrivileges()) {
                showError("관리자 권한이 필요합니다.")
                // 에러 발생 시 이전 화면으로 이동
                parentFragmentManager.popBackStack()
            }
        }
    }

    /**
     * 사용자 목록 로드
     */
    private fun loadUsers() {
        AdminService.loadAllUsers { users ->
            lifecycleScope.launch {
                allUsers = users
                filteredUsers = users

                // binding null 체크 추가
                if (_binding != null) {
                    updateDashboardStats()
                    updateUserList()

                    if (users.isEmpty()) {
                        binding!!.emptyStateLayout.visibility = View.VISIBLE
                        binding!!.usersRecyclerView.visibility = View.GONE
                    } else {
                        binding!!.emptyStateLayout.visibility = View.GONE
                        binding!!.usersRecyclerView.visibility = View.VISIBLE
                    }
                } else {
                    Log.w(TAG, "binding이 null입니다. Fragment가 destroy되었을 수 있습니다.")
                }
            }
        }
    }

    /**
     * 대시보드 통계 업데이트
     */
    private fun updateDashboardStats() {
        if (_binding == null) {
            Log.w(TAG, "updateDashboardStats: binding이 null입니다.")
            return
        }

        val stats = AdminService.calculateDashboardStats(allUsers)

        binding!!.totalUsersText.text = stats.totalUsers.toString()
        binding!!.onlineUsersText.text = stats.onlineUsers.toString()
        binding!!.warningUsersText.text = stats.warningUsers.toString()
        binding!!.dangerUsersText.text = stats.dangerUsers.toString()
    }

    /**
     * 사용자 목록 업데이트
     */
    private fun updateUserList() {
        if (_binding == null) {
            Log.w(TAG, "updateUserList: binding이 null입니다.")
            return
        }

        userAdapter.updateData(filteredUsers)
    }

    /**
     * 검색 수행
     */
    private fun performSearch() {
        val query = binding!!.searchEditText.text.toString().trim()
        filteredUsers = AdminService.searchUsers(allUsers, query)
        updateUserList()
        
        if (filteredUsers.isEmpty() && query.isNotEmpty()) {
            showMessage("검색 결과가 없습니다.")
        }
    }

    /**
     * 사용자 상세 정보로 이동
     */
    private fun navigateToUserDetail(userUid: String) {
        val fragment = UserDetailFragment.newInstance(userUid)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "사용자 상세 정보로 이동: $userUid")
    }

    /**
     * 사용자 액션 다이얼로그 표시
     */
    private fun showUserActionDialog(user: UserSummary) {
        val actions = arrayOf("상세 정보", "편집", "삭제", "알림 보내기")
        
        AlertDialog.Builder(requireContext())
            .setTitle("작업자 관리")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> navigateToUserDetail(user.uid)
                    1 -> showEditUserDialog(user)
                    2 -> showDeleteUserDialog(user)
                    3 -> showSendNotificationDialog(user)
                }
            }
            .show()
    }

    /**
     * 사용자 추가 다이얼로그
     */
    private fun showAddUserDialog() {
        // TODO: 사용자 추가 다이얼로그 구현
        showMessage("작업자 추가 기능은 아직 구현되지 않았습니다.")
    }

    /**
     * 사용자 편집 다이얼로그
     */
    private fun showEditUserDialog(user: UserSummary) {
        // TODO: 사용자 편집 다이얼로그 구현
        showMessage("작업자 편집 기능은 아직 구현되지 않았습니다.")
    }

    /**
     * 사용자 삭제 다이얼로그
     */
    private fun showDeleteUserDialog(user: UserSummary) {
        AlertDialog.Builder(requireContext())
            .setTitle("작업자 삭제")
            .setMessage("정말로 ${user.name} 작업자를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteUser(user.uid)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 사용자 삭제
     */
    private fun deleteUser(userUid: String) {
        AdminService.deleteUser(userUid) { success, errorMessage ->
            lifecycleScope.launch {
                if (success) {
                    showMessage("작업자가 삭제되었습니다.")
                    loadUsers() // 목록 새로고침
                } else {
                    showError("작업자 삭제 실패: $errorMessage")
                }
            }
        }
    }

    /**
     * 알림 보내기 다이얼로그
     */
    private fun showSendNotificationDialog(user: UserSummary) {
        // TODO: 알림 보내기 다이얼로그 구현
        showMessage("알림 보내기 기능은 아직 구현되지 않았습니다.")
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

    /**
     * 관리자 로그인 화면으로 이동
     */
    private fun navigateToAdminLogin() {
        val fragment = AdminLoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "관리자 로그인 화면으로 이동")
    }
    
    /**
     * 로그아웃 수행
     */
    private fun performLogout() {
        Log.d(TAG, "관리자 모드 로그아웃 수행")
        
        // Firebase 로그아웃
        FirebaseAuthService.signOut()
        
        // 세션 삭제
        SessionManager.clearLoginSession()
        
        // 시작 화면으로 이동
        requireActivity().onBackPressedDispatcher.onBackPressed()
        
        Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "관리자 모드 로그아웃 완료")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "AdminDashboardFragment"
    }
} 