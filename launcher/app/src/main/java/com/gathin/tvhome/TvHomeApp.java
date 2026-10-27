package com.gathin.tvhome;

import android.app.Application;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Keeps the launcher in English whatever language the TV is set to, so every clock, month and day
 * name reads the same (12-hour AM/PM, "OCT" not "OKT"). Only this app is affected; the TV's own
 * menus keep the system language. Android resets the default locale on a configuration change,
 * so it is set again there.
 */
public final class TvHomeApp extends Application {
    static final Locale LOCALE = Locale.US;   // "AM"/"PM" in capitals; UK English gives "am"/"pm"

    @Override
    public void onCreate() {
        super.onCreate();
        Locale.setDefault(LOCALE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Locale.setDefault(LOCALE);
    }
}
