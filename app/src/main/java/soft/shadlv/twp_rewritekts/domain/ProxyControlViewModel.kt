package soft.shadlv.twp_rewritekts.domain

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import soft.shadlv.twp_rewritekts.ServiceDataStoreProvider
import soft.shadlv.twp_rewritekts.TGProxyService
import soft.shadlv.twp_rewritekts.repository.ProxyConfigRepository

class ProxyControlViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val repository = ProxyConfigRepository(application)
    private val navigator: ExternalNavigator = AndroidExternalNavigator(context)

    val isRunning = ServiceDataStoreProvider.getInstance(context)
        .data
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(1000),
            initialValue = false
        )

    fun onIntent(intent: ProxyControlIntent) {
        when (intent) {
            is ProxyControlIntent.OpenTelegram -> openTelegram()
            is ProxyControlIntent.ToggleProxy -> toggleProxy()
        }
    }

    fun openTelegram() =
        viewModelScope.launch {
            val config = repository.getConfig()
            if (config != null) {
                navigator.openTelegramFromProxy(config.host, config.port, config.secret)
            } else {
                Toast.makeText(context, "Нет необходимой конфигурации", Toast.LENGTH_SHORT).show()
            }
        }

    fun toggleProxy() {
        val intent = Intent(context, TGProxyService::class.java)

        if (isRunning.value) {
            context.stopService(intent)
        } else {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Ошибка запуска TG Proxy", Toast.LENGTH_SHORT).show()
                Log.e("ProxyControlVM", "Error starting ForegroundService", ex)
            }
        }
    }

    sealed class ProxyControlIntent {
        data object ToggleProxy : ProxyControlIntent()
        data object OpenTelegram : ProxyControlIntent()
    }
}