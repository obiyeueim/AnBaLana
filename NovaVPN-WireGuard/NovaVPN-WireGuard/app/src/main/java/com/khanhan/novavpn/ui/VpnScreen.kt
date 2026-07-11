package com.khanhan.novavpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanhan.novavpn.ConnectionStatus
import com.khanhan.novavpn.VpnUiState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VpnScreen(
    state: VpnUiState,
    onToggleVpn: () -> Unit,
    onImportConfig: () -> Unit,
    onRemoveConfig: () -> Unit,
    onDismissError: () -> Unit,
    overlayEnabled: Boolean,
    onToggleOverlay: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF351018), NovaBackground, Color(0xFF050507)),
                    center = Offset(540f, -80f),
                    radius = 1_150f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar()
            Spacer(Modifier.height(28.dp))
            StatusHeader(state)
            Spacer(Modifier.height(6.dp))
            PowerOrb(
                status = state.status,
                hasConfig = state.hasConfig,
                enabled = !state.isBusy,
                onClick = onToggleVpn,
            )
            SessionTimer(state.sessionSeconds, state.status == ConnectionStatus.CONNECTED)
            Spacer(Modifier.height(22.dp))

            AnimatedVisibility(visible = state.errorMessage != null) {
                ErrorCard(
                    message = state.errorMessage.orEmpty(),
                    onDismiss = onDismissError,
                )
            }

            ConfigCard(
                state = state,
                onImport = onImportConfig,
                onRemove = { showDeleteDialog = true },
            )
            Spacer(Modifier.height(12.dp))
            OverlayControlCard(
                enabled = overlayEnabled,
                onToggle = onToggleOverlay,
            )
            Spacer(Modifier.height(12.dp))
            StatsRow(state)
            Spacer(Modifier.height(18.dp))
            SecurityNote()
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xóa cấu hình?") },
            text = { Text("File WireGuard đã lưu trong app sẽ bị xóa. Hãy ngắt VPN trước khi thực hiện.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onRemoveConfig()
                    },
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") }
            },
            containerColor = NovaSurfaceHigh,
        )
    }
}

