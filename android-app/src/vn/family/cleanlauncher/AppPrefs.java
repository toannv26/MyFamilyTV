package vn.family.cleanlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppPrefs {
    public static final int DEFAULT_MAX_KIDS_MINUTES = 45;
    public static final int QUIZ_BONUS_MINUTES = 5;
    public static final int MAX_QUIZ_PER_DAY = 2;

    public static class ScheduleItem {
        public String time;
        public String title;
        public boolean playMusic;

        public ScheduleItem(String time, String title, boolean playMusic) {
            this.time = time;
            this.title = title;
            this.playMusic = playMusic;
        }
    }

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getSharedPreferences("family_clean_tv", Context.MODE_PRIVATE);
        ensureDayReset();
    }

    public void ensureDayReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String saved = sp.getString("last_date", "");
        if (!today.equals(saved)) {
            sp.edit()
                .putString("last_date", today)
                .putInt("kids_watched_min", 0)
                .putInt("kids_bonus_min", 0)
                .putInt("quiz_used_today", 0)
                .putBoolean("sleep_locked", false)
                .putString("last_alarm_fired", "")
                .apply();
        }
    }

    public boolean isKidsMode() { return sp.getBoolean("kids_mode", false); }
    public void setKidsMode(boolean v) { sp.edit().putBoolean("kids_mode", v).apply(); }

    public boolean isSleepLocked() { ensureDayReset(); return sp.getBoolean("sleep_locked", false); }
    public void setSleepLocked(boolean v) { sp.edit().putBoolean("sleep_locked", v).apply(); }

    public int getKidsWatchedMin() { ensureDayReset(); return sp.getInt("kids_watched_min", 0); }
    public void addKidsWatchedMin(int delta) {
        ensureDayReset();
        int cur = sp.getInt("kids_watched_min", 0) + delta;
        sp.edit().putInt("kids_watched_min", Math.max(0, cur)).apply();
    }

    public int getBaseMaxKidsMinutes() {
        return sp.getInt("base_max_kids_min", DEFAULT_MAX_KIDS_MINUTES);
    }

    public void setBaseMaxKidsMinutes(int mins) {
        int clamped = Math.max(5, Math.min(240, mins));
        sp.edit().putInt("base_max_kids_min", clamped).apply();
        if (getKidsRemainingMin() > 0) {
            sp.edit().putBoolean("sleep_locked", false).apply();
        }
    }

    public int getKidsBonusMin() { ensureDayReset(); return sp.getInt("kids_bonus_min", 0); }
    public int getMaxAllowedMin() { return getBaseMaxKidsMinutes() + getKidsBonusMin(); }
    public int getKidsRemainingMin() { return Math.max(0, getMaxAllowedMin() - getKidsWatchedMin()); }

    public int getQuizUsedToday() { ensureDayReset(); return sp.getInt("quiz_used_today", 0); }
    public boolean canTakeQuizBonus() { return getQuizUsedToday() < MAX_QUIZ_PER_DAY; }

    public void recordQuizBonus() {
        ensureDayReset();
        if (canTakeQuizBonus()) {
            sp.edit()
                .putInt("quiz_used_today", getQuizUsedToday() + 1)
                .putInt("kids_bonus_min", getKidsBonusMin() + QUIZ_BONUS_MINUTES)
                .putBoolean("sleep_locked", false)
                .apply();
        }
    }

    public void parentGrantMinutes(int mins) {
        ensureDayReset();
        sp.edit()
            .putInt("kids_bonus_min", getKidsBonusMin() + mins)
            .putBoolean("sleep_locked", false)
            .apply();
    }

    public boolean verifyDynamicPin(String input) {
        if (input == null) return false;
        String clean = input.trim();
        Calendar c = Calendar.getInstance();
        String hhmm = String.format(Locale.US, "%02d%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        String ddmm = String.format(Locale.US, "%02d%02d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
        return clean.equals(hhmm) || clean.equals(ddmm);
    }

    public String getCityName() { return sp.getString("city_name", "Hà Nội"); }
    public double getCityLat() { return Double.parseDouble(sp.getString("city_lat", "21.0285")); }
    public double getCityLon() { return Double.parseDouble(sp.getString("city_lon", "105.8542")); }

    public void setCity(String name, double lat, double lon) {
        sp.edit()
            .putString("city_name", name)
            .putString("city_lat", String.valueOf(lat))
            .putString("city_lon", String.valueOf(lon))
            .apply();
    }

    public String getLastAlarmFired() { return sp.getString("last_alarm_fired", ""); }
    public void setLastAlarmFired(String key) { sp.edit().putString("last_alarm_fired", key).apply(); }

    public List<ScheduleItem> getSchedules() {
        List<ScheduleItem> list = new ArrayList<>();
        String raw = sp.getString("schedules_v3_json", null);
        if (raw == null) {
            list.add(new ScheduleItem("06:15", "🏃 Nhạc thể dục sáng", true));
            list.add(new ScheduleItem("07:00", "🎒 Đi học", true));
            list.add(new ScheduleItem("19:00", "🍚 Ăn tối", true));
            list.add(new ScheduleItem("22:00", "🛁 Vệ sinh cá nhân", true));
            list.add(new ScheduleItem("22:10", "🌙 Đi ngủ", true));
            return list;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new ScheduleItem(
                        o.optString("time", "06:15"),
                        o.optString("title", ""),
                        o.optBoolean("playMusic", true)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveSchedules(List<ScheduleItem> list) {
        JSONArray arr = new JSONArray();
        try {
            for (ScheduleItem s : list) {
                JSONObject o = new JSONObject();
                o.put("time", s.time);
                o.put("title", s.title);
                o.put("playMusic", s.playMusic);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        sp.edit().putString("schedules_v3_json", arr.toString()).apply();
    }

    public List<String> getQuotes() {
        List<String> list = new ArrayList<>();
        // Always include the 150 built-in curated quotes (shuffled)
        list.addAll(QuoteBank.getAllDefaultQuotes());
        // Plus any custom quotes parents added from phone
        String raw = sp.getString("quotes_custom_json", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String q = arr.optString(i, "").trim();
                    if (!q.isEmpty()) list.add(0, q);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public void saveQuotes(List<String> list) {
        // Save only newly added custom quotes cleanly
        JSONArray arr = new JSONArray();
        for (String q : list) arr.put(q);
        sp.edit().putString("quotes_custom_json", arr.toString()).apply();
    }

    public void addCustomQuotes(List<String> newOnes) {
        List<String> existing = new ArrayList<>();
        String raw = sp.getString("quotes_custom_json", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String q = arr.optString(i, "").trim();
                    if (!q.isEmpty()) existing.add(q);
                }
            } catch (Exception ignored) {}
        }
        existing.addAll(newOnes);
        saveQuotes(existing);
    }

    public static class BannerItem {
        public String time; // "" or "HH:MM"
        public String msg;
        public BannerItem(String time, String msg) {
            this.time = time == null ? "" : time;
            this.msg = msg == null ? "" : msg;
        }
    }

    public List<BannerItem> getBannerItems() {
        List<BannerItem> list = new ArrayList<>();
        String raw = sp.getString("banners_v2_json", null);
        if (raw == null) {
            list.add(new BannerItem("06:30", "☀️ Thức dậy chào buổi sáng!"));
            list.add(new BannerItem("18:00", "🍚 Đến giờ ăn tối rồi cả nhà ơi!"));
            list.add(new BannerItem("22:00", "🌙 Đến giờ đi ngủ rồi, chúc ngủ ngon!"));
            return list;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String m = o.optString("msg", "").trim();
                if (!m.isEmpty()) list.add(new BannerItem(o.optString("time", "").trim(), m));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveBannerItems(List<BannerItem> list) {
        JSONArray arr = new JSONArray();
        try {
            for (BannerItem b : list) {
                JSONObject o = new JSONObject();
                o.put("time", b.time);
                o.put("msg", b.msg);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        sp.edit().putString("banners_v2_json", arr.toString()).apply();
    }

    public String getLastBannerFired() { return sp.getString("last_banner_fired", ""); }
    public void setLastBannerFired(String key) { sp.edit().putString("last_banner_fired", key).apply(); }

    public boolean isNightScheduleEnabled() { return sp.getBoolean("night_sched_on", true); }
    public void setNightScheduleEnabled(boolean v) { sp.edit().putBoolean("night_sched_on", v).apply(); }

    // 2 active display windows: default 06:30-07:30 and 18:00-22:00
    public String getSlot1Start() { return sp.getString("slot1_start", "06:30"); }
    public String getSlot1End() { return sp.getString("slot1_end", "07:30"); }
    public String getSlot2Start() { return sp.getString("slot2_start", "18:00"); }
    public String getSlot2End() { return sp.getString("slot2_end", "22:00"); }

    public void setActiveSlots(String s1Start, String s1End, String s2Start, String s2End) {
        sp.edit()
            .putString("slot1_start", s1Start)
            .putString("slot1_end", s1End)
            .putString("slot2_start", s2Start)
            .putString("slot2_end", s2End)
            .apply();
    }

    public boolean isAppPinLockEnabled() { return sp.getBoolean("app_pin_lock_on", true); }
    public void setAppPinLockEnabled(boolean v) { sp.edit().putBoolean("app_pin_lock_on", v).apply(); }

    public static int toMinutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (Exception e) { return -1; }
    }

    public boolean isWithinActiveWindows(int curMinutes) {
        int a1 = toMinutes(getSlot1Start()), b1 = toMinutes(getSlot1End());
        int a2 = toMinutes(getSlot2Start()), b2 = toMinutes(getSlot2End());
        boolean in1 = (a1 >= 0 && b1 >= 0 && curMinutes >= a1 && curMinutes < b1);
        boolean in2 = (a2 >= 0 && b2 >= 0 && curMinutes >= a2 && curMinutes < b2);
        return in1 || in2;
    }
}
