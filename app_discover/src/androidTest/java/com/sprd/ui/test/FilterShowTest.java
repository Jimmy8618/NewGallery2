package com.sprd.ui.test;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;

import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FilterShowTest {
    private static final String TAG = "FilterShowTest";
    private UiDevice mUiDevice;

    @Before
    public void launchFilterShowTest() {
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        mUiDevice.pressHome();

        String filePath = Utils.RES_DIR + "/FilterShow/0.jpg";
        Log.d(TAG, "launchFilterShowTest: filePath=" + filePath);
        Uri uri = Utils.getUriFromFilePath(filePath);
        Log.d(TAG, "launchFilterShowTest: uri=" + uri);

        Intent intent = new Intent(GalleryUtils.ACTION_NEXTGEN_EDIT);
        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplicationContext().startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(Utils.BASIC_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);

        Utils.checkRuntimePermission(mUiDevice, true);
    }

    @Test
    public void addBorderTest() throws UiObjectNotFoundException {
        Utils.waitMillis(5000);
        UiObject2 borderButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "borderButton"));
        assertNotNull(borderButton);
        borderButton.click();
        Utils.waitMillis(2000);
        UiObject border_4X5 = mUiDevice.findObject(new UiSelector().description("4X5"));
        border_4X5.click();
        Utils.waitMillis(2000);
        UiObject2 saveButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "saveButton"));
        assertNotNull(saveButton);
        long time = System.currentTimeMillis();
        String expectedName = "IMG" + new SimpleDateFormat("_yyyyMMdd_HHmmss").
                format(new Date(time)) + ".jpg";
        Log.d(TAG, "addBorderTest: expectedName = " + expectedName + ", time = " + time);
        saveButton.click();
        Utils.waitMillis(2000);
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= ((time + 2000) / 1000));
    }

    @Test
    public void addFiltersTest() throws UiObjectNotFoundException {
        Utils.waitMillis(5000);
        UiObject2 fxButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fxButton"));
        Log.d(TAG, "addFiltersTest: fxButton=" + fxButton);
        assertNotNull(fxButton);
        fxButton.click();
        Utils.waitMillis(2000);
        String fx_bw = getApplicationContext().getString(R.string.ffx_bw_contrast);
        UiObject border_4X5 = mUiDevice.findObject(new UiSelector().description(fx_bw));
        border_4X5.click();
        Utils.waitMillis(2000);
        UiObject2 saveButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "saveButton"));
        assertNotNull(saveButton);

        long time = System.currentTimeMillis();
//        String expectedName = "IMG" + new SimpleDateFormat("_yyyyMMdd_HHmmss").
//                format(new Date(System.currentTimeMillis())) + ".jpg";
        saveButton.click();
        Utils.waitMillis(2000);
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= (time + 2000) / 1000);
    }
}
