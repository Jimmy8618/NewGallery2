package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import androidx.test.espresso.matcher.ViewMatchers;

import com.android.gallery3d.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static androidx.test.espresso.Espresso.onView;

@RunWith(AndroidJUnit4.class)
public class SampleTest {
    private static final String TAG = SampleTest.class.getSimpleName();

    private UiDevice mUiDevice;

    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());

        // Start from the home screen
        mUiDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = getLauncherPackageName();
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
    }

    @Test
    public void clickAlbumPageItem() {
        assertThat(mUiDevice, notNullValue());

        //点击到 照片 Tab
        pressPhotoTab();
        //点击 position 为 1 的图片
        clickPhoto(1);
        //点击返回键
        mUiDevice.pressBack();
        waitMillis(2000);
        //判断是否存在删除菜单
        assertFalse(checkHasDeleteMenu());
    }

    @Test
    public void longClickAlbumPageItem() {
        assertThat(mUiDevice, notNullValue());

        //点击到 照片 Tab
        pressPhotoTab();
        //长按 position 为 1 的图片
        longClickPhoto(1);
        //判断是否出现删除菜单
        assertTrue(checkHasDeleteMenu());
    }

    private void pressPhotoTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_photo")).click();
        waitMillis(3000);
    }

    private void longClickPhoto(int position) {
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(position, longClick()));
        waitMillis(3000);
    }

    private void clickPhoto(int position) {
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(position, click()));
        waitMillis(3000);
    }

    private boolean checkHasDeleteMenu() {
        return mUiDevice.hasObject(By.res(Utils.BASIC_PACKAGE, "action_delete"));
    }

    private void pressAlbumTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        waitMillis(3000);
    }

    private String getLauncherPackageName() {
        // Create launcher Intent
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        // Use PackageManager to get the launcher package name
        PackageManager pm = getApplicationContext().getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    private void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
