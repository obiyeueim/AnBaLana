# WireGuard JNI entry points and VPN service are referenced from native code/manifest.
-keep class com.wireguard.android.backend.GoBackend { *; }
-keep class com.wireguard.android.backend.GoBackend$VpnService { *; }
-keep class com.wireguard.config.** { *; }

# Keep the app tunnel callback contract.
-keep class com.khanhan.novavpn.NovaTunnel { *; }
