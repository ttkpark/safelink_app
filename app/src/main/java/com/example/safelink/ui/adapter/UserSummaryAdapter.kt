package com.example.safelink.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.safelink.databinding.ItemUserSummaryBinding
import com.example.safelink.model.UserSummary
import com.example.safelink.model.UserStatus
import com.example.safelink.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.*

class UserSummaryAdapter(
    private var userSummaries: List<UserSummary> = emptyList(),
    private val onUserClick: (UserSummary) -> Unit = {},
    private val onUserLongClick: (UserSummary) -> Boolean = { false }
) : RecyclerView.Adapter<UserSummaryAdapter.UserViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    inner class UserViewHolder(
        private val binding: ItemUserSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(userSummary: UserSummary) {
            // 사용자 기본 정보
            binding.userNameText.text = userSummary.name
            binding.userEmailText.text = userSummary.email
            
            // 사용자 상태 표시
            updateUserStatus(userSummary.status)
            
            // 마지막 연결 시간
            binding.lastSeenText.text = formatLastSeen(userSummary.lastSeen)
            
            // 위험도 표시
            updateRiskLevel(userSummary.riskLevel)
            
            // 온라인 상태 표시
            updateOnlineStatus(userSummary.isOnline)
            
            // 클릭 리스너 설정
            binding.root.setOnClickListener {
                onUserClick(userSummary)
            }
            
            binding.root.setOnLongClickListener {
                onUserLongClick(userSummary)
            }
        }

        /**
         * 사용자 상태 업데이트
         */
        private fun updateUserStatus(status: UserStatus) {
            val (statusText, statusColor) = when (status) {
                UserStatus.ONLINE -> "온라인" to android.R.color.holo_green_dark
                UserStatus.OFFLINE -> "오프라인" to android.R.color.darker_gray
                UserStatus.WARNING -> "주의" to android.R.color.holo_orange_dark
                UserStatus.DANGER -> "위험" to android.R.color.holo_red_dark
                UserStatus.EMERGENCY -> "긴급" to android.R.color.holo_red_dark
            }
            
            binding.statusText.text = statusText
            binding.statusText.setTextColor(itemView.context.getColor(statusColor))
        }

        /**
         * 위험도 업데이트
         */
        private fun updateRiskLevel(riskLevel: RiskLevel) {
            val (riskText, riskColor) = when (riskLevel) {
                RiskLevel.SAFE -> "안전" to android.R.color.holo_green_dark
                RiskLevel.WARNING -> "주의" to android.R.color.holo_orange_dark
                RiskLevel.DANGER -> "위험" to android.R.color.holo_red_dark
                RiskLevel.EMERGENCY -> "긴급" to android.R.color.holo_red_dark
            }
            
            binding.riskLevelText.text = riskText
            binding.riskLevelText.setTextColor(itemView.context.getColor(riskColor))
        }

        /**
         * 온라인 상태 업데이트
         */
        private fun updateOnlineStatus(isOnline: Boolean) {
            val onlineColor = if (isOnline) {
                itemView.context.getColor(android.R.color.holo_green_dark)
            } else {
                itemView.context.getColor(android.R.color.darker_gray)
            }
            
            binding.statusIndicator.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(onlineColor)
        }

        /**
         * 마지막 연결 시간 포맷팅
         */
        private fun formatLastSeen(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60000 -> "방금 전" // 1분 미만
                diff < 3600000 -> "${diff / 60000}분 전" // 1시간 미만
                diff < 86400000 -> "${diff / 3600000}시간 전" // 1일 미만
                else -> dateFormat.format(Date(timestamp))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userSummaries[position])
    }

    override fun getItemCount(): Int = userSummaries.size

    fun updateData(newUserSummaries: List<UserSummary>) {
        userSummaries = newUserSummaries
        notifyDataSetChanged()
    }
} 