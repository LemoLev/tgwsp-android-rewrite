package soft.shadlv.twp_rewritekts.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import soft.shadlv.twp_rewritekts.store.DataStoreSecurity
import soft.shadlv.twp_rewritekts.store.ProxyConfig

class ProxyConfigRepository(context: Context) {
    private val dataStoreSecurity = DataStoreSecurity(context)

    suspend fun getConfig(): ProxyConfig? = withContext(Dispatchers.IO) {
        dataStoreSecurity.getObject<ProxyConfig>()
    }

    suspend fun saveConfig(config: ProxyConfig) = withContext(Dispatchers.IO) {
        dataStoreSecurity.saveObject(config)
    }
}