package com.khanhan.novavpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khanhan.novavpn.ui.NovaTheme
import com.khanhan.novavpn.ui.TikTokGateScreen
import com.khanhan.novavpn.ui.VpnScreen

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

                val overlayPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (Settings.canDrawOverlays(context)) {
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
                        vpnViewModel.connect()
                    } else {
                        vpnViewModel.onPermissionDenied()
                    }
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
                                    vpnViewModel.connect()
                                } else {
                                    vpnPermission.launch(permissionIntent)
                                }
                            } catch (_: SecurityException) {
                                vpnViewModel.onPermissionDenied()
                            }
                        }
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
                } else {
                    VpnScreen(
                        state = state,
                        onToggleVpn = toggleVpn,
                        onImportConfig = openConfig,
                        onRemoveConfig = vpnViewModel::removeConfig,
                        onDismissError = vpnViewModel::clearError,
                        overlayEnabled = overlayEnabled,
                        onToggleOverlay = toggleOverlay,
                    )
                }
            }
        }
    }

    private companion object {
        const val TIKTOK_URL = "https://vt.tiktok.com/ZSXRafKgQ/"
    }
}
