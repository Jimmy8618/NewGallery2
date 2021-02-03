package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;;
import androidx.test.espresso.contrib.RecyclerViewActions;
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
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/* Interaction with sms/email/wallpaper */
public class InteractionWithOtherAppsTest {
    private UiDevice mUiDevice;
    private final String WALLPAPER_PACKAGE = "com.android.wallpapercropper";

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

    public void pressPhotoTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_photo")).click();
        Utils.waitMillis(3000);
    }

    @Test
    public void sharePicForSmsTest() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //点击 position 为 1 位置的图片, 应该进入浏览界面
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(1000);
        //判断是否存在gl_root_view, 表示成功进入了浏览界面
        UiObject2 glRoot = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertNotNull(glRoot);

        UiObject2 shareMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "share"));
        assertThat(shareMenu, notNullValue());
        shareMenu.click();

        UiObject smsOption = mUiDevice.findObject(new UiSelector().text("Messaging"));
        assertThat(smsOption, notNullValue());
        //smsOption.click();

        //UiObject2 newMessage = mUiDevice.findObject(By.text("New message"));
        //assertThat(newMessage, notNullValue());
    }

    @Test
    public void sharePicForEmailTest() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //点击 position 为 1 位置的图片, 应该进入浏览界面
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(1000);
        //判断是否存在gl_root_view, 表示成功进入了浏览界面
        UiObject2 glRoot = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertNotNull(glRoot);

        UiObject2 shareMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "share"));
        assertThat(shareMenu, notNullValue());
        shareMenu.click();
        UiObject emailOption = mUiDevice.findObject(new UiSelector().text("Email"));
        assertThat(emailOption, notNullValue());
        /*
        emailOption.click();

        Utils.waitMillis(1500);
        Utils.checkRuntimePermission(mUiDevice, false);
        Utils.waitMillis(1500);
        UiObject emailTitle = mUiDevice.findObject(new UiSelector().text("Account setup"));
        assertTrue(emailTitle.exists());
        mUiDevice.pressBack();
        */
    }

    @Test
    public void setWallpaperTest() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //点击 position 为 1 位置的图片, 应该进入浏览界面
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(2000);
        //判断是否存在gl_root_view, 表示成功进入了浏览界面
        UiObject2 glRoot = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertNotNull(glRoot);

        int sWidth = mUiDevice.getDisplayWidth();
        int sHeight = mUiDevice.getDisplayHeight();

        UiObject2 toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));

        if (toolbar == null) {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
        }

        assertNotNull(toolbar);

        //点击右上角三个点, 展开菜单
        mUiDevice.pressMenu();
        Utils.waitMillis(3000);

        String setImage = getApplicationContext().getString(R.string.set_image);
        UiObject uiObject = mUiDevice.findObject(new UiSelector().text(setImage));
        assertTrue(uiObject.exists());
        uiObject.click();
        Utils.waitMillis(2000);

        UiObject selectWallpaer = mUiDevice.findObject(new UiSelector().text("Wallpaper"));
        assertTrue(selectWallpaer.exists());
        selectWallpaer.click();
        Utils.waitMillis(1000);

        UiObject set_wallpaper_button = mUiDevice.findObject(new UiSelector().text("SET WALLPAPER"));
        assertThat(set_wallpaper_button, notNullValue());
        set_wallpaper_button.click();
        Utils.waitMillis(1000);

        UiObject setHomeSceen = mUiDevice.findObject(new UiSelector().text("Home screen"));
        assertTrue(setHomeSceen.exists());
        setHomeSceen.click();

        Utils.waitMillis(1000);
        mUiDevice.pressBack();
    }

}