package soft.shadlv.twp_rewritekts;

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

public class ProxyService extends Service {
    private ProxyControl proxy;
    private static final String CHANNEL_ID = "ProxyChannel";
    private volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TG WS Proxy работает")
                .setContentText("ну тип работает и ч")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        proxy = new ProxyControl();
        if (!isRunning) {
            isRunning = true;
            new Thread(
                    () -> proxy.start_proxy(
                            intent.getStringExtra("host"),
                            intent.getIntExtra("port", 1080),
                            intent.getStringExtra("dcip")
                    )
            ).start();
        }

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
//        synchronized (lock) {
//            isRunning = false;
//            lock.notifyAll();
//            proxyThread.interrupt();
//        }
        isRunning = false;
        proxy.stop_proxy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}