package soft.shadlv.twp_rewritekts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ProxyManager {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun setRunning(value: Boolean) {
        _isRunning.update { value }
    }
}