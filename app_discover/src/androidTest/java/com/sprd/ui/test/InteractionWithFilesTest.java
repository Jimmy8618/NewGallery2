package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;

import org.junit.Before;
import org.junit.Test;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class InteractionWithFilesTest {

    private UiDevice mUiDevice;
    private final String FILES_PACKAGE = "com.google.android.documentsui";
    private final String FILES_PACKAGE2 = "com.google.android.apps.nbu.files";
    private final String FILES_PACKAGE3 = "com.android.documentsui";
    private String pac;

    @Before
    public void startActivityForFileManager() {
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
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(FILES_PACKAGE);
        pac = FILES_PACKAGE;
        if (intent == null) {
            intent = context.getPackageManager()
                    .getLaunchIntentForPackage(FILES_PACKAGE2);
            pac = FILES_PACKAGE2;
        }
        if (intent == null) {
            intent = context.getPackageManager()
                    .getLaunchIntentForPackage(FILES_PACKAGE3);
            pac = FILES_PACKAGE3;
        }
        assertThat(intent, notNullValue());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);
    }

    @Test
    public void viewPicFromFilesTest() throws UiObjectNotFoundException {
        UiObject uiObject = mUiDevice.findObject(new UiSelector().descriptionMatches("Show roots"));
        assertThat(uiObject, notNullValue());
        try {
            uiObject.click();
        } catch (UiObjectNotFoundException e) {
            Log.w("InteractionWithFilesTest", "Show roots not found");
            return;
        }
        Utils.waitMillis(1000);
        /* click images ,enter into the file direcotry include pics*/
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(pac, "roots_list"));
        assertThat(uiObject1, notNullValue());
        UiObject2 images = uiObject1.getChildren().get(1);
        assertThat(images, notNullValue());
        images.click();
        Utils.waitMillis(1500);
        /* click the first directory ,show preview pics*/
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(pac, "dir_list"));
        assertThat(uiObject2, notNullValue());
        UiObject2 uiObject3 = uiObject2.getChildren().get(0);
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        Utils.waitMillis(1500);
        /* click the first pic ,enter into gallery to view*/
        UiObject2 uiObject4 = mUiDevice.findObject(By.res(pac, "dir_list"));
        assertThat(uiObject4, notNullValue());
        UiObject2 uiObject5 = uiObject4.getChildren().get(0);
        assertThat(uiObject5, notNullValue());
        uiObject5.click();
        Utils.waitMillis(1500);
        UiObject2 uiObject6 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertThat(uiObject6, notNullValue());
        mUiDevice.pressBack();
    }
}