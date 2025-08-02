package com.example.safelink.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safelink.MainActivity
import com.example.safelink.databinding.FragmentDeviceDiscoveryBinding
import com.example.safelink.bluetooth.BleManager
import com.example.safelink.ui.adapter.BluetoothDeviceAdapter
import com.example.safelink.ui.model.BluetoothDeviceItem
import kotlinx.coroutines.launch
import com.example.safelink.bluetooth.BleConstants
import com.example.safelink.service.BluetoothService
import com.example.safelink.service.ConnectionStatus
import com.example.safelink.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope

class DeviceDiscoveryFragment : Fragment() {
    private var _binding: FragmentDeviceDiscoveryBinding? = null
    private val binding get() = _binding!!

    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private val discoveredDevices = mutableListOf<BluetoothDeviceItem>()
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private var bluetoothService: BluetoothService? = null
    private var isServiceBound = false
    private var connectingDevice: BluetoothDeviceItem? = null
    private var setConnectionDeviceState: ((Boolean)->Unit)? = null
    
    // 서비스 연결을 위한 ServiceConnection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "BluetoothService 연결됨")
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isServiceBound = true
            
            // 서비스 콜백 설정
            setupBleManager()
            setupServiceCallbacks()

            startScan()
            
            Log.d(TAG, "DeviceDiscoveryFragment에서 BluetoothService 바인딩 완료")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isServiceBound = false
            Log.d(TAG, "BluetoothService 연결 해제됨")
        }
    }

    // ActivityResultLauncher for enabling Bluetooth
    @Suppress("DEPRECATION")
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            Log.d(TAG, "블루투스가 활성화되었습니다.")
            updateBluetoothStatus()
            checkPermissions()
        } else {
            Log.w(TAG, "블루투스 활성화가 거부되었습니다.")
            updateBluetoothStatus()
        }
    }
    
    // ActivityResultLauncher for permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "모든 권한이 승인되었습니다.")
        } else {
            Log.w(TAG, "일부 권한이 거부되었습니다.")
            Toast.makeText(requireContext(), "BLE 스캔을 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        initializeBluetooth()
        bindBluetoothService()
    }
    
    /**
     * BluetoothService 바인딩
     * 1. 이 화면에 바인딩하여 블루투스 연결할 수 있도록 돕는다.
     */
    private fun bindBluetoothService() {
        try {
            val intent = Intent(requireContext(), BluetoothService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "DeviceDiscoveryFragment에서 BluetoothService 바인딩 시도")
        } catch (e: Exception) {
            Log.e(TAG, "BluetoothService 바인딩 실패", e)
            Toast.makeText(requireContext(), "BluetoothService 바인딩에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 서비스 콜백 설정
     * 3. UI변경에 필요한 모든 파라메터를 주고받을 수 있어야 한다.
     */
    private fun setupServiceCallbacks() {
        bluetoothService?.let { service ->
            // 디바이스 발견 콜백
            service.onDeviceFound = { deviceInfo ->
                lifecycleScope.launch {
                    addDeviceToList(deviceInfo)
                }
            }
            
            // 스캔 시작 콜백
            service.onScanStarted = {
                lifecycleScope.launch {
                    updateScanStatus(true)
                }
            }
            
            // 스캔 중지 콜백
            service.onScanStopped = {
                lifecycleScope.launch {
                    updateScanStatus(false)
                }
            }
            
            // 연결 상태 변경 콜백
            service.onConnectionStatusChanged = { status ->
                lifecycleScope.launch {
                    updateConnectionStatusFromService(status)
                }
            }
            
            // 오류 콜백
            service.onError = { errorMessage ->
                lifecycleScope.launch {
                    Log.e(TAG, "서비스 오류: $errorMessage")
                    Toast.makeText(requireContext(), "오류: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
            
            Log.d(TAG, "서비스 콜백 설정 완료")
        }
    }
    
    /**
     * 서비스에서 연결 상태 업데이트
     */
    private fun updateConnectionStatusFromService(status: ConnectionStatus) {
        when (status) {
            is ConnectionStatus.Connected -> {
                Log.i(TAG, "ESP32C6 센서 디바이스 연결 성공: ${status.deviceName}")
                Toast.makeText(requireContext(), "ESP32C6 센서에 연결되었습니다!", Toast.LENGTH_SHORT).show()
                
                // 연결 성공 시 ClientMainFragment로 이동
                navigateToClientMain()
            }
            is ConnectionStatus.Failed -> {
                Log.e(TAG, "ESP32C6 센서 디바이스 연결 실패: ${status.reason}")
                Toast.makeText(requireContext(), "연결 실패: ${status.reason}", Toast.LENGTH_SHORT).show()
                
                // 연결 실패 시 UI 상태 초기화
                updateDeviceConnectionStatus(false)
            }
            else -> {
                // 다른 상태는 무시
            }
        }
    }
    
    /**
     * 스캔 상태 업데이트
     */
    private fun updateScanStatus(isScanning: Boolean) {
        this.isScanning = isScanning
        
        if (isScanning) {
            binding.scanButton.text = "스캔 중지"
            binding.scanStatusText.text = "웨어러블 허브 디바이스를 스캔 중..."
        } else {
            val wearableHubDevices = discoveredDevices.count { it.name.contains("Wearable") || it.name.contains("Hub") }
            binding.scanButton.text = "스캔 시작"
            binding.scanStatusText.text = "스캔 완료 - 총 ${discoveredDevices.size}개 디바이스 (웨어러블 허브: ${wearableHubDevices}개)"
        }
    }
    
    /**
     * 디바이스 연결 상태 업데이트
     */
    private fun updateDeviceConnectionStatus(isConnected: Boolean) {
        discoveredDevices.forEach { device ->
            device.isConnected = isConnected
            device.isConnecting = false
        }
        deviceAdapter.notifyDataSetChanged()
        
        binding.connectedDevicesText.text = if (isConnected) "연결된 디바이스: 1개" else "연결된 디바이스: 0개"
    }
    
    private fun setupBleManager() {
        bluetoothService?.let { service ->
            bleManager = service.getBLEManager()

            bleManager.onConnectedFragment = {
                lifecycleScope.launch {
                    updateConnectionStatus(true)
                    Toast.makeText(requireContext(), "ESP32C6_Sensor에 연결되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }

            bleManager.onDisconnectedFragment = {
                if(setConnectionDeviceState != null) {
                    Log.e(TAG, "ESP32C6 센서 디바이스 연결 시도 실패: ${connectingDevice?.address}")


                    // 연결 상태 초기화
                    connectingDevice?.isConnecting = false

                    (context as MainActivity).runOnUiThread{
                        Toast.makeText(requireContext(), "연결 시도 실패", Toast.LENGTH_SHORT).show()
                        setConnectionDeviceState?.invoke(false)
                    }
                    connectingDevice = null
                    setConnectionDeviceState = null
                }

                lifecycleScope.launch {
                    updateConnectionStatus(false)
                    Toast.makeText(requireContext(), "ESP32C6_Sensor 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Log.e(TAG, "BluetoothService가 바인딩되지 않음")
            Toast.makeText(requireContext(), "BluetoothService가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }

    }
    
    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter(discoveredDevices) { device ->
            connectToDevice(device)
        }
        
        binding.deviceList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }
        
        binding.disconnectButton.setOnClickListener {
            disconnectAllDevices()
        }
    }
    
    private fun initializeBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "이 기기는 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show()
            return
        }
        
        updateBluetoothStatus()
        checkPermissions()
    }
    
    private fun updateBluetoothStatus() {
        if (bluetoothAdapter?.isEnabled == true) {
            binding.bluetoothStatusText.text = "블루투스 활성화됨"
            binding.scanButton.isEnabled = true
            binding.statusCard.isVisible = false
        } else {
            binding.bluetoothStatusText.text = "블루투스 비활성화됨"
            binding.scanButton.isEnabled = false
            binding.statusCard.isVisible = true
            requestEnableBluetooth()
        }
    }
    
    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
    
    /**
     * 블루투스 스캔 시작
     * 2. 여러 통신을 통해 BluetoothService를 통하여 검색, 연결판단, 연결 등을 관리한다.
     */
    private fun startScan() {
        if (!hasRequiredPermissions()) {
            showPermissionDialog()
            return
        }
        
        // 기존 디바이스 목록 초기화
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        // BluetoothService를 통한 스캔 시작
        bluetoothService?.let { service ->
            val success = service.startScan({stopScan()})
            if (success) {
                Log.d(TAG, "BluetoothService를 통한 스캔 시작")
            } else {
                Log.e(TAG, "BluetoothService 스캔 시작 실패")
                Toast.makeText(requireContext(), "스캔 시작에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e(TAG, "초기 스캔 : BluetoothService가 바인딩되지 않음")
            //Toast.makeText(requireContext(), "BluetoothService가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 블루투스 스캔 중지
     */
    private fun stopScan() {
        bluetoothService?.let { service ->
            service.stopScan()
            Log.d(TAG, "BluetoothService를 통한 스캔 중지")
        } ?: run {
            Log.e(TAG, "BluetoothService가 바인딩되지 않음")
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addDeviceToList(device: BluetoothDevice) {
        val deviceName = device.name ?: "Unknown Device"
        val deviceAddress = device.address
        
        // 웨어러블 허브 디바이스인지 확인
        val isWearableHubDevice = deviceName == BleConstants.DEVICE_NAME || 
                                 deviceName.contains("Wearable") || 
                                 deviceName.contains("Hub")
        
        val deviceItem = BluetoothDeviceItem(
            name = deviceName,
            address = deviceAddress,
            rssi = 0,
            isConnected = false,
            isConnecting = false,
            hasGattServer = false,
            isEsp32Device = isWearableHubDevice // 웨어러블 허브 디바이스 여부 추가
        )
        
        // 중복 디바이스 체크
        val existingIndex = discoveredDevices.indexOfFirst { it.address == device.address }
        if (existingIndex == -1) {
            discoveredDevices.add(deviceItem)
            deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
            
            if (isWearableHubDevice) {
                Log.i(TAG, "웨어러블 허브 디바이스 발견: $deviceName (${device.address})")
            } else {
                Log.d(TAG, "일반 BLE 디바이스 발견: $deviceName (${device.address})")
            }
            
            // UI 업데이트
            updateDeviceCount()
            
            // GATT Server 지원 여부 확인 (백그라운드에서)
            lifecycleScope.launch {
                try {
                    val hasGattServer = device.fetchUuidsWithSdp()
                    deviceItem.hasGattServer = hasGattServer
                    deviceAdapter.notifyItemChanged(discoveredDevices.indexOf(deviceItem))
                } catch (e: Exception) {
                    Log.e(TAG, "GATT Server 확인 실패: ${e.message}")
                }
            }
        }
    }
    
    /**
     * DeviceInfo를 BluetoothDeviceItem으로 변환하여 목록에 추가
     */
    private fun addDeviceToList(deviceInfo: DeviceInfo) {
        val deviceItem = BluetoothDeviceItem(
            name = deviceInfo.deviceName,
            address = deviceInfo.deviceAddress,
            rssi = 0,
            isConnected = deviceInfo.isConnected,
            isConnecting = false,
            hasGattServer = true, // 서비스에서 온 디바이스는 GATT Server가 있다고 가정
            isEsp32Device = deviceInfo.deviceName.contains("ESP32") || deviceInfo.deviceName.contains("Sensor")
        )
        
        // 중복 디바이스 체크
        val existingIndex = discoveredDevices.indexOfFirst { it.address == deviceInfo.deviceAddress }
        if (existingIndex == -1) {
            discoveredDevices.add(deviceItem)
            deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
            
            if (deviceItem.isEsp32Device) {
                Log.i(TAG, "ESP32C6 센서 디바이스 발견: ${deviceItem.name} (${deviceItem.address})")
            } else {
                Log.d(TAG, "일반 BLE 디바이스 발견: ${deviceItem.name} (${deviceItem.address})")
            }
            
            // UI 업데이트
            updateDeviceCount()
        }
    }
    
    /**
     * 디바이스 연결 처리
     * 4. 기기에 연결이 성공했다면, 서비스를 Foreground Service로 승격시키고, 데이터를 받는다.
     */
    private fun connectToDevice(device: BluetoothDeviceItem) {
        Log.d(TAG, "디바이스 연결 시도: ${device.name}")
        
        // 웨어러블 허브 디바이스인지 확인
        if (device.isEsp32Device) {
            // 디바이스 주소 저장
            saveWearableHubAddress(device.address)
            
            // 연결 상태 업데이트
            device.isConnecting = true
            deviceAdapter.notifyDataSetChanged()

            connectingDevice = device
            setConnectionDeviceState = {
                device.isConnecting = it
                deviceAdapter.notifyDataSetChanged()
            }
            
            // BluetoothService를 통한 연결
            bluetoothService?.let { service ->
                val success = service.connectToWearableHubDevice(device.address)
                if (success) {
                    Log.d(TAG, "웨어러블 허브 디바이스 연결 시도 성공: ${device.address}")
                    Toast.makeText(requireContext(), "웨어러블 허브 연결을 시도합니다.", Toast.LENGTH_SHORT).show()
                    
                } else {
                    Log.e(TAG, "웨어러블 허브 디바이스 연결 시도 실패: ${device.address}")
                    Toast.makeText(requireContext(), "연결 시도 실패", Toast.LENGTH_SHORT).show()
                    
                    // 연결 상태 초기화
                    device.isConnecting = false
                    deviceAdapter.notifyDataSetChanged()
                }
            } ?: run {
                Log.e(TAG, "BluetoothService가 바인딩되지 않음")
                Toast.makeText(requireContext(), "BluetoothService가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
                
                // 연결 상태 초기화
                device.isConnecting = false
                deviceAdapter.notifyDataSetChanged()
            }
        } else {
            Toast.makeText(requireContext(), "웨어러블 허브 디바이스가 아닙니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 웨어러블 허브 디바이스 주소 저장
     */
    private fun saveWearableHubAddress(deviceAddress: String) {
        val sharedPrefs = requireContext().getSharedPreferences("SafeLinkPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("wearable_hub_device_address", deviceAddress).apply()
        Log.d(TAG, "웨어러블 허브 디바이스 주소 저장: $deviceAddress")
    }
    
    /**
     * ClientMainFragment로 이동
     */
    private fun navigateToClientMain() {
        val fragment = ClientMainFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "ClientMainFragment로 이동")
    }
    
    /**
     * 센서 모니터링 화면으로 이동 (작업자 서비스 흐름 문서 기반)
     */
    private fun navigateToSensorMonitor() {
        val fragment = SensorMonitorFragment()
        parentFragmentManager.beginTransaction()
            .replace(com.example.safelink.R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        
        Log.d(TAG, "센서 모니터링 화면으로 이동")
    }
    
    private fun disconnectAllDevices() {
        bluetoothService?.let { service ->
            // Foreground Service 해제
            service.demoteFromForeground()
            
            // 모든 연결 해제 및 정리
            service.cleanup()
            Log.d(TAG, "모든 디바이스 연결 해제 및 Foreground Service 해제")
        } ?: run {
            Log.e(TAG, "BluetoothService가 바인딩되지 않음")
        }
        
        discoveredDevices.forEach { it.isConnected = false }
        deviceAdapter.notifyDataSetChanged()
        updateConnectionStatus(false)
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        discoveredDevices.forEach { device ->
            device.isConnected = isConnected
            device.isConnecting = false
        }
        deviceAdapter.notifyDataSetChanged()
        
        binding.connectedDevicesText.text = if (isConnected) "연결된 디바이스: 1개" else "연결된 디바이스: 0개"
    }
    
    private fun updateDeviceCount() {
        val count = discoveredDevices.size
        binding.scanStatusText.text = "발견된 디바이스: ${count}개"
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        return permissions.all {
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun showPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 필요")
            .setMessage("BLE 스캔을 위해 위치 권한이 필요합니다.")
            .setPositiveButton("권한 요청") { _, _ ->
                checkPermissions()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onDestroyView() {
        super.onDestroyView()
        if (isScanning) {
            stopScan()
        }
        bleManager.cleanup()
        
        // 서비스 바인딩 해제
        if (isServiceBound) {
            try {
                requireContext().unbindService(serviceConnection)
                isServiceBound = false
                Log.d(TAG, "DeviceDiscoveryFragment에서 BluetoothService 바인딩 해제")
            } catch (e: Exception) {
                Log.e(TAG, "BluetoothService 바인딩 해제 실패", e)
            }
        }
        
        _binding = null
    }
    
    companion object {
        private const val TAG = "DeviceDiscoveryFragment"
        private const val PERMISSION_REQUEST_CODE = 100
    }
} 