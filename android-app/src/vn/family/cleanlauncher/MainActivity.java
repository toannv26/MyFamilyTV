package vn.family.cleanlauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.view.ViewOutlineProvider;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements FamilyWebServer.ControlBridge {

    private static class AppEntry {
        final String name;
        final String shortIcon;
        final String pkg;
        final int color;
        AppEntry(String name, String shortIcon, String pkg, int color) {
            this.name = name;
            this.shortIcon = shortIcon;
            this.pkg = pkg;
            this.color = color;
        }
    }

    private final AppEntry[] homeApps = new AppEntry[] {
        new AppEntry("YouTube Kids", "YK", "com.google.android.youtube.tvkids", 0xFFDB2777),
        new AppEntry("FPT Play", "FPT", "net.fptplay.ottbox", 0xFFEA580C),
        new AppEntry("VTV Go", "VTV", "vn.vtv.vtvgotv", 0xFF2563EB),
        new AppEntry("Cài đặt", "⚙", "__SETTINGS__", 0xFF475569)
    };

    private final AppEntry[] kidsApps = new AppEntry[] {
        new AppEntry("YouTube Kids", "YK", "com.google.android.youtube.tvkids", 0xFFDB2777),
        new AppEntry("Nhạc Thiếu Nhi", "🎵", "__KIDS_MUSIC__", 0xFFF59E0B)
    };

    private final int[][] fallbackGradients = new int[][] {
        {0xFF0EA5E9, 0xFF38BDF8, 0xFF6366F1},
        {0xFF14B8A6, 0xFF0EA5E9, 0xFF2563EB},
        {0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6},
        {0xFF0284C7, 0xFF06B6D4, 0xFF10B981}
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppPrefs prefs;
    private MusicPlayerManager musicManager;
    private FamilyWebServer webServer;

    private FrameLayout rootFrame;
    private ImageView wallpaperBgView;
    private ImageView wallpaperView;
    private View overlayView;
    private LinearLayout mainUi;
    private LinearLayout topChipsRow;
    private LinearLayout quoteCenterBox;
    private LinearLayout dockContainer;
    private LinearLayout appDockRow;
    private TextView clockTimeView;
    private TextView clockDateView;
    private TextView weatherView;
    private TextView quoteTagView;
    private TextView quoteTextView;
    private TextView scheduleStripView;
    private LinearLayout kidsQuotaBar;
    private TextView kidsStatusView;
    private TextView musicChipView;
    private TextView familyBannerView;

    // Fullscreen gates
    private LinearLayout sleepGateView;
    private TextView sleepSubView;
    private Button sleepQuizBtn;
    private LinearLayout quizGateView;
    private TextView quizBadgeView;
    private TextView quizQuestionView;
    private LinearLayout quizOptionsBox;
    private View nightOffOverlay;
    private boolean inNightStandby = false;
    private String lastNightAutoOffDate = "";
    private String lastMorningAutoWakeDate = "";

    private boolean frameMode = false;
    private int wallpaperIdx = 0;
    private int quoteIdx = 0;
    private List<File> photoFiles = new ArrayList<>();
    private List<String> cachedQuotes = new ArrayList<>();
    private Bitmap currentBitmap;
    private int sleepTimerRemainMin = 0;
    private QuizBank.Question currentQuizQuestion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = new AppPrefs(this);
        musicManager = new MusicPlayerManager();
        musicManager.setListener(new MusicPlayerManager.Listener() {
            @Override
            public void onStateChanged(final boolean playing, final String stationName) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (musicChipView != null) {
                            musicChipView.setText("🎵");
                            if (!musicChipView.isFocused()) {
                                musicChipView.setBackground(roundedBg(playing ? 0xCC0EA5E9 : 0x66FFFFFF, 99, 0x88FFFFFF, 1));
                            }
                        }
                    }
                });
            }
        });

        buildUi();
        setContentView(rootFrame);

        reloadPhotos();
        applyWallpaper();
        updateClockAndSchedule();
        rotateQuote();
        refreshWeather();
        applyModeUi();

        webServer = new FamilyWebServer(this, prefs, this);
        webServer.start();

        scheduleNightAlarms();
        startLoops();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNightIntent(intent);
    }

    private void handleNightIntent(Intent intent) {
        if (intent == null) return;
        String cmd = intent.getStringExtra("night_cmd");
        if ("SLEEP".equals(cmd)) {
            enterNightStandby();
        } else if ("WAKE".equals(cmd)) {
            wakeFromNightStandby(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.ensureDayReset();
        updateClockAndSchedule();
        applyModeUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (webServer != null) webServer.stop();
        if (musicManager != null) musicManager.stop();
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedBg(int fillColor, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), strokeColor);
        return gd;
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(0xFF090D16);

        wallpaperBgView = new ImageView(this);
        wallpaperBgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperBgView.setAlpha(0.32f);
        rootFrame.addView(wallpaperBgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        rootFrame.addView(wallpaperView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlayView = new View(this);
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x550B1528, 0x08000000, 0x18000000, 0x880B1528});
        overlayView.setBackground(scrim);
        rootFrame.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mainUi = new LinearLayout(this);
        mainUi.setOrientation(LinearLayout.VERTICAL);
        mainUi.setPadding(dp(42), dp(34), dp(42), dp(30));
        mainUi.setClipChildren(false);
        mainUi.setClipToPadding(false);
        rootFrame.addView(mainUi, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // TOP BAR
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.TOP);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);

        clockTimeView = new TextView(this);
        clockTimeView.setTextColor(Color.WHITE);
        clockTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        clockTimeView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockTimeView.setShadowLayer(12f, 0f, 3f, 0xAA000000);
        clockCol.addView(clockTimeView);

        clockDateView = new TextView(this);
        clockDateView.setTextColor(0xFFE2E8F0);
        clockDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        clockDateView.setShadowLayer(8f, 0f, 2f, 0xAA000000);
        clockCol.addView(clockDateView);

        weatherView = new TextView(this);
        weatherView.setTextColor(Color.WHITE);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        weatherView.setTypeface(null, Typeface.BOLD);
        weatherView.setPadding(dp(14), dp(6), dp(14), dp(6));
        weatherView.setBackground(roundedBg(0x660EA5E9, 99, 0x66FFFFFF, 1));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(8);
        clockCol.addView(weatherView, wlp);

        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(clockCol, clockLp);

        topChipsRow = new LinearLayout(this);
        topChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        topChipsRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topChipsRow.setClipChildren(false);
        topChipsRow.setClipToPadding(false);
        topChipsRow.setPadding(dp(6), dp(6), dp(6), dp(6));

        musicChipView = makeIconCircle("🎵", new View.OnClickListener() {
            @Override public void onClick(View v) { showMusicDialog(); }
        });
        topChipsRow.addView(musicChipView);

        topChipsRow.addView(makeStatsIconCircle(new View.OnClickListener() {
            @Override public void onClick(View v) { showStatsDialog(); }
        }));
        topChipsRow.addView(makeIconCircle("🧸", new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setKidsMode(true);
                applyModeUi();
                toast("Đã bật Chế độ Trẻ em");
            }
        }));

        topBar.addView(topChipsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mainUi.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // CENTER QUOTES + DAILY SCHEDULE (Theo đúng mockup: nhãn riêng cho từng câu)
        quoteCenterBox = new LinearLayout(this);
        quoteCenterBox.setOrientation(LinearLayout.VERTICAL);
        quoteCenterBox.setGravity(Gravity.CENTER);
        quoteCenterBox.setPadding(dp(36), dp(8), dp(36), dp(8));

        quoteTagView = new TextView(this);
        quoteTagView.setTextColor(Color.WHITE);
        quoteTagView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        quoteTagView.setTypeface(null, Typeface.BOLD);
        quoteTagView.setGravity(Gravity.CENTER);
        quoteTagView.setPadding(dp(18), dp(6), dp(18), dp(6));
        quoteTagView.setBackground(roundedBg(0x880284C7, 99, 0x80FFFFFF, 1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        quoteCenterBox.addView(quoteTagView, tlp);

        quoteTextView = new TextView(this);
        quoteTextView.setTextColor(0xFFFEF08A);
        quoteTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        quoteTextView.setTypeface(null, Typeface.BOLD);
        quoteTextView.setGravity(Gravity.CENTER);
        quoteTextView.setShadowLayer(18f, 0f, 3f, 0xDD000000);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = dp(10);
        quoteCenterBox.addView(quoteTextView, qlp);

        scheduleStripView = new TextView(this);
        scheduleStripView.setTextColor(0xFFA5F3FC);
        scheduleStripView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        scheduleStripView.setTypeface(null, Typeface.BOLD);
        scheduleStripView.setGravity(Gravity.CENTER);
        scheduleStripView.setShadowLayer(6f, 0f, 1f, 0xAA000000);
        scheduleStripView.setPadding(dp(18), dp(8), dp(18), dp(8));
        scheduleStripView.setBackground(roundedBg(0x550B2447, 99, 0x667DD3FC, 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(12);
        quoteCenterBox.addView(scheduleStripView, slp);

        mainUi.addView(quoteCenterBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // KIDS STATUS BAR + 3 ACTION BUTTONS (only in Kids Mode)
        kidsQuotaBar = new LinearLayout(this);
        kidsQuotaBar.setOrientation(LinearLayout.HORIZONTAL);
        kidsQuotaBar.setGravity(Gravity.CENTER_VERTICAL);
        kidsQuotaBar.setClipChildren(false);
        kidsQuotaBar.setClipToPadding(false);
        kidsQuotaBar.setPadding(dp(18), dp(10), dp(18), dp(10));
        kidsQuotaBar.setBackground(roundedBg(0x45FB7185, 20, 0x88FB7185, 1));
        kidsQuotaBar.setVisibility(View.GONE);

        kidsStatusView = new TextView(this);
        kidsStatusView.setTextColor(0xFFFFE4E6);
        kidsStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        kidsStatusView.setTypeface(null, Typeface.BOLD);
        kidsQuotaBar.addView(kidsStatusView);

        kidsQuotaBar.addView(makeChip("🧩 Thử Câu đố (+5p)", new View.OnClickListener() {
            @Override public void onClick(View v) { openQuizGate(); }
        }));
        kidsQuotaBar.addView(makeChip("😴 TV đi ngủ", new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSleepLocked(true);
                musicManager.stop();
                openSleepGate();
            }
        }));
        kidsQuotaBar.addView(makeChip("🔓 Mở khóa", new View.OnClickListener() {
            @Override public void onClick(View v) { showPinDialog(); }
        }));

        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.gravity = Gravity.CENTER_HORIZONTAL;
        klp.bottomMargin = dp(14);
        mainUi.addView(kidsQuotaBar, klp);

        // BOTTOM CENTERED DOCK (3 Apps + Settings)
        dockContainer = new LinearLayout(this);
        dockContainer.setOrientation(LinearLayout.VERTICAL);
        dockContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        dockContainer.setClipChildren(false);
        dockContainer.setClipToPadding(false);

        appDockRow = new LinearLayout(this);
        appDockRow.setOrientation(LinearLayout.HORIZONTAL);
        appDockRow.setGravity(Gravity.CENTER);
        appDockRow.setClipChildren(false);
        appDockRow.setClipToPadding(false);
        appDockRow.setPadding(dp(8), dp(10), dp(8), dp(10));
        dockContainer.addView(appDockRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mainUi.addView(dockContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // LARGE CENTERED FAMILY NOTIFICATION CARD (Chính giữa màn hình)
        familyBannerView = new TextView(this);
        familyBannerView.setTextColor(Color.WHITE);
        familyBannerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        familyBannerView.setTypeface(null, Typeface.BOLD);
        familyBannerView.setGravity(Gravity.CENTER);
        familyBannerView.setPadding(dp(44), dp(28), dp(44), dp(28));
        GradientDrawable bannerBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xF2EA580C, 0xF2DC2626});
        bannerBg.setCornerRadius(dp(26));
        bannerBg.setStroke(dp(3), 0xFFFDE68A);
        familyBannerView.setBackground(bannerBg);
        familyBannerView.setElevation(dp(28));
        familyBannerView.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        rootFrame.addView(familyBannerView, blp);

        buildSleepGate();
        buildQuizGate();

        nightOffOverlay = new View(this);
        nightOffOverlay.setBackgroundColor(Color.BLACK);
        nightOffOverlay.setVisibility(View.GONE);
        nightOffOverlay.setClickable(true);
        nightOffOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { wakeFromNightStandby(false); }
        });
        rootFrame.addView(nightOffOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView makeIconCircle(String icon, View.OnClickListener click) {
        final TextView tv = new TextView(this);
        tv.setText(icon);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(roundedBg(0x16FFFFFF, 99, 0x33FFFFFF, 1));
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            tv.setDefaultFocusHighlightEnabled(false);
        }
        tv.setOnClickListener(click);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    tv.setBackground(roundedBg(0x88FFFFFF, 99, 0xAAFFFFFF, 1));
                    tv.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start();
                    tv.setElevation(dp(10));
                } else {
                    boolean activeMusic = (tv == musicChipView && musicManager.isPlaying());
                    tv.setBackground(roundedBg(activeMusic ? 0x770EA5E9 : 0x16FFFFFF, 99, 0x33FFFFFF, 1));
                    tv.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    tv.setElevation(0f);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.leftMargin = dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private FrameLayout makeStatsIconCircle(View.OnClickListener click) {
        final FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(roundedBg(0x16FFFFFF, 99, 0x33FFFFFF, 1));
        wrap.setFocusable(true);
        wrap.setFocusableInTouchMode(false);
        wrap.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            wrap.setDefaultFocusHighlightEnabled(false);
        }

        // Minimalist crisp 3-bar white chart icon
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int[] heights = { dp(10), dp(17), dp(13) };
        for (int i = 0; i < heights.length; i++) {
            View bar = new View(this);
            bar.setBackground(roundedBg(0xEEFFFFFF, 3, 0, 0));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), heights[i]);
            if (i > 0) blp.leftMargin = dp(3);
            bars.addView(bar, blp);
        }
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        wrap.addView(bars, flp);

        wrap.setOnClickListener(click);
        wrap.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    wrap.setBackground(roundedBg(0x88FFFFFF, 99, 0xAAFFFFFF, 1));
                    wrap.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start();
                    wrap.setElevation(dp(10));
                } else {
                    wrap.setBackground(roundedBg(0x16FFFFFF, 99, 0x33FFFFFF, 1));
                    wrap.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    wrap.setElevation(0f);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.leftMargin = dp(10);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private TextView makeChip(String label, View.OnClickListener click) {
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(15), dp(9), dp(15), dp(9));
        tv.setBackground(roundedBg(0x280F172A, 99, 0x44FFFFFF, 1));
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            tv.setDefaultFocusHighlightEnabled(false);
        }
        tv.setOnClickListener(click);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    tv.setBackground(roundedBg(0xE60284C7, 99, 0xAAFFFFFF, 1));
                    tv.setTextColor(Color.WHITE);
                    tv.animate().scaleX(1.07f).scaleY(1.07f).setDuration(150).start();
                    tv.setElevation(dp(10));
                } else {
                    tv.setBackground(roundedBg(0x280F172A, 99, 0x44FFFFFF, 1));
                    tv.setTextColor(Color.WHITE);
                    tv.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    tv.setElevation(0f);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private Dialog createPremiumDialog(View content, int widthDp) {
        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setDimAmount(0.72f);
        }
        return dlg;
    }

    private LinearLayout createPremiumSheet(String titleText) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26), dp(22), dp(26), dp(22));
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xF21A3358, 0xF2244475});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0x55FFFFFF);
        sheet.setBackground(bg);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(16);
        sheet.addView(title, tlp);
        return sheet;
    }

    private LinearLayout createMusicTile(String emoji, String title, String sub, final int accentColor, final View.OnClickListener onClick) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(18), dp(14), dp(18), dp(14));
        tile.setBackground(roundedBg(0xFF25426E, 16, 0, 0));
        tile.setFocusable(true);
        tile.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            tile.setDefaultFocusHighlightEnabled(false);
        }

        TextView t = new TextView(this);
        t.setText(emoji + "  " + title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(null, Typeface.BOLD);
        tile.addView(t);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(0xFFBAE6FD);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        tile.addView(s, slp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    tile.setBackground(roundedBg(accentColor, 16, 0, 0));
                    s.setTextColor(Color.WHITE);
                    tile.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                    tile.setElevation(dp(12));
                } else {
                    tile.setBackground(roundedBg(0xFF25426E, 16, 0, 0));
                    s.setTextColor(0xFFBAE6FD);
                    tile.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    tile.setElevation(0f);
                }
            }
        });
        tile.setOnClickListener(onClick);
        return tile;
    }

    private TextView createMenuRow(String text, final View.OnClickListener onClick) {
        final TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(0xFFF1F5F9);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setTypeface(null, Typeface.BOLD);
        row.setPadding(dp(18), dp(13), dp(18), dp(13));
        row.setBackground(roundedBg(0xFF172338, 14, 0x22FFFFFF, 1));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    row.setBackground(roundedBg(0xFF0284C7, 14, 0xAAFFFFFF, 1));
                    row.setTextColor(Color.WHITE);
                    row.animate().scaleX(1.03f).scaleY(1.03f).setDuration(130).start();
                } else {
                    row.setBackground(roundedBg(0xFF172338, 14, 0x22FFFFFF, 1));
                    row.setTextColor(0xFFF1F5F9);
                    row.animate().scaleX(1f).scaleY(1f).setDuration(130).start();
                }
            }
        });
        row.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private Drawable loadRealTvBanner(String pkg) {
        if (pkg == null || pkg.startsWith("__")) return null;
        PackageManager pm = getPackageManager();
        try {
            Intent leanback = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (leanback != null) {
                ResolveInfo ri = pm.resolveActivity(leanback, 0);
                if (ri != null && ri.activityInfo != null) {
                    Drawable b = ri.activityInfo.loadBanner(pm);
                    if (b != null) return b;
                }
            }
        } catch (Exception ignored) {}
        try {
            Drawable b = pm.getApplicationBanner(pkg);
            if (b != null) return b;
        } catch (Exception ignored) {}
        return null;
    }

    private Drawable loadRealAppIcon(String pkg) {
        if (pkg == null || pkg.startsWith("__")) return null;
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderDock() {
        appDockRow.removeAllViews();
        final boolean kids = prefs.isKidsMode();
        AppEntry[] list = kids ? kidsApps : homeApps;
        final int cardW = dp(188);
        final int cardH = dp(106);
        final int cornerPx = dp(18);

        for (int i = 0; i < list.length; i++) {
            final AppEntry app = list[i];
            final FrameLayout card = new FrameLayout(this);
            card.setFocusable(true);
            card.setClickable(true);
            card.setAlpha(0.65f);
            card.setElevation(dp(8));
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerPx);
                }
            });
            card.setClipToOutline(true);

            Drawable banner = loadRealTvBanner(app.pkg);
            if (banner != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(banner);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                LinearLayout fallback = new LinearLayout(this);
                fallback.setOrientation(LinearLayout.HORIZONTAL);
                fallback.setGravity(Gravity.CENTER);
                if ("__SETTINGS__".equals(app.pkg)) {
                    GradientDrawable gd = new GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            new int[]{0xFF0284C7, 0xFF38BDF8});
                    fallback.setBackground(gd);
                } else {
                    GradientDrawable gd = new GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            new int[]{0xFFF59E0B, 0xFFF97316});
                    fallback.setBackground(gd);
                }
                fallback.setPadding(dp(16), dp(12), dp(16), dp(12));

                Drawable appIcon = loadRealAppIcon(app.pkg);
                if (appIcon != null) {
                    ImageView iv = new ImageView(this);
                    iv.setImageDrawable(appIcon);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
                    ilp.rightMargin = dp(10);
                    fallback.addView(iv, ilp);
                } else {
                    TextView badge = new TextView(this);
                    badge.setText("__SETTINGS__".equals(app.pkg) ? "⚙" : app.shortIcon);
                    badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    badge.setTextColor(Color.WHITE);
                    badge.setGravity(Gravity.CENTER);
                    badge.setBackground(roundedBg("__SETTINGS__".equals(app.pkg) ? 0xFF0284C7 : 0x33FFFFFF, 99, 0x44FFFFFF, 1));
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(44), dp(44));
                    blp.rightMargin = dp(12);
                    fallback.addView(badge, blp);
                }

                TextView name = new TextView(this);
                name.setText(app.name);
                name.setTextColor(Color.WHITE);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
                name.setTypeface(null, Typeface.BOLD);
                fallback.addView(name);

                card.addView(fallback, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            final View focusRing = new View(this);
            focusRing.setBackground(roundedBg(0x00000000, 18, 0x1AFFFFFF, 1));
            card.addView(focusRing, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        focusRing.setBackground(roundedBg(0x00000000, 18, 0x66FFFFFF, 1));
                        card.animate()
                            .scaleX(1.10f).scaleY(1.10f)
                            .translationY(-dp(6))
                            .alpha(0.96f)
                            .setDuration(180)
                            .start();
                        card.setElevation(dp(22));
                    } else {
                        focusRing.setBackground(roundedBg(0x00000000, 18, 0x1AFFFFFF, 1));
                        card.animate()
                            .scaleX(1f).scaleY(1f)
                            .translationY(0f)
                            .alpha(0.65f)
                            .setDuration(180)
                            .start();
                        card.setElevation(dp(6));
                    }
                }
            });

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { launchAppEntry(app); }
            });
            card.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            && !prefs.isKidsMode() && !frameMode) {
                        showAllAppsDialog();
                        return true;
                    }
                    return false;
                }
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cardW, cardH);
            clp.leftMargin = dp(11);
            clp.rightMargin = dp(11);
            appDockRow.addView(card, clp);

            if (i == 0) card.requestFocus();
        }
    }

    private void buildSleepGate() {
        sleepGateView = new LinearLayout(this);
        sleepGateView.setOrientation(LinearLayout.VERTICAL);
        sleepGateView.setGravity(Gravity.CENTER);
        sleepGateView.setBackgroundColor(0xF5070B18);
        sleepGateView.setPadding(dp(40), dp(40), dp(40), dp(40));
        sleepGateView.setVisibility(View.GONE);

        TextView emo = new TextView(this);
        emo.setText("📺 😴 🌙");
        emo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        sleepGateView.addView(emo);

        TextView title = new TextView(this);
        title.setText("Bạn Tivi buồn ngủ rồi, đang đi sạc pin nhé!");
        title.setTextColor(0xFFFDE68A);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        sleepGateView.addView(title);

        sleepSubView = new TextView(this);
        sleepSubView.setText("Hôm nay bé đã xem đủ 45 phút rồi. Mình để bạn Tivi nghỉ ngơi nạp năng lượng, Tivi khỏe lại sẽ chơi tiếp cùng bé nha!");
        sleepSubView.setTextColor(0xFFCBD5E1);
        sleepSubView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        sleepSubView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(14);
        slp.bottomMargin = dp(24);
        sleepGateView.addView(sleepSubView, slp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        sleepQuizBtn = new Button(this);
        sleepQuizBtn.setTextColor(0xFF07101C);
        sleepQuizBtn.setBackground(roundedBg(0xFF38BDF8, 14, 0xFFFFFFFF, 2));
        sleepQuizBtn.setPadding(dp(20), dp(12), dp(20), dp(12));
        sleepQuizBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openQuizGate(); }
        });
        btnRow.addView(sleepQuizBtn);

        Button pinBtn = new Button(this);
        pinBtn.setText("🔓 Phụ huynh mở khóa");
        pinBtn.setTextColor(Color.WHITE);
        pinBtn.setBackground(roundedBg(0xFF1E293B, 14, 0x66FFFFFF, 1));
        pinBtn.setPadding(dp(20), dp(12), dp(20), dp(12));
        pinBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPinDialog(); }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(16);
        btnRow.addView(pinBtn, plp);

        sleepGateView.addView(btnRow);
        rootFrame.addView(sleepGateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildQuizGate() {
        quizGateView = new LinearLayout(this);
        quizGateView.setOrientation(LinearLayout.VERTICAL);
        quizGateView.setGravity(Gravity.CENTER);
        quizGateView.setBackgroundColor(0xF20B1224);
        quizGateView.setPadding(dp(48), dp(36), dp(48), dp(36));
        quizGateView.setVisibility(View.GONE);

        quizBadgeView = new TextView(this);
        quizBadgeView.setTextColor(0xFF7DD3FC);
        quizBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        quizBadgeView.setTypeface(null, Typeface.BOLD);
        quizBadgeView.setPadding(dp(16), dp(6), dp(16), dp(6));
        quizBadgeView.setBackground(roundedBg(0x3338BDF8, 99, 0x6638BDF8, 1));
        quizGateView.addView(quizBadgeView);

        quizQuestionView = new TextView(this);
        quizQuestionView.setTextColor(Color.WHITE);
        quizQuestionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        quizQuestionView.setTypeface(null, Typeface.BOLD);
        quizQuestionView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(700), ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = dp(16);
        qlp.bottomMargin = dp(22);
        quizGateView.addView(quizQuestionView, qlp);

        quizOptionsBox = new LinearLayout(this);
        quizOptionsBox.setOrientation(LinearLayout.VERTICAL);
        quizOptionsBox.setGravity(Gravity.CENTER);
        quizGateView.addView(quizOptionsBox, new LinearLayout.LayoutParams(dp(540), ViewGroup.LayoutParams.WRAP_CONTENT));

        rootFrame.addView(quizGateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void applyModeUi() {
        boolean kids = prefs.isKidsMode();
        boolean sleep = prefs.isSleepLocked();

        topChipsRow.setVisibility((frameMode || kids) ? View.GONE : View.VISIBLE);
        dockContainer.setVisibility(frameMode ? View.GONE : View.VISIBLE);
        quoteCenterBox.setVisibility(kids ? View.INVISIBLE : View.VISIBLE);
        clockTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, frameMode ? 72 : 46);

        if (kids) {
            kidsQuotaBar.setVisibility(View.VISIBLE);
            kidsStatusView.setText("⏳ Bé còn: " + prefs.getKidsRemainingMin() + " phút (Đã xem "
                    + prefs.getKidsWatchedMin() + "/" + prefs.getMaxAllowedMin() + "p • Câu đố thêm: "
                    + prefs.getQuizUsedToday() + "/" + AppPrefs.MAX_QUIZ_PER_DAY + ")");
        } else {
            kidsQuotaBar.setVisibility(View.GONE);
        }

        if (sleep) {
            openSleepGate();
        } else {
            sleepGateView.setVisibility(View.GONE);
        }

        renderDock();
    }

    private void toggleFrameMode(boolean on) {
        frameMode = on;
        applyModeUi();
        if (on) toast("Chế độ Khung tranh • Bấm Back để quay lại");
    }

    private void openSleepGate() {
        quizGateView.setVisibility(View.GONE);
        sleepGateView.setVisibility(View.VISIBLE);
        sleepSubView.setText("Hôm nay bé đã xem đủ " + prefs.getMaxAllowedMin()
                + " phút rồi. Mình để bạn Tivi nghỉ ngơi nạp năng lượng, Tivi khỏe lại sẽ chơi tiếp cùng bé nha!");
        int left = AppPrefs.MAX_QUIZ_PER_DAY - prefs.getQuizUsedToday();
        if (left > 0) {
            sleepQuizBtn.setEnabled(true);
            sleepQuizBtn.setAlpha(1f);
            sleepQuizBtn.setText("🧩 Giải 1 câu đố thưởng +5 phút (Còn " + left + " lần)");
            sleepQuizBtn.requestFocus();
        } else {
            sleepQuizBtn.setEnabled(false);
            sleepQuizBtn.setAlpha(0.45f);
            sleepQuizBtn.setText("🧩 Đã dùng hết 2/2 lượt câu đố hôm nay");
        }
    }

    private void openQuizGate() {
        if (!prefs.canTakeQuizBonus()) {
            toast("Hôm nay bé đã dùng hết 2/2 lượt câu đố thưởng rồi!");
            return;
        }
        sleepGateView.setVisibility(View.GONE);
        quizGateView.setVisibility(View.VISIBLE);
        final QuizBank.Question q = QuizBank.randomQuestion();
        currentQuizQuestion = q;
        quizBadgeView.setText("🧩 THỬ THÁCH THÔNG MINH • TRẢ LỜI ĐÚNG THƯỞNG +5 PHÚT (Lần "
                + (prefs.getQuizUsedToday() + 1) + "/" + AppPrefs.MAX_QUIZ_PER_DAY + " hôm nay) • Bấm phím 1-4 hoặc OK");
        quizQuestionView.setText(q.q);
        quizOptionsBox.removeAllViews();

        for (int i = 0; i < q.opts.length; i++) {
            final int idx = i;
            final Button b = new Button(this);
            b.setText("[" + (i + 1) + "]  " + q.opts[i]);
            b.setTextColor(Color.WHITE);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            b.setFocusable(true);
            b.setBackground(roundedBg(0xFF1D2D4D, 16, 0x44FFFFFF, 2));
            b.setPadding(dp(18), dp(14), dp(18), dp(14));
            b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean hasFocus) {
                    b.setBackground(roundedBg(hasFocus ? 0xFF0284C7 : 0xFF1D2D4D, 16,
                            hasFocus ? 0xFFFFFFFF : 0x44FFFFFF, hasFocus ? 3 : 2));
                    b.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(120).start();
                }
            });
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { submitQuizAnswer(idx); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            quizOptionsBox.addView(b, lp);
            if (i == 0) b.requestFocus();
        }
    }

    private void submitQuizAnswer(int idx) {
        if (currentQuizQuestion == null || quizGateView.getVisibility() != View.VISIBLE) return;
        if (idx == currentQuizQuestion.ans) {
            prefs.recordQuizBonus();
            quizGateView.setVisibility(View.GONE);
            currentQuizQuestion = null;
            applyModeUi();
            toast("🎉 Chính xác! Thưởng thêm +5 phút cho bé!");
        } else {
            toast("😅 Chưa đúng rồi, bé thử chọn đáp án khác nhé!");
        }
    }

    private void showPinDialog() {
        final StringBuilder pinBuf = new StringBuilder();
        LinearLayout root = createPremiumSheet("🔐 Nhập mã PIN phụ huynh");
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        final TextView dotsView = new TextView(this);
        dotsView.setText("○   ○   ○   ○");
        dotsView.setTextColor(0xFF38BDF8);
        dotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setPadding(0, dp(4), 0, dp(16));
        root.addView(dotsView);

        final Dialog dlg = createPremiumDialog(root, 360);

        final Runnable updateDots = new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (i > 0) sb.append("   ");
                    sb.append(i < pinBuf.length() ? "●" : "○");
                }
                dotsView.setText(sb.toString());
            }
        };

        final java.util.function.Consumer<String> onKey = new java.util.function.Consumer<String>() {
            @Override public void accept(String k) {
                if ("DEL".equals(k)) {
                    if (pinBuf.length() > 0) pinBuf.deleteCharAt(pinBuf.length() - 1);
                    updateDots.run();
                } else if ("CLOSE".equals(k)) {
                    dlg.dismiss();
                } else if (pinBuf.length() < 4) {
                    pinBuf.append(k);
                    updateDots.run();
                    if (pinBuf.length() == 4) {
                        String code = pinBuf.toString();
                        if (prefs.verifyDynamicPin(code)) {
                            handlePinSubmit(code, dlg);
                        } else {
                            pinBuf.setLength(0);
                            updateDots.run();
                            toast("Mã PIN không chính xác!");
                        }
                    }
                }
            }
        };

        String[][] rows = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"⌫", "0", "Đóng"}
        };

        Button firstBtn = null;
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < 3; c++) {
                final String label = rows[r][c];
                final String action = "⌫".equals(label) ? "DEL" : ("Đóng".equals(label) ? "CLOSE" : label);
                final Button b = new Button(this);
                b.setText(label);
                b.setTextColor(Color.WHITE);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, "Đóng".equals(label) ? 14 : 20);
                b.setTypeface(null, Typeface.BOLD);
                b.setFocusable(true);
                b.setBackground(roundedBg(0xFF1E293B, 16, 0x26FFFFFF, 1));
                b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override public void onFocusChange(View v, boolean hasFocus) {
                        b.setBackground(roundedBg(hasFocus ? 0xFF0284C7 : 0xFF1E293B, 16,
                                hasFocus ? 0xCCFFFFFF : 0x26FFFFFF, 1));
                        b.animate().scaleX(hasFocus ? 1.07f : 1f).scaleY(hasFocus ? 1.07f : 1f).setDuration(120).start();
                    }
                });
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { onKey.accept(action); }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(54));
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(b, lp);
                if (firstBtn == null) firstBtn = b;
            }
            root.addView(row);
        }

        dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    onKey.accept(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                    return true;
                }
                if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                    onKey.accept(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0));
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    onKey.accept("DEL");
                    return true;
                }
                return false;
            }
        });

        dlg.show();
        if (firstBtn != null) firstBtn.requestFocus();
    }

    private void handlePinSubmit(String code, Dialog dlg) {
        if (prefs.verifyDynamicPin(code)) {
            if (dlg != null && dlg.isShowing()) dlg.dismiss();
            prefs.setKidsMode(false);
            prefs.setSleepLocked(false);
            sleepGateView.setVisibility(View.GONE);
            quizGateView.setVisibility(View.GONE);
            applyModeUi();
            toast("Đã mở khóa phụ huynh!");
        } else {
            toast("Mã PIN không chính xác!");
        }
    }

    private void showMusicDialog() {
        LinearLayout sheet = createPremiumSheet("🎵 Nhạc Gia Đình");
        final Dialog dlg = createPremiumDialog(sheet, 560);

        String[] emojis = { "🏃", "🧸", "🇻🇳", "🌙" };
        String[] shortNames = { "Thể dục sáng", "Thiếu nhi", "Nhạc Việt", "Thư giãn" };
        int[] accents = { 0xFF0284C7, 0xFFDB2777, 0xFF059669, 0xFF4F46E5 };

        LinearLayout firstTile = null;
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                final String key = MusicPlayerManager.GENRE_KEYS[idx];
                int cnt = MusicPlayerManager.getTracksForGenre(this, key).size();
                LinearLayout tile = createMusicTile(emojis[idx], shortNames[idx], cnt + " bài đã tải", accents[idx],
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            dlg.dismiss();
                            if (!musicManager.playOrNextGenre(MainActivity.this, key)) {
                                toast("Chưa có bài MP3 trong mục " + shortNames[idx] + "!");
                            } else {
                                toast("🎵 " + musicManager.getCurrentName());
                            }
                        }
                    });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (c == 0) lp.rightMargin = dp(6);
                else lp.leftMargin = dp(6);
                row.addView(tile, lp);
                if (firstTile == null) firstTile = tile;
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(12);
            sheet.addView(row, rlp);
        }

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setClipChildren(false);
        actionRow.setClipToPadding(false);
        LinearLayout ytTile = createMusicTile("▶️", "YouTube Nhạc HOT", "Mở YouTube tìm nhạc mới nhất", 0xFFDC2626,
            new View.OnClickListener() {
                @Override public void onClick(View v) { dlg.dismiss(); openYoutubeHotMusic(); }
            });
        LinearLayout stopTile = createMusicTile("⏹", "Tắt nhạc", musicManager.getCurrentName(), 0xFF334155,
            new View.OnClickListener() {
                @Override public void onClick(View v) { dlg.dismiss(); musicManager.stop(); toast("Đã tắt nhạc"); }
            });
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f);
        a1.rightMargin = dp(6);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a2.leftMargin = dp(6);
        actionRow.addView(ytTile, a1);
        actionRow.addView(stopTile, a2);
        sheet.addView(actionRow);

        dlg.show();
        if (firstTile != null) firstTile.requestFocus();
    }

    private void openYoutubeHotMusic() {
        String ytPkg = "com.google.android.youtube.tv";
        try {
            Intent view = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=nhac+viet+hot+nhat+hien+nay"));
            view.setPackage(ytPkg);
            if (view.resolveActivity(getPackageManager()) != null) {
                startActivity(view);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Intent i = new Intent(Intent.ACTION_SEARCH);
            i.setPackage(ytPkg);
            i.putExtra(SearchManager.QUERY, "nhạc Việt hot nhất hiện nay");
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
        } catch (Exception ignored) {}
        launchPackageByName(ytPkg, "YouTube");
    }

    private void launchPackageByName(String pkg, String label) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
        else toast("Không tìm thấy " + label);
    }

    private LinearLayout makeStatCard(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(roundedBg(0xFF172338, 16, 0x22FFFFFF, 1));

        TextView k = new TextView(this);
        k.setText(label);
        k.setTextColor(0xFF94A3B8);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(k);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(valueColor);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        v.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(4);
        box.addView(v, vlp);
        return box;
    }

    private void showStatsDialog() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long freeMb = mi.availMem / (1024 * 1024);
        long totalMb = mi.totalMem / (1024 * 1024);

        LinearLayout sheet = createPremiumSheet("📊 Thống kê MyFamilyTV");
        final Dialog dlg = createPremiumDialog(sheet, 520);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(8);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(8);
        row1.addView(makeStatCard("RAM trống hiện tại", freeMb + " / " + totalMb + " MB", 0xFF34D399), half);
        row1.addView(makeStatCard("Bé xem hôm nay", prefs.getKidsWatchedMin() + " / " + prefs.getMaxAllowedMin() + " phút", 0xFFFB7185), half2);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1lp.bottomMargin = dp(10);
        sheet.addView(row1, r1lp);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(makeStatCard("Câu đố thưởng (+5p)", prefs.getQuizUsedToday() + " / " + AppPrefs.MAX_QUIZ_PER_DAY + " lần", 0xFFFBBF24), half);
        row2.addView(makeStatCard("Kho Gia đình", photoFiles.size() + " ảnh • " + MusicPlayerManager.countAllTracks(this) + " nhạc", 0xFF38BDF8), half2);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.bottomMargin = dp(16);
        sheet.addView(row2, r2lp);

        TextView closeRow = createMenuRow("Đóng", new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        closeRow.setGravity(Gravity.CENTER);
        sheet.addView(closeRow);

        dlg.show();
        closeRow.requestFocus();
    }

    private void showAllAppsDialog() {
        PackageManager pm = getPackageManager();
        java.util.Map<String, ResolveInfo> unique = new java.util.LinkedHashMap<>();

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(leanback, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!getPackageName().equals(pkg) && !unique.containsKey(pkg)) unique.put(pkg, ri);
        }
        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(normal, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!getPackageName().equals(pkg) && !unique.containsKey(pkg)) unique.put(pkg, ri);
        }

        List<ResolveInfo> apps = new ArrayList<>(unique.values());
        Collections.sort(apps, new java.util.Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });

        LinearLayout sheet = createPremiumSheet("📱 Tất cả ứng dụng (" + apps.size() + ")");
        final Dialog dlg = createPremiumDialog(sheet, 680);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));

        View firstItem = null;
        int cols = 4;
        for (int i = 0; i < apps.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int c = 0; c < cols; c++) {
                int idx = i + c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(dp(6), dp(8), dp(6), dp(8));
                if (idx < apps.size()) {
                    final ResolveInfo ri = apps.get(idx);
                    final String pkg = ri.activityInfo.packageName;
                    final String label = ri.loadLabel(pm).toString();

                    final LinearLayout cell = new LinearLayout(this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(dp(8), dp(10), dp(8), dp(10));
                    cell.setBackgroundColor(Color.TRANSPARENT);
                    cell.setAlpha(0.84f);
                    cell.setFocusable(true);
                    cell.setClickable(true);
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        cell.setDefaultFocusHighlightEnabled(false);
                    }

                    ImageView iv = new ImageView(this);
                    iv.setImageDrawable(ri.loadIcon(pm));
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(56), dp(56));
                    cell.addView(iv, ilp);

                    TextView tv = new TextView(this);
                    tv.setText(label);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tv.setTypeface(null, Typeface.BOLD);
                    tv.setGravity(Gravity.CENTER);
                    tv.setMaxLines(2);
                    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tlp.topMargin = dp(8);
                    cell.addView(tv, tlp);

                    cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        @Override public void onFocusChange(View v, boolean hasFocus) {
                            cell.setBackground(hasFocus ? roundedBg(0x28FFFFFF, 16, 0, 0) : null);
                            cell.animate()
                                .scaleX(hasFocus ? 1.12f : 1f)
                                .scaleY(hasFocus ? 1.12f : 1f)
                                .alpha(hasFocus ? 1f : 0.84f)
                                .setDuration(140)
                                .start();
                        }
                    });
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            dlg.dismiss();
                            launchPackageByName(pkg, label);
                        }
                    });
                    row.addView(cell, lp);
                    if (firstItem == null) firstItem = cell;
                } else {
                    row.addView(new View(this), lp);
                }
            }
            grid.addView(row);
        }

        sv.addView(grid);
        sheet.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));

        dlg.show();
        if (firstItem != null) firstItem.requestFocus();
    }

    private void launchAppEntry(AppEntry app) {
        if ("__SETTINGS__".equals(app.pkg)) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        if ("__KIDS_MUSIC__".equals(app.pkg)) {
            if (musicManager.playOrNextGenre(this, "thieunhi")) {
                toast("🎵 " + musicManager.getCurrentName());
            } else {
                toast("Chưa có bài nhạc Thiếu nhi!");
            }
            return;
        }
        if (prefs.isKidsMode() && prefs.getKidsRemainingMin() <= 0) {
            prefs.setSleepLocked(true);
            openSleepGate();
            return;
        }
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (launch != null) {
            startActivity(launch);
        } else {
            toast("Không tìm thấy ứng dụng: " + app.name);
        }
    }

    private void reloadPhotos() {
        photoFiles.clear();
        File dir = new File(getFilesDir(), "photos");
        if (dir.exists() && dir.listFiles() != null) {
            File[] files = dir.listFiles();
            for (File f : files) {
                if (f.isFile() && f.length() > 100) photoFiles.add(f);
            }
        }
    }

    private void applyWallpaper() {
        if (!photoFiles.isEmpty()) {
            File f = photoFiles.get(wallpaperIdx % photoFiles.size());
            Bitmap bmp = decodeSampledBitmap(f.getAbsolutePath(), 1920, 1080);
            if (bmp != null) {
                float ratio = (float) bmp.getWidth() / Math.max(1, bmp.getHeight());
                // Mọi ảnh ngang (kể cả 4:3 và 16:9) phủ kín đồng nhất toàn màn hình; chỉ ảnh dọc mới căn giữa
                if (ratio >= 1.05f) {
                    wallpaperBgView.setImageDrawable(null);
                    wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    wallpaperBgView.setImageBitmap(bmp);
                    wallpaperView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
                wallpaperView.setAlpha(0.15f);
                wallpaperView.setImageBitmap(bmp);
                wallpaperView.animate().alpha(1f).setDuration(420).start();
                if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
                currentBitmap = bmp;
                return;
            }
        }
        wallpaperBgView.setImageDrawable(null);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int[] colors = fallbackGradients[wallpaperIdx % fallbackGradients.length];
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        wallpaperView.setImageDrawable(gd);
    }

    private Bitmap decodeSampledBitmap(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int inSampleSize = 1;
            int halfH = opts.outHeight / 2;
            int halfW = opts.outWidth / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null) return null;

            int rot = 0;
            try {
                ExifInterface exif = new ExifInterface(path);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Exception ignored) {}

            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (rotated != bmp) bmp.recycle();
                return rotated;
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateClockAndSchedule() {
        Calendar c = Calendar.getInstance();
        String hhmm = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        clockTimeView.setText(hhmm);

        String[] days = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
        String solar = days[c.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + String.format(Locale.US, "%02d/%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
        String lunar = LunarCalendar.getLunarDateString(
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
        clockDateView.setText(solar + " • Âm lịch: " + lunar);

        // Schedule strip + morning alarm check
        List<AppPrefs.ScheduleItem> schedules = prefs.getSchedules();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schedules.size(); i++) {
            AppPrefs.ScheduleItem s = schedules.get(i);
            if (i > 0) sb.append("   •   ");
            sb.append(s.time).append(" ").append(s.title);

            String alarmKey = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + "_" + s.time;
            if (hhmm.equals(s.time) && !alarmKey.equals(prefs.getLastAlarmFired())) {
                prefs.setLastAlarmFired(alarmKey);
                showFamilyBanner("⏰ " + s.time + "\n" + s.title);
                if (s.playMusic) {
                    playMoodMusicForSchedule(s.title);
                }
            }
        }
        scheduleStripView.setText(sb.length() > 0 ? sb.toString() : "Chưa có lịch trình");

        // Timed family notification banners (e.g. 06:30 Thức dậy, 18:00 Ăn tối, 22:00 Đi ngủ)
        String todayTag = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        for (AppPrefs.BannerItem b : prefs.getBannerItems()) {
            if (!b.time.isEmpty() && hhmm.equals(b.time)) {
                String bKey = todayTag + "_" + b.time + "_" + b.msg.hashCode();
                if (!bKey.equals(prefs.getLastBannerFired())) {
                    prefs.setLastBannerFired(bKey);
                    showFamilyBanner(b.time + " • " + b.msg);
                }
            }
        }

        // Auto Night Off (22:30) & Auto Morning Wake (06:30) — human key press overrides!
        if (prefs.isNightScheduleEnabled()) {
            if (hhmm.equals(prefs.getNightOffTime()) && !todayTag.equals(lastNightAutoOffDate)) {
                lastNightAutoOffDate = todayTag;
                enterNightStandby();
            } else if (hhmm.equals(prefs.getMorningOnTime()) && !todayTag.equals(lastMorningAutoWakeDate)) {
                lastMorningAutoWakeDate = todayTag;
                wakeFromNightStandby(true);
            }
        }
    }

    private void enterNightStandby() {
        inNightStandby = true;
        musicManager.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
        nightOffOverlay.setVisibility(View.VISIBLE);
        scheduleNightAlarms();
    }

    private void wakeFromNightStandby(boolean isMorningAuto) {
        inNightStandby = false;
        nightOffOverlay.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        if (isMorningAuto) {
            showFamilyBanner("☀️ " + prefs.getMorningOnTime() + " • Chào buổi sáng!");
        } else {
            toast("Đã bật lại màn hình TV thủ công");
        }
        scheduleNightAlarms();
    }

    private void scheduleNightAlarms() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (!prefs.isNightScheduleEnabled()) return;
            scheduleDailyAlarm(am, prefs.getNightOffTime(), NightScheduleReceiver.ACTION_NIGHT_OFF, 901);
            scheduleDailyAlarm(am, prefs.getMorningOnTime(), NightScheduleReceiver.ACTION_MORNING_WAKE, 902);
        } catch (Exception ignored) {}
    }

    private void scheduleDailyAlarm(AlarmManager am, String hhmm, String action, int reqCode) {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) return;
        int h = Integer.parseInt(parts[0].trim());
        int m = Integer.parseInt(parts[1].trim());
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent i = new Intent(this, NightScheduleReceiver.class);
        i.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(this, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    private void rotateQuote() {
        if (cachedQuotes.isEmpty() || quoteIdx >= cachedQuotes.size()) {
            cachedQuotes = prefs.getQuotes();
            quoteIdx = 0;
        }
        if (!cachedQuotes.isEmpty()) {
            String raw = cachedQuotes.get(quoteIdx % cachedQuotes.size());
            int sep = raw.indexOf('|');
            quoteTextView.setAlpha(0f);
            if (sep > 0) {
                quoteTagView.setText(raw.substring(0, sep));
                quoteTextView.setText(raw.substring(sep + 1));
            } else {
                quoteTagView.setText("💛 NGUYÊN TẮC GIA ĐÌNH");
                quoteTextView.setText(raw);
            }
            quoteTextView.animate().alpha(1f).setDuration(420).start();
            quoteIdx++;
        }
    }

    private void refreshWeather() {
        WeatherService.fetch(prefs.getCityName(), prefs.getCityLat(), prefs.getCityLon(),
            new WeatherService.Callback() {
                @Override public void onWeather(final String formatted) {
                    handler.post(new Runnable() {
                        @Override public void run() { weatherView.setText(formatted); }
                    });
                }
            });
    }

    private void playMoodMusicForSchedule(String title) {
        String lower = title == null ? "" : title.toLowerCase();
        String genre = "nhacviet";
        if (lower.contains("ngủ") || lower.contains("vệ sinh")) {
            genre = "thugian";
        } else if (lower.contains("thể dục") || lower.contains("đi học")) {
            genre = "theduc";
        } else if (lower.contains("thiếu nhi") || lower.contains("bé")) {
            genre = "thieunhi";
        }
        if (!musicManager.playGenre(this, genre, 0)) {
            // Fallback to any uploaded genre that has tracks
            for (String g : MusicPlayerManager.GENRE_KEYS) {
                if (musicManager.playGenre(this, g, 0)) return;
            }
        }
    }

    private void playNotificationChime() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (r != null) {
                r.play();
                return;
            }
        } catch (Exception ignored) {}
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 450);
        } catch (Exception ignored) {}
    }

    private void showFamilyBanner(final String msg) {
        handler.post(new Runnable() {
            @Override public void run() {
                playNotificationChime();
                familyBannerView.setText("📢  " + msg);
                familyBannerView.setAlpha(0f);
                familyBannerView.setScaleX(0.9f);
                familyBannerView.setScaleY(0.9f);
                familyBannerView.setVisibility(View.VISIBLE);
                familyBannerView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
                handler.postDelayed(new Runnable() {
                    @Override public void run() { familyBannerView.setVisibility(View.GONE); }
                }, 10000);
            }
        });
    }

    private void startLoops() {
        // Every 15 seconds: clock, schedule alarm, slideshow, quote
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                updateClockAndSchedule();
                wallpaperIdx++;
                applyWallpaper();
                rotateQuote();
                handler.postDelayed(this, 15000);
            }
        }, 15000);

        // Every 60 seconds: kids timer & sleep timer
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (prefs.isKidsMode() && !prefs.isSleepLocked()) {
                    prefs.addKidsWatchedMin(1);
                    int remain = prefs.getKidsRemainingMin();
                    if (remain == 5) {
                        showFamilyBanner("⏰ Còn 5 phút nữa bạn Tivi đi ngủ nhé!");
                    } else if (remain <= 0) {
                        prefs.setSleepLocked(true);
                        musicManager.stop();
                    }
                    applyModeUi();
                }
                if (sleepTimerRemainMin > 0) {
                    sleepTimerRemainMin--;
                    if (sleepTimerRemainMin == 0) {
                        musicManager.stop();
                        toggleFrameMode(true);
                        showFamilyBanner("🌙 Đã đến giờ nghỉ ngơi, nhạc đã tự tắt!");
                    }
                }
                handler.postDelayed(this, 60000);
            }
        }, 60000);

        // Every 30 minutes: refresh weather
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                refreshWeather();
                handler.postDelayed(this, 30 * 60 * 1000);
            }
        }, 30 * 60 * 1000);
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> addrs = intf.getInetAddresses(); addrs.hasMoreElements(); ) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1.152";
    }

    private void toast(final String msg) {
        handler.post(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (inNightStandby) {
            wakeFromNightStandby(false);
            return true;
        }
        if (quizGateView.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_1 || keyCode == KeyEvent.KEYCODE_NUMPAD_1) { submitQuizAnswer(0); return true; }
            if (keyCode == KeyEvent.KEYCODE_2 || keyCode == KeyEvent.KEYCODE_NUMPAD_2) { submitQuizAnswer(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_3 || keyCode == KeyEvent.KEYCODE_NUMPAD_3) { submitQuizAnswer(2); return true; }
            if (keyCode == KeyEvent.KEYCODE_4 || keyCode == KeyEvent.KEYCODE_NUMPAD_4) { submitQuizAnswer(3); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (frameMode) {
                toggleFrameMode(false);
                return true;
            }
            if (quizGateView.getVisibility() == View.VISIBLE) {
                if (prefs.isSleepLocked()) openSleepGate();
                else quizGateView.setVisibility(View.GONE);
                return true;
            }
            if (sleepGateView.getVisibility() == View.VISIBLE) {
                showPinDialog();
                return true;
            }
            if (prefs.isKidsMode()) {
                showPinDialog();
                return true;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // === ControlBridge from Phone Web Server ===
    @Override public void onToggleKidsRemote() {
        handler.post(new Runnable() {
            @Override public void run() {
                boolean next = !prefs.isKidsMode();
                prefs.setKidsMode(next);
                if (!next) prefs.setSleepLocked(false);
                applyModeUi();
                toast(next ? "📱 Bố mẹ đã BẬT Chế độ Trẻ em" : "📱 Bố mẹ đã MỞ KHÓA Chế độ Trẻ em");
            }
        });
    }
    @Override public void onTriggerSleepGate() {
        handler.post(new Runnable() {
            @Override public void run() {
                prefs.setSleepLocked(true);
                musicManager.stop();
                openSleepGate();
            }
        });
    }
    @Override public void onTriggerQuizGate() {
        handler.post(new Runnable() {
            @Override public void run() { openQuizGate(); }
        });
    }
    @Override public void onSendFamilyBanner(String msg) { showFamilyBanner(msg); }
    @Override public void onPhotosUpdated() {
        handler.post(new Runnable() {
            @Override public void run() {
                reloadPhotos();
                wallpaperIdx = Math.max(0, photoFiles.size() - 1);
                applyWallpaper();
                toast("🖼️ Đã cập nhật ảnh gia đình mới!");
            }
        });
    }
    @Override public void onMusicUploaded() {
        handler.post(new Runnable() {
            @Override public void run() {
                int n = MusicPlayerManager.countAllTracks(MainActivity.this);
                toast("🎵 Đã cập nhật kho nhạc (" + n + " bài MP3)");
            }
        });
    }
    @Override public void onPlayGenreMusic(final String genre) {
        handler.post(new Runnable() {
            @Override public void run() {
                if (!musicManager.playOrNextGenre(MainActivity.this, genre)) {
                    toast("Thể loại này chưa có bài MP3 nào!");
                } else {
                    toast("🎵 " + musicManager.getCurrentName());
                }
            }
        });
    }
    @Override public void onOpenMusicApp(final String target) {
        handler.post(new Runnable() {
            @Override public void run() {
                if ("youtube".equals(target)) openYoutubeHotMusic();
            }
        });
    }
    @Override public void onScheduleUpdated() {
        handler.post(new Runnable() {
            @Override public void run() { updateClockAndSchedule(); toast("📅 Đã cập nhật lịch trình!"); }
        });
    }
    @Override public void onQuotesUpdated() {
        handler.post(new Runnable() {
            @Override public void run() {
                cachedQuotes = prefs.getQuotes();
                quoteIdx = 0;
                rotateQuote();
                toast("✨ Đã thêm câu ca dao/nguyên tắc mới!");
            }
        });
    }
    @Override public void onMusicToggle() {
        handler.post(new Runnable() {
            @Override public void run() { musicManager.stop(); toast("⏹ Đã tắt nhạc"); }
        });
    }
    @Override public void onSleepTimer(final int minutes) {
        handler.post(new Runnable() {
            @Override public void run() {
                sleepTimerRemainMin = minutes;
                toast(minutes > 0 ? ("⏰ Hẹn tắt sau " + minutes + " phút") : "Đã hủy hẹn giờ");
            }
        });
    }
    @Override public void onGrantMinutes(final int minutes) {
        handler.post(new Runnable() {
            @Override public void run() {
                prefs.parentGrantMinutes(minutes);
                applyModeUi();
                toast("🎁 Bố mẹ đã thưởng thêm +" + minutes + " phút!");
            }
        });
    }
    @Override public void onKidsLimitChanged(final int minutes) {
        handler.post(new Runnable() {
            @Override public void run() {
                applyModeUi();
                toast("⏳ Đã đổi giới hạn xem của bé thành " + minutes + " phút/ngày");
            }
        });
    }
    @Override public void onNightScheduleUpdated() {
        handler.post(new Runnable() {
            @Override public void run() {
                scheduleNightAlarms();
                toast("🌙 Đã cập nhật lịch tự tắt " + prefs.getNightOffTime() + " → bật " + prefs.getMorningOnTime());
            }
        });
    }
    @Override public String getMusicStatus() {
        return musicManager.isPlaying() ? musicManager.getCurrentName() : "Đang tắt";
    }
}
