package vn.family.cleanlauncher;

import android.app.ActivityManager;
import android.content.Context;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded HTTP server on port 8080 for parents' phone control (multi-photo, multi-music, multi-quotes).
 */
public class FamilyWebServer {
    public interface ControlBridge {
        void onToggleKidsRemote();
        void onTriggerSleepGate();
        void onTriggerQuizGate();
        void onSendFamilyBanner(String msg);
        void onPhotosUpdated();
        void onMusicUploaded();
        void onPlayGenreMusic(String genre);
        void onOpenMusicApp(String target);
        void onScheduleUpdated();
        void onQuotesUpdated();
        void onMusicToggle();
        void onSleepTimer(int minutes);
        void onGrantMinutes(int minutes);
        void onKidsLimitChanged(int minutes);
        void onNightScheduleUpdated();
        void onRemoteAppsLockToggle(boolean unlock);
        boolean isAppsUnlocked();
        String getMusicStatus();
    }

    private final Context ctx;
    private final AppPrefs prefs;
    private final ControlBridge bridge;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public FamilyWebServer(Context ctx, AppPrefs prefs, ControlBridge bridge) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.bridge = bridge;
    }

    public void start() {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(8080);
                    while (running) {
                        final Socket client = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() { handleClient(client); }
                        }).start();
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(30000);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) { socket.close(); return; }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String fullPath = parts.length > 1 ? parts[1] : "/";

            Map<String, String> headers = new HashMap<>();
            String hLine;
            while ((hLine = readLine(in)) != null && !hLine.isEmpty()) {
                int idx = hLine.indexOf(':');
                if (idx > 0) {
                    headers.put(hLine.substring(0, idx).trim().toLowerCase(),
                            hLine.substring(idx + 1).trim());
                }
            }

            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                try { contentLength = Integer.parseInt(headers.get("content-length")); } catch (Exception ignored) {}
            }

            String path = fullPath;
            String query = "";
            int qIdx = fullPath.indexOf('?');
            if (qIdx >= 0) {
                path = fullPath.substring(0, qIdx);
                query = fullPath.substring(qIdx + 1);
            }
            Map<String, String> params = parseQuery(query);

            if (method.equals("POST") && path.equals("/api/upload-photo")) {
                byte[] data = readExactBytes(in, Math.min(contentLength, 10 * 1024 * 1024));
                File dir = new File(ctx.getFilesDir(), "photos");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "fam_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                bridge.onPhotosUpdated();
                sendJson(out, "{\"ok\":true}");
                socket.close();
                return;
            }

            if (method.equals("POST") && path.equals("/api/upload-music")) {
                byte[] data = readExactBytes(in, Math.min(contentLength, 25 * 1024 * 1024));
                String genre = params.get("genre");
                File dir = MusicPlayerManager.getGenreDir(ctx, genre);
                String rawName = params.get("name");
                if (rawName == null || rawName.trim().isEmpty()) rawName = "track.mp3";
                rawName = rawName.replaceAll("[^a-zA-Z0-9._\\-\\p{L} ]", "_");
                File f = new File(dir, System.currentTimeMillis() + "_" + rawName);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                bridge.onMusicUploaded();
                sendJson(out, "{\"ok\":true}");
                socket.close();
                return;
            }

            if (method.equals("POST") && contentLength > 0 && contentLength < 131072) {
                byte[] body = readExactBytes(in, contentLength);
                params.putAll(parseQuery(new String(body, StandardCharsets.UTF_8)));
            }

            if (path.equals("/api/toggle-kids")) {
                bridge.onToggleKidsRemote();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/sleep-gate")) {
                bridge.onTriggerSleepGate();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/quiz-gate")) {
                bridge.onTriggerQuizGate();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/grant")) {
                bridge.onGrantMinutes(parseInt(params.get("min"), 10));
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/kids-limit")) {
                int lim = parseInt(params.get("min"), 45);
                prefs.setBaseMaxKidsMinutes(lim);
                bridge.onKidsLimitChanged(lim);
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/banner")) {
                String msg = params.get("msg");
                if (msg != null && !msg.trim().isEmpty()) bridge.onSendFamilyBanner(msg.trim());
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/banner/save")) {
                int idx = parseInt(params.get("idx"), -1);
                String time = params.get("time");
                String msg = params.get("msg");
                if (msg != null && !msg.trim().isEmpty()) {
                    List<AppPrefs.BannerItem> bs = prefs.getBannerItems();
                    AppPrefs.BannerItem item = new AppPrefs.BannerItem(time == null ? "" : time.trim(), msg.trim());
                    if (idx >= 0 && idx < bs.size()) bs.set(idx, item);
                    else bs.add(item);
                    prefs.saveBannerItems(bs);
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/banner/del")) {
                int idx = parseInt(params.get("idx"), -1);
                List<AppPrefs.BannerItem> bs = prefs.getBannerItems();
                if (idx >= 0 && idx < bs.size()) {
                    bs.remove(idx);
                    prefs.saveBannerItems(bs);
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/music")) {
                String g = params.get("genre");
                String app = params.get("app");
                if (g != null && !g.isEmpty()) bridge.onPlayGenreMusic(g);
                else if (app != null && !app.isEmpty()) bridge.onOpenMusicApp(app);
                else bridge.onMusicToggle();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/music/clear")) {
                String g = params.get("genre");
                if (g != null && MusicPlayerManager.isValidGenre(g)) {
                    File dir = MusicPlayerManager.getGenreDir(ctx, g);
                    if (dir.listFiles() != null) for (File f : dir.listFiles()) f.delete();
                } else {
                    for (String k : MusicPlayerManager.GENRE_KEYS) {
                        File dir = MusicPlayerManager.getGenreDir(ctx, k);
                        if (dir.listFiles() != null) for (File f : dir.listFiles()) f.delete();
                    }
                }
                bridge.onMusicUploaded();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/timer")) {
                bridge.onSleepTimer(parseInt(params.get("min"), 0));
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/night-schedule")) {
                String en = params.get("en");
                String s1a = params.get("s1a"), s1b = params.get("s1b");
                String s2a = params.get("s2a"), s2b = params.get("s2b");
                if (en != null) prefs.setNightScheduleEnabled("1".equals(en));
                if (s1a != null && s1b != null && s2a != null && s2b != null
                        && s1a.contains(":") && s1b.contains(":") && s2a.contains(":") && s2b.contains(":")) {
                    prefs.setActiveSlots(s1a.trim(), s1b.trim(), s2a.trim(), s2b.trim());
                }
                bridge.onNightScheduleUpdated();
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/app-lock")) {
                String unlock = params.get("unlock");
                bridge.onRemoteAppsLockToggle("1".equals(unlock));
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/schedule/add")) {
                int idx = parseInt(params.get("idx"), -1);
                String time = params.get("time");
                String title = params.get("title");
                boolean play = "1".equals(params.get("music"));
                if (time != null && title != null && !time.trim().isEmpty() && !title.trim().isEmpty()) {
                    List<AppPrefs.ScheduleItem> list = prefs.getSchedules();
                    AppPrefs.ScheduleItem item = new AppPrefs.ScheduleItem(time.trim(), title.trim(), play);
                    if (idx >= 0 && idx < list.size()) list.set(idx, item);
                    else list.add(item);
                    prefs.saveSchedules(list);
                    bridge.onScheduleUpdated();
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/schedule/move")) {
                int from = parseInt(params.get("from"), -1);
                int to = parseInt(params.get("to"), -1);
                List<AppPrefs.ScheduleItem> list = prefs.getSchedules();
                if (from >= 0 && from < list.size() && to >= 0 && to < list.size() && from != to) {
                    AppPrefs.ScheduleItem item = list.remove(from);
                    list.add(to, item);
                    prefs.saveSchedules(list);
                    bridge.onScheduleUpdated();
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/schedule/del")) {
                int idx = parseInt(params.get("idx"), -1);
                List<AppPrefs.ScheduleItem> list = prefs.getSchedules();
                if (idx >= 0 && idx < list.size()) {
                    list.remove(idx);
                    prefs.saveSchedules(list);
                    bridge.onScheduleUpdated();
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/quote/add-bulk")) {
                String tag = params.get("tag");
                if (tag == null || tag.trim().isEmpty()) tag = "💛 NGUYÊN TẮC GIA ĐÌNH";
                String raw = params.get("lines");
                if (raw != null && !raw.trim().isEmpty()) {
                    List<String> added = new java.util.ArrayList<>();
                    for (String line : raw.split("\\r?\\n")) {
                        String t = line.trim();
                        if (!t.isEmpty()) {
                            if (!t.startsWith("“")) t = "“" + t + "”";
                            added.add(tag.trim() + "|" + t);
                        }
                    }
                    prefs.addCustomQuotes(added);
                    bridge.onQuotesUpdated();
                }
                sendJson(out, "{\"ok\":true}");
            } else if (path.equals("/api/photos/clear")) {
                File dir = new File(ctx.getFilesDir(), "photos");
                if (dir.exists() && dir.listFiles() != null) {
                    for (File f : dir.listFiles()) f.delete();
                }
                bridge.onPhotosUpdated();
                sendJson(out, "{\"ok\":true}");
            } else {
                sendHtml(out, buildDashboardHtml());
            }
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private String buildDashboardHtml() {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long freeMb = mi.availMem / (1024 * 1024);
        long totalMb = mi.totalMem / (1024 * 1024);

        File photoDir = new File(ctx.getFilesDir(), "photos");
        int photoCount = (photoDir.exists() && photoDir.listFiles() != null) ? photoDir.listFiles().length : 0;
        int musicCount = MusicPlayerManager.countAllTracks(ctx);

        StringBuilder genreRowsHtml = new StringBuilder();
        for (int i = 0; i < MusicPlayerManager.GENRE_KEYS.length; i++) {
            String k = MusicPlayerManager.GENRE_KEYS[i];
            String lbl = MusicPlayerManager.GENRE_LABELS[i];
            int cnt = MusicPlayerManager.getTracksForGenre(ctx, k).size();
            genreRowsHtml.append("<div class='row'><span>").append(esc(lbl)).append(" <b>(").append(cnt).append(" bài)</b></span>")
                    .append("<span style='display:flex;gap:4px'>")
                    .append("<button class='sm' onclick='api(\"/api/music?genre=").append(k).append("\")'>▶ Phát</button>")
                    .append("<button class='sm' onclick='api(\"/api/music/clear?genre=").append(k).append("\")'>Xóa</button></span></div>");
        }

        StringBuilder bannerHtml = new StringBuilder();
        List<AppPrefs.BannerItem> banners = prefs.getBannerItems();
        for (int i = 0; i < banners.size(); i++) {
            AppPrefs.BannerItem b = banners.get(i);
            String tEsc = b.time.replace("'", "\\'");
            String jsEsc = b.msg.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "&quot;");
            String timeTag = b.time.isEmpty() ? "" : ("<b style='color:#fbbf24;margin-right:6px'>" + esc(b.time) + "</b>");
            bannerHtml.append("<div class='row'><span style='flex:1;cursor:pointer' onclick=\"sendBanner('").append(jsEsc).append("')\">")
                    .append(timeTag).append(esc(b.msg)).append("</span><span style='display:flex;gap:4px'>")
                    .append("<button class='sm' onclick=\"sendBanner('").append(jsEsc).append("')\">Gửi</button>")
                    .append("<button class='sm' onclick=\"editBanner(").append(i).append(",'").append(tEsc).append("','").append(jsEsc).append("')\">Sửa</button>")
                    .append("<button class='sm' onclick='api(\"/api/banner/del?idx=").append(i).append("\")'>Xóa</button></span></div>");
        }

        StringBuilder schedHtml = new StringBuilder();
        List<AppPrefs.ScheduleItem> schedules = prefs.getSchedules();
        for (int i = 0; i < schedules.size(); i++) {
            AppPrefs.ScheduleItem s = schedules.get(i);
            String tEsc = s.time.replace("'", "\\'");
            String tiEsc = s.title.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "&quot;");
            schedHtml.append("<div class='row' draggable='true' data-idx='").append(i)
                    .append("' ondragstart='dStart(").append(i).append(")' ondragover='event.preventDefault()' ondrop='dDrop(").append(i).append(")' style='cursor:grab'>")
                    .append("<span><span style='opacity:0.5;margin-right:6px'>☰</span><b>").append(esc(s.time)).append("</b> ")
                    .append(esc(s.title)).append(s.playMusic ? " 🎵" : "")
                    .append("</span><span style='display:flex;gap:4px'>")
                    .append(i > 0 ? ("<button class='sm' onclick='moveSched(" + i + "," + (i - 1) + ")'>▲</button>") : "")
                    .append(i < schedules.size() - 1 ? ("<button class='sm' onclick='moveSched(" + i + "," + (i + 1) + ")'>▼</button>") : "")
                    .append("<button class='sm' onclick=\"editSched(").append(i).append(",'").append(tEsc).append("','").append(tiEsc).append("',").append(s.playMusic ? "true" : "false").append(")\">Sửa</button>")
                    .append("<button class='sm' onclick='api(\"/api/schedule/del?idx=").append(i).append("\")'>Xóa</button></span></div>");
        }

        return "<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>MyFamilyTV — Điều khiển</title>"
            + "<style>body{font-family:system-ui,sans-serif;background:#0b1120;color:#f8fafc;margin:0;padding:16px;max-width:540px;margin:auto}"
            + "h2{font-size:20px;margin:0 0 12px}.card{background:#162033;border:1px solid #26354d;border-radius:16px;padding:14px;margin-bottom:12px}"
            + ".grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}button{background:#38bdf8;color:#06101c;border:none;border-radius:10px;padding:11px;font-weight:700;font-size:13px;cursor:pointer}"
            + "button.warn{background:#f59e0b}button.pink{background:#fb7185;color:#fff}button.sm{background:#293954;color:#fff;padding:7px 10px;font-size:12px}"
            + "input,textarea,select{width:100%;box-sizing:border-box;background:#0f172a;border:1px solid #334155;color:#fff;padding:10px;border-radius:10px;margin:6px 0;font-family:inherit}"
            + ".row{display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid #223047;font-size:14px}"
            + ".muted{color:#94a3b8;font-size:12px}</style></head><body>"
            + "<h2>📺 MyFamilyTV — Quản lý Gia Đình</h2>"
            + "<div class='card'><div class='muted'>RAM trống: <b>" + freeMb + " / " + totalMb + " MB</b> • Trẻ em: <b>"
            + (prefs.isKidsMode() ? "ĐANG BẬT" : "TẮT") + "</b> • Bé xem: <b>" + prefs.getKidsWatchedMin() + "/" + prefs.getMaxAllowedMin()
            + "p</b> • Câu đố: <b>" + prefs.getQuizUsedToday() + "/2</b></div></div>"
            + "<div class='card'><b>🧸 Kiểm soát Trẻ em (Không cần PIN)</b><div class='grid' style='margin-top:8px'>"
            + "<button class='pink' onclick='api(\"/api/toggle-kids\")'>Bật / Tắt Trẻ em</button>"
            + "<button class='warn' onclick='api(\"/api/sleep-gate\")'>😴 Cho TV đi ngủ</button>"
            + "<button onclick='api(\"/api/quiz-gate\")'>🧩 Bắn Câu đố</button>"
            + "<button onclick='api(\"/api/grant?min=5\")'>+5 phút cho bé</button>"
            + "</div><div class='muted' style='margin-top:10px'>Giới hạn xem/ngày hiện tại: <b>" + prefs.getBaseMaxKidsMinutes() + " phút</b></div>"
            + "<div class='grid' style='margin-top:6px'>"
            + "<button class='sm' onclick='api(\"/api/kids-limit?min=20\")'>20 phút</button>"
            + "<button class='sm' onclick='api(\"/api/kids-limit?min=30\")'>30 phút</button>"
            + "<button class='sm' onclick='api(\"/api/kids-limit?min=45\")'>45 phút</button>"
            + "<button class='sm' onclick='api(\"/api/kids-limit?min=60\")'>60 phút</button>"
            + "</div><div style='display:flex;gap:8px;margin-top:6px'><input id='kLim' type='number' placeholder='Hoặc nhập số phút...'>"
            + "<button onclick='if(kLim.value)api(\"/api/kids-limit?min=\"+kLim.value)'>Lưu</button></div></div>"
            + "<div class='card'><b>📢 Thông báo tự hiện theo giờ & Gửi nhanh lên TV</b>" + bannerHtml
            + "<input type='hidden' id='bIdx' value='-1'>"
            + "<div style='display:flex;gap:8px'><input id='bTime' placeholder='Giờ (VD: 18:00)' style='max-width:125px'>"
            + "<input id='bMsg' placeholder='Nội dung thông báo (VD: Ăn tối)...'></div>"
            + "<div class='grid'><button onclick='sendBanner(bMsg.value)'>📢 Gửi ngay lên TV</button>"
            + "<button class='sm' id='bSaveBtn' onclick='saveBanner()'>💾 Lưu hẹn giờ thông báo</button></div></div>"
            + "<div class='card'><b>🖼️ Gửi NHIỀU ảnh gia đình lên TV (Đang có " + photoCount + " ảnh)</b>"
            + "<input type='file' id='photoFiles' accept='image/*' multiple>"
            + "<div id='photoProg' class='muted'></div>"
            + "<div class='grid'><button onclick='uploadPhotos()'>Tải tất cả ảnh</button>"
            + "<button class='sm' onclick='api(\"/api/photos/clear\")'>Xóa hết ảnh</button></div></div>"
            + "<div class='card'><b>🎵 Quản lý Nhạc theo Thể loại (Tổng " + musicCount + " bài đã tải)</b>"
            + genreRowsHtml
            + "<select id='mGenre' style='margin-top:8px'>"
            + "<option value='theduc'>🏃 Thể dục sáng</option>"
            + "<option value='thieunhi'>🧸 Thiếu nhi</option>"
            + "<option value='nhacviet' selected>🇻🇳 Nhạc Việt / Gia đình</option>"
            + "<option value='thugian'>🌙 Thư giãn / Ru ngủ</option></select>"
            + "<input type='file' id='musicFiles' multiple>"
            + "<div id='musicProg' class='muted'></div>"
            + "<div class='grid'><button onclick='uploadMusic()'>Tải nhạc vào thể loại</button>"
            + "<button class='sm' onclick='api(\"/api/music\")'>⏹ Tắt nhạc đang phát</button></div>"
            + "<button class='warn' style='width:100%;margin-top:8px' onclick='api(\"/api/music?app=youtube\")'>▶️ Mở YouTube Nhạc HOT</button></div>"
            + "<div class='card'><b>✨ Thêm NHIỀU câu Ca dao / Nguyên tắc sống (mỗi dòng 1 câu)</b>"
            + "<select id='qTag'>"
            + "<option value='✨ CA DAO VIỆT NAM'>✨ CA DAO VIỆT NAM</option>"
            + "<option value='💛 NGUYÊN TẮC GIA ĐÌNH'>💛 NGUYÊN TẮC GIA ĐÌNH</option>"
            + "<option value='🌱 NGUYÊN TẮC SỐNG'>🌱 NGUYÊN TẮC SỐNG</option></select>"
            + "<textarea id='qLines' rows='4' placeholder='Mỗi dòng 1 câu ca dao hoặc nguyên tắc...'></textarea>"
            + "<button style='width:100%' onclick='addQuotesBulk()'>Lưu tất cả lên TV</button></div>"
            + "<div class='card'><b>🔐 Khóa mở App chống trẻ tự xem</b>"
            + "<div class='muted'>Trạng thái mở App hiện tại: <b>" + (bridge.isAppsUnlocked() ? "ĐÃ MỞ KHÓA" : "ĐANG KHÓA (cần PIN)") + "</b></div>"
            + "<div class='grid' style='margin-top:8px'>"
            + "<button onclick='api(\"/api/app-lock?unlock=1\")'>🔓 Mở khóa mở App</button>"
            + "<button class='pink' onclick='api(\"/api/app-lock?unlock=0\")'>🔒 Khóa lại ngay</button></div></div>"
            + "<div class='card'><b>🌙 Khung giờ TV tự động bật màn hình</b>"
            + "<div class='muted'>Trạng thái: <b>" + (prefs.isNightScheduleEnabled() ? "ĐANG BẬT" : "ĐANG TẮT")
            + "</b> • Sáng: <b>" + esc(prefs.getSlot1Start()) + "–" + esc(prefs.getSlot1End())
            + "</b> • Tối: <b>" + esc(prefs.getSlot2Start()) + "–" + esc(prefs.getSlot2End())
            + "</b> (Ngoài giờ tự tắt, bấm remote để tự bật)</div>"
            + "<div style='display:flex;gap:6px;margin-top:6px'><input id='s1a' value='" + esc(prefs.getSlot1Start()) + "' placeholder='06:30'>"
            + "<input id='s1b' value='" + esc(prefs.getSlot1End()) + "' placeholder='07:30'></div>"
            + "<div style='display:flex;gap:6px'><input id='s2a' value='" + esc(prefs.getSlot2Start()) + "' placeholder='18:00'>"
            + "<input id='s2b' value='" + esc(prefs.getSlot2End()) + "' placeholder='22:00'></div>"
            + "<div class='grid'><button onclick='api(\"/api/night-schedule?en=1&s1a=\"+encodeURIComponent(s1a.value)+\"&s1b=\"+encodeURIComponent(s1b.value)+\"&s2a=\"+encodeURIComponent(s2a.value)+\"&s2b=\"+encodeURIComponent(s2b.value))'>💾 Lưu & Bật</button>"
            + "<button class='sm' onclick='api(\"/api/night-schedule?en=" + (prefs.isNightScheduleEnabled() ? "0" : "1") + "\")'>"
            + (prefs.isNightScheduleEnabled() ? "Tắt tự động" : "Bật tự động") + "</button></div></div>"
            + "<div class='card'><b>📅 Lịch trình hàng ngày (Kéo thả ☰ hoặc bấm ▲▼ để đổi thứ tự)</b>" + schedHtml
            + "<input type='hidden' id='sIdx' value='-1'>"
            + "<input id='sTime' placeholder='Giờ (VD: 06:15)'><input id='sTitle' placeholder='Nội dung (VD: 🏃 Tập thể dục)'>"
            + "<label class='muted'><input type='checkbox' id='sMusic' style='width:auto'> Tự bật nhạc khi đến giờ</label>"
            + "<button style='width:100%;margin-top:8px' id='sSaveBtn' onclick='addSched()'>+ Thêm / Lưu lịch trình</button></div>"
            + "<script>"
            + "function api(u){fetch(u).then(()=>location.reload());}"
            + "function sendBanner(m){if(!m)return;fetch('/api/banner?msg='+encodeURIComponent(m)).then(()=>alert('Đã hiện trên TV!'));}"
            + "function editBanner(i,t,m){bIdx.value=i;bTime.value=t;bMsg.value=m;bSaveBtn.textContent='💾 Cập nhật thông báo';bMsg.focus();}"
            + "function saveBanner(){if(!bMsg.value.trim())return;api('/api/banner/save?idx='+bIdx.value+'&time='+encodeURIComponent(bTime.value.trim())+'&msg='+encodeURIComponent(bMsg.value.trim()));}"
            + "var dragFrom=-1;function dStart(i){dragFrom=i;}function dDrop(to){if(dragFrom>=0&&dragFrom!==to)moveSched(dragFrom,to);dragFrom=-1;}"
            + "function moveSched(f,t){api('/api/schedule/move?from='+f+'&to='+t);}"
            + "function editSched(i,t,ti,m){sIdx.value=i;sTime.value=t;sTitle.value=ti;sMusic.checked=m;sSaveBtn.textContent='💾 Cập nhật mốc lịch trình';sTime.focus();}"
            + "function addSched(){var t=sTime.value,ti=sTitle.value,m=sMusic.checked?'1':'0';if(!t||!ti)return;api('/api/schedule/add?idx='+sIdx.value+'&time='+encodeURIComponent(t)+'&title='+encodeURIComponent(ti)+'&music='+m);}"
            + "async function addQuotesBulk(){if(!qLines.value.trim())return;await fetch('/api/quote/add-bulk',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'tag='+encodeURIComponent(qTag.value)+'&lines='+encodeURIComponent(qLines.value)});alert('Đã lưu các câu mới lên TV!');location.reload();}"
            + "async function uploadPhotos(){var fs=photoFiles.files;if(!fs.length){alert('Chọn ít nhất 1 ảnh!');return;}for(var i=0;i<fs.length;i++){photoProg.textContent='Đang tải ảnh '+(i+1)+'/'+fs.length+'...';await fetch('/api/upload-photo',{method:'POST',body:fs[i]});}alert('Đã tải xong '+fs.length+' ảnh!');location.reload();}"
            + "async function uploadMusic(){var fs=musicFiles.files;if(!fs.length){alert('Chọn ít nhất 1 file nhạc MP3!');return;}var g=mGenre.value;for(var i=0;i<fs.length;i++){musicProg.textContent='Đang tải nhạc '+(i+1)+'/'+fs.length+': '+fs[i].name;await fetch('/api/upload-music?genre='+encodeURIComponent(g)+'&name='+encodeURIComponent(fs[i].name),{method:'POST',body:fs[i]});}alert('Đã tải xong '+fs.length+' bài nhạc!');location.reload();}"
            + "</script></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') bos.write(b);
        }
        if (b == -1 && bos.size() == 0) return null;
        return bos.toString("UTF-8");
    }

    private static byte[] readExactBytes(InputStream in, int len) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int remaining = len;
        while (remaining > 0) {
            int n = in.read(buf, 0, Math.min(buf.length, remaining));
            if (n <= 0) break;
            bos.write(buf, 0, n);
            remaining -= n;
        }
        return bos.toByteArray();
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null || q.isEmpty()) return map;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(k, v);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static void sendHtml(OutputStream out, String html) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private static void sendJson(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }
}
