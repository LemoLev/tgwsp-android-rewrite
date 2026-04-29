package soft.shadlv.twp_rewritekts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
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
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

gc collectimport android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
import android.util.Log
import android.view.Display
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
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class TGProxyService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID: String = "ProxyChannel"
    }

    private val proxyControl by lazy { ProxyControl() }

    @Volatile
    private var isRun = false

    override fun onCreate() {
        super.onCreate()

        val proxyDir = File(applicationContext.filesDir, "proxy_engine")
        if (!proxyDir.exists()) {
            proxyDir.mkdirs()
        }

        val statusFile = File(proxyDir, "proxy_status.txt")
        if (!statusFile.exists()) {
            statusFile.createNewFile()
            statusFile.writeText(false.toString())
            Log.d("Proxy", "The file was created from scratch")
        } else {
            Log.d("Proxy", "File already exists, current content: ${statusFile.readText()}")
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(ACTION_SCREEN_ON)
            addAction(ACTION_SCREEN_OFF)
        }
        registerReceiver(dozeModeReceiver, filter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                commandReceiver,
                IntentFilter("${applicationContext.packageName}.PROXY_COMMAND"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                commandReceiver,
                IntentFilter("${applicationContext.packageName}.PROXY_COMMAND")
            )
        }
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

        startProxy()

        return START_STICKY
    }

    private fun startProxyEngine(input: ProxyConfig) =
        lifecycleScope.launch(PythonBackgroundEngine.getDispatcher()) {
            try {
                Log.d(
                    "TGProxyService",
                    "Proxy starting: Proxy Process PID: ${android.os.Process.myPid()}"
                )

                val host = input.host
                val port = input.port
                val dcip = input.dcip.replace(";", "\n")
                val secret = input.secret

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

                updateProxyStatus(true)
                proxyControl.start_proxy(host, port, dcip, secret)
                Log.d("TGProxyService", "Proxy control stopped")
            } catch (e: Exception) {
                if (e is PyException) {
                    val errorMessage = e.message ?: "Unknown Python error"
                    Log.e("TGProxyService", "PYTHON CRASHED: $errorMessage")
                } else {
                    Log.e("TGProxyService", "Generic error: ${e.message}")
                }

                val upd: Notification = NotificationCompat.Builder(this@TGProxyService, CHANNEL_ID)
                    .setContentTitle("TG Proxy")
                    .setContentText("Прокси остановлен")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(false)
                    .build()
                val notificationManager: NotificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(1, upd)

                updateProxyStatus(false)
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

        stopProxy()

        PythonBackgroundEngine.shutdown()
        updateProxyStatus(false)

        val upd: Notification = NotificationCompat.Builder(this@TGProxyService, CHANNEL_ID)
            .setContentTitle("TG Proxy")
            .setContentText("Прокси остановлен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(false)
            .build()
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, upd)

        unregisterReceiver(dozeModeReceiver)
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }

    private fun updateProxyStatus(status: Boolean) {
        val proxyDir = File(applicationContext.filesDir, "proxy_engine")
        val statusFile = File(proxyDir, "proxy_status.txt")
        val tempFile = File(proxyDir, "status.tmp")
        tempFile.writeText(status.toString())
        tempFile.renameTo(statusFile)
        isRun = status
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getStringExtra("action")
            when (action) {
                "START" -> startProxy()
                "STOP" -> stopProxy()
            }
        }
    }

    @Synchronized
    private fun startProxy() {
        if (isRun) {
            return
        }
        val dataStore = DataStore(applicationContext)
        val proxyConfig = dataStore.getObject<ProxyConfig>()
        if (proxyConfig != null) {
            startProxyEngine(proxyConfig)
        } else {
            stopSelf()
        }
    }

    @Synchronized
    private fun stopProxy() {
        if (!isRun) {
            return
        }
        runBlocking {
            withTimeoutOrNull(1500) {
                launch(Dispatchers.IO) {
                    proxyControl.stop_proxy()
                    updateProxyStatus(false)
                }
            }
        }
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

private val dozeModeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val isIdle = powerManager.isDeviceIdleMode
        val isScreenOn = powerManager.isInteractive

        if (isIdle) {
            Log.d("ProxyWatchdog", "The system went into a Doze Mode. Slow down the proxy.")
            val intent = Intent("${context.packageName}.PROXY_COMMAND").apply {
                putExtra("action", "STOP")
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } else if (isScreenOn) {
            Log.d("ProxyWatchdog", "The system woke up. Running a proxy.")
            val intent = Intent("${context.packageName}.PROXY_COMMAND").apply {
                putExtra("action", "START")
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}