package soft.shadlv.twp_rewritekts

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState = _uiState.asStateFlow()

    private val context = getApplication<Application>()

    fun onIntent(intent: ProxyIntent) {
        when (intent) {
            is ProxyIntent.UpdateHost -> _uiState.update { it.copy(host = intent.host) }
            is ProxyIntent.ToggleProxy -> handleToggle()
            is ProxyIntent.UpdatePort -> _uiState.update { it.copy(port = intent.port.toInt()) }
            is ProxyIntent.UpdateDcip -> _uiState.update { it.copy(dcip = intent.dcip) }
//            is ProxyIntent.SaveConfig -> saveToDisk()
            // и так далее
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
            ContextCompat.startForegroundService(context, intent)
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
        object ToggleProxy : ProxyIntent()
//        object SaveConfig : ProxyIntent()
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
