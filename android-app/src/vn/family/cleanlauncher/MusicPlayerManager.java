package vn.family.cleanlauncher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MusicPlayerManager {
    public static final String[] GENRE_KEYS = { "theduc", "thieunhi", "nhacviet", "thugian" };
    public static final String[] GENRE_LABELS = {
        "🏃 Nhạc Thể dục sáng",
        "🧸 Nhạc Thiếu nhi",
        "🇻🇳 Nhạc Việt / Gia đình",
        "🌙 Nhạc Thư giãn / Ru ngủ"
    };

    private MediaPlayer player;
    private int localIndex = 0;
    private boolean playing = false;
    private String currentLabel = "Đang tắt";
    private String currentGenre = "nhacviet";
    private List<File> currentTracks = new ArrayList<>();

    public interface Listener {
        void onStateChanged(boolean playing, String label);
    }

    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }
    public boolean isPlaying() { return playing; }
    public String getCurrentName() { return playing ? currentLabel : "Đang tắt"; }

    public static File getGenreDir(Context ctx, String genre) {
        String g = isValidGenre(genre) ? genre : "nhacviet";
        File dir = new File(new File(ctx.getFilesDir(), "music"), g);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static boolean isValidGenre(String g) {
        if (g == null) return false;
        for (String k : GENRE_KEYS) if (k.equals(g)) return true;
        return false;
    }

    public static List<File> getTracksForGenre(Context ctx, String genre) {
        List<File> list = new ArrayList<>();
        // Migrate any legacy root music/*.mp3 into nhacviet once
        if ("nhacviet".equals(genre)) {
            File root = new File(ctx.getFilesDir(), "music");
            if (root.exists() && root.listFiles() != null) {
                File targetDir = getGenreDir(ctx, "nhacviet");
                for (File f : root.listFiles()) {
                    if (f.isFile() && f.length() > 1000) {
                        f.renameTo(new File(targetDir, f.getName()));
                    }
                }
            }
        }
        File dir = getGenreDir(ctx, genre);
        File[] arr = dir.listFiles();
        if (arr != null) {
            Arrays.sort(arr, new Comparator<File>() {
                @Override public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (File f : arr) {
                if (f.isFile() && f.length() > 1000) list.add(f);
            }
        }
        return list;
    }

    public static int countAllTracks(Context ctx) {
        int total = 0;
        for (String g : GENRE_KEYS) total += getTracksForGenre(ctx, g).size();
        return total;
    }

    public boolean playOrNextGenre(final Context ctx, final String genre) {
        if (playing && genre != null && genre.equals(currentGenre)) {
            return playGenre(ctx, genre, localIndex + 1);
        }
        return playGenre(ctx, genre, 0);
    }

    public boolean playGenre(final Context ctx, final String genre, int startIdx) {
        List<File> tracks = getTracksForGenre(ctx, genre);
        if (tracks.isEmpty()) return false;
        currentGenre = genre;
        currentTracks = tracks;
        localIndex = Math.max(0, startIdx) % currentTracks.size();
        final File track = currentTracks.get(localIndex);
        String cleanName = track.getName().replaceFirst("^\\d+_", "").replaceFirst("\\.[^.]+$", "");
        if (cleanName.length() > 22) cleanName = cleanName.substring(0, 22) + "…";
        currentLabel = cleanName;
        stopInternal();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(track.getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    if (playing && !currentTracks.isEmpty()) {
                        playGenre(ctx, currentGenre, (localIndex + 1) % currentTracks.size());
                    }
                }
            });
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    playing = true;
                    notifyListener();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    playing = false;
                    notifyListener();
                    return true;
                }
            });
            player.prepareAsync();
            playing = true;
            notifyListener();
            return true;
        } catch (Exception e) {
            playing = false;
            notifyListener();
            return false;
        }
    }

    public void toggle(Context ctx) {
        if (playing) stop();
        else playGenre(ctx, currentGenre, localIndex);
    }

    public void stop() {
        stopInternal();
        playing = false;
        currentLabel = "Đang tắt";
        notifyListener();
    }

    private void stopInternal() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void notifyListener() {
        if (listener != null) listener.onStateChanged(playing, currentLabel);
    }
}
