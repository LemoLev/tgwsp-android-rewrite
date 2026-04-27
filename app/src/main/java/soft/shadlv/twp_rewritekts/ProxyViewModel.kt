package soft.shadlv.twp_rewritekts

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import soft.shadlv.twp_rewritekts.store.DataStore
import soft.shadlv.twp_rewritekts.store.ProxyConfig
import java.security.SecureRandom

@RequiresApi(Build.VERSION_CODES.S)
class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState = _uiState.asStateFlow()

    private val context = getApplication<Application>()

    init {
        loadConfig()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = DataStore(context).getObject<ProxyConfig>()

            if (config != null) {
                _uiState.update {
                    it.copy(
                        host = config.host,
                        port = config.port,
                        dcip = config.dcip,
                        secret = config.secret
                    )
                }
            }
        }
    }

    fun onIntent(intent: ProxyIntent) {
        when (intent) {
            is ProxyIntent.UpdateHost -> _uiState.update { it.copy(host = intent.host) }
            is ProxyIntent.UpdatePort -> _uiState.update { it.copy(port = intent.port.toInt()) }
            is ProxyIntent.UpdateDcip -> _uiState.update { it.copy(dcip = intent.dcip) }
            is ProxyIntent.RegenerateSecret -> _uiState.update { it.copy(secret = generateHexToken()) }
            is ProxyIntent.ToggleProxy -> handleToggle()
            is ProxyIntent.SaveConfig -> saveToDisk()
        }
    }

    private fun handleToggle() {
        val state = _uiState.value
        val intent = Intent(context, TGProxyService::class.java).apply {
            putExtra("host", state.host)
            putExtra("port", state.port)
            putExtra("dcip", state.dcip)
            putExtra("secret", state.secret)
        }

        if (ProxyManager.isRunning.value) {
            context.stopService(intent)
        } else {
            saveToDisk()
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun saveToDisk() {
        val state = _uiState.value
        val proxyConfig = ProxyConfig(
            host = state.host,
            port = state.port,
            dcip = state.dcip,
            secret = state.secret
        )
        val dataStore = DataStore(context)
        dataStore.saveObject(proxyConfig)
    }

    data class ProxyUiState(
        val host: String = "127.0.0.1",
        val port: Int = 1443,
        val dcip: String = "2:149.154.167.220; 4:149.154.167.220; 203:149.154.167.220",
        val secret: String = generateHexToken()
    )

    sealed class ProxyIntent {
        data class UpdateHost(val host: String) : ProxyIntent()
        data class UpdatePort(val port: String) : ProxyIntent()
        data class UpdateDcip(val dcip: String) : ProxyIntent()
        object RegenerateSecret : ProxyIntent()
        object ToggleProxy : ProxyIntent()
        object SaveConfig : ProxyIntent()
    }
}

internal fun generateHexToken(): String {
    val random = SecureRandom()
    val bytes = ByteArray(16)
    random.nextBytes(bytes)

    val sb = StringBuilder()
    for (b in bytes) {
        // Форматируем каждый байт в 2 символа hex
        sb.append(String.format("%02x", b))
    }
    return sb.toString()
}
