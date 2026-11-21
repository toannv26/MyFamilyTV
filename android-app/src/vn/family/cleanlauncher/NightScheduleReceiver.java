package vn.family.cleanlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class NightScheduleReceiver extends BroadcastReceiver {
    public static final String ACTION_NIGHT_OFF = "vn.family.cleanlauncher.ACTION_NIGHT_OFF";
    public static final String ACTION_MORNING_WAKE = "vn.family.cleanlauncher.ACTION_MORNING_WAKE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        AppPrefs prefs = new AppPrefs(context);
        if (!prefs.isNightScheduleEnabled()) return;

        if (ACTION_MORNING_WAKE.equals(action)) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock wl = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "MyFamilyTV:MorningWake");
                    wl.acquire(10000);
                }
            } catch (Exception ignored) {}
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("night_cmd", "WAKE");
            context.startActivity(i);
        } else if (ACTION_NIGHT_OFF.equals(action)) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("night_cmd", "SLEEP");
            context.startActivity(i);
        }
    }
}
