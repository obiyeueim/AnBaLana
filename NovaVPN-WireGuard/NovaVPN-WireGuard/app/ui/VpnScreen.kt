package com.anbalan.app.ui // VUI LÒNG KIỂM TRA: Thay đổi tên package cho khớp nếu dự án của bạn khác

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VpnScreen(
    uiState: AppUiState,
    onRequestOverlayPermission: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onToggleVpn: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tiêu đề App & Phiên bản
            Text(
                text = "AnBaLan Engine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Version 2.6.0 (Build 8)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Kiểm tra điều kiện 2 quyền bắt buộc
            val allPermissionsGranted = uiState.hasOverlayPermission && uiState.hasVpnPermission

            if (!allPermissionsGranted) {
                // =========================================================================
                // MÀN HÌNH 1: CỔNG KIỂM TRA QUYỀN (PERMISSION GATE)
                // =========================================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "YÊU CẦU CẤP QUYỀN HỆ THỐNG",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Để kích hoạt Tab nổi và tối ưu hóa mạng cho Free Fire, bạn bắt buộc phải cấp đủ 2 quyền dưới đây:",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Nút 1: Quyền Overlay (Tab nổi)
                Button(
                    onClick = onRequestOverlayPermission,
                    enabled = !uiState.hasOverlayPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.hasOverlayPermission) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (uiState.hasOverlayPermission) "1. Quyền Tab Nổi (Overlay): ĐÃ CẤP" else "1. Cấp Quyền Tab Nổi (Overlay)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nút 2: Quyền VpnService (Android)
                Button(
                    onClick = onRequestVpnPermission,
                    enabled = !uiState.hasVpnPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.hasVpnPermission) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (uiState.hasVpnPermission) "2. Quyền VPN Hệ Thống: ĐÃ CẤP" else "2. Cấp Quyền VPN Hệ Thống",
                        fontWeight = FontWeight.Bold
                    )
                }

            } else {
                // =========================================================================
                // MÀN HÌNH 2: BẢNG ĐIỀU KHIỂN CHÍNH (MAIN CONTROL PANEL)
                // =========================================================================
                
                // Hiển thị trạng thái kết nối
                val statusColor = when (uiState.engineState) {
                    EngineState.CONNECTED -> Color(0xFFD32F2F) // Đỏ
                    EngineState.CONNECTING, EngineState.DISCONNECTING -> Color(0xFFFBC02D) // Vàng
                    EngineState.DISCONNECTED -> Color(0xFF388E3C) // Xanh lá
                }

                val statusText = when (uiState.engineState) {
                    EngineState.CONNECTED -> "ĐANG HOẠT ĐỘNG (ACTIVE)"
                    EngineState.CONNECTING -> "ĐANG KẾT NỐI TUNNEL..."
                    EngineState.DISCONNECTING -> "ĐANG NGẮT KẾT NỐI..."
                    EngineState.DISCONNECTED -> "ĐÃ TẮT (READY)"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Trạng thái: $statusText",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Xác định trạng thái khóa thao tác
                val isProcessing = uiState.engineState == EngineState.CONNECTING || uiState.engineState == EngineState.DISCONNECTING

                val buttonLabel = when (uiState.engineState) {
                    EngineState.DISCONNECTED -> "BẬT ENGINE"
                    EngineState.CONNECTING -> "ĐANG XỬ LÝ..."
                    EngineState.CONNECTED -> "TẮT ENGINE"
                    EngineState.DISCONNECTING -> "ĐANG XỬ LÝ..."
                }

                val buttonColor = when (uiState.engineState) {
                    EngineState.CONNECTED -> Color(0xFFD32F2F) // Đỏ khi đang chạy để bấm tắt
                    else -> Color(0xFF388E3C) // Xanh lá khi đã tắt để bấm bật
                }

                // Nút kích hoạt chính
                Button(
                    onClick = onToggleVpn,
                    enabled = !isProcessing, // Khóa nút chống spam nhấp nháy
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        disabledContainerColor = Color.Gray
                    ),
                    modifier = Modifier
                        .size(220.dp, 64.dp),
                    shape = RoundedCornerShape(32.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = buttonLabel,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Tab nổi sẽ tự động duy trì trên màn hình game.\nBạn có thể bấm ON/OFF trực tiếp từ Tab nổi.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }

            // Hiển thị thông báo lỗi nếu có
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Lỗi: ${uiState.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}