package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it; on Google TV the stock launcher handles that key, so with the stock launcher
 * disabled the button does nothing.
 *
 * Xiaomi firmware also takes the Input and Settings keys (KEYCODE_TV_INPUT, KEYCODE_SETTINGS; the Phone
 * Remote sends both) before any app sees them and hands them to the stock launcher, so with it disabled
 * they do nothing either. Here they open Home's own Inputs and Settings panels, from any app.
 * Only these three keys are consumed; every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_SETTINGS) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_PANEL, code == KeyEvent.KEYCODE_TV_INPUT
                                ? MainActivity.PANEL_INPUTS : MainActivity.PANEL_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (code != KeyEvent.KEYCODE_TV) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
