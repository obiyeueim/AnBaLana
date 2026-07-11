package com.anbalan.app.ui // VUI LÒNG KIỂM TRA: Thay đổi tên package cho khớp nếu dự án của bạn khác

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anbalan.app.FloatingOverlayService
import com.anbalan.app.core.LocalVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Các trạng thái hoạt động cốt lõi của Engine mạng
 */
enum class EngineState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
}

/**
 * Cấu trúc dữ liệu trạng thái UI (Immutable State) dành cho Jetpack Compose
 */
data class AppUiState(
    val engineState: EngineState = EngineState.DISCONNECTED,
    val hasOverlayPermission: Boolean = false,
    val hasVpnPermission: Boolean = false,
    val errorMessage: String? = null
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AnBaLan_ViewModel"
    }

    // MutableStateFlow nội bộ để cập nhật dữ liệu, StateFlow công khai chỉ đọc cho UI
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        Log.i(TAG, "VpnViewModel initialized. Checking system permissions...")
        checkPermissions()
    }

    /**
     * Quét và cập nhật trạng thái của 2 quyền bắt buộc (Overlay & VPN)
     */
    fun checkPermissions() {
        val context = getApplication<Application>().applicationContext
        
        // 1. Kiểm tra quyền Tab nổi (Overlay / System Alert Window)
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        // 2. Kiểm tra quyền VpnService của Android
        // Nếu VpnService.prepare() trả về null nghĩa là hệ thống đã cấp quyền từ trước
        val vpnIntent = VpnService.prepare(context)
        val vpnGranted = (vpnIntent == null)

        Log.d(TAG, "Permission status -> Overlay: $overlayGranted | VPN: $vpnGranted")

        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = overlayGranted,
            hasVpnPermission = vpnGranted
        )
    }

    /**
     * Được gọi từ MainActivity khi người dùng đồng ý hộp thoại cấp quyền VPN
     */
    fun onVpnPermissionGranted() {
        Log.i(TAG, "VPN permission confirmed by user.")
        _uiState.value = _uiState.value.copy(hasVpnPermission = true)
        checkAndStartOverlay()
    }

    /**
     * Tự động khởi chạy Tab nổi (FloatingOverlayService) ngay khoảnh khắc hội đủ 2 quyền
     */
    fun checkAndStartOverlay() {
        val currentState = _uiState.value
        if (currentState.hasOverlayPermission && currentState.hasVpnPermission) {
            Log.i(TAG, "All permissions granted! Launching FloatingOverlayService...")
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, FloatingOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FloatingOverlayService", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Lỗi khởi chạy Tab nổi: ${e.message}")
            }
        } else {
            Log.w(TAG, "Cannot start overlay yet. Permissions missing.")
        }
    }

    /**
     * Xử lý thao tác gạt bật/tắt Engine mạng từ nút bấm trên màn hình chính
     */
    fun toggleEngine(context: Context) {
        val currentEngineState = _uiState.value.engineState

        // CƠ CHẾ KHÓA THAO TÁC (Debounce & State Lock):
        // Chặn đứng mọi lệnh bấm nếu hệ thống đang trong quá trình chuyển đổi kết nối
        if (currentEngineState == EngineState.CONNECTING || currentEngineState == EngineState.DISCONNECTING) {
            Log.w(TAG, "Engine is currently busy ($currentEngineState). Button click ignored to prevent spam.")
            return
        }

        if (currentEngineState == EngineState.DISCONNECTED) {
            startEngine(context)
        } else if (currentEngineState == EngineState.CONNECTED) {
            stopEngine(context)
        }
    }

    /**
     * Gửi lệnh khởi chạy Core VPN Engine trên luồng cách ly IO
     */
    private fun startEngine(context: Context) {
        viewModelScope.launch {
            Log.i(TAG, "Initiating Engine start sequence...")
            _uiState.value = _uiState.value.copy(engineState = EngineState.CONNECTING)
            FloatingOverlayService.updateState(EngineState.CONNECTING)

            try {
                // Chuyển toàn bộ công việc khởi tạo mạng sang luồng I/O ngầm
                withContext(Dispatchers.IO) {
                    val intent = Intent(context, LocalVpnService::class.java).apply {
                        action = LocalVpnService.ACTION_CONNECT
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }

                // Cập nhật trạng thái thành công cho UI và Tab nổi
                _uiState.value = _uiState.value.copy(engineState = EngineState.CONNECTED, errorMessage = null)
                FloatingOverlayService.updateState(EngineState.CONNECTED)
                Log.i(TAG, "Engine started successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Engine start", e)
                _uiState.value = _uiState.value.copy(
                    engineState = EngineState.DISCONNECTED,
                    errorMessage = "Lỗi khởi chạy Engine: ${e.message}"
                )
                FloatingOverlayService.updateState(EngineState.DISCONNECTED)
            }
        }
    }

    /**
     * Gửi lệnh ngắt kết nối Core VPN Engine trên luồng cách ly IO
     */
    private fun stopEngine(context: Context) {
        viewModelScope.launch {
            Log.i(TAG, "Initiating Engine stop sequence...")
            _uiState.value = _uiState.value.copy(engineState = EngineState.DISCONNECTING)
            FloatingOverlayService.updateState(EngineState.DISCONNECTING)

            try {
                withContext(Dispatchers.IO) {
                    val intent = Intent(context, LocalVpnService::class.java).apply {
                        action = LocalVpnService.ACTION_DISCONNECT
                    }
                    context.startService(intent)
                }

                _uiState.value = _uiState.value.copy(engineState = EngineState.DISCONNECTED, errorMessage = null)
                FloatingOverlayService.updateState(EngineState.DISCONNECTED)
                Log.i(TAG, "Engine stopped successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Engine stop", e)
                // Đưa về trạng thái ngắt kết nối an toàn nếu có lỗi xảy ra
                _uiState.value = _uiState.value.copy(engineState = EngineState.DISCONNECTED)
                FloatingOverlayService.updateState(EngineState.DISCONNECTED)
            }
        }
    }
}