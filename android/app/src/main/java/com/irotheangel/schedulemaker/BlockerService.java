package com.irotheangel.schedulemaker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private View overlay = null;
    private WindowManager wm = null;

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
        removeOverlay();
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
        String apps = "," + sp.getString("apps", "") + ",";
        boolean blocked = current != null && !current.equals(getPackageName()) && apps.contains("," + current + ",");
        if (!blocked) {
            removeOverlay();
            return true;
        }
        showOverlay(current);
        if (now - lastKick > 3000) {
            lastKick = now;
            openApp(current, false);
        }
        return true;
    }

    private void openApp(String pkg, boolean fromTap) {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            i.putExtra("blocked", pkg);
            startActivity(i);
        } catch (Exception ignored) {
        }
        if (fromTap) removeOverlay();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            if (pkg.contains("youtube")) return "YouTube";
            if (pkg.contains("musically") || pkg.contains("trill")) return "TikTok";
            if (pkg.contains("instagram")) return "Instagram";
            if (pkg.contains("snapchat")) return "Snapchat";
            return "This app";
        }
    }

    /** Full-screen cover over a blocked app. */
    private void showOverlay(final String pkg) {
        if (overlay != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xF7161615);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));
        box.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Focus is on");
        title.setTextColor(0xFFF0EFEA);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.SERIF);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText(label(pkg) + " is blocked until you finish your task.");
        sub.setTextColor(0xFF9C9A94);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(12);
        box.addView(sub, sp);

        Button back = new Button(this);
        back.setText("Back to Schedule Maker");
        back.setAllCaps(false);
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFD97757);
        bg.setCornerRadius(dp(14));
        back.setBackground(bg);
        back.setPadding(dp(28), dp(14), dp(28), dp(14));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApp(pkg, true);
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(28);
        box.addView(back, bp);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try {
            wm.addView(box, lp);
            overlay = box;
        } catch (Exception ignored) {
        }
    }

    private void removeOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
    }
}
