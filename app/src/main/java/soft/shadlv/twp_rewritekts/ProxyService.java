package soft.shadlv.twp_rewritekts;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import main.ProxyControl;

import androidx.core.app.NotificationCompat;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.Objects;


public class ProxyService extends Service {
    private ProxyControl proxy;
    private static final String CHANNEL_ID = "ProxyChannel";
    private volatile boolean isRunning = false;
    private String proxyResult;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TG WS Proxy")
                .setContentText("waiting for message")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
        new Thread(() -> {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }
            proxy = new ProxyControl();
            if (!isRunning) {
                isRunning = true;
                proxyResult = proxy.start_and_check(
                        intent.getStringExtra("host"),
                        intent.getIntExtra("port", 1080),
                        intent.getStringExtra("dcip")
                );
            }
        }).start();
        if (Objects.equals(proxyResult, "SUCCESS") || proxyResult == null){
            proxyResult = "";
            proxyResult = String.format("Работает на %s:%d", intent.getStringExtra("host"), intent.getIntExtra("port", 1080));
            StaticEventManager.triggerEvent("event.proxy.on");
        }
        else{
            StaticEventManager.triggerEvent(proxyResult);
            stopSelf();
        }
        Log.d("idk", proxyResult);
        NotificationManager manager = getSystemService(NotificationManager.class);
        Notification upd = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TG WS Proxy")
                .setContentText(proxyResult)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        manager.notify(1, upd);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Proxy Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        Log.d("idk", "proxy stopping");
        isRunning = false;
        if (proxy != null)
            proxy.stop_proxy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}