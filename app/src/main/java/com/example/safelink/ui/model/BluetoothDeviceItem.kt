package com.example.safelink.ui.model

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int,
    var isConnected: Boolean = false,
    var isConnecting: Boolean = false,
    var hasGattServer: Boolean = false,
    var isEsp32Device: Boolean = false // ESP32C6 센서 디바이스 여부
) 