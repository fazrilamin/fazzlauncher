package com.fazrilamin.fazzlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements LocationListener {
    // Dark minimal palette
    static final int BG = 0xFF0B0D10, PANEL = 0xFF15181D, STROKE = 0xFF242A33;
    static final int TEXT = 0xFFECEFF4, SUB = 0xFF8A93A0, ACCENT = 0xFF4FD1C5, ACCENT_BG = 0x264FD1C5;

    /** Home-screen shortcuts: label, package, explicit activity (or null to use the app's launch intent). */
    private static final String[][] TILES = {
        {"Spotify", "com.spotify.music", null},
        {"YouTube", "com.google.android.youtube", null},
        {"Maps", "com.google.android.apps.maps", null},
        {"Radio", "com.tw.radio", "com.tw.radio.RadioActivity"},
    };
    static final ComponentName STOCK_HOME =
        new ComponentName("com.dofun.variety", "com.dofun.overseasvariety.Launcher");
    private static final String DEFAULT_MEDIA_APP = "com.spotify.music";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newCachedThreadPool();
    private SharedPreferences prefs;
    private float density;

    // Location / speed
    private LocationManager lm;
    private Location lastLoc;
    private long lastFixAt; // elapsedRealtime of last GPS fix
    private ClusterView cluster;

    // Clock panel
    private TextView hijriView, weatherTemp, weatherDesc;
    private volatile boolean fetchingWeather, fetchingPrayer;

    // Prayer panel
    private TextView prayerHeader, countdown, placeView, zoneView;
    private volatile boolean fetchingPlace;
    private final TextView[] pName = new TextView[6], pTime = new TextView[6];
    private final LinearLayout[] pRow = new LinearLayout[6];
    private long[] times;          // today's 6 times, epoch ms
    private long tomorrowFajr;     // epoch ms, 0 if unknown
    private String parsedKey = ""; // day + data version, avoids re-parsing every second
    private int highlighted = -2;

    // Media card
    private MediaSessionManager msm;
    private ComponentName listenerCn;
    private MediaController controller;
    private boolean mediaListening, mediaAllowed;
    private ImageView mediaArt;
    private TextView mediaTitle, mediaSub;
    private Glyph playBtn;

    // App drawer
    private FrameLayout drawer;
    private final AppAdapter adapter = new AppAdapter();

    private int tickCount;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("fazzlauncher", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(buildHome(), new FrameLayout.LayoutParams(-1, -1));
        drawer = buildDrawer();
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        lm = getSystemService(LocationManager.class);
        msm = getSystemService(MediaSessionManager.class);
        listenerCn = new ComponentName(this, MediaListener.class);

        showCachedWeather();
        renderPrayer();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startLocation();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupMedia();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(tick);
        cluster.model.save(prefs);
        try {
            lm.removeUpdates(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaListening) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        if (controller != null) controller.unregisterCallback(mediaCb);
        bg.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeDrawer(); // Home pressed while already home
        // For testing over ADB: am start -n com.fazrilamin.fazzlauncher/.MainActivity --ez demo true
        if (intent.getBooleanExtra("demo", false)) cluster.startDemo();
        // Privacy mode for screenshots: am start -n com.fazrilamin.fazzlauncher/.MainActivity --ez hide_town true|false
        if (intent.hasExtra("hide_town")) {
            prefs.edit().putBoolean("hide_town", intent.getBooleanExtra("hide_town", false)).apply();
            showPlace();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
        // A launcher never finishes on Back.
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        startLocation();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tickCount++;
            boolean fix = lastFixAt != 0 && SystemClock.elapsedRealtime() - lastFixAt < 5000;
            cluster.setGps(fix);
            if (tickCount % 10 == 0) cluster.model.save(prefs);
            renderPrayer();
            if (tickCount % 5 == 1) {
                maybeFetchPrayer();
                maybeFetchWeather();
                maybeFetchPlace();
                if (mediaAllowed) refreshSessions();
            }
            ui.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------------------------ layout

    private View buildHome() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));
        top.addView(buildClockPanel(), weighted(0.9f, 0));
        top.addView(buildSpeedPanel(), weighted(1.3f, dp(16)));
        top.addView(buildPrayerPanel(), weighted(0.9f, dp(16)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(176));
        blp.topMargin = dp(16);
        col.addView(bottom, blp);
        bottom.addView(buildMediaCard(), weighted(1.5f, 0));
        for (String[] t : TILES) bottom.addView(buildTile(t), weighted(0.5f, dp(16)));
        bottom.addView(buildAllAppsTile(), weighted(0.5f, dp(16)));
        return col;
    }

    private View buildClockPanel() {
        LinearLayout p = panel();
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        style(clock, 92, TEXT, "sans-serif-light");
        clock.setIncludeFontPadding(false);
        p.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM yyyy");
        date.setFormat12Hour("EEEE, d MMMM yyyy");
        style(date, 22, SUB, "sans-serif");
        date.setPadding(0, dp(6), 0, 0);
        p.addView(date);

        hijriView = text("", 18, SUB, "sans-serif");
        hijriView.setPadding(0, dp(4), 0, 0);
        p.addView(hijriView);

        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.HORIZONTAL);
        w.setGravity(Gravity.BOTTOM);
        w.setPadding(0, dp(28), 0, 0);
        weatherTemp = text("--°", 44, TEXT, "sans-serif-light");
        weatherTemp.setIncludeFontPadding(false);
        w.addView(weatherTemp);
        weatherDesc = text("Weather", 20, SUB, "sans-serif");
        weatherDesc.setPadding(dp(14), 0, 0, dp(4));
        w.addView(weatherDesc);
        p.addView(w);
        return p;
    }

    private View buildSpeedPanel() {
        LinearLayout p = panel();
        p.setPadding(dp(10), dp(10), dp(10), dp(8));
        cluster = new ClusterView(this, prefs);
        cluster.setLongClickable(true);
        cluster.setOnLongClickListener(v -> {
            showClusterMenu();
            return true;
        });
        p.addView(cluster, new LinearLayout.LayoutParams(-1, -1));
        return p;
    }

    /** Long-press on the cluster: speed limit, reset trip, demo drive. */
    private void showClusterMenu() {
        DriveModel m = cluster.model;
        String limit = m.limit > 0 ? m.limit + " km/h" : "Off";
        String[] items = {"Speed limit: " + limit, "Reset trip", m.demo ? "Stop demo" : "Demo drive (60 s)"};
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Drive cluster")
            .setItems(items, (dlg, which) -> {
                if (which == 0) {
                    final int[] values = {60, 80, 90, 110, 130, 0};
                    String[] labels = {"60 km/h", "80 km/h", "90 km/h", "110 km/h", "130 km/h", "Off"};
                    int sel = 3;
                    for (int i = 0; i < values.length; i++) if (values[i] == m.limit) sel = i;
                    new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Speed limit warning")
                        .setSingleChoiceItems(labels, sel, (d2, i) -> {
                            m.limit = values[i];
                            m.save(prefs);
                            cluster.invalidate();
                            d2.dismiss();
                        })
                        .show();
                } else if (which == 1) {
                    m.resetTrip();
                    m.save(prefs);
                    cluster.invalidate();
                } else if (m.demo) {
                    m.stopDemo();
                    cluster.invalidate();
                } else {
                    cluster.startDemo();
                }
            })
            .show();
    }

    private View buildPrayerPanel() {
        LinearLayout p = panel();
        p.setPadding(dp(20), dp(18), dp(20), dp(16));
        prayerHeader = text("WAKTU SOLAT", 15, SUB, "sans-serif-medium");
        prayerHeader.setLetterSpacing(0.08f);
        prayerHeader.setPadding(dp(12), 0, 0, dp(4));
        p.addView(prayerHeader);

        // Location line: "Shah Alam, Selangor" + zone code tag "SGR01"
        LinearLayout loc = new LinearLayout(this);
        loc.setOrientation(LinearLayout.HORIZONTAL);
        loc.setGravity(Gravity.CENTER_VERTICAL);
        loc.setPadding(dp(12), 0, dp(4), dp(6));
        placeView = text("", 17, TEXT, "sans-serif");
        placeView.setSingleLine(true);
        placeView.setEllipsize(TextUtils.TruncateAt.END);
        loc.addView(placeView, new LinearLayout.LayoutParams(0, -2, 1f));
        zoneView = text("", 14, ACCENT, "sans-serif-medium");
        zoneView.setLetterSpacing(0.05f);
        zoneView.setPadding(dp(10), dp(3), dp(10), dp(3));
        zoneView.setBackground(rounded(ACCENT_BG, 8, 0));
        zoneView.setVisibility(View.GONE);
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(-2, -2);
        zlp.leftMargin = dp(8);
        loc.addView(zoneView, zlp);
        p.addView(loc);
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            pName[i] = text(Net.PRAYER_NAMES[i], 21, i == 1 ? SUB : TEXT, "sans-serif");
            row.addView(pName[i], new LinearLayout.LayoutParams(0, -2, 1f));
            pTime[i] = text("--:--", 21, i == 1 ? SUB : TEXT, "sans-serif-medium");
            row.addView(pTime[i]);
            pRow[i] = row;
            p.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        countdown = text("", 16, ACCENT, "sans-serif-medium");
        countdown.setPadding(dp(12), dp(6), 0, 0);
        p.addView(countdown);
        return p;
    }

    private View buildMediaCard() {
        LinearLayout card = panel();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        pressable(card);
        card.setOnClickListener(v -> openMediaApp());

        mediaArt = new ImageView(this);
        mediaArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArt.setBackground(rounded(STROKE, 16, 0));
        mediaArt.setClipToOutline(true);
        card.addView(mediaArt, new LinearLayout.LayoutParams(dp(144), dp(144)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), 0, 0, 0);
        mediaTitle = text("Nothing playing", 21, TEXT, "sans-serif-medium");
        mediaTitle.setSingleLine(true);
        mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(mediaTitle);
        mediaSub = text("", 16, SUB, "sans-serif");
        mediaSub.setSingleLine(true);
        mediaSub.setEllipsize(TextUtils.TruncateAt.END);
        mediaSub.setPadding(0, dp(4), 0, dp(14));
        info.addView(mediaSub);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Glyph prev = new Glyph(this, Glyph.PREV, TEXT, 0);
        playBtn = new Glyph(this, Glyph.PLAY, BG, ACCENT);
        Glyph next = new Glyph(this, Glyph.NEXT, TEXT, 0);
        prev.setOnClickListener(v -> transport(Glyph.PREV));
        playBtn.setOnClickListener(v -> transport(Glyph.PLAY));
        next.setOnClickListener(v -> transport(Glyph.NEXT));
        controls.addView(prev, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(60), dp(60));
        plp.leftMargin = dp(10);
        plp.rightMargin = dp(10);
        controls.addView(playBtn, plp);
        controls.addView(next, new LinearLayout.LayoutParams(dp(56), dp(56)));
        info.addView(controls);
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    private View buildTile(String[] t) {
        LinearLayout p = panel();
        p.setGravity(Gravity.CENTER);
        pressable(p);
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(t[1]));
        } catch (PackageManager.NameNotFoundException e) {
            icon.setAlpha(0.3f);
        }
        p.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView l = text(t[0], 18, TEXT, "sans-serif-medium");
        l.setPadding(0, dp(12), 0, 0);
        l.setGravity(Gravity.CENTER);
        p.addView(l);
        p.setOnClickListener(v -> launch(t[1], t[2]));
        return p;
    }

    private View buildAllAppsTile() {
        LinearLayout p = panel();
        p.setGravity(Gravity.CENTER);
        pressable(p);
        p.addView(new Glyph(this, Glyph.GRID, TEXT, STROKE), new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView l = text("All apps", 18, TEXT, "sans-serif-medium");
        l.setPadding(0, dp(12), 0, 0);
        l.setGravity(Gravity.CENTER);
        p.addView(l);
        p.setOnClickListener(v -> openDrawer());
        return p;
    }

    private FrameLayout buildDrawer() {
        FrameLayout d = new FrameLayout(this);
        d.setBackgroundColor(0xF70B0D10);
        d.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(32), dp(24), dp(32), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("All apps", 26, TEXT, "sans-serif-medium");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Glyph close = new Glyph(this, Glyph.CLOSE, TEXT, STROKE);
        close.setOnClickListener(v -> closeDrawer());
        header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
        col.addView(header);

        GridView grid = new GridView(this);
        grid.setNumColumns(8);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(8));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setPadding(0, dp(16), 0, 0);
        grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((a, v, pos, id) -> {
            AppEntry e = adapter.items.get(pos);
            try {
                startActivity(e.intent);
            } catch (Exception ex) {
                toast("Cannot open " + e.label);
            }
            closeDrawer();
        });
        grid.setOnItemLongClickListener((a, v, pos, id) -> {
            AppEntry e = adapter.items.get(pos);
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", e.intent.getComponent().getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        d.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return d;
    }

    // ------------------------------------------------------------------ app launching

    private void launch(String pkg, String cls) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && cls != null) {
            i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg, cls);
        }
        if (i == null) {
            toast("App not installed");
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Cannot open app");
        }
    }

    private void openDrawer() {
        drawer.setVisibility(View.VISIBLE);
        bg.execute(() -> {
            PackageManager pm = getPackageManager();
            List<AppEntry> list = new ArrayList<>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (ri.activityInfo.packageName.equals(getPackageName())) continue;
                AppEntry e = new AppEntry();
                e.label = String.valueOf(ri.loadLabel(pm));
                e.icon = ri.loadIcon(pm);
                e.intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                list.add(e);
            }
            Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
            // First entry: the original Topway/DoFun home screen, always reachable from here.
            try {
                AppEntry stock = new AppEntry();
                stock.label = "Stock Home";
                stock.icon = pm.getApplicationIcon(STOCK_HOME.getPackageName());
                stock.intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(STOCK_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                list.add(0, stock);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            ui.post(() -> adapter.set(list));
        });
    }

    private void closeDrawer() {
        if (drawer != null) drawer.setVisibility(View.GONE);
    }

    static final class AppEntry {
        String label;
        Drawable icon;
        Intent intent;
    }

    private final class AppAdapter extends BaseAdapter {
        List<AppEntry> items = new ArrayList<>();

        void set(List<AppEntry> l) {
            items = l;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convert;
            if (cell == null) {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(6), dp(12), dp(6), dp(12));
                cell.setBackground(ripple(rounded(0, 16, 0)));
                cell.addView(new ImageView(MainActivity.this), new LinearLayout.LayoutParams(dp(64), dp(64)));
                TextView t = text("", 15, TEXT, "sans-serif");
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, dp(8), 0, 0);
                cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            }
            AppEntry e = items.get(pos);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(e.icon);
            ((TextView) cell.getChildAt(1)).setText(e.label);
            return cell;
        }
    }

    // ------------------------------------------------------------------ location, speed

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (lastLoc == null || l.getTime() > lastLoc.getTime())) lastLoc = l;
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // 0 = as fast as the GNSS chip delivers (1 Hz on this unit).
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
        saveCoords();
    }

    @Override
    public void onLocationChanged(Location l) {
        float kmh;
        if (l.hasSpeed()) {
            kmh = l.getSpeed() * 3.6f;
        } else if (lastLoc != null && LocationManager.GPS_PROVIDER.equals(lastLoc.getProvider())) {
            // No Doppler speed in this fix: derive it from distance travelled.
            double dt = (l.getElapsedRealtimeNanos() - lastLoc.getElapsedRealtimeNanos()) / 1e9;
            kmh = dt > 0.2 && dt < 5 ? (float) (lastLoc.distanceTo(l) / dt * 3.6) : 0f;
        } else {
            kmh = 0f;
        }
        lastLoc = l;
        lastFixAt = SystemClock.elapsedRealtime();
        cluster.onFix(kmh, l);
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void saveCoords() {
        if (lastLoc != null) {
            prefs.edit().putFloat("lat", (float) lastLoc.getLatitude())
                .putFloat("lon", (float) lastLoc.getLongitude()).apply();
        }
    }

    /** Current position, or the last one remembered, or null if never known. */
    private double[] coords() {
        if (lastLoc != null) return new double[]{lastLoc.getLatitude(), lastLoc.getLongitude()};
        if (prefs.contains("lat")) return new double[]{prefs.getFloat("lat", 0), prefs.getFloat("lon", 0)};
        return null;
    }

    // ------------------------------------------------------------------ weather

    private void maybeFetchWeather() {
        double[] ll = coords();
        long now = System.currentTimeMillis();
        if (ll == null || fetchingWeather || now - prefs.getLong("w_at", 0) < 20 * 60_000L) return;
        fetchingWeather = true;
        saveCoords();
        bg.execute(() -> {
            try {
                Net.Weather w = Net.weather(ll[0], ll[1]);
                prefs.edit().putFloat("w_temp", w.temp).putInt("w_code", w.code).putLong("w_at", now).apply();
                ui.post(this::showCachedWeather);
            } catch (Exception e) {
                prefs.edit().putLong("w_at", now - 15 * 60_000L).apply(); // retry in ~5 min
            } finally {
                fetchingWeather = false;
            }
        });
    }

    private void showCachedWeather() {
        if (!prefs.contains("w_temp")) return;
        weatherTemp.setText(Math.round(prefs.getFloat("w_temp", 0)) + "°C");
        weatherDesc.setText(Net.weatherLabel(prefs.getInt("w_code", -1)));
    }

    // ------------------------------------------------------------------ prayer times (JAKIM)

    private static String ymNow() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1);
    }

    private void maybeFetchPrayer() {
        double[] ll = coords();
        long now = System.currentTimeMillis();
        if (ll == null || fetchingPrayer || now - prefs.getLong("p_try", 0) < 5 * 60_000L) return;
        boolean need = prefs.getString("p_json", null) == null || !ymNow().equals(prefs.getString("p_ym", ""));
        if (!need) {
            // Moved to another zone (e.g. drove interstate)? Re-check when 15 km away from the last fetch.
            float[] dist = new float[1];
            Location.distanceBetween(ll[0], ll[1], prefs.getFloat("p_lat", 0), prefs.getFloat("p_lon", 0), dist);
            need = dist[0] > 15_000;
        }
        if (!need) return;
        fetchingPrayer = true;
        prefs.edit().putLong("p_try", now).apply();
        bg.execute(() -> {
            try {
                String body = Net.get(Net.prayerUrl(ll[0], ll[1]));
                JSONObject o = new JSONObject(body);
                o.getJSONArray("prayers"); // validate
                prefs.edit().putString("p_json", body)
                    .putString("p_ym", o.getInt("year") + "-" + o.getInt("month_number"))
                    .putString("p_zone", o.optString("zone", ""))
                    .putFloat("p_lat", (float) ll[0]).putFloat("p_lon", (float) ll[1])
                    .putLong("p_ver", now).apply();
                ui.post(this::renderPrayer);
            } catch (Exception ignored) {
            } finally {
                fetchingPrayer = false;
            }
        });
    }

    private void renderPrayer() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String key = ymNow() + "/" + day + "/" + prefs.getLong("p_ver", 0);
        if (!key.equals(parsedKey)) {
            parsedKey = key;
            times = null;
            tomorrowFajr = 0;
            highlighted = -2;
            String json = prefs.getString("p_json", null);
            if (json != null && ymNow().equals(prefs.getString("p_ym", ""))) {
                try {
                    JSONArray arr = new JSONObject(json).getJSONArray("prayers");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject d = arr.getJSONObject(i);
                        int dd = d.getInt("day");
                        if (dd == day) {
                            times = new long[6];
                            for (int k = 0; k < 6; k++) times[k] = d.getLong(Net.PRAYER_KEYS[k]) * 1000L;
                            hijriView.setText(Net.hijri(d.optString("hijri", "")));
                        } else if (dd == day + 1) {
                            tomorrowFajr = d.getLong("fajr") * 1000L;
                        }
                    }
                } catch (Exception ignored) {
                    times = null;
                }
            }
            showPlace();
            DateFormat tf = android.text.format.DateFormat.getTimeFormat(this);
            for (int k = 0; k < 6; k++) pTime[k].setText(times == null ? "--:--" : tf.format(new Date(times[k])));
        }

        if (times == null) {
            countdown.setText(coords() == null ? "Waiting for GPS location…" : "Loading prayer times…");
            setHighlight(-1);
            return;
        }
        long now = System.currentTimeMillis();
        int next = -1;
        for (int k = 0; k < 6; k++) {
            if (k == 1) continue; // Syuruk is shown but is not a prayer
            if (times[k] > now) {
                next = k;
                break;
            }
        }
        long target;
        if (next >= 0) {
            target = times[next];
            setHighlight(next);
        } else if (tomorrowFajr > 0) {
            next = 0;
            target = tomorrowFajr;
            setHighlight(0);
        } else {
            countdown.setText("");
            setHighlight(-1);
            return;
        }
        long mins = (target - now + 59_999) / 60_000;
        String left = mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + " min";
        countdown.setText(Net.PRAYER_NAMES[next] + " in " + left);
    }

    /** Place name (cached reverse geocode, else state from the zone code) and the JAKIM zone tag. */
    private void showPlace() {
        String zone = prefs.getString("p_zone", "");
        // Privacy mode (for screenshots): state only, no town and no zone code.
        boolean hide = prefs.getBoolean("hide_town", false);
        String place = hide ? Net.stateOfZone(zone) : prefs.getString("loc_name", "");
        if (place.isEmpty()) place = Net.stateOfZone(zone);
        placeView.setText(place);
        zoneView.setText(zone);
        zoneView.setVisibility(zone.isEmpty() || hide ? View.GONE : View.VISIBLE);
    }

    private void maybeFetchPlace() {
        double[] ll = coords();
        long now = System.currentTimeMillis();
        if (ll == null || fetchingPlace || now - prefs.getLong("loc_try", 0) < 60_000L) return;
        boolean need = !prefs.contains("loc_name");
        if (!need) {
            float[] dist = new float[1];
            Location.distanceBetween(ll[0], ll[1], prefs.getFloat("loc_lat", 0), prefs.getFloat("loc_lon", 0), dist);
            need = dist[0] > 2_000 && now - prefs.getLong("loc_at", 0) > 5 * 60_000L;
        }
        if (!need) return;
        fetchingPlace = true;
        prefs.edit().putLong("loc_try", now).apply();
        bg.execute(() -> {
            try {
                String name = Net.place(ll[0], ll[1]);
                prefs.edit().putString("loc_name", name).putLong("loc_at", now)
                    .putFloat("loc_lat", (float) ll[0]).putFloat("loc_lon", (float) ll[1]).apply();
                ui.post(this::showPlace);
            } catch (Exception ignored) {
            } finally {
                fetchingPlace = false;
            }
        });
    }

    private void setHighlight(int idx) {
        if (idx == highlighted) return;
        highlighted = idx;
        for (int k = 0; k < 6; k++) {
            boolean on = k == idx;
            int base = k == 1 ? SUB : TEXT;
            pRow[k].setBackground(on ? rounded(ACCENT_BG, 12, 0) : null);
            pName[k].setTextColor(on ? ACCENT : base);
            pTime[k].setTextColor(on ? ACCENT : base);
        }
    }

    // ------------------------------------------------------------------ media card

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pickController;

    private final MediaController.Callback mediaCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { updateMedia(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { updateMedia(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };

    private void setupMedia() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        mediaAllowed = nm.isNotificationListenerAccessGranted(listenerCn);
        if (!mediaAllowed) {
            mediaTitle.setText("Media access needed");
            mediaSub.setText("Tap to allow Fazz Launcher to show what's playing");
            return;
        }
        try {
            if (!mediaListening) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerCn, ui);
                mediaListening = true;
            }
            refreshSessions();
        } catch (SecurityException e) {
            mediaAllowed = false;
        }
    }

    private void refreshSessions() {
        try {
            pickController(msm.getActiveSessions(listenerCn));
        } catch (SecurityException e) {
            mediaAllowed = false;
        }
    }

    /** Prefer whatever is playing now; otherwise keep the most recent session. */
    private void pickController(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController mc : list) {
                if (isPlaying(mc)) {
                    best = mc;
                    break;
                }
            }
            if (best == null && controller != null) {
                for (MediaController mc : list) {
                    if (mc.getSessionToken().equals(controller.getSessionToken())) best = mc;
                }
            }
            // Nothing playing: prefer a session that has a track title (an idle BT session has none).
            if (best == null) {
                for (MediaController mc : list) {
                    if (hasTitle(mc)) {
                        best = mc;
                        break;
                    }
                }
            }
            // An idle session with no track (e.g. Topway BT with no phone audio) is treated as nothing playing.
        }
        if (controller != null && best != null && controller.getSessionToken().equals(best.getSessionToken())) {
            updateMedia();
            return;
        }
        if (controller != null) controller.unregisterCallback(mediaCb);
        controller = best;
        if (controller != null) controller.registerCallback(mediaCb, ui);
        updateMedia();
    }

    private static boolean hasTitle(MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        return md != null && first(md.getString(MediaMetadata.METADATA_KEY_TITLE),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)) != null;
    }

    private static boolean isPlaying(MediaController mc) {
        PlaybackState st = mc == null ? null : mc.getPlaybackState();
        return st != null && (st.getState() == PlaybackState.STATE_PLAYING
            || st.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void updateMedia() {
        if (!mediaAllowed) return;
        if (controller == null) {
            mediaTitle.setText("Nothing playing");
            mediaSub.setText("Press play to open Spotify");
            mediaArt.setImageDrawable(appIcon(DEFAULT_MEDIA_APP));
            playBtn.setType(Glyph.PLAY);
            return;
        }
        String pkg = controller.getPackageName();
        MediaMetadata md = controller.getMetadata();
        String title = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_TITLE),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String artist = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_ARTIST),
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        Bitmap art = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null && md != null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null && md != null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

        String app = appLabel(pkg);
        mediaTitle.setText(title != null ? title : app);
        mediaSub.setText(artist != null ? artist + "  ·  " + app : app);
        if (art != null) mediaArt.setImageBitmap(art);
        else mediaArt.setImageDrawable(appIcon(pkg));
        playBtn.setType(isPlaying(controller) ? Glyph.PAUSE : Glyph.PLAY);
    }

    private void transport(int which) {
        if (controller == null) {
            if (which == Glyph.PLAY) launch(DEFAULT_MEDIA_APP, null);
            return;
        }
        MediaController.TransportControls tc = controller.getTransportControls();
        if (which == Glyph.PREV) tc.skipToPrevious();
        else if (which == Glyph.NEXT) tc.skipToNext();
        else if (isPlaying(controller)) tc.pause();
        else tc.play();
    }

    private void openMediaApp() {
        if (!mediaAllowed) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        launch(controller != null ? controller.getPackageName() : DEFAULT_MEDIA_APP, null);
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) {
        return Math.round(v * density);
    }

    private LinearLayout.LayoutParams weighted(float weight, int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.leftMargin = leftMargin;
        return lp;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(rounded(PANEL, 24, STROKE));
        return p;
    }

    private GradientDrawable rounded(int color, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private RippleDrawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, rounded(0xFFFFFFFF, 24, 0));
    }

    private void pressable(View v) {
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, rounded(0xFFFFFFFF, 24, 0)));
        v.setClickable(true);
    }

    private TextView text(String s, float sp, int color, String family) {
        TextView t = new TextView(this);
        style(t, sp, color, family);
        t.setText(s);
        return t;
    }

    private void style(TextView t, float sp, int color, String family) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setFontFeatureSettings("tnum");
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private static String first(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v;
        return null;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
