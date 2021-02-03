package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;

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

public class InteractionWithContactTest {
    private UiDevice mUiDevice;
    private final String CONTACT_PACKAGE = "com.android.contacts";

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

        Context context = getApplicationContext();
        final Intent contactIntent = context.getPackageManager()
                .getLaunchIntentForPackage(CONTACT_PACKAGE);
        assertThat(contactIntent, notNullValue());
        contactIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(contactIntent);

        // Wait for the app to appear
        mUiDevice.wait(Until.hasObject(By.pkg(CONTACT_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);
        Utils.checkRuntimePermission(mUiDevice, false);
        Utils.waitMillis(1000);
        addContact();
        Utils.waitMillis(2000);
        // Launch the blueprint app
        final Intent galleryIntent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        assertThat(galleryIntent, notNullValue());
        galleryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(galleryIntent);

        // Wait for the app to appear
        mUiDevice.wait(Until.hasObject(By.pkg(Utils.BASIC_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);
        Utils.checkRuntimePermission(mUiDevice, false);
    }

    public void addContact() {
        mUiDevice.findObject(By.res(CONTACT_PACKAGE, "floating_action_button")).click();
        Utils.waitMillis(1000);
        mUiDevice.findObject(By.res(CONTACT_PACKAGE, "editors")).getChildren().get(0).setText("SprdTest");
        Utils.waitMillis(3000);
        mUiDevice.findObject(By.res(CONTACT_PACKAGE, "editor_menu_save_button")).click();

    }

    private void pressPhotoTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_photo")).click();
        Utils.waitMillis(3000);
    }

    @Test
    public void setWallpaperForContactTest() throws UiObjectNotFoundException {
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

        String setPicture = getApplicationContext().getString(R.string.set_image);
        UiObject setImageButton = mUiDevice.findObject(new UiSelector().text(setPicture));
        assertTrue(setImageButton.exists());
        setImageButton.click();
        Utils.waitMillis(2000);

        UiObject selectContact = mUiDevice.findObject(new UiSelector().text("Contact photo"));
        assertTrue(selectContact.exists());
        if (selectContact.exists()) {
            selectContact.click();
        }
        UiObject contactTitle = mUiDevice.findObject(new UiSelector().text("Choose a contact"));
        if (contactTitle.exists()) {
            UiObject2 selectContactItem = mUiDevice.findObject(By.res(CONTACT_PACKAGE, "cliv_name_textview"));
            selectContactItem.click();
        }

        Utils.waitMillis(2000);
        onView(ViewMatchers.withText("Save")).perform(click());
        Utils.waitMillis(2000);
        mUiDevice.pressBack();
    }

}