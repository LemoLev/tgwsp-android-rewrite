package soft.shadlv.twp_rewritekts.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

interface ExternalNavigator {
    fun openTelegramFromProxy(host: String, port: Int, secret: String)
}

class AndroidExternalNavigator(private val context: Context) : ExternalNavigator {
    override fun openTelegramFromProxy(host: String, port: Int, secret: String) {
        val uri = Uri.Builder()
            .scheme("tg")
            .authority("proxy")
            .appendQueryParameter("server", host)
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("secret", secret)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
//            setPackage("org.telegram.messenger")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Ошибка открытия прокси для Телеграм", Toast.LENGTH_SHORT).show()
        }
    }
}