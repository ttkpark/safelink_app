package com.example.safelink.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.safelink.databinding.ItemHistoryEntryBinding
import com.example.safelink.model.HistoryEntry
import com.example.safelink.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.*

class HistoryEntryAdapter(
    private var historyEntries: List<HistoryEntry> = emptyList()
) : RecyclerView.Adapter<HistoryEntryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    inner class HistoryViewHolder(
        private val binding: ItemHistoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: HistoryEntry) {
            // 타임스탬프
            binding.timestampText.text = dateFormat.format(Date(entry.timestamp))

            // 위험도 표시
            val (riskText, riskColor) = when (entry.riskLevel) {
                RiskLevel.SAFE -> "안전" to com.google.android.material.R.color.design_default_color_primary
                RiskLevel.WARNING -> "주의" to com.google.android.material.R.color.design_default_color_secondary
                RiskLevel.DANGER -> "위험" to com.google.android.material.R.color.design_default_color_error
                RiskLevel.EMERGENCY -> "긴급" to com.google.android.material.R.color.design_default_color_error
            }

            binding.riskLevelIndicator.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(itemView.context.getColor(riskColor))
            binding.riskLevelText.text = riskText
            binding.riskLevelText.setTextColor(itemView.context.getColor(riskColor))

            // SafeLink Band 데이터
            val sensorData = entry.sensorData
            binding.heartRateText.text = "${sensorData.heartRate} BPM"
            binding.bodyTemperatureText.text = "${sensorData.bodyTemperature}°C"
            binding.bandBatteryText.text = "${sensorData.batteryLevel}%"

            // SafeLink Hub 데이터
            binding.ambientTemperatureText.text = "${sensorData.ambientTemperature}°C"
            binding.humidityText.text = "${sensorData.humidity}%"
            binding.noiseLevelText.text = "${sensorData.noiseLevel} dB"
            binding.wbgtText.text = "${sensorData.wbgt}°C"

            // 위치 정보
            binding.locationText.text = entry.location ?: "알 수 없음"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyEntries[position])
    }

    override fun getItemCount(): Int = historyEntries.size

    fun updateData(newEntries: List<HistoryEntry>) {
        historyEntries = newEntries
        notifyDataSetChanged()
    }
} 