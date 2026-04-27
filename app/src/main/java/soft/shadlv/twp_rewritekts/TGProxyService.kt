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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import main.ProxyControl
import soft.shadlv.twp_rewritekts.store.DataStore
import soft.shadlv.twp_rewritekts.store.ProxyConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class TGProxyService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID: String = "ProxyChannel"
    }

    private val proxyControl by lazy { ProxyControl() }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()

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

        val dataStore = DataStore(applicationContext)
        val proxyConfig = dataStore.getObject<ProxyConfig>()
        startProxyEngine(proxyConfig!!)

        return START_STICKY
    }

    private fun startProxyEngine(input: ProxyConfig) =
        lifecycleScope.launch(PythonBackgroundEngine.getDispatcher()) {
            try {
                Log.d("TGProxyService", "proxy starting")

                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@TGProxyService))
                }

                if (!ProxyManager.isRunning.value) {
                    val host = input.host
                    val port = input.port
                    val dcip = input.dcip.replace(";", "\n")
                    val secret = input.secret

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
                    .setContentText("Прокси остановлен")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(false)
                    .build()
                val notificationManager: NotificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(1, upd)
            }
        }

    private fun createNotificationChannel() {
        val name = "TG Proxy подготовка"
        val descriptionText = "Уведомления о включении прокси-сервера"
        val importance =
            NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        Log.d("proxy", "proxy stopping")

        runBlocking {
            withTimeoutOrNull(1500) {
                launch(Dispatchers.IO) {
                    proxyControl.stop_proxy()
                }
            }
        }

        PythonBackgroundEngine.shutdown()
        ProxyManager.setRunning(false)

        val upd: Notification = NotificationCompat.Builder(this@TGProxyService, CHANNEL_ID)
            .setContentTitle("TG Proxy")
            .setContentText("Прокси остановлен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(false)
            .build()
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, upd)

        super.onDestroy()
    }
}

internal object PythonBackgroundEngine {
    private var executor: ExecutorService? = null
    private var dispatcher: CoroutineDispatcher? = null

    @Synchronized
    fun getDispatcher(): CoroutineDispatcher {
        if (executor == null || executor!!.isShutdown) {
            executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "PythonEngineThread").apply {
                    isDaemon = true
                    priority = 8
                }
            }
            dispatcher = executor!!.asCoroutineDispatcher()
        }
        return dispatcher!!
    }

    @Synchronized
    fun shutdown() {
        val exec = executor ?: return
        exec.shutdown()
        try {
            if (!exec.awaitTermination(3, SECONDS)) {
                Log.w("Engine", "Executor didn't stop in time, forcing shutdownNow")
                exec.shutdownNow()

                if (!exec.awaitTermination(1, SECONDS)) {
                    Log.e("Engine", "Executor pool did not terminate")
                }
            }
        } catch (ie: InterruptedException) {
            exec.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            executor = null
            dispatcher = null
            Log.d("Engine", "Cleaned up all resources")
        }
    }
}