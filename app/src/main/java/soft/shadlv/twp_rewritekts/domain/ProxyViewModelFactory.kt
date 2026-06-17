package soft.shadlv.twp_rewritekts.domain

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ProxyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(ProxyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProxyViewModel(application) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ProxyControlViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(ProxyControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProxyControlViewModel(application) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}