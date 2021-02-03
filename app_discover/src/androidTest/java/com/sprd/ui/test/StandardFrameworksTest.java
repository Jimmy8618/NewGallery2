package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.fw.SprdFramewoks;
import com.sprd.frameworks.StandardFrameworks;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class StandardFrameworksTest {
    private static final String TAG = StandardFrameworksTest.class.getSimpleName();

    private UiDevice mUiDevice;

    //启动图库
    @Before
    public void startGalleryActivity() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());

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
    public void getInstancesTest() {
        StandardFrameworks framework = StandardFrameworks.getInstances();
        assertTrue(framework instanceof SprdFramewoks);
    }

    @Test
    public void getInternalStoragePathTest() {
        File internal = StandardFrameworks.getInstances().getInternalStoragePath();
        assertEquals("/storage/emulated/0", internal.getAbsolutePath());
    }

    @Test
    public void getIsDrmSupportedTest() {
        assertTrue(StandardFrameworks.getInstances().getIsDrmSupported());
    }

    @Test
    public void isSupportBurstImageTest() {
        assertTrue(StandardFrameworks.getInstances().isSupportBurstImage());
    }

    @Test
    public void isSupportVoiceImageTest() {
    }

    @Test
    public void isSupportFileFlagTest() {
        assertTrue(StandardFrameworks.getInstances().isSupportFileFlag());
    }

    @Test
    public void isSupportIsDrmTest() {
        assertTrue(StandardFrameworks.getInstances().isSupportIsDrm());
    }

    @Test
    public void isSupportPathTypeTest() {
    }

    @Test
    public void isSupportShareAsVideoTest() {
        assertTrue(StandardFrameworks.getInstances().isSupportShareAsVideo());
    }

    @Test
    public void getMtpObjectsUriTest() {
        Uri mtpUri = StandardFrameworks.getInstances().getMtpObjectsUri("external");
        assertEquals("content://media/external/object", mtpUri.toString());
    }

    //需要插入sd卡测试
    @Test
    public void getExternalStorageStateTest() {
        String state = StandardFrameworks.getInstances().getExternalStorageState(getApplicationContext());
        assertEquals("mounted", state);
    }

    @Test
    public void SystemPropertiesTest() {
        boolean sdcardfs = StandardFrameworks.getInstances().getBooleanFromSystemProperties("ro.sys.sdcardfs", false);
        int value = StandardFrameworks.getInstances().getIntFromSystemProperties("sys.boot_completed", 0);
        String abi = StandardFrameworks.getInstances().getStringSystemProperties("ro.vendor.product.cpu.abilist", "arm64-v8a");
    }
}
