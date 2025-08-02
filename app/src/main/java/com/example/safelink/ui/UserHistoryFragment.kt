package com.example.safelink.ui

import android.app.DatePickerDialog
import android.content.Intent
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
import com.example.safelink.databinding.FragmentUserHistoryBinding
import com.example.safelink.model.HistoryEntry
import com.example.safelink.model.HistoryStats
import com.example.safelink.service.HistoryService
import com.example.safelink.service.ExportService
import com.example.safelink.ui.adapter.HistoryEntryAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.OnBackPressedCallback

class UserHistoryFragment : Fragment() {

    private var _binding: FragmentUserHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var historyAdapter: HistoryEntryAdapter
    private var userUid: String? = null
    private var currentHistoryEntries: List<HistoryEntry> = emptyList()
    private var startDate: Long = 0L
    private var endDate: Long = 0L
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        getUserUidFromArguments()
        initializeDateRange()
        loadDummyHistory()
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 사용자 히스토리 화면에서는 이전 화면으로 이동
                parentFragmentManager.popBackStack()
            }
        })
    }

    /**
     * RecyclerView 설정
     */
    private fun setupRecyclerView() {
        historyAdapter = HistoryEntryAdapter()
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        // 날짜 선택
        binding.startDateText.setOnClickListener {
            showDatePicker(true)
        }
        
        binding.endDateText.setOnClickListener {
            showDatePicker(false)
        }
        
        // 빠른 날짜 선택
        binding.quickDate1day.setOnClickListener {
            setQuickDateRange(1)
        }
        
        binding.quickDate7days.setOnClickListener {
            setQuickDateRange(7)
        }
        
        binding.quickDate30days.setOnClickListener {
            setQuickDateRange(30)
        }
        
        // 히스토리 로드
        binding.loadHistoryButton.setOnClickListener {
            loadHistory()
        }
        
        // CSV 내보내기
        binding.exportCsvButton.setOnClickListener {
            exportToCSV()
        }
    }

    /**
     * 인자에서 사용자 UID 가져오기
     */
    private fun getUserUidFromArguments() {
        userUid = arguments?.getString("user_uid")
        if (userUid == null) {
            showError("사용자 정보를 찾을 수 없습니다.")
            // 에러 발생 시 이전 화면으로 이동
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 날짜 범위 초기화
     */
    private fun initializeDateRange() {
        // 기본값: 최근 7일
        setQuickDateRange(7)
    }

    /**
     * 빠른 날짜 범위 설정
     */
    private fun setQuickDateRange(days: Int) {
        calendar.timeInMillis = System.currentTimeMillis()
        endDate = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_MONTH, -days + 1)
        startDate = calendar.timeInMillis
        
        updateDateDisplay()
    }

    /**
     * 날짜 선택 다이얼로그 표시
     */
    private fun showDatePicker(isStartDate: Boolean) {
        val currentDate = if (isStartDate) startDate else endDate
        calendar.timeInMillis = currentDate
        
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                if (isStartDate) {
                    startDate = calendar.timeInMillis
                } else {
                    endDate = calendar.timeInMillis
                }
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * 날짜 표시 업데이트
     */
    private fun updateDateDisplay() {
        binding.startDateText.text = dateFormat.format(Date(startDate))
        binding.endDateText.text = dateFormat.format(Date(endDate))
    }

    /**
     * 히스토리 데이터 로드 (초기 로드)
     */
    private fun loadDummyHistory() {
        userUid?.let { uid ->
            // 실제 데이터가 없으므로 빈 상태로 시작
            currentHistoryEntries = emptyList()
            updateHistoryDisplay()
            
            // 사용자에게 안내 메시지 표시
            showMessage("날짜 범위를 선택하고 '조회' 버튼을 눌러 히스토리를 확인하세요.")
        }
    }

    /**
     * 히스토리 데이터 로드
     */
    private fun loadHistory() {
        if (startDate == 0L || endDate == 0L) {
            showError("날짜 범위를 선택해주세요.")
            return
        }
        
        if (startDate > endDate) {
            showError("시작일이 종료일보다 늦을 수 없습니다.")
            return
        }
        
        showLoading(true)
        
        userUid?.let { uid ->
            HistoryService.loadUserHistory(uid, startDate, endDate) { entries ->
                lifecycleScope.launch {
                    currentHistoryEntries = entries
                    updateHistoryDisplay()
                    showLoading(false)
                    
                    if (entries.isEmpty()) {
                        showMessage("선택한 날짜 범위에 히스토리 데이터가 없습니다.")
                    } else {
                        showMessage("${entries.size}개의 히스토리 데이터를 로드했습니다.")
                    }
                }
            }
        }
    }

    /**
     * 히스토리 표시 업데이트
     */
    private fun updateHistoryDisplay() {
        // 어댑터 업데이트
        historyAdapter.updateData(currentHistoryEntries)
        
        // 통계 계산 및 표시
        val stats = HistoryService.calculateHistoryStats(currentHistoryEntries)
        updateStatsDisplay(stats)
        
        // UI 상태 업데이트
        if (currentHistoryEntries.isEmpty()) {
            binding.noHistoryText.visibility = View.VISIBLE
            binding.historyRecyclerView.visibility = View.GONE
            binding.historyCountText.text = "(0개)"
        } else {
            binding.noHistoryText.visibility = View.GONE
            binding.historyRecyclerView.visibility = View.VISIBLE
            binding.historyCountText.text = "(${currentHistoryEntries.size}개)"
        }
    }

    /**
     * 통계 정보 표시 업데이트
     */
    private fun updateStatsDisplay(stats: HistoryStats) {
        binding.totalEntriesText.text = stats.totalEntries.toString()
        binding.riskEventsText.text = stats.riskEvents.toString()
        binding.avgHeartRateText.text = "${String.format("%.1f", stats.averageHeartRate)} BPM"
        binding.maxTemperatureText.text = "${String.format("%.1f", stats.maxTemperature)}°C"
        binding.avgWbgtText.text = "${String.format("%.1f", stats.averageWBGT)}°C"
        binding.connectedTimeText.text = HistoryService.formatDuration(stats.deviceConnectedTime)
    }

    /**
     * CSV 파일로 내보내기
     */
    private fun exportToCSV() {
        if (currentHistoryEntries.isEmpty()) {
            showError("내보낼 히스토리 데이터가 없습니다.")
            return
        }
        
        userUid?.let { uid ->
            ExportService.exportToCSV(requireContext(), currentHistoryEntries, uid) { success, filePath ->
                lifecycleScope.launch {
                    if (success && filePath != null) {
                        showExportSuccessDialog(filePath)
                    } else {
                        showError("CSV 내보내기에 실패했습니다: ${filePath ?: "알 수 없는 오류"}")
                    }
                }
            }
        }
    }

    /**
     * 내보내기 성공 다이얼로그
     */
    private fun showExportSuccessDialog(filePath: String) {
        val file = java.io.File(filePath)
        val fileSize = ExportService.formatFileSize(file.length())
        
        AlertDialog.Builder(requireContext())
            .setTitle("내보내기 완료")
            .setMessage("CSV 파일이 성공적으로 생성되었습니다.\n\n파일 크기: $fileSize\n\n파일을 공유하시겠습니까?")
            .setPositiveButton("공유") { _, _ ->
                shareExportedFile(filePath)
            }
            .setNegativeButton("닫기") { _, _ ->
                // 파일은 나중에 정리
            }
            .show()
    }

    /**
     * 내보낸 파일 공유
     */
    private fun shareExportedFile(filePath: String) {
        try {
            val shareIntent = ExportService.createShareIntent(requireContext(), filePath)
            startActivity(Intent.createChooser(shareIntent, "CSV 파일 공유"))
        } catch (e: Exception) {
            Log.e(TAG, "파일 공유 실패", e)
            showError("파일 공유에 실패했습니다.")
        }
    }

    /**
     * 로딩 상태 표시
     */
    private fun showLoading(show: Boolean) {
        binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        binding.loadHistoryButton.isEnabled = !show
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
        private const val TAG = "UserHistoryFragment"
        
        /**
         * 새로운 UserHistoryFragment 인스턴스 생성
         */
        fun newInstance(userUid: String): UserHistoryFragment {
            return UserHistoryFragment().apply {
                arguments = Bundle().apply {
                    putString("user_uid", userUid)
                }
            }
        }
    }
} 