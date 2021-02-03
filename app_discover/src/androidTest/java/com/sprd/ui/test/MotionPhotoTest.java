package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class MotionPhotoTest {
    private UiDevice mUiDevice;

    //启动图库
    @Before
    public void launchGalleryActivity() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());

        // Start from the home screen
        mUiDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = Utils.getLauncherPackageName();
        assertThat(launcherPackage, notNullValue());
        mUiDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), Utils.LAUNCH_TIMEOUT);

        Context context = getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        assertThat(intent, notNullValue());
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg")),
                "image/jpeg");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        mUiDevice.wait(Until.hasObject(By.pkg(Utils.BASIC_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);

        Utils.checkRuntimePermission(mUiDevice, false);
    }

    //播放 motion photo
    @Test
    public void playMotionPhotoTest() {
        Utils.waitMillis(2000);

        int sWidth = mUiDevice.getDisplayWidth();
        int sHeight = mUiDevice.getDisplayHeight();

        UiObject2 toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));

        if (toolbar == null) {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
        }

        assertNotNull(toolbar);

        UiObject2 playMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_play_motion_photo"));
        assertNotNull(playMenu);
        playMenu.click();

        Utils.waitMillis(5000);
    }

    //进入 优选照片
    @Test
    public void enterPreferredPhoto() {
        Utils.waitMillis(2000);

        int sWidth = mUiDevice.getDisplayWidth();
        int sHeight = mUiDevice.getDisplayHeight();

        UiObject2 toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));

        if (toolbar == null) {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
        }

        assertNotNull(toolbar);

        UiObject2 icon_button = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        assertNotNull(icon_button);
        icon_button.click();

        Utils.waitMillis(15000);
    }

    //保存优选照片
    @Test
    public void savePreferredPhoto() {
        enterPreferredPhoto();

        UiObject2 save = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_save"));
        assertNotNull(save);
        save.click();

        Utils.checkSdPermission(getApplicationContext(), mUiDevice);

        Utils.waitMillis(5000);

        //检测保存后的图片是否带有exif信息
        //跟原图的exif信息比较一个值
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", "0, 1").build();

        Cursor cursor = getApplicationContext().getContentResolver().query(uri,
                new String[]{
                        MediaStore.Images.ImageColumns._ID
                }, null, null, MediaStore.Images.ImageColumns._ID + " DESC");

        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());

        cursor.moveToFirst();
        int id = cursor.getInt(0);
        cursor.close();

        Uri savedUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendPath(String.valueOf(id)).build();
        Log.d("MotionPhotoTest", "savePreferredPhoto savedUri = " + savedUri);

        InputStream is = null;
        try {
            is = getApplicationContext().getContentResolver().openInputStream(savedUri);
            assertNotNull(is);
            ExifInterface oldExif = new ExifInterface(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
            ExifInterface newExif = new ExifInterface(is);
            String oldMaker = oldExif.getAttribute(ExifInterface.TAG_MAKE);
            String newMaker = newExif.getAttribute(ExifInterface.TAG_MAKE);
            assertNotNull(newMaker);
            assertEquals(oldMaker, newMaker);
        } catch (IOException e) {
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
