package com.irotheangel.schedulemaker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

/** Sends you back to Schedule Maker when you open a blocked app during focus. */
public class FocusService extends AccessibilityService {

    private long lastKick = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) return;

        SharedPreferences sp = getSharedPreferences(FocusPlugin.PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("active", false)) return;
        long until = sp.getLong("until", 0L);
        if (until > 0 && System.currentTimeMillis() > until) {
            sp.edit().putBoolean("active", false).apply();
            return;
        }
        String apps = "," + sp.getString("apps", "") + ",";
        if (!apps.contains("," + pkg + ",")) return;

        long now = System.currentTimeMillis();
        if (now - lastKick < 800) return;
        lastKick = now;

        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra("blocked", pkg);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {}
}
