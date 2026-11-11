package com.phuoc.aisummaryflashcard.helper;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    private static final String PREF_NAME = "app_theme_prefs";
    private static final String KEY_IS_DARK = "is_dark_mode";

    public static void applyTheme(Context context) {
        boolean isDark = isDarkMode(context);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_DARK, false);
    }

    public static boolean toggleTheme(Activity activity) {
        boolean newDark = !isDarkMode(activity);
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_DARK, newDark).apply();

        AppCompatDelegate.setDefaultNightMode(
                newDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        activity.recreate();
        return newDark;
    }
}
