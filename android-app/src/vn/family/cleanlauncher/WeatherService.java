package vn.family.cleanlauncher;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherService {
    public interface Callback {
        void onWeather(String formatted);
    }

    public static void fetch(final String city, final double lat, final double lon, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String api = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                            + "&longitude=" + lon + "&current_weather=true";
                    HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject cw = root.getJSONObject("current_weather");
                    int temp = (int) Math.round(cw.getDouble("temperature"));
                    int code = cw.getInt("weathercode");
                    String desc = describeCode(code);
                    cb.onWeather(desc.substring(0, desc.indexOf(' ')) + " " + temp + "°C • " + city
                            + " • " + desc.substring(desc.indexOf(' ') + 1));
                } catch (Exception e) {
                    cb.onWeather("⛅ " + city + " • Đang cập nhật thời tiết");
                }
            }
        }).start();
    }

    private static String describeCode(int code) {
        if (code == 0) return "☀️ Trời nắng đẹp";
        if (code <= 3) return "⛅ Ít mây, trời mát";
        if (code <= 48) return "🌫️ Có sương mù";
        if (code <= 67) return "🌧️ Có mưa nhẹ";
        if (code <= 82) return "🌦️ Mưa rào";
        if (code >= 95) return "⛈️ Có dông";
        return "☁️ Nhiều mây";
    }
}
