package com.khanhan.novavpn

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

data class VpnUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val isImporting: Boolean = false,
    val tunnelName: String? = null,
    val endpoint: String? = null,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val receivedBytes: Long = 0L,
    val transmittedBytes: Long = 0L,
    val sessionSeconds: Long = 0L,
    val errorMessage: String? = null,
) {
    val hasConfig: Boolean get() = tunnelName != null
    val isBusy: Boolean
        get() = isImporting || status == ConnectionStatus.CONNECTING || status == ConnectionStatus.DISCONNECTING
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = GoBackend(application.applicationContext)
    private val configStore = SecureConfigStore(application.applicationContext)

    private val _uiState = MutableStateFlow(VpnUiState())
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    private var wireGuardConfig: Config? = null
    private var tunnel: NovaTunnel? = null

    init {
        activeInstance = WeakReference(this)
        restoreConfig()
    }

    override fun onCleared() {
        if (activeInstance?.get() === this) activeInstance = null
        super.onCleared()
    }

    fun importConfig(uri: Uri) {
        if (_uiState.value.status == ConnectionStatus.CONNECTED) {
            showError("Hãy ngắt kết nối trước khi thay file cấu hình.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                val imported = withContext(Dispatchers.IO) { readConfig(uri) }
                wireGuardConfig = imported.config
                tunnel = createTunnel(imported.name)
                withContext(Dispatchers.IO) {
                    configStore.save(imported.name, imported.config.toWgQuickString())
                }
                _uiState.update {
                    VpnUiState(
                        status = ConnectionStatus.DISCONNECTED,
                        tunnelName = imported.name,
                        endpoint = imported.endpoint,
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        status = ConnectionStatus.ERROR,
                        errorMessage = readableError(error, "Không thể nhập file .conf."),
                    )
                }
            }
        }
    }

    fun connect() {
        val activeConfig = wireGuardConfig
        val activeTunnel = tunnel
        if (activeConfig == null || activeTunnel == null) {
            showError("Hãy nhập file WireGuard .conf trước.")
            return
        }
        if (_uiState.value.isBusy || _uiState.value.status == ConnectionStatus.CONNECTED) return

        _uiState.update {
            it.copy(
                status = ConnectionStatus.CONNECTING,
                errorMessage = null,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newState = backend.setState(activeTunnel, Tunnel.State.UP, activeConfig)
                if (newState == Tunnel.State.UP) markConnected()
            } catch (error: Throwable) {
                markFailed(readableError(error, "Không thể kết nối tới máy chủ WireGuard."))
            }
        }
    }

    fun disconnect() {
        val activeTunnel = tunnel ?: return
        if (_uiState.value.isBusy || _uiState.value.status != ConnectionStatus.CONNECTED) return

        _uiState.update { it.copy(status = ConnectionStatus.DISCONNECTING, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backend.setState(activeTunnel, Tunnel.State.DOWN, null)
                markDisconnected()
            } catch (error: Throwable) {
                markFailed(readableError(error, "Không thể ngắt kết nối VPN."))
            }
        }
    }

    fun removeConfig() {
        if (_uiState.value.status == ConnectionStatus.CONNECTED || _uiState.value.isBusy) {
            showError("Hãy ngắt kết nối trước khi xóa cấu hình.")
            return
        }
        configStore.clear()
        wireGuardConfig = null
        tunnel = null
        _uiState.value = VpnUiState()
    }

    fun onPermissionDenied() {
        showError("Android chưa cấp quyền tạo kết nối VPN.")
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                status = if (it.status == ConnectionStatus.ERROR) ConnectionStatus.DISCONNECTED else it.status,
            )
        }
    }

    private fun restoreConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = configStore.load() ?: return@launch
            try {
                val parsed = Config.parse(ByteArrayInputStream(stored.configText.toByteArray(Charsets.UTF_8)))
                wireGuardConfig = parsed
                tunnel = createTunnel(stored.name)
                _uiState.update {
                    it.copy(
                        tunnelName = stored.name,
                        endpoint = extractEndpoint(stored.configText),
                    )
                }
            } catch (_: Exception) {
                configStore.clear()
            }
        }
    }

    private fun createTunnel(name: String): NovaTunnel = NovaTunnel(name) { newState ->
        when (newState) {
            Tunnel.State.UP -> markConnected()
            Tunnel.State.DOWN -> markDisconnected()
            Tunnel.State.TOGGLE -> Unit
        }
    }

    private fun markConnected() {
        if (_uiState.value.status == ConnectionStatus.CONNECTED) return
        _uiState.update {
            it.copy(
                status = ConnectionStatus.CONNECTED,
                errorMessage = null,
                sessionSeconds = 0,
            )
        }
    }

    private fun markDisconnected() {
        _uiState.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                receivedBytes = 0L,
                transmittedBytes = 0L,
                sessionSeconds = 0L,
            )
        }
    }

    private fun markFailed(message: String) {
        _uiState.update {
            it.copy(
                status = ConnectionStatus.ERROR,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                errorMessage = message,
            )
        }
    }

    private suspend fun readConfig(uri: Uri): ImportedConfig {
        val resolver = getApplication<Application>().contentResolver
        val displayName = queryDisplayName(uri) ?: "nova.conf"
        val rawBytes = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_CONFIG_BYTES) throw IllegalArgumentException("File cấu hình quá lớn.")
                output.write(buffer, 0, read)
            }
            buffer.fill(0)
            output.toByteArray()
        } ?: throw IllegalArgumentException("Không đọc được file đã chọn.")

        try {
            val configText = rawBytes.toString(Charsets.UTF_8)
            val parsed = Config.parse(ByteArrayInputStream(rawBytes))
            if (parsed.peers.isEmpty()) throw IllegalArgumentException("Cấu hình chưa có máy chủ [Peer].")
            return ImportedConfig(
                name = sanitizeTunnelName(displayName),
                endpoint = extractEndpoint(configText),
                config = parsed,
            )
        } finally {
            rawBytes.fill(0)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }

    private fun sanitizeTunnelName(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.').ifBlank { "nova" }
        val clean = withoutExtension
            .replace(Regex("[^a-zA-Z0-9_=+.-]"), "_")
            .take(15)
            .ifBlank { "nova" }
        return if (Tunnel.isNameInvalid(clean)) "nova" else clean
    }

    private fun extractEndpoint(configText: String): String? {
        return ENDPOINT_PATTERN.find(configText)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun readableError(error: Throwable, fallback: String): String {
        if (error is BadConfigException) return "File .conf không đúng định dạng WireGuard."
        val message = error.message
            ?.replace(Regex("(?i)privatekey\\s*=\\s*\\S+"), "PrivateKey=[ẩn]")
            ?.replace(Regex("[\\r\\n]+"), " ")
            ?.trim()
            ?.take(180)
        return if (message.isNullOrBlank()) fallback else "$fallback $message"
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private data class ImportedConfig(
        val name: String,
        val endpoint: String?,
        val config: Config,
    )

    companion object {
        private const val MAX_CONFIG_BYTES = 256 * 1024
        private val ENDPOINT_PATTERN = Regex("(?im)^\\s*Endpoint\\s*=\\s*([^#\\r\\n]+)")
        private var activeInstance: WeakReference<VpnViewModel>? = null

        fun overlayStatus(): ConnectionStatus =
            activeInstance?.get()?._uiState?.value?.status ?: ConnectionStatus.DISCONNECTED

        fun hasOverlayConfig(): Boolean = activeInstance?.get()?._uiState?.value?.hasConfig == true

        fun overlayStateFlow(): StateFlow<VpnUiState>? = activeInstance?.get()?.uiState

        fun toggleFromOverlay(): Boolean {
            val instance = activeInstance?.get() ?: return false
            return when (instance._uiState.value.status) {
                ConnectionStatus.CONNECTED -> {
                    instance.disconnect()
                    true
                }
                ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                    if (!instance._uiState.value.hasConfig) return false
                    instance.clearError()
                    instance.connect()
                    true
                }
                ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> true
            }
        }
    }
}
