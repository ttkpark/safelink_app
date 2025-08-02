package com.example.safelink.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.safelink.databinding.ItemBluetoothDeviceBinding
import com.example.safelink.ui.model.BluetoothDeviceItem

class BluetoothDeviceAdapter(
    private var devices: List<BluetoothDeviceItem>,
    private val onDeviceClick: (BluetoothDeviceItem) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(
        private val binding: ItemBluetoothDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BluetoothDeviceItem) {
            binding.deviceName.text = device.name
            binding.deviceAddress.text = device.address
            
            // ESP32C6 센서 디바이스 여부에 따른 디바이스 타입 표시
            when {
                device.isEsp32Device -> {
                    binding.deviceType.text = "ESP32C6 센서"
                    binding.deviceType.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_blue_dark)
                    )
                }
                device.hasGattServer -> {
                    binding.deviceType.text = "GATT Server"
                    binding.deviceType.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_green_dark)
                    )
                }
                else -> {
                    binding.deviceType.text = "Unknown Device"
                    binding.deviceType.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_red_dark)
                    )
                }
            }
            
            binding.signalStrength.text = "${device.rssi} dBm"
            
            // 연결 상태에 따른 UI 업데이트
            when {
                device.isConnecting -> {
                    binding.connectionStatus.text = "연결 중..."
                    binding.connectionStatus.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_blue_dark)
                    )
                    binding.root.strokeColor = binding.root.context.getColor(android.R.color.holo_blue_dark)
                }
                device.isConnected -> {
                    binding.connectionStatus.text = "연결됨"
                    binding.connectionStatus.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_green_dark)
                    )
                    binding.root.strokeColor = binding.root.context.getColor(android.R.color.holo_green_dark)
                }
                else -> {
                    binding.connectionStatus.text = "연결 안됨"
                    binding.connectionStatus.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_red_dark)
                    )
                    binding.root.strokeColor = binding.root.context.getColor(android.R.color.holo_red_dark)
                }
            }
            
            // 신호 강도에 따른 색상 변경
            val signalColor = when {
                device.rssi >= -50 -> android.R.color.holo_green_dark
                device.rssi >= -70 -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_red_dark
            }
            binding.signalStrength.setTextColor(binding.root.context.getColor(signalColor))
            
            // ESP32C6 센서 디바이스인 경우 배경색 변경
            if (device.isEsp32Device) {
                binding.root.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.holo_blue_light)
                )
            } else {
                binding.root.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.transparent)
                )
            }
            
            // 클릭 이벤트
            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<BluetoothDeviceItem>) {
        devices = newDevices
        notifyDataSetChanged()
    }
} 