package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Google TV's own account switcher and Google Photos album picker. Both live in, or need, the stock
 * Google TV launcher, which Glass TV keeps disabled so it cannot take Home: with it off, the picker
 * flickers and closes. This turns the stock launcher on just for that screen, through the TV's debugging
 * service (see {@link CacheCleaner#exec}), watches until the person leaves it, then turns it off again and
 * brings Glass TV back, also when switching accounts drops them on the stock home. The screensaver keeps
 * the chosen account and albums with the stock launcher off.
 */
final class StockScreens {
    private static final String TAG = "TvHome.Stock";
    static final String LAUNCHER = "com.google.android.apps.tv.launcherx";
    private static final String DREAMX = "com.google.android.apps.tv.dreamx";
    static final String ALBUMS = DREAMX + "/.v2.photossetup.PhotosSetupActivity";
    static final String ACCOUNTS = LAUNCHER + "/.profile.chooser.ProfileChooserActivity";
    /** The screensaver's own settings: slideshow speed and mode, on-screen info. */
    static final String SETTINGS = DREAMX + "/.settingsv2.TwoPanelActivity";
    private static final String HOME = "com.gathin.tvhome/.MainActivity";
    private static final long POLL_MS = 2000;
    /** Turned off again after this long whatever happens, so it cannot stay on and take Home. */
    private static final long MAX_MS = 15 * 60_000;
    private static volatile boolean busy;

    private StockScreens() {
    }

    /** Whether this TV has the stock Google TV launcher (enabled or not). */
    static boolean available(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(LAUNCHER, PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Opens {@link #ALBUMS}, {@link #ACCOUNTS} or {@link #SETTINGS}. */
    static void open(Context ctx, String component) {
        if (busy) return;
        busy = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean wasDisabled = false;
            try {
                String disabled = CacheCleaner.exec(app, "pm list packages -d " + LAUNCHER);
                if (disabled == null) {
                    toast(app, "Needs the TV's debugging permission: allow \"USB debugging\" when the TV asks");
                    return;
                }
                wasDisabled = disabled.contains("package:" + LAUNCHER);
                // The screensaver app caches a failed account lookup, so it starts fresh once the launcher is on.
                CacheCleaner.exec(app, (wasDisabled ? "pm enable --user 0 " + LAUNCHER + "; sleep 1; " : "")
                        + "am force-stop " + DREAMX + "; am start -n " + component);
                Thread.sleep(3000);
                watch(app);
            } catch (Exception e) {
                Log.w(TAG, "stock screen failed: " + e);
            } finally {
                if (wasDisabled) {
                    String focus = CacheCleaner.exec(app, "dumpsys window | grep -m1 mCurrentFocus");
                    boolean back = focus == null || focus.contains(LAUNCHER) || focus.contains(DREAMX);
                    CacheCleaner.exec(app, "pm disable-user --user 0 " + LAUNCHER
                            + "; cmd package set-home-activity --user 0 " + HOME
                            + (back ? "; am start -n " + HOME : ""));
                    Log.i(TAG, "stock launcher off again");
                }
                busy = false;
            }
        }, "stock-screens").start();
    }

    /** Returns once the person has left the picker or switcher (two readings in a row), or after MAX_MS. */
    private static void watch(Context app) throws InterruptedException {
        long end = System.currentTimeMillis() + MAX_MS;
        int outside = 0;
        while (System.currentTimeMillis() < end) {
            String focus = CacheCleaner.exec(app, "dumpsys window | grep -m1 mCurrentFocus");
            if (focus == null) return;
            // Sign-in and verification screens belong to Google Play services; mCurrentFocus=null is a transition.
            boolean inside = focus.contains("=null") || focus.contains("com.google.android.gms")
                    || focus.contains(DREAMX) || (focus.contains(LAUNCHER) && !focus.contains("HomeActivity"));
            outside = inside ? 0 : outside + 1;
            if (outside >= 2) return;
            Thread.sleep(POLL_MS);
        }
    }

    private static void toast(Context app, String text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
    }
}
