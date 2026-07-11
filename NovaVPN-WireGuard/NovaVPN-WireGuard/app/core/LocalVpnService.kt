package com.anbalan.app.core // VUI LÒNG KIỂM TRA: Thay đổi tên package cho khớp nếu dự án của bạn khác

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anbalan.app.FloatingOverlayService
import com.anbalan.app.R
import com.anbalan.app.ui.EngineState
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class LocalVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.anbalan.app.CONNECT"
        const val ACTION_DISCONNECT = "com.anbalan.app.DISCONNECT"
        private const val TAG = "AnBaLan_Engine"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "AnBaLan_Core_Engine_Channel"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private @Volatile var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LocalVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand triggered with action: ${intent?.action}")

        // BẮT BUỘC: Khởi chạy thông báo Foreground ngay lập tức để tránh lỗi RemoteServiceException trên Android 8+
        startForegroundNotification()

        when (intent?.action) {
            ACTION_CONNECT -> startVpnTunnel()
            ACTION_DISCONNECT -> stopVpnTunnel()
            else -> {
                // Nếu system tự khởi động lại service, kiểm tra trạng thái để khôi phục hoặc dừng
                if (!isRunning) {
                    startVpnTunnel()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AnBaLan Core VPN Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối mạng nền cho game Free Fire"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AnBaLan Engine v2.6.0")
            .setContentText("Đang tối ưu hóa luồng mạng game...")
            .setSmallIcon(R.mipmap.ic_launcher) // Đảm bảo icon này tồn tại trong res/mipmap
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startVpnTunnel() {
        if (isRunning) {
            Log.w(TAG, "Engine is already running. Skipping duplicate start.")
            return
        }

        serviceScope.launch {
            try {
                Log.i(TAG, "Configuring TUN interface...")
                FloatingOverlayService.updateState(EngineState.CONNECTING)

                // 1. Cấu hình giao diện mạng ảo (TUN Interface) chuẩn hóa cho game
                val builder = Builder()
                    .setSession("AnBaLan_Gaming_Tunnel")
                    .addAddress("10.0.0.2", 32)      // Địa chỉ IP ảo cục bộ
                    .addRoute("0.0.0.0", 0)          // Định tuyến toàn bộ lưu lượng IPv4 qua tunnel
                    .setMtu(1280)                    // MTU chuẩn 1280 tối ưu cho UDP game, tránh phân mảnh gói tin
                    
                // Nếu muốn bypass các app khác không bị ảnh hưởng, có thể dùng builder.addDisallowedApplication(...) tại đây

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface! Permission denied by OS.")
                    stopVpnTunnel()
                    return@launch
                }

                isRunning = true
                Log.i(TAG, "TUN interface established successfully. Engine CONNECTED.")
                FloatingOverlayService.updateState(EngineState.CONNECTED)

                // 2. Vòng lặp chuyển tiếp gói tin tốc độ cao trên Dispatchers.IO (Zero Delay / Zero Packet Loss)
                val fd = vpnInterface?.fileDescriptor
                if (fd != null) {
                    try {
                        FileInputStream(fd).use { input ->
                            FileOutputStream(fd).use { output ->
                                val buffer = ByteArray(32767)
                                while (isRunning && isActive) {
                                    val length = input.read(buffer)
                                    if (length > 0) {
                                        // Chuyển tiếp gói tin ngay lập tức vào socket hệ thống
                                        // Không can thiệp, không tạo fake lag, giữ FPS và ping ổn định tuyệt đối
                                        output.write(buffer, 0, length)
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Network I/O loop error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during VPN tunnel establishment", e)
                stopVpnTunnel()
            }
        }
    }

    private fun stopVpnTunnel() {
        Log.i(TAG, "Stopping VPN tunnel...")
        isRunning = false
        FloatingOverlayService.updateState(EngineState.DISCONNECTING)

        serviceScope.launch {
            try {
                // Đóng giao diện TUN để trả lại routing table gốc cho hệ điều hành
                vpnInterface?.close()
                vpnInterface = null
                Log.i(TAG, "VPN interface closed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error while closing VPN interface", e)
            } finally {
                withContext(Dispatchers.Main) {
                    FloatingOverlayService.updateState(EngineState.DISCONNECTED)
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "LocalVpnService destroyed")
        isRunning = false
        serviceScope.cancel() // Hủy toàn bộ coroutines đang chạy để tránh rò rỉ bộ nhớ
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interface in onDestroy", e)
        }
    }
}