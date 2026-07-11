package com.khanhan.novavpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var powerButton: TextView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        if (Settings.canDrawOverlays(this)) showOverlay()
        VpnViewModel.overlayStateFlow()?.let { stateFlow ->
            serviceScope.launch {
                stateFlow
                    .map { it.status }
                    .distinctUntilChanged()
                    .collect { updatePowerButton(it) }
            }
        } ?: updatePowerButton(ConnectionStatus.DISCONNECTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null && Settings.canDrawOverlays(this)) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(238, 14, 14, 18))
                setStroke(dp(1), Color.rgb(92, 37, 47))
            }
            elevation = dp(10).toFloat()
        }

        val openButton = overlayButton("OFF", Color.rgb(241, 43, 67))
        powerButton = openButton
        val settingsButton = overlayButton("APP", Color.WHITE)
        val closeButton = overlayButton("×", Color.rgb(160, 160, 170))
        panel.addView(openButton)
        panel.addView(settingsButton)
        panel.addView(closeButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        openButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(panel, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) view.performClick()
                    true
                }
                else -> false
            }
        }
        openButton.setOnClickListener { toggleVpn() }
        settingsButton.setOnClickListener { openApp() }
        closeButton.setOnClickListener { stopSelf() }

        overlayView = panel
        windowManager.addView(panel, params)
    }

    private fun overlayButton(label: String, color: Int): TextView = TextView(this).apply {
        text = label
        textSize = if (label == "×") 24f else 11f
        setTextColor(color)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(15).toFloat()
            setColor(Color.rgb(28, 28, 34))
            setStroke(dp(1), Color.rgb(59, 59, 69))
        }
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun toggleVpn() {
        if (!VpnViewModel.hasOverlayConfig() || VpnService.prepare(this) != null) {
            openApp()
            return
        }
        // Disconnecting mid-game changes the device route/IP and interrupts live
        // UDP sessions. Require the full app for disconnect so an accidental tap
        // on the overlay cannot freeze an active match.
        if (VpnViewModel.overlayStatus() == ConnectionStatus.CONNECTED) {
            openApp()
            return
        }
        if (!VpnViewModel.toggleFromOverlay()) openApp()
    }

    private fun updatePowerButton(status: ConnectionStatus) {
        val button = powerButton ?: return
        when (status) {
            ConnectionStatus.CONNECTED -> {
                button.text = "ON"
                button.setTextColor(Color.rgb(49, 224, 123))
            }
            ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> {
                button.text = "..."
                button.setTextColor(Color.rgb(255, 201, 92))
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                button.text = "OFF"
                button.setTextColor(Color.rgb(241, 43, 67))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tab nổi AnBaLan",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Giữ tab điều khiển VPN hiển thị trên màn hình" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("Tab nổi AnBaLan đang bật")
        .setContentText("Chạm để mở ứng dụng")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "anbalan_overlay"
        private const val NOTIFICATION_ID = 2101

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
