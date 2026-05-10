package soft.shadlv.twp_rewritekts.domain

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import soft.shadlv.twp_rewritekts.repository.ProxyConfigRepository
import soft.shadlv.twp_rewritekts.store.ProxyConfig
import java.security.SecureRandom

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val repository = ProxyConfigRepository(application)
    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val config = repository.getConfig()

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
            is ProxyIntent.UpdatePort -> _uiState.update {
                it.copy(
                    port = intent.port.toIntOrNull() ?: it.port
                )
            }

            is ProxyIntent.UpdateDcip -> _uiState.update { it.copy(dcip = intent.dcip) }
            is ProxyIntent.RegenerateSecret -> _uiState.update { it.copy(secret = generateHexToken()) }
            is ProxyIntent.SaveConfig -> viewModelScope.launch { saveToDisk() }
        }
    }

    private suspend fun saveToDisk() {
        try {
            val state = _uiState.value
            val proxyConfig = ProxyConfig(
                host = state.host,
                port = state.port,
                dcip = state.dcip,
                secret = state.secret
            )
            repository.saveConfig(proxyConfig)
            Toast.makeText(context, "Успешно сохранено", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
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
        data object SaveConfig : ProxyIntent()
    }
}

internal fun generateHexToken(): String {
    val random = SecureRandom()
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
