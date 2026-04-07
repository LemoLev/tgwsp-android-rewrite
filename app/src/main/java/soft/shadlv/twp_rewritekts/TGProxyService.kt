package soft.shadlv.twp_rewritekts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import main.ProxyControl

class TGProxyService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID: String = "ProxyChannel"
    }

    lateinit var proxyControl: ProxyControl

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val pythonDispatcher = newSingleThreadContext("PythonThread")

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TG Proxy")
            .setContentText("Запущен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }

        startProxyEngine(intent)

        return START_REDELIVER_INTENT
    }

    private fun startProxyEngine(intent: Intent?) = lifecycleScope.launch(pythonDispatcher) {
        try {
            Log.d("TGProxyService", "proxy starting")

            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this@TGProxyService))
            }

            proxyControl = ProxyControl()
            if (!ProxyManager.isRunning.value) {
                val host = intent!!.getStringExtra("host")
                val port = intent.getIntExtra("port", 1080)
                val dcip = intent.getStringExtra("dcip")?.replace(";", "\n")
                val secret = intent.getStringExtra("secret")

                ProxyManager.setRunning(true)

                val upd: Notification =
                    NotificationCompat.Builder(this@TGProxyService, CHANNEL_ID)
                        .setContentTitle("TG Proxy")
                        .setContentText("Прокси запущен")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setOngoing(true)
                        .build()

                val notificationManager: NotificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(1, upd)

                proxyControl.start_proxy(host, port, dcip, secret)
            }
        } catch (e: Exception) {
            if (e is PyException) {
                val errorMessage = e.message ?: "Unknown Python error"
                Log.e("TGProxyService", "PYTHON CRASHED: $errorMessage")
            } else {
                Log.e("TGProxyService", "Generic error: ${e.message}")
            }

            ProxyManager.setRunning(false)

            val upd: Notification = NotificationCompat.Builder(this@TGProxyService, CHANNEL_ID)
                .setContentTitle("TG Proxy")
                .setContentText("Ошибка запуска движка прокси")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(false)
                .build()
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, upd)
        }
    }

    private fun createNotificationChannel() {
        // ID канала должен совпадать с тем, что ты передаешь в NotificationCompat.Builder
        val name = "TG Proxy подготовка"
        val descriptionText = "Уведомления о включении прокси-сервера"
        val importance =
            NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        // Получаем системный менеджер и регистрируем канал
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    fun stopProxy() {
        Log.d("proxy", "proxy stopping")
        ProxyManager.setRunning(false)
        proxyControl.stop_proxy()
        stopSelf()
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }
}