package com.sprd.ui.test;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Utils {
    public static final int LAUNCH_TIMEOUT = 5000;

    public static final String BASIC_PACKAGE = "com.android.gallery3d";

    public static String RES_DIR = "/storage/emulated/0";

    //获取Launcher包名
    static String getLauncherPackageName() {
        // Create launcher Intent
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        // Use PackageManager to get the launcher package name
        PackageManager pm = getApplicationContext().getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    //等待一段时间
    static void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //用于检测运行时权限框是否显示出来, 自动点击允许
    static void checkRuntimePermission(UiDevice uiDevice, boolean failIf) {
        while (allowRuntimePermission(uiDevice, failIf)) {
            Log.d("Utils", "checkRuntimePermission continue check.");
        }
    }

    private static boolean allowRuntimePermission(UiDevice uiDevice, boolean failIf) {
        waitMillis(1500);
        UiObject a1 = uiDevice.findObject(new UiSelector().text("ALLOW"));
        UiObject a2 = uiDevice.findObject(new UiSelector().text("ALLOW ALL THE TIME"));
        UiObject a3 = uiDevice.findObject(new UiSelector().text("ALLOW ONLY WHILE USING THE APP"));
        UiObject a4 = uiDevice.findObject(new UiSelector().textStartsWith("ALLOW ACCESS TO"));

        boolean clicked = false;

        if (!clicked) {
            if (a1.exists()) {
                try {
                    if (a1.isClickable()) {
                        a1.click();
                        clicked = true;
                    }
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        }

        if (!clicked) {
            if (a2.exists()) {
                try {
                    if (a2.isClickable()) {
                        a2.click();
                        clicked = true;
                    }
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        }

        if (!clicked) {
            if (a3.exists()) {
                try {
                    if (a3.isClickable()) {
                        a3.click();
                        clicked = true;
                    }
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        }

        if (!clicked) {
            if (a4.exists()) {
                try {
                    if (a4.isClickable()) {
                        a4.click();
                        clicked = true;
                    }
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        }

        waitMillis(1500);
        a1 = uiDevice.findObject(new UiSelector().text("ALLOW"));
        a2 = uiDevice.findObject(new UiSelector().text("ALLOW ALL THE TIME"));
        a3 = uiDevice.findObject(new UiSelector().text("ALLOW ONLY WHILE USING THE APP"));
        a4 = uiDevice.findObject(new UiSelector().textStartsWith("ALLOW ACCESS TO"));

        return a1.exists() || a2.exists() || a3.exists() || a4.exists();
    }

    //用于检测是否弹出请求 sd卡 权限框, 自动点击允许
    static void checkSdPermission(Context context, UiDevice uiDevice) {
        waitMillis(2000);
        String title = context.getString(R.string.request_delete_permission_title);
        UiObject dialogTitle = uiDevice.findObject(new UiSelector().text(title));
        if (!dialogTitle.exists()) {
            return;
        }

        String okString = context.getString(R.string.ok);
        UiObject ok = uiDevice.findObject(new UiSelector().text(okString));
        assertTrue(ok.exists());

        try {
            ok.click();
        } catch (UiObjectNotFoundException ignored) {
        }

        checkRuntimePermission(uiDevice, true);
    }

    //权限框自动点击拒绝
    static void runtimePermissionDeny(UiDevice uiDevice, boolean failIf) {
        waitMillis(1000);
        UiObject deny_en = uiDevice.findObject(new UiSelector().text("DENY"));
        UiObject deny_ch = uiDevice.findObject(new UiSelector().text("拒绝"));
        if (!deny_en.exists() && !deny_ch.exists()) {
            if (failIf) fail();
            return;
        }

        boolean deny_en_exist;
        boolean deny_ch_exist;

        do {
            deny_en_exist = false;
            deny_ch_exist = false;
            waitMillis(1000);

            deny_en = uiDevice.findObject(new UiSelector().text("DENY"));
            deny_ch = uiDevice.findObject(new UiSelector().text("拒绝"));

            if (deny_en.exists()) {
                try {
                    deny_en.click();
                } catch (UiObjectNotFoundException ignored) {
                }
                deny_en_exist = true;
            } else if (deny_ch.exists()) {
                try {
                    deny_ch.click();
                } catch (UiObjectNotFoundException ignored) {
                }
                deny_ch_exist = true;
            }
        } while (deny_en_exist || deny_ch_exist);
    }

    // sd卡 权限框，isSelectOk是否选择确定
    static void checkSdPermissionSelect(Context context, UiDevice uiDevice, boolean isSelectOk) {
        Utils.waitMillis(1000);
        String title = context.getString(R.string.request_delete_permission_title);
        UiObject dialogTitle = uiDevice.findObject(new UiSelector().text(title));
        if (!dialogTitle.exists()) {
            return;
        }
        if (isSelectOk) {
            String okString = context.getString(R.string.ok);
            UiObject ok = uiDevice.findObject(new UiSelector().text(okString));
            assertTrue(ok.exists());

            try {
                ok.click();
            } catch (UiObjectNotFoundException ignored) {
            }
        } else {
            UiObject cancel_en = uiDevice.findObject(new UiSelector().text("CANCEL"));
            UiObject cancel_ch = uiDevice.findObject(new UiSelector().text("取消"));

            if (cancel_en.exists()) {
                try {
                    cancel_en.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            } else if (cancel_ch.exists()) {
                try {
                    cancel_ch.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            }
            Utils.waitMillis(1000);
            checkSdcardPermissionError(context, uiDevice);
        }

    }

    //isSelectAllow是否选择允许
    static void runtimePermissionSelect(UiDevice uiDevice, boolean isSelectAllow) {
        Utils.waitMillis(1000);
        UiObject allow_en = uiDevice.findObject(new UiSelector().text("ALLOW"));
        UiObject allow_ch = uiDevice.findObject(new UiSelector().text("允许"));

        UiObject deny_en = uiDevice.findObject(new UiSelector().text("DENY"));
        UiObject deny_ch = uiDevice.findObject(new UiSelector().text("拒绝"));

        if (isSelectAllow) {
            if (allow_en.exists()) {
                try {
                    allow_en.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            } else if (allow_ch.exists()) {
                try {
                    allow_ch.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        } else {
            if (deny_en.exists()) {
                try {
                    deny_en.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            } else if (deny_ch.exists()) {
                try {
                    deny_ch.click();
                } catch (UiObjectNotFoundException ignored) {
                }
            }
        }
    }

    //无法完成对话框
    static void checkSdcardPermissionError(Context context, UiDevice uiDevice) {
        Utils.waitMillis(1000);
        String title = context.getString(R.string.no_delete_permission_title);
        UiObject dialogTitle = uiDevice.findObject(new UiSelector().text(title));
        if (!dialogTitle.exists()) {
            return;
        }

        String okString = context.getString(R.string.ok);
        UiObject ok = uiDevice.findObject(new UiSelector().text(okString));
        assertTrue(ok.exists());
        try {
            ok.click();
        } catch (UiObjectNotFoundException ignored) {
        }
    }

    //清除sd卡访问权限
    static void clearGallerySdPermisson(Context context, UiDevice uiDevice) {
        uiDevice.pressHome();

        int sHeight = uiDevice.getDisplayHeight();
        int sWidth = uiDevice.getDisplayWidth();
        uiDevice.swipe(sWidth / 2, sHeight / 2, 0, 0, 50);
        waitMillis(1000);

        UiObject app = uiDevice.findObject(new UiSelector().text("Gallery"));
        assertTrue(app.exists());
        try {
            app.longClick();
        } catch (UiObjectNotFoundException ignored) {

        }
        waitMillis(1000);
        UiObject app_info = uiDevice.findObject(new UiSelector().text("App info"));
        if (!app_info.exists()) {
            Log.w("Utils", "app info not found.");
            return;
        }
        try {
            app_info.click();
        } catch (UiObjectNotFoundException ignored) {

        }
        Utils.waitMillis(1000);

        UiObject storage = uiDevice.findObject(new UiSelector().text("Storage & cache"));
        assertTrue(storage.exists());
        try {
            storage.click();
        } catch (UiObjectNotFoundException ignored) {
        }
        Utils.waitMillis(1000);

        UiObject clear_access = uiDevice.findObject(new UiSelector().text("CLEAR ACCESS"));
        if (clear_access.exists()) {
            try {
                clear_access.click();
            } catch (UiObjectNotFoundException ignored) {
            }
        }
    }

    static Uri getUriFromFilePath(String filePath) {
        Cursor cursor = null;
        Uri uri = null;
        try {
            String selection = MediaStore.Images.Media.DATA + " = ?";
            String[] proj = {"_id"};
            cursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    proj, selection, new String[]{filePath}, null);
            if (cursor != null && cursor.moveToFirst()) {
                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getInt(0));
                return uri;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            com.android.gallery3d.common.Utils.closeSilently(cursor);
        }
        return uri;
    }

    static long getLatestImageAddedTime() {
        Cursor cursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED},
                null, null, MediaStore.Images.ImageColumns._ID + " DESC");
        assertNotNull(cursor);
        cursor.moveToFirst();
        String name = cursor.getString(0);
        long date_added = cursor.getLong(1);
        Log.d("Utils", "getLatestImageName: name = " + name + ", date_added = " + date_added);
        cursor.close();
        return date_added;
    }
}
