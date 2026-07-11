package com.anbalan.app // VUI LÒNG KIỂM TRA: Thay đổi tên package cho khớp nếu dự án của bạn khác

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anbalan.app.ui.VpnScreen
import com.anbalan.app.ui.VpnViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AnBaLan_Main"
    }

    // Khởi tạo ViewModel quản lý StateFlow theo chuẩn Jetpack
    private val viewModel: VpnViewModel by viewModels()

    /**
     * 1. Bộ lắng nghe kết quả trả về từ màn hình Cài đặt Quyền Overlay (Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
     */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        Log.i(TAG, "Returned from Overlay Settings. Re-checking permissions...")
        viewModel.checkPermissions()
        viewModel.checkAndStartOverlay()
    }

    /**
     * 2. Bộ lắng nghe kết quả trả về từ hộp thoại đồng ý kết nối VPN của Android (VpnService.prepare)
     */
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "VPN permission GRANTED by user.")
            viewModel.onVpnPermissionGranted()
            Toast.makeText(this, "Đã cấp quyền VPN thành công!", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "VPN permission DENIED by user.")
            Toast.makeText(this, "Bạn bắt buộc phải chấp nhận quyền VPN để sử dụng Engine!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate")

        setContent {
            // Lắng nghe luồng dữ liệu StateFlow từ ViewModel theo thời gian thực (Zero Polling)
            val uiState by viewModel.uiState.collectAsState()

            // Kết nối dữ liệu vào màn hình Jetpack Compose
            VpnScreen(
                uiState = uiState,
                onRequestOverlayPermission = { requestOverlayPermission() },
                onRequestVpnPermission = { requestVpnPermission() },
                onToggleVpn = { viewModel.toggleEngine(this@MainActivity) }
            )
        }
    }

    /**
     * Mỗi khi người dùng mở lại app hoặc từ Settings quay lại, tự động quét lại quyền
     * và khởi chạy Tab nổi nếu điều kiện đã thỏa mãn.
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "MainActivity onResume - verifying system permissions...")
        viewModel.checkPermissions()
        viewModel.checkAndStartOverlay()
    }

    /**
     * Xử lý kích hoạt màn hình cấp quyền Tab nổi (Overlay / System Alert Window)
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch overlay settings", e)
                    Toast.makeText(this, "Không thể mở trang cài đặt quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Quyền Tab nổi (Overlay) đã được cấp sẵn!", Toast.LENGTH_SHORT).show()
                viewModel.checkPermissions()
                viewModel.checkAndStartOverlay()
            }
        }
    }

    /**
     * Xử lý hiển thị hộp thoại cấp quyền VpnService của hệ điều hành Android
     */
    private fun requestVpnPermission() {
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                // Nếu intent != null, nghĩa là người dùng chưa cấp quyền -> Mở hộp thoại hệ thống
                vpnPermissionLauncher.launch(intent)
            } else {
                // Nếu intent == null, nghĩa là Android ghi nhận quyền đã được cấp từ trước
                Log.i(TAG, "VPN permission already granted previously.")
                Toast.makeText(this, "Quyền VPN hệ thống đã sẵn sàng!", Toast.LENGTH_SHORT).show()
                viewModel.onVpnPermissionGranted()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error checking VpnService permission", e)
            Toast.makeText(this, "Lỗi kiểm tra quyền VPN: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}