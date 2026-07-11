package com.khanhan.novavpn; // ВАЖНО: Замени на свое имя пакета!

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MyVpnService extends VpnService {

    private static final String TAG = "AnBaLana_VpnService";
    private static final String CHANNEL_ID = "VpnLagChannel";
    private static final int NOTIFICATION_ID = 1;

    private ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread = null;
    private volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VpnService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting VpnService...");

        // 1. КРИТИЧЕСКИ ВАЖНО: Запуск уведомления в шторке для защиты от вылета (Android 8+)
        startForegroundNotification();

        // 2. Если туннель уже работает, не запускаем второй поток
        if (vpnThread != null && vpnThread.isAlive()) {
            Log.w(TAG, "VPN Thread already running");
            return START_STICKY;
        }

        // 3. Запуск фонового потока для обработки сетевого трафика
        isRunning = true;
        vpnThread = new Thread(this::runVpnLoop, "VpnNetworkThread");
        vpnThread.start();

        return START_STICKY;
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AnBaLana Fake Lag Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background packet interception for Fake Lag");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake Lag Active")
            .setContentText("Intercepting and buffering network packets...")
            .setSmallIcon(R.mipmap.ic_launcher) // Иконка твоего приложения
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();

        // Обязательный вызов для защиты от ошибки RemoteServiceException
        startForeground(NOTIFICATION_ID, notification);
    }

    private void runVpnLoop() {
        try {
            // 1. Настройка и конфигурация виртуального сетевого адаптера (TUN)
            Builder builder = new Builder()
                .setSession("AnBaLana FakeLag")
                .addAddress("10.0.0.2", 32) // Локальный IP виртуального адаптера
                .addRoute("0.0.0.0", 0)     // Перехватывать весь трафик IPv4
                .setMtu(1500);              // Стандартный размер MTU

            // Создаем виртуальный интерфейс
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface! Permission denied or OS error.");
                stopSelf();
                return;
            }

            Log.i(TAG, "VPN Interface established successfully. Starting packet loop...");

            // 2. Чтение и запись трафика через файловые дескрипторы сокета
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

                byte[] buffer = new byte[32767]; // Буфер для чтения сырых сетевых пакетов

                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    // Читаем исходящий пакет из ОС
                    int length = in.read(buffer);
                    if (length > 0) {
                        byte[] packet = new byte[length];
                        System.arraycopy(buffer, 0, packet, 0, length);

                        // 3. ПЕРЕДАЕМ ПАКЕТ В ПЕРЕХВАТЧИК ЛАГА
                        // Если режим лага включен — пакет удерживается в очереди в PacketInterceptor.
                        // Если выключен — отправляется напрямую в сокет через out.write().
                        PacketInterceptor.intercept(packet, out);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Network loop terminated with exception", e);
        } finally {
            cleanupVpn();
        }
    }

    private void cleanupVpn() {
        Log.i(TAG, "Cleaning up VPN interface and buffers...");
        isRunning = false;
        try {
            // Сбрасываем любые зависшие в буфере пакеты при остановке туннеля
            if (vpnInterface != null) {
                try (FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {
                    PacketInterceptor.flush(out);
                }
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Stopping VpnService...");
        isRunning = false;
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }
        cleanupVpn();
    }
}