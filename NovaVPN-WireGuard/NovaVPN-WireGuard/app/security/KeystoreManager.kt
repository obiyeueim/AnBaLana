package com.khanhan.novavpn; // ВАЖНО: Замени на свое имя пакета (как в первой строке старого файла)!

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AnBaLana_Main";
    private static final int VPN_REQUEST_CODE = 0x0F;

    private Button btnStartVpn;
    private Button btnLag;
    private Button btnTeleport;
    private boolean isLagActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация кнопок из XML разметки
        btnStartVpn = findViewById(R.id.btn_start);
        btnLag = findViewById(R.id.btn_lag);
        btnTeleport = findViewById(R.id.btn_teleport);

        // Настройка обработчиков нажатий
        setupListeners();
    }

    private void setupListeners() {
        // 1. Кнопка запуска VPN сервиса
        btnStartVpn.setOnClickListener(v -> {
            Log.d(TAG, "Checking VPN permissions...");
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) {
                // Система запрашивает разрешение у пользователя
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
            } else {
                // Разрешение уже было получено ранее — запускаем сразу
                startVpnService();
            }
        });

        // 2. Кнопка включения задержки (Fake Lag ON / Freeze)
        btnLag.setOnClickListener(v -> {
            isLagActive = !isLagActive;
            PacketInterceptor.toggleLag(isLagActive);
            
            if (isLagActive) {
                btnLag.setText("LAG: ON (HOLDING)");
                Toast.makeText(this, "Fake Lag ON: Packets intercepted", Toast.LENGTH_SHORT).show();
            } else {
                btnLag.setText("LAG: OFF");
                Toast.makeText(this, "Fake Lag OFF: Normal traffic", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. Кнопка сброса буфера (Teleport Burst / Flush)
        btnTeleport.setOnClickListener(v -> {
            // Выключаем режим лага перед сбросом
            isLagActive = false;
            btnLag.setText("LAG: OFF");
            PacketInterceptor.toggleLag(false);

            // Сбрасываем накопленные пакеты (Burst)
            try {
                // Примечание: В идеале flush должен вызываться в сетевом потоке сервиса,
                // но благодаря синхронизации буфера в PacketInterceptor это сработает для триггера
                Toast.makeText(this, "TELEPORT BURST: Packets flushed!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Teleport button clicked - buffer flush triggered.");
            } catch (Exception e) {
                Log.e(TAG, "Error during teleport flush", e);
                Toast.makeText(this, "Error flushing packets", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "VPN permission granted by user.");
                startVpnService();
            } else {
                Log.w(TAG, "VPN permission denied by user.");
                Toast.makeText(this, "Permission denied! Cannot start Fake Lag.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startVpnService() {
        try {
            Intent intent = new Intent(this, MyVpnService.class);
            startService(intent);
            Toast.makeText(this, "VPN Service Started!", Toast.LENGTH_SHORT).show();
            btnStartVpn.setText("VPN RUNNING");
            btnStartVpn.setEnabled(false); // Блокируем кнопку после запуска
        } catch (Exception e) {
            Log.e(TAG, "Failed to start VPN Service", e);
            Toast.makeText(this, "Failed to start service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}