package soft.shadlv.twp_rewritekts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import main.ProxyControl
import soft.shadlv.twp_rewritekts.repository.ProxyConfigRepository
import soft.shadlv.twp_rewritekts.store.ProxyConfig
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

object StatusSerializer : Serializer<Boolean> {
    override val defaultValue: Boolean
        get() = false

    override suspend fun readFrom(input: InputStream): Boolean {
        try {
            return Json.decodeFromString<Boolean>(
                input.readBytes().decodeToString()
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Time", serialization)
        }
    }

    override suspend fun writeTo(t: Boolean, output: OutputStream) {
        output.write(
            Json.encodeToString(t)
                .encodeToByteArray()
        )
    }
}

object ServiceDataStoreProvider {
    @Volatile
    private var instance: DataStore<Boolean>? = null

    fun getInstance(context: Context): DataStore<Boolean> {
        return instance ?: synchronized(this) {
            instance ?: MultiProcessDataStoreFactory.create(
                serializer = StatusSerializer,
                produceFile = {
                    File(context.filesDir, "time.json")
                },
                corruptionHandler = null
            ).also { instance = it }
        }
    }
}

class TGProxyService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID: String = "ProxyChannel"
        private const val NOTIFICATION_ID = 1
    }

    private val repository by lazy { ProxyConfigRepository(application) }
    private val proxyControl by lazy { ProxyControl() }
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    @Volatile
    private var isRun = false

    private val dozeModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIdle = powerManager.isDeviceIdleMode
            val isScreenOn = powerManager.isInteractive

            if (isIdle) {
                Log.d("ProxyWatchdog", "System went into Doze Mode. Stopping proxy.")
                stopProxy()
            } else if (isScreenOn && !isRun) {
                Log.d("ProxyWatchdog", "System woke up. Starting proxy.")
                startProxy()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(ACTION_SCREEN_ON)
            addAction(ACTION_SCREEN_OFF)
        }
        registerReceiver(dozeModeReceiver, filter)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Подготовка..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Подготовка..."))
        }

        startProxy()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("TGProxyService", "proxy stopping")
        unregisterReceiver(dozeModeReceiver)
        stopProxy()
        PythonBackgroundEngine.shutdown()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("TGProxyService", "Stop_hard")
    }

    @Synchronized
    private fun startProxy() {
        if (isRun) return

        lifecycleScope.launch {
            try {
                val proxyConfig = repository.getConfig()

                if (proxyConfig != null) {
                    startProxyEngine(proxyConfig)
                } else {
                    Log.e("TGProxyService", "Config is null, stopping self")
                    Toast.makeText(applicationContext, "Сохраните параметры в настройках", Toast.LENGTH_SHORT)
                        .show()
                    stopSelf()
                }
            } catch (ex: Exception) {
                Toast.makeText(applicationContext, "Ошибка запуска сервиса", Toast.LENGTH_SHORT)
                    .show()
                stopSelf()
            }
        }
    }

    @Synchronized
    private fun stopProxy() {
        if (!isRun) return

        runBlocking {
            withTimeoutOrNull(1500) {
                proxyControl.stop_proxy()
            }
            updateProxyStatus(false, "Прокси остановлен")
        }
    }

    @Synchronized
    private fun startProxyEngine(input: ProxyConfig) =
        lifecycleScope.launch(PythonBackgroundEngine.getDispatcher()) {
            try {
                Log.d(
                    "TGProxyService",
                    "Proxy starting: Proxy Process PID: ${android.os.Process.myPid()}"
                )

                val dcip = input.dcip.replace(";", "\n")

                updateProxyStatus(true, "Прокси запущен")

                proxyControl.start_proxy(input.host, input.port, dcip, input.secret)

                Log.d("TGProxyService", "Proxy control stopped")
            } catch (e: Exception) {
                handleProxyCrash(e)
            }
        }

    private suspend fun handleProxyCrash(e: Exception) {
        val errorMessage =
            if (e is PyException) "PYTHON CRASHED: ${e.message}" else "Generic error: ${e.message}"
        Log.e("TGProxyService", errorMessage)

        updateProxyStatus(false, "Ошибка: Прокси остановлен")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Управление TG прокси",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления о состоянии прокси-сервера"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TG Proxy")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(isRun)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private suspend fun updateProxyStatus(status: Boolean, text: String) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("TGProxyService", "Update status $status")
                val dataStore = ServiceDataStoreProvider.getInstance(this@TGProxyService)
                dataStore.updateData { prefs ->
                    status
                }
                isRun = status
                updateNotificationStatus(text)
            } catch (ex: Exception) {
                Log.d("TGProxyService", "Error write status")
            }
        }

    private fun updateNotificationStatus(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
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
                Log.w("TGProxyService - Pool", "Executor didn't stop in time, forcing shutdownNow")
                exec.shutdownNow()

                if (!exec.awaitTermination(1, SECONDS)) {
                    Log.e("TGProxyService - Pool", "Executor pool did not terminate")
                }
            }
        } catch (ie: InterruptedException) {
            exec.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            executor = null
            dispatcher = null
            Log.d("TGProxyService - Pool", "Cleaned up all resources")
        }
    }
}