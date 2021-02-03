package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.File;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BokenSaveManagerTest {
    private UiDevice mUiDevice;

    //启动图库
    @Before
    public void startGalleryActivity() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        //清除sd卡访问权限
        Utils.clearGallerySdPermisson(getApplicationContext(), mUiDevice);
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

    public void openBokenPicFromSd() {
        Context context = getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        String filePath = StandardFrameworks.getInstances().getExternalStoragePath(context).getAbsolutePath()
                + "/SpecialImage/IMG_bokeh_BP.jpg";
        Uri uri = GalleryUtils.transFileToContentUri(context, new File(filePath));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertThat(intent, notNullValue());
        context.startActivity(intent);
    }

    @Test
    public void testSdPermission() {
        Context context = getApplicationContext();

        openBokenPicFromSd();
        //第一个权限框点击取消
        Utils.waitMillis(1000);
        Utils.checkSdPermissionSelect(context, mUiDevice, false);

        //第一个权限框点击确定
        Utils.waitMillis(1000);
        openBokenPicFromSd();
        Utils.checkSdPermissionSelect(context, mUiDevice, true);
        //第二个权限框点击拒绝
        Utils.waitMillis(1000);
        Utils.runtimePermissionSelect(mUiDevice, false);
        Utils.waitMillis(1000);
        Utils.checkSdcardPermissionError(context, mUiDevice);

        //权限框都选择允许
        Utils.waitMillis(1000);
        openBokenPicFromSd();
        Utils.checkSdPermission(context, mUiDevice);
    }
}
