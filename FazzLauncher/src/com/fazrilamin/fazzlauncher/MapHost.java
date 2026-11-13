package com.fazrilamin.fazzlauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Skin 2 shows the real Google Maps app in a window on the home screen, the same way the stock
 * Topway launcher does. This is a Topway framework feature, not a public Android API:
 *
 *  - Starting the head unit's configured navigation app (its "PiP component", Google Maps here) with
 *    the extra NAVI=OPEN makes the system open it as a borderless freeform window. Any app may do this.
 *  - The window's position comes from the system properties sys.tw.forcepip.x/y/w/h (set by the
 *    firmware; on the reference unit x=63 y=54 w=733 h=511). Skin 2 lays out its panels around it.
 *  - Starting the same app normally (without the extra) turns the window back into fullscreen.
 *    Pressing HOME then puts it behind the launcher. {@link #sendBehind} uses the same two steps.
 *
 * On other firmware NAVI=OPEN does nothing special: Maps simply opens fullscreen.
 */
final class MapHost {
    static final String PKG = "com.google.android.apps.maps";
    static final String CLS = "com.google.android.maps.MapsActivity";
    private static final Rect DEFAULT = new Rect(63, 54, 63 + 733, 54 + 511);
    private static Rect cached;

    private MapHost() {}

    /** Google Maps installed and the unit has freeform windows. */
    static boolean available(Activity a) {
        if (!a.getPackageManager().hasSystemFeature("android.software.freeform_window_management")) return false;
        try {
            a.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens Maps in the firmware's navigation window. */
    static boolean show(Activity a) {
        return start(a, true);
    }

    /** Opens Maps fullscreen (the Maps tile does the same through its normal launch intent). */
    static boolean showFullscreen(Activity a) {
        return start(a, false);
    }

    /**
     * Takes the Maps window off the home screen (for the app drawer, dialogs, or another skin): Maps goes
     * fullscreen for a moment, then the launcher comes back in front of it. The caller's activity receives
     * {@code homeIntent} through onNewIntent.
     */
    static void sendBehind(Activity a, Intent homeIntent) {
        if (start(a, false)) {
            try {
                a.startActivity(homeIntent);
            } catch (Exception ignored) {
            }
        }
    }

    /** The navigation window in screen pixels, read once from the firmware's properties. */
    static synchronized Rect bounds() {
        if (cached != null) return cached;
        Rect r = new Rect(DEFAULT);
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "getprop sys.tw.forcepip.x; getprop sys.tw.forcepip.y; getprop sys.tw.forcepip.w; getprop sys.tw.forcepip.h"});
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            int[] v = new int[4];
            for (int i = 0; i < 4; i++) v[i] = Integer.parseInt(in.readLine().trim());
            p.waitFor();
            if (v[2] > 100 && v[3] > 100) r.set(v[0], v[1], v[0] + v[2], v[1] + v[3]);
        } catch (Exception ignored) {
        }
        cached = r;
        return r;
    }

    private static boolean start(Activity a, boolean window) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(PKG, CLS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (window) i.putExtra("NAVI", "OPEN");
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
