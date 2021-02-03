package com.android.gallery3d.v2.util;

import android.preference.PreferenceManager;

import com.android.gallery3d.app.GalleryAppImpl;

import java.util.Set;

/**
 * @author baolin.li
 */
public class Config {
    public static void setPref(String key, int value) {
        PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .edit().putInt(key, value).commit();
    }

    public static int getPref(String key, int def) {
        return PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .getInt(key, def);
    }

    public static void setPref(String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .edit().putString(key, value).commit();
    }

    public static String getPref(String key, String def) {
        return PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .getString(key, def);
    }

    public static void setPref(String key, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .edit().putBoolean(key, value).commit();
    }

    public static boolean getPref(String key, boolean def) {
        return PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .getBoolean(key, def);
    }

    public static void setPref(String key, Set<String> value) {
        PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .edit().putStringSet(key, value).commit();
    }

    public static Set<String> getPref(String key, Set<String> def) {
        return PreferenceManager.getDefaultSharedPreferences(GalleryAppImpl.getApplication())
                .getStringSet(key, def);
    }
}
