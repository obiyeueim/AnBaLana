package com.khanhan.novavpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khanhan.novavpn.ui.NovaTheme
import com.khanhan.novavpn.ui.PermissionGateScreen
import com.khanhan.novavpn.ui.TikTokGateScreen
import com.khanhan.novavpn.ui.VpnScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(8, 8, 11)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            NovaTheme {
                val vpnViewModel: VpnViewModel = viewModel()
                val state by vpnViewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val onboarding = remember {
                    context.getSharedPreferences("anbalan_onboarding", MODE_PRIVATE)
                }
                var onboardingComplete by remember {
                    mutableStateOf(onboarding.getBoolean("tiktok_opened", false))
                }
                var tiktokOpened by remember { mutableStateOf(false) }
                var overlayEnabled by remember {
                    mutableStateOf(Settings.canDrawOverlays(context) && FloatingOverlayService.isRunning)
                }
                var overlayPermissionGranted by remember {
                    mutableStateOf(Settings.canDrawOverlays(context))
                }
                var vpnPermissionGranted by remember {
                    mutableStateOf(runCatching { VpnService.prepare(context) == null }.getOrDefault(false))
                }
                var connectAfterVpnPermission by remember { mutableStateOf(false) }
                var launchGameWhenConnected by remember { mutableStateOf(false) }

                val launchFreeFire: () -> Unit = {
                    val packages = listOf("com.dts.freefireth", "com.dts.freefiremax")
                    val launchIntent = packages.firstNotNullOfOrNull {
                        context.packageManager.getLaunchIntentForPackage(it)
                    }
                    if (launchIntent == null) {
                        Toast.makeText(context, "Không tìm thấy Free Fire hoặc Free Fire MAX.", Toast.LENGTH_LONG).show()
                    } else {
                        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }

                val overlayPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (Settings.canDrawOverlays(context)) {
                        overlayPermissionGranted = true
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, FloatingOverlayService::class.java),
                        )
                        overlayEnabled = true
                    }
                }

                val configPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) vpnViewModel.importConfig(uri)
                }

                val vpnPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        vpnPermissionGranted = true
                        if (connectAfterVpnPermission) vpnViewModel.connect()
                    } else {
                        vpnPermissionGranted = false
                        vpnViewModel.onPermissionDenied()
                    }
                    connectAfterVpnPermission = false
                }

                val openConfig: () -> Unit = {
                    configPicker.launch(arrayOf("*/*"))
                }

                val toggleVpn: () -> Unit = toggle@{
                    when (state.status) {
                        ConnectionStatus.CONNECTED -> vpnViewModel.disconnect()
                        ConnectionStatus.CONNECTING,
                        ConnectionStatus.DISCONNECTING -> return@toggle
                        ConnectionStatus.DISCONNECTED,
                        ConnectionStatus.ERROR -> {
                            if (!state.hasConfig) {
                                openConfig()
                                return@toggle
                            }
                            try {
                                val permissionIntent = VpnService.prepare(context)
                                if (permissionIntent == null) {
                                    vpnPermissionGranted = true
                                    vpnViewModel.connect()
                                } else {
                                    connectAfterVpnPermission = true
                                    vpnPermission.launch(permissionIntent)
                                }
                            } catch (_: SecurityException) {
                                vpnViewModel.onPermissionDenied()
                            }
                        }
                    }
                }

                val connectAndLaunchGame: () -> Unit = launch@{
                    if (!state.hasConfig) {
                        Toast.makeText(context, "Hãy nhập file .conf trước, rồi nhấn lại nút mở game.", Toast.LENGTH_LONG).show()
                        openConfig()
                        return@launch
                    }
                    if (state.status == ConnectionStatus.CONNECTED) {
                        launchFreeFire()
                        return@launch
                    }
                    if (state.isBusy) return@launch
                    launchGameWhenConnected = true
                    try {
                        val permissionIntent = VpnService.prepare(context)
                        if (permissionIntent == null) {
                            vpnPermissionGranted = true
                            vpnViewModel.connect()
                        } else {
                            connectAfterVpnPermission = true
                            vpnPermission.launch(permissionIntent)
                        }
                    } catch (_: SecurityException) {
                        launchGameWhenConnected = false
                        vpnViewModel.onPermissionDenied()
                    }
                }

                LaunchedEffect(state.status, launchGameWhenConnected) {
                    if (launchGameWhenConnected && state.status == ConnectionStatus.CONNECTED) {
                        delay(1_500L)
                        launchGameWhenConnected = false
                        launchFreeFire()
                    } else if (launchGameWhenConnected && state.status == ConnectionStatus.ERROR) {
                        launchGameWhenConnected = false
                    }
                }

                val toggleOverlay: () -> Unit = {
                    if (overlayEnabled) {
                        context.stopService(Intent(context, FloatingOverlayService::class.java))
                        overlayEnabled = false
                    } else if (Settings.canDrawOverlays(context)) {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, FloatingOverlayService::class.java),
                        )
                        overlayEnabled = true
                    } else {
                        overlayPermission.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }

                val requestOverlayPermission: () -> Unit = {
                    if (Settings.canDrawOverlays(context)) {
                        overlayPermissionGranted = true
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, FloatingOverlayService::class.java),
                        )
                        overlayEnabled = true
                    } else {
                        overlayPermission.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }

                val requestVpnPermission: () -> Unit = {
                    try {
                        val permissionIntent = VpnService.prepare(context)
                        if (permissionIntent == null) {
                            vpnPermissionGranted = true
                        } else {
                            connectAfterVpnPermission = false
                            vpnPermission.launch(permissionIntent)
                        }
                    } catch (_: SecurityException) {
                        vpnPermissionGranted = false
                        vpnViewModel.onPermissionDenied()
                    }
                }

                LaunchedEffect(onboardingComplete, overlayPermissionGranted, vpnPermissionGranted) {
                    if (
                        onboardingComplete && overlayPermissionGranted && vpnPermissionGranted &&
                        !FloatingOverlayService.isRunning
                    ) {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, FloatingOverlayService::class.java),
                        )
                        overlayEnabled = true
                    }
                }

                if (!onboardingComplete) {
                    TikTokGateScreen(
                        canContinue = tiktokOpened,
                        onOpenTikTok = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TIKTOK_URL))
                            runCatching { context.startActivity(intent) }
                            tiktokOpened = true
                        },
                        onContinue = {
                            onboarding.edit().putBoolean("tiktok_opened", true).apply()
                            onboardingComplete = true
                        },
                    )
                } else if (!overlayPermissionGranted || !vpnPermissionGranted) {
                    PermissionGateScreen(
                        overlayGranted = overlayPermissionGranted,
                        vpnGranted = vpnPermissionGranted,
                        onRequestOverlay = requestOverlayPermission,
                        onRequestVpn = requestVpnPermission,
                    )
                } else {
                    VpnScreen(
                        state = state,
                        onToggleVpn = toggleVpn,
                        onImportConfig = openConfig,
                        onRemoveConfig = vpnViewModel::removeConfig,
                        onDismissError = vpnViewModel::clearError,
                        overlayEnabled = overlayEnabled,
                        onToggleOverlay = toggleOverlay,
                        onConnectAndLaunchGame = connectAndLaunchGame,
                    )
                }
            }
        }
    }

    private companion object {
        const val TIKTOK_URL = "https://vt.tiktok.com/ZSXRafKgQ/"
    }
}
