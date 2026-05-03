package soft.shadlv.twp_rewritekts

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import soft.shadlv.twp_rewritekts.store.DataStoreSecurity
import soft.shadlv.twp_rewritekts.store.ProxyConfig
import java.security.SecureRandom

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val dataStoreSecurity = DataStoreSecurity(context)
    val isRunning = ServiceDataStoreProvider.getInstance(context)
        .data
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(1000),
            initialValue = false
        )

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = DataStoreSecurity(context).getObject<ProxyConfig>()

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
            is ProxyIntent.SaveConfig -> viewModelScope.launch { saveToDisk() }
        }
    }

    private fun handleToggle() {
        val intent = Intent(context, TGProxyService::class.java)
        if (isRunning.value) {
            context.stopService(intent)
        } else {
            viewModelScope.launch {
                saveToDisk()
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (ex: Exception) {
                    Log.e("Error start ForegroundService", "Error starting", ex)
                }
            }
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        val state = _uiState.value
        val proxyConfig = ProxyConfig(
            host = state.host,
            port = state.port,
            dcip = state.dcip,
            secret = state.secret
        )
        dataStoreSecurity.saveObject(proxyConfig)
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
        data object RegenerateSecret : ProxyIntent()
        data object ToggleProxy : ProxyIntent()
        data object SaveConfig : ProxyIntent()
    }
}

internal fun generateHexToken(): String {
    val random = SecureRandom()
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
