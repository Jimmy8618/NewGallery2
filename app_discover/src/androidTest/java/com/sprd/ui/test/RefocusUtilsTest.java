package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;

import com.android.gallery3d.util.GalleryUtils;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusData;
import com.sprd.refocus.RefocusUtils;

import android.view.WindowManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.util.Log;
import android.view.Display;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

public class RefocusUtilsTest {

    private final String TAG = RefocusUtilsTest.class.getSimpleName();

    private UiDevice mUiDevice;

    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        assertThat(mUiDevice, notNullValue());
        // Start from the home screen
        mUiDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = Utils.getLauncherPackageName();
        assertThat(launcherPackage, notNullValue());
        mUiDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), Utils.LAUNCH_TIMEOUT);

        // Launch the blueprint app
        Context context = getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        assertThat(intent, notNullValue());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);

        // Wait for the app to appear
        mUiDevice.wait(Until.hasObject(By.pkg(Utils.BASIC_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);
        Utils.checkRuntimePermission(mUiDevice, false);
    }

    @Test
    public void streamToByteTest() {
        byte[] content = streamToByte();
        assertThat(content, notNullValue());
    }

    @Test
    public void bokeAllParametersTest() {
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        byte[] content = streamToByte();
        assertThat(content, notNullValue());
        String bokeFlag = RefocusUtils.getStringValue(content, 1);
        assertEquals(bokeFlag, "BOKE");
        int dataVersion = RefocusUtils.getIntValue(content, 2);
        int mainVersion = dataVersion >> 16 & 0xFF;
        assertEquals(mainVersion, 28);
        int rotation = RefocusUtils.getIntValue(content, 5);
        assertEquals(rotation, 90);
        int param_state = RefocusUtils.getIntValue(content, 6);
        assertEquals(param_state, 0);
        int sel_y = RefocusUtils.getIntValue(content, 7);
        assertEquals(sel_y, 1512);
        int sel_x = RefocusUtils.getIntValue(content, 8);
        assertEquals(sel_x, 2016);
        int blurIntensity = RefocusUtils.getIntValue(content, 9);
        assertEquals(blurIntensity, 178);
        int depthSize = RefocusUtils.getIntValue(content, 10);
        assertEquals(depthSize, 960000);
        int depthHeight = RefocusUtils.getIntValue(content, 11);
        assertEquals(depthHeight, 600);
        int depthWidth = RefocusUtils.getIntValue(content, 12);
        assertEquals(depthWidth, 800);
        int yuvHeight = RefocusUtils.getIntValue(content, 13);
        assertEquals(yuvHeight, 3000);
        int yuvWidth = RefocusUtils.getIntValue(content, 14);
        assertEquals(yuvWidth, 4000);
        int decryptMode = RefocusUtils.getIntValue(content, 15);
        assertEquals(decryptMode, 0);
    }

    @Test
    public void loadBitmapTest() {
        Context context = getApplicationContext();
        Uri uri = initResource();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeScale();
        Bitmap bitmap = RefocusUtils.loadBitmap(context, uri, options);
        assertThat(bitmap, notNullValue());
        assertEquals(bitmap.getWidth(), 750);
        assertEquals(bitmap.getHeight(), 1000);

    }

    @Test
    public void rotateBitmapTest() {
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        byte[] oriJpg = getRefocusData().getOriJpeg();
        assertThat(oriJpg, notNullValue());
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeScale();
        Bitmap bitmap = BitmapFactory.decodeByteArray(oriJpg, 0, oriJpg.length, options);
        assertThat(bitmap, notNullValue());
        assertEquals(bitmap.getWidth(), 1000);
        assertEquals(bitmap.getHeight(), 750);

        Bitmap bitmap1 = RefocusUtils.rotateBitmap(bitmap, 90);
        assertThat(bitmap1, notNullValue());
        assertEquals(bitmap1.getWidth(), 750);
        assertEquals(bitmap1.getHeight(), 1000);
    }

    @Test
    public void recycleBitmapTest() {
        RefocusData refocusData = getRefocusData();
        byte[] oriJpeg = refocusData.getOriJpeg();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeScale();
        Bitmap bitmap = BitmapFactory.decodeByteArray(oriJpeg, 0, oriJpeg.length, options);
        RefocusUtils.recycleBitmap(bitmap);
        assertTrue(bitmap.isRecycled());
    }

    @Test
    public void jpeg2yuvTest() {
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        byte[] oriJpg = refocusData.getOriJpeg();
        byte[] mainYuv = RefocusUtils.jpeg2yuv(oriJpg);
        assertThat(mainYuv, notNullValue());
    }

    @Test
    public void yuv2bitmapTest() {
        RefocusData refocusData = getRefocusData();
        byte[] mainYuv = refocusData.getMainYuv();
        int width = refocusData.getYuvWidth();
        int height = refocusData.getYuvHeight();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeScale();
        assertThat(mainYuv, notNullValue());
        Bitmap bitmap = RefocusUtils.yuv2bitmap(mainYuv, width, height, 0, options);
        assertThat(bitmap, notNullValue());
        assertEquals(bitmap.getWidth(), 1000);
        assertEquals(bitmap.getHeight(), 750);
    }

  /*  @Test
    public void hwYuv2bitmapTest() {
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        byte[] mainYuv = refocusData.getMainYuv();
        int mainWidth = refocusData.getYuvWidth();
        int mainHeight = refocusData.getYuvHeight();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeScale();
        Bitmap bitmap = RefocusUtils.hwYuv2bitmap(mainYuv, mainWidth, mainHeight, 0, options);
        assertThat(bitmap, notNullValue());
    }

    @Test
    public void hwJpeg2yuvTest() {
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        byte[] oriJpg = refocusData.getOriJpeg();
        byte[] yuv = RefocusUtils.hwJpeg2yuv(oriJpg);
        assertThat(yuv, notNullValue());
    }*/

    @Test
    public void doUpdateBokehTest() throws FileNotFoundException {
        if (!RefocusUtils.isSupportBokeh()) {
            Log.w(TAG, "not support bokeh.");
            return;
        }
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_bokeh_BP.jpg";
        InputStream inputStream = null;
        try {
            inputStream = getApplicationContext().getContentResolver().openInputStream(initResource());
            byte[] bokehType = RefocusUtils.doUpdateBokeh(inputStream, filePath);
            assertThat(bokehType, notNullValue());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    @Test
    public void yuvToSrcPointTest() {
        RefocusData refocusData = getRefocusData();
        int yuvWidth = refocusData.getYuvWidth();
        int yuvHeight = refocusData.getYuvHeight();
        int rotation = refocusData.getRotation();
        Point yuvPoint = new Point(refocusData.getSel_x(), refocusData.getSel_y());
        Point point = RefocusUtils.yuvToSrcPoint(yuvPoint, yuvHeight, yuvWidth, rotation);
        assertThat(point, notNullValue());
    }

    @Test
    public void srcToYuvPointTest() {
        RefocusData refocusData = getRefocusData();
        int yuvWidth = refocusData.getYuvWidth();
        int yuvHeight = refocusData.getYuvHeight();
        int rotation = refocusData.getRotation();
        Point srcPoint = new Point(200, 200);
        Point point = RefocusUtils.srcToYuvPoint(srcPoint, yuvHeight, yuvWidth, rotation);
        assertThat(point, notNullValue());
    }

    @Test
    public void isNeedSRTest() {
        boolean sr = RefocusUtils.isNeedSR();
        assertFalse(sr);
    }

    @Test
    public void isRefocusTestModeTest() {
        boolean refocusTestMode = RefocusUtils.isRefocusTestMode();
        assertFalse("open debug flag", refocusTestMode);
    }

    @Test
    public void isSupportBlurTest() {
        boolean isSupportBlur = RefocusUtils.isSupportBlur();
        //assertTrue(isSupportBlur);
    }

    @Test
    public void isSupportBokehTest() {
        boolean isSupportBokeh = RefocusUtils.isSupportBokeh();
        //assertTrue(isSupportBokeh);
    }

    private RefocusData getRefocusData() {
        byte[] content = streamToByte();
        CommonRefocus commonRefocus = CommonRefocus.getInstance(content);
        RefocusData refocusData = commonRefocus.getRefocusData();
        return refocusData;
    }

    private Uri initResource() {
        Context context = getApplicationContext();
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_bokeh_BP.jpg";
        return GalleryUtils.transFileToContentUri(context, new File(filePath));
    }

    private int computeScale() {
        /*
        Context context = getApplicationContext();
        RefocusData refocusData = getRefocusData();
        assertThat(refocusData, notNullValue());
        int mSrcWidth = refocusData.getYuvWidth();
        int mSrcHeight = refocusData.getYuvHeight();
        int mScale;
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        Log.d(TAG, "Screen w: " + screenWidth + ", Screen h: " + screenHeight);
        float wScale = (float) mSrcWidth / (float) screenWidth;
        float hScale = (float) mSrcHeight / (float) screenHeight;
        float scale = Math.max(wScale, hScale);
        mScale = (int) ((scale < 1.0f) ? 1 : scale);
        Log.d(TAG, "computeScale mScale = " + mScale);
        return mScale;
        */
        return 4;
    }

    private byte[] streamToByte() {
        InputStream inputStream = null;
        byte[] content = null;
        Context context = getApplicationContext();
        Uri uri = initResource();
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            content = RefocusUtils.streamToByte(inputStream);
            assertThat(content, notNullValue());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return content;
    }


}