@Composable
private fun OverlayControlCard(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NovaSurface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, if (enabled) NovaAccent.copy(alpha = 0.55f) else Color(0xFF34343E)),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (enabled) NovaAccent.copy(alpha = 0.14f) else NovaSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text("▦", color = if (enabled) NovaAccent else NovaMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("TAB NỔI VPN", color = NovaMuted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text(if (enabled) "Đang hiển thị trên màn hình" else "Hiện nút điều khiển trên ứng dụng khác", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Chạm ON/OFF để bật tắt • kéo để đổi vị trí", color = NovaMuted, fontSize = 9.sp)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (enabled) NovaAccent.copy(alpha = 0.16f) else NovaSurfaceHigh,
            ) {
                Text(
                    if (enabled) "BẬT" else "TẮT",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (enabled) NovaAccent else NovaMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NovaAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                ShieldGlyph(modifier = Modifier.size(25.dp), color = NovaAccent)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("AnBa", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 0.2.sp)
                Text(
                    "Lan",
                    color = NovaAccent,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 0.2.sp,
                )
            }
        }
        Surface(
            color = NovaAccent.copy(alpha = 0.08f),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                "BY AN",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                color = NovaAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
private fun StatusHeader(state: VpnUiState) {
    val accent by animateColorAsState(
        targetValue = when (state.status) {
            ConnectionStatus.CONNECTED -> NovaAccent
            ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> NovaWarning
            ConnectionStatus.ERROR -> NovaError
            ConnectionStatus.DISCONNECTED -> NovaMuted
        },
        label = "statusColor",
    )
    val title = when (state.status) {
        ConnectionStatus.CONNECTED -> "Đã kết nối an toàn"
        ConnectionStatus.CONNECTING -> "Đang tạo đường hầm"
        ConnectionStatus.DISCONNECTING -> "Đang ngắt kết nối"
        ConnectionStatus.ERROR -> "Cần kiểm tra lại"
        ConnectionStatus.DISCONNECTED -> if (state.hasConfig) "Sẵn sàng kết nối" else "Thêm cấu hình VPN"
    }
    val subtitle = when {
        state.status == ConnectionStatus.CONNECTED -> state.endpoint ?: "WireGuard tunnel đang hoạt động"
        state.status == ConnectionStatus.CONNECTING -> "Đang xác thực với máy chủ WireGuard"
        state.status == ConnectionStatus.DISCONNECTING -> "Đang đóng kết nối an toàn"
        state.hasConfig -> state.tunnelName ?: "WireGuard"
        else -> "Nhập file .conf để bắt đầu"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Text(
                text = statusLabel(state.status),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = title, label = "statusTitle") { currentTitle ->
            Text(currentTitle, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        }
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 4.dp),
            color = NovaMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PowerOrb(
    status: ConnectionStatus,
    hasConfig: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = status == ConnectionStatus.CONNECTED
    val busy = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.DISCONNECTING
    val accent = when {
        active -> NovaAccent
        busy -> NovaWarning
        status == ConnectionStatus.ERROR -> NovaError
        else -> Color(0xFF8BA098)
    }
    val transition = rememberInfiniteTransition(label = "powerAnimation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (busy) 1_500 else 10_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringRotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "powerPulse",
    )

    Box(
        modifier = Modifier
            .size(264.dp)
            .semantics { contentDescription = if (active) "Ngắt kết nối VPN" else "Kết nối VPN" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            val center = this.center
            val radii = listOf(size.minDimension * 0.47f, size.minDimension * 0.39f, size.minDimension * 0.31f)
            radii.forEachIndexed { index, radius ->
                drawCircle(
                    color = accent.copy(alpha = if (active || busy) 0.18f - index * 0.035f else 0.07f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = if (index == 1) 1.4.dp.toPx() else 1.dp.toPx()),
                )
                val angle = Math.toRadians((index * 123 + 32).toDouble())
                drawCircle(
                    color = accent.copy(alpha = if (active || busy) 0.9f else 0.3f),
                    radius = (2.7f - index * 0.45f).dp.toPx(),
                    center = Offset(
                        center.x + cos(angle).toFloat() * radius,
                        center.y + sin(angle).toFloat() * radius,
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(144.dp)
                .graphicsLayer {
                    scaleX = if (active) pulse else 1f
                    scaleY = if (active) pulse else 1f
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (active) {
                            listOf(NovaAccent.copy(alpha = 0.28f), NovaAccentDark, Color(0xFF0A1C17))
                        } else {
                            listOf(accent.copy(alpha = 0.12f), Color(0xFF12231E), Color(0xFF0A1713))
                        },
                    ),
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.76f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = accent.copy(alpha = if (active) 0.52f else 0.22f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerGlyph(modifier = Modifier.size(42.dp), color = accent)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = when {
                            active -> "NGẮT KẾT NỐI"
                            hasConfig -> "KẾT NỐI"
                            else -> "NHẬP .CONF"
                        },
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 1.1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTimer(seconds: Long, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "THỜI GIAN PHIÊN",
            color = NovaMuted.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 1.4.sp,
        )
        Text(
            text = formatDuration(seconds),
            color = if (active) NovaAccent else NovaMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun ConfigCard(
    state: VpnUiState,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NovaSurface.copy(alpha = 0.88f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(Color(0xFF28483D), Color(0xFF142B24))),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NovaAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (state.hasConfig) "WG" else "+", color = NovaAccent, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.hasConfig) "CẤU HÌNH ĐANG DÙNG" else "CHƯA CÓ CẤU HÌNH",
                        color = NovaMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        state.tunnelName ?: "Nhập WireGuard .conf",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.endpoint ?: "Chọn file được cấp bởi máy chủ VPN",
                        color = NovaMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBusy && state.status != ConnectionStatus.CONNECTED,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NovaAccent, contentColor = Color(0xFF03241A)),
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFF03241A), strokeWidth = 2.dp)
                    } else {
                        Text(if (state.hasConfig) "Đổi file" else "Nhập .conf")
                    }
                }
                if (state.hasConfig) {
                    OutlinedButton(
                        onClick = onRemove,
                        enabled = !state.isBusy && state.status != ConnectionStatus.CONNECTED,
                        shape = RoundedCornerShape(15.dp),
                    ) { Text("Xóa") }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(state: VpnUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "TẢI XUỐNG",
            value = String.format(Locale.US, "%.1f", state.downloadMbps),
            unit = "Mbps",
            total = formatBytes(state.receivedBytes),
            accent = NovaAccent,
            arrow = "↓",
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "TẢI LÊN",
            value = String.format(Locale.US, "%.1f", state.uploadMbps),
            unit = "Mbps",
            total = formatBytes(state.transmittedBytes),
            accent = Color(0xFF79AAFF),
            arrow = "↑",
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    total: String,
    accent: Color,
    arrow: String,
) {
    Card(
        modifier = modifier.aspectRatio(1.36f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NovaSurface.copy(alpha = 0.82f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    modifier = Modifier.size(29.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Text(arrow, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Text(label, color = NovaMuted, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, letterSpacing = 0.8.sp)
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(" $unit", modifier = Modifier.padding(bottom = 3.dp), color = NovaMuted, fontSize = 8.sp)
                }
                Text("Tổng $total", color = NovaMuted.copy(alpha = 0.76f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = NovaError.copy(alpha = 0.12f)),
        onClick = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("!", color = NovaError, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                text = message,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                color = Color(0xFFFFC8CD),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Text("×", color = NovaMuted, fontSize = 19.sp)
        }
    }
}

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NovaAccent.copy(alpha = 0.055f))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldGlyph(Modifier.size(24.dp), NovaAccent)
        Spacer(Modifier.size(10.dp))
        Column {
            Text("AnBaLan bảo vệ cấu hình trên thiết bị", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text("Khóa riêng được mã hóa bằng Android Keystore và không sao lưu cloud.", color = NovaMuted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
fun TikTokGateScreen(
    canContinue: Boolean,
    onOpenTikTok: () -> Unit,
    onContinue: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF211016), NovaBackground, Color(0xFF050507)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("AnBa", fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text("Lan", color = NovaAccent, fontWeight = FontWeight.Black, fontSize = 30.sp)
            }
            Text(
                "VPN CLIENT • BY AN",
                color = NovaWarning,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 2.3.sp,
            )
            Spacer(Modifier.height(52.dp))

            Surface(
                modifier = Modifier.size(190.dp),
                shape = CircleShape,
                color = if (canContinue) NovaAccent.copy(alpha = 0.12f) else NovaSurfaceHigh,
                border = BorderStroke(2.dp, if (canContinue) NovaAccent.copy(alpha = 0.55f) else Color(0xFF3C3C46)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShieldGlyph(
                        modifier = Modifier.size(72.dp),
                        color = if (canContinue) NovaAccent else NovaMuted,
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (canContinue) NovaAccent.copy(alpha = 0.10f) else NovaAccentDark.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, NovaAccent.copy(alpha = 0.45f)),
            ) {
                Text(
                    if (canContinue) "ĐÃ MỞ TIKTOK" else "BƯỚC KHỞI ĐỘNG",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = if (canContinue) NovaAccent else Color(0xFFFF8794),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.8.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (canContinue) "Sẵn sàng sử dụng AnBaLan" else "Mở kênh TikTok trước khi bắt đầu",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                if (canContinue) "Nhấn tiếp tục để vào màn hình VPN." else "Ứng dụng chỉ ghi nhận TikTok đã được mở và không xác minh lượt follow.",
                modifier = Modifier.padding(top = 8.dp),
                color = NovaMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NovaSurface),
                border = BorderStroke(1.dp, Color(0xFF34343E)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(NovaWarning.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♪", color = NovaWarning, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TikTok của An", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("vt.tiktok.com/ZSXRafKgQ", color = NovaMuted, fontSize = 10.sp)
                    }
                    if (canContinue) Text("✓", color = NovaAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenTikTok,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
            ) {
                Text(if (canContinue) "MỞ LẠI TIKTOK" else "MỞ TIKTOK", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaAccent,
                    disabledContainerColor = NovaSurfaceHigh,
                    disabledContentColor = NovaMuted,
                ),
            ) {
                Text("TIẾP TỤC SỬ DỤNG", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
private fun ShieldGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.08f)
            lineTo(size.width * 0.86f, size.height * 0.23f)
            lineTo(size.width * 0.86f, size.height * 0.51f)
            cubicTo(size.width * 0.86f, size.height * 0.76f, size.width * 0.7f, size.height * 0.9f, size.width * 0.5f, size.height * 0.98f)
            cubicTo(size.width * 0.3f, size.height * 0.9f, size.width * 0.14f, size.height * 0.76f, size.width * 0.14f, size.height * 0.51f)
            lineTo(size.width * 0.14f, size.height * 0.23f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.31f, size.height * 0.52f), Offset(size.width * 0.45f, size.height * 0.67f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.45f, size.height * 0.67f), Offset(size.width * 0.72f, size.height * 0.37f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun PowerGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        drawLine(
            color = color,
            start = Offset(size.width / 2f, size.height * 0.08f),
            end = Offset(size.width / 2f, size.height * 0.53f),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = -48f,
            sweepAngle = 276f,
            useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
            size = Size(size.width * 0.76f, size.height * 0.76f),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTED -> "ĐÃ KẾT NỐI"
    ConnectionStatus.CONNECTING -> "ĐANG KẾT NỐI"
    ConnectionStatus.DISCONNECTING -> "ĐANG NGẮT"
    ConnectionStatus.ERROR -> "LỖI KẾT NỐI"
    ConnectionStatus.DISCONNECTED -> "CHƯA KẾT NỐI"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remaining)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1_024 && unit < units.lastIndex) {
        value /= 1_024
        unit++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}
