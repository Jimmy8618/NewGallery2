package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.sprd.refocus.RefocusUtils;

import org.junit.Before;
import org.junit.Test;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class InteractionWithCameraTest {
    public final String CAMERA_PACKAGE = "com.android.camera2";
    private UiDevice mUiDevice;

    @Before
    public void startActivityForCamera() {

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
                .getLaunchIntentForPackage(CAMERA_PACKAGE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThat(intent, notNullValue());
        context.startActivity(intent);

        // Wait for the app to appear
        mUiDevice.wait(Until.hasObject(By.pkg(CAMERA_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);
        Utils.checkRuntimePermission(mUiDevice, false);

    }

    /* test common pic */
    @Test
    public void viewAnormalPicTest() throws UiObjectNotFoundException {
        UiObject uiObject = mUiDevice.findObject(new UiSelector().text("START CAPTURE"));
        assertThat(uiObject, notNullValue());
        if (uiObject.exists() && uiObject.isEnabled()) {
            uiObject.click();
        }
        if (RefocusUtils.isSupportBokeh()) {
            UiObject2 uiObject2 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "blur_f_number_seekbar"));
            UiObject2 uiObject3 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "refocus_toggle_button_dream"));
            if (uiObject3 == null) {
                Log.w("InteractionWithCameraTest", "can not find refocus toggle button.");
                return;
            }
            if (uiObject2 != null) {
                uiObject3.click();
            }
        }
        UiObject2 uiObject4 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "shutter_button"));
        assertThat(uiObject4, notNullValue());
        if (uiObject4.isEnabled()) {
            uiObject4.click();
        }
        Utils.waitMillis(4000);
        UiObject2 uiObject5 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "rounded_thumbnail_view"));
        assertThat(uiObject5, notNullValue());
        if (uiObject5.isEnabled()) {
            uiObject5.click();
        }
        Utils.waitMillis(1500);
        Utils.checkRuntimePermission(mUiDevice, false);
        Utils.waitMillis(1500);
        UiObject2 uiObject6 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertThat(uiObject6, notNullValue());
        uiObject6.click();
        Utils.waitMillis(2000);
        mUiDevice.pressBack();
    }

    @Test
    public void viewBokehPicTest() {
        if (!RefocusUtils.isSupportBokeh()) return;
        UiObject2 seekbarButton = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "blur_f_number_seekbar"));
        if (seekbarButton == null) {
            UiObject2 uiObject1 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "refocus_toggle_button_dream"));
            if (uiObject1 == null) {
                Log.w("InteractionWithCameraTest", "can not find refocus toggle button.");
                return;
            }
            if (uiObject1 != null) {
                uiObject1.click();
            }
        }
        Utils.waitMillis(10000);
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "shutter_button"));
        assertThat(uiObject2, notNullValue());
        if (uiObject2.isClickable()) {
            uiObject2.click();
        }
        Utils.waitMillis(10000);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(CAMERA_PACKAGE, "rounded_thumbnail_view"));
        assertThat(uiObject3, notNullValue());
        if (uiObject3.isEnabled()) {
            uiObject3.click();
        }
        Utils.waitMillis(5000);
        Utils.checkRuntimePermission(mUiDevice, false);
        Utils.waitMillis(6000);
        UiObject2 uiObject4 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        assertThat(uiObject4, notNullValue());
        Utils.waitMillis(2000);
        mUiDevice.pressBack();
    }
}