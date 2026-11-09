package com.irotheangel.schedulemaker;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.os.Build;
import android.os.Process;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.List;

/** Focus mode (app blocking), opening the Claude app, and text shared into this app. */
@CapacitorPlugin(name = "Focus")
public class FocusPlugin extends Plugin {

    static final String PREFS = "focus";
    static final String CLAUDE = "com.anthropic.claude";

    @Override
    public void load() {
        handleIntent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.isEmpty()) {
                JSObject o = new JSObject();
                o.put("text", text);
                notifyListeners("sharedText", o, true);
            }
            intent.setAction(Intent.ACTION_MAIN);
            intent.removeExtra(Intent.EXTRA_TEXT);
        }
        String blocked = intent.getStringExtra("blocked");
        if (blocked != null) {
            JSObject o = new JSObject();
            o.put("app", blocked);
            notifyListeners("blocked", o, true);
            intent.removeExtra("blocked");
        }
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void start(PluginCall call) {
        long until = call.getLong("until", 0L);
        JSArray apps = call.getArray("apps", new JSArray());
        StringBuilder sb = new StringBuilder();
        try {
            List<String> list = apps.toList();
            for (String a : list) {
                if (sb.length() > 0) sb.append(',');
                sb.append(a);
            }
        } catch (Exception e) {
            // keep whatever was collected
        }
        prefs().edit().putBoolean("active", true).putLong("until", until).putString("apps", sb.toString()).apply();
        try {
            ContextCompat.startForegroundService(getContext(), new Intent(getContext(), BlockerService.class));
        } catch (Exception e) {
            call.reject("Could not start focus: " + e.getMessage());
            return;
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        prefs().edit().putBoolean("active", false).apply();
        getContext().stopService(new Intent(getContext(), BlockerService.class));
        call.resolve();
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        boolean usage = usageAllowed();
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext());
        JSObject r = new JSObject();
        r.put("usage", usage);
        r.put("overlay", overlay);
        r.put("enabled", usage && overlay);
        call.resolve(r);
    }

    private boolean usageAllowed() {
        AppOpsManager ops = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getContext().getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getContext().getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @PluginMethod
    public void openUsageSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openClaude(PluginCall call) {
        String text = call.getString("text", "");
        JSObject r = new JSObject();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        send.setPackage(CLAUDE);
        send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(send);
            r.put("opened", "share");
            call.resolve(r);
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(CLAUDE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launch);
            r.put("opened", "app");
            call.resolve(r);
            return;
        }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new?q=" + Uri.encode(text)));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(web);
        r.put("opened", "web");
        call.resolve(r);
    }
}
