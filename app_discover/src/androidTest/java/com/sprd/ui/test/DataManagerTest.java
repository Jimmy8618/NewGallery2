package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.app.GalleryAppImpl;

import org.junit.Before;
import org.junit.Test;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class DataManagerTest {
    private static final String TAG = DataManagerTest.class.getSimpleName();

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
    public void getMediaObjectTest() {
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/local/image"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/local/video"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/local/all"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/camera/all/image"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/camera/all/video"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/camera/all"));
        assertNotNull(GalleryAppImpl.getApplication().getDataManager().getMediaObject("/trash/all"));
    }
}
