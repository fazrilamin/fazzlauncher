package com.fazrilamin.fazzlauncher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import org.json.JSONObject;

/** Network helpers for prayer times (waktusolat.app, JAKIM data) and weather (Open-Meteo). */
final class Net {
    private Net() {}

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("User-Agent", "FazzLauncher/1.0 (Android car launcher)");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- Prayer times ----

    static final String[] PRAYER_KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] PRAYER_NAMES = {"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final String[] HIJRI_MONTHS = {
        "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
        "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"
    };

    /** Whole month of JAKIM times for the zone containing this point. */
    static String prayerUrl(double lat, double lon) {
        return String.format(Locale.US, "https://api.waktusolat.app/v2/solat/gps/%.4f/%.4f", lat, lon);
    }

    /** "1448-05-21" -> "21 Jamadilawal 1448H" */
    static String hijri(String s) {
        try {
            String[] p = s.split("-");
            int m = Integer.parseInt(p[1]);
            return Integer.parseInt(p[2]) + " " + HIJRI_MONTHS[m - 1] + " " + p[0] + "H";
        } catch (Exception e) {
            return "";
        }
    }

    // ---- Place name ----

    /** Town and state for a point via OpenStreetMap Nominatim, e.g. "Kuala Lumpur" or "Shah Alam, Selangor". */
    static String place(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1"
                + "&accept-language=ms,en&lat=%.5f&lon=%.5f", lat, lon);
        JSONObject a = new JSONObject(get(url)).getJSONObject("address");
        String town = null;
        for (String k : new String[]{"town", "city", "village", "suburb", "municipality", "county"}) {
            String v = a.optString(k, "");
            if (!v.isEmpty()) {
                town = v;
                break;
            }
        }
        String state = a.optString("state", "");
        if (town == null) return state;
        return state.isEmpty() || state.equalsIgnoreCase(town) ? town : town + ", " + state;
    }

    /** State from a JAKIM zone code prefix (offline fallback when no place name). */
    static String stateOfZone(String zone) {
        if (zone == null || zone.length() < 3) return "";
        switch (zone.substring(0, 3).toUpperCase(Locale.US)) {
            case "JHR": return "Johor";
            case "KDH": return "Kedah";
            case "KTN": return "Kelantan";
            case "MLK": return "Melaka";
            case "NGS": return "Negeri Sembilan";
            case "PHG": return "Pahang";
            case "PLS": return "Perlis";
            case "PNG": return "Pulau Pinang";
            case "PRK": return "Perak";
            case "SBH": return "Sabah";
            case "SGR": return "Selangor";
            case "SWK": return "Sarawak";
            case "TRG": return "Terengganu";
            case "WLY": return "Wilayah Persekutuan";
            default: return "";
        }
    }

    // ---- Weather ----

    static final class Weather {
        float temp;
        int code;
    }

    static Weather weather(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
        JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
        Weather w = new Weather();
        w.temp = (float) cur.getDouble("temperature_2m");
        w.code = cur.getInt("weather_code");
        return w;
    }

    /** WMO weather code to a short label. */
    static String weatherLabel(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }
}
