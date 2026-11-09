package com.irotheangel.schedulemaker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

/** While focus is on, checks once a second which app is open and sends you back to Schedule Maker if it is a blocked one. */
public class BlockerService extends Service {

    private static final String CHANNEL = "focus";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String current = null;
    private long lastKick = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!check()) {
                stopSelf();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Focus mode", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_icon)
            .setContentTitle("Focus is on")
            .setContentText("Distracting apps are blocked until you finish.")
            .setOngoing(true)
            .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, b.build());
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    /** Returns false when focus is over. */
    private boolean check() {
        SharedPreferences sp = getSharedPreferences(FocusPlugin.PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("active", false)) return false;
        long now = System.currentTimeMillis();
        long until = sp.getLong("until", 0L);
        if (until > 0 && now > until) {
            sp.edit().putBoolean("active", false).apply();
            return false;
        }
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usm == null) return true;
        UsageEvents events = usm.queryEvents(now - 5000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.getPackageName();
        }
        if (current == null || current.equals(getPackageName())) return true;
        String apps = "," + sp.getString("apps", "") + ",";
        if (apps.contains("," + current + ",") && now - lastKick > 1500) {
            lastKick = now;
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            i.putExtra("blocked", current);
            startActivity(i);
        }
        return true;
    }
}
