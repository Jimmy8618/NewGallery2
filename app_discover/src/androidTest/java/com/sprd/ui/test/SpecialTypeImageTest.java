package com.sprd.ui.test;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.util.GalleryUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SpecialTypeImageTest {

    private UiDevice mUiDevice;

    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        assertThat(mUiDevice, notNullValue());
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
        Utils.checkRuntimePermission(mUiDevice, false);
    }

    private void updateBurstImages() {
        updateBurstImageDB("00000IMG_00000_BURST20120101082036_COVER.jpg", true);
        updateBurstImageDB("00001IMG_00001_BURST20120101082036.jpg", false);
        updateBurstImageDB("00002IMG_00002_BURST20120101082036.jpg", false);
        updateBurstImageDB("00003IMG_00003_BURST20120101082036.jpg", false);
        updateBurstImageDB("00004IMG_00004_BURST20120101082036.jpg", false);
        updateBurstImageDB("00005IMG_00005_BURST20120101082036.jpg", false);
        updateBurstImageDB("00006IMG_00006_BURST20120101082036.jpg", false);
    }

    private void updateBurstImageDB(String burstImageFile, boolean isCover) {
        String file = Utils.RES_DIR + "/BurstImage/" + burstImageFile;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, 1509491805122L);
        if (isCover) {
            values.put("file_flag", LocalImage.IMG_TYPE_MODE_BURST_COVER);
        } else {
            values.put("file_flag", LocalImage.IMG_TYPE_MODE_BURST);
        }
        try {
            getApplicationContext().getContentResolver().update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values, MediaStore.Images.ImageColumns.DATA + " = ?", new String[]{
                            file
                    });
        } catch (SecurityException ignored) {
        }
    }

    /* view audioPic */
    public void viewAudioPic() {
        Context context = getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_audio_photo_AP.jpg";
        Uri uri = GalleryUtils.transFileToContentUri(context, new File(filePath));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.android.gallery3d");
        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertThat(intent, notNullValue());
        context.startActivity(intent);
    }

    @Test
    public void testAudioPic() {
        viewAudioPic();
        waitMillis(2000);
        UiObject uiObject = mUiDevice.findObject(new UiSelector().descriptionMatches("Audio picture"));
        assertThat(uiObject, notNullValue());
        onView(withText("Audio picture")).perform(click()).check(ViewAssertions.matches(isDisplayed()));
    }

    /* view bokeh pic */
    private void viewBokehTest() {
        Context context = getApplicationContext();
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_bokeh_BP.jpg";
        Uri uri = GalleryUtils.transFileToContentUri(context, new File(filePath));
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.android.gallery3d");
        intent.setDataAndType(uri, "image/jpeg");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* test button refocus_seekbar/refocus_edit_compare/back */
    @Test
    public void testBokehSeekbarCompareBack() throws UiObjectNotFoundException {
        viewBokehTest();
        waitMillis(2000);
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        if (uiObject1 == null) {
            Log.w("SpecialTypeImageTest", "not support bokeh");
            return;
        }
        uiObject1.click();
        waitMillis(4000);
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_seekbar"));
        assertThat(uiObject2, notNullValue());
        uiObject2.fling(Direction.LEFT);
        waitMillis(500);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_edit_compare"));
        assertThat(uiObject3, notNullValue());
        onView(withId(R.id.refocus_edit_compare)).perform(click());//.check(ViewAssertions.matches(isEnabled()));
        waitMillis(2000);
        UiObject uiObject4 = mUiDevice.findObject(new UiSelector().descriptionMatches("Navigate up"));
        assertThat(uiObject4, notNullValue());
        uiObject4.click();
        waitMillis(500);
        UiObject2 uiObject5 = mUiDevice.findObject(By.text("QUIT"));
        assertThat(uiObject5, notNullValue());
        uiObject5.click();
    }

    /* test view refocus_seekbar/exit and save*/
    @Test
    public void testBokehSeekbarExitSave() throws UiObjectNotFoundException {
        viewBokehTest();
        waitMillis(2000);
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        if (uiObject1 == null) {
            Log.w("SpecialTypeImageTest", "not support bokeh");
            return;
        }
        uiObject1.click();
        waitMillis(2000);
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_seekbar"));
        assertThat(uiObject2, notNullValue());
        uiObject2.fling(Direction.LEFT);
        waitMillis(2000);
        UiObject uiObject3 = mUiDevice.findObject(new UiSelector().descriptionMatches("Navigate up"));
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        waitMillis(2000);

        UiObject2 uiObject4 = mUiDevice.findObject(By.text("SAVE AND EXIT"));
        assertThat(uiObject4, notNullValue());
        Utils.checkSdPermission(getApplicationContext(), mUiDevice);
        waitMillis(2000);
        long time = System.currentTimeMillis();
        uiObject4.click();
        waitMillis(2000);
        //判断实际的文件名是否与期望的一致
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= (time + 2000) / 1000);
    }

    /* test view refocus_seekbar/refocus_edit_save*/
    @Test
    public void testBokehSeekbarSave() {
        viewBokehTest();
        waitMillis(2000);
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        if (uiObject1 == null) {
            Log.w("SpecialTypeImageTest", "not support bokeh");
            return;
        }
        uiObject1.click();
        waitMillis(2000);
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_seekbar"));
        assertThat(uiObject2, notNullValue());
        uiObject2.fling(Direction.LEFT);
        waitMillis(1000);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_edit_save"));
        assertThat(uiObject3, notNullValue());
        Utils.checkSdPermission(getApplicationContext(), mUiDevice);
        waitMillis(2000);
        if (uiObject3.isClickable()) {
            long time = System.currentTimeMillis();
            uiObject3.click();
            waitMillis(2000);
            long date_added = Utils.getLatestImageAddedTime();
            assertTrue(date_added <= (time + 2000) / 1000);
        }
    }

    /* click the screen and save*/
    @Test
    public void testBokehClickAndSave() {
        DisplayMetrics metrics = new DisplayMetrics();
        Context context = getApplicationContext();
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        viewBokehTest();
        waitMillis(2000);
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        if (uiObject1 == null) {
            Log.w("SpecialTypeImageTest", "not support bokeh");
            return;
        }
        uiObject1.click();
        waitMillis(2000);
        mUiDevice.click(width / 2, height / 2);
        waitMillis(2000);
        UiObject2 uiObject2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "refocus_edit_save"));
        assertThat(uiObject2, notNullValue());
        if (uiObject2.isClickable()) {
            long time = System.currentTimeMillis();
            uiObject2.click();
            long date_added = Utils.getLatestImageAddedTime();
            assertTrue(date_added <= (time + 2000) / 1000);
        }
    }

    private void viewBackgroundReplace() {
        Context context = getApplicationContext();
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_boke_1_BP.jpg";
        Uri uri = GalleryUtils.transFileToContentUri(context, new File(filePath));
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.android.gallery3d");
        intent.setDataAndType(uri, "image/jpeg");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* test help and exit/ */
    @Test
    public void testBackgroundReplaceHelpExit() throws UiObjectNotFoundException {
        viewBackgroundReplace();
        waitMillis(1500);
        UiObject2 uiObject1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "imageblending"));
        if (uiObject1 == null) {
            Log.w("SpecialTypeImageTest", "not support imageblending");
            return;
        }
        uiObject1.click();
        waitMillis(8000);

        UiObject uiObject2 = mUiDevice.findObject(new UiSelector().text(getApplicationContext().getString(R.string.confirm)));
        assertThat(uiObject2, notNullValue());
        if (uiObject2.exists() && uiObject2.isEnabled()) {
            mUiDevice.pressBack();
        }
        waitMillis(3000);
        UiObject uiObject3 = mUiDevice.findObject(new UiSelector().resourceId("help"));
        assertThat(uiObject3, notNullValue());
        onView(withId(R.id.help)).perform(click());
        waitMillis(1000);
        mUiDevice.pressBack();
        waitMillis(1000);
        UiObject uiObject5 = mUiDevice.findObject(new UiSelector().descriptionMatches("Navigate up"));
        assertThat(uiObject5, notNullValue());
        uiObject5.click();
    }

    /* BackgroundReplace select and correct foreground  */
    @Test
    public void testBackgroundReplaceSelectAndCorrect() throws UiObjectNotFoundException {
        viewBackgroundReplace();
        waitMillis(1500);
        UiObject2 imageblending = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "imageblending"));
        if (imageblending == null) {
            Log.w("SpecialTypeImageTest", "not support imageblending");
            return;
        }
        imageblending.click();
        waitMillis(4000);
        UiObject confirm = mUiDevice.findObject(new UiSelector().text(getApplicationContext().getString(R.string.confirm)));
        assertThat(confirm, notNullValue());
        if (confirm.exists() && confirm.isEnabled()) {
            mUiDevice.pressBack();
        }
        waitMillis(1000);
        int width = mUiDevice.getDisplayWidth();
        int height = mUiDevice.getDisplayHeight();
        /* select foregound */
        mUiDevice.drag(width / 2 - 200, height / 2 - 300, width / 2 + 200, height / 2 + 300, 0);
        Utils.waitMillis(1000);

        UiObject2 selectButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok"));
        assertThat(selectButton, notNullValue());
        selectButton.click();
        Utils.waitMillis(2000);
        /* select foregound */
        /* fix subject */
        mUiDevice.drag(width / 2 - 200, height / 2, width / 2 + 200, height / 2, 20);
        Utils.waitMillis(1000);
        UiObject2 fixSubject = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fix_subject_button"));
        assertThat(fixSubject, notNullValue());
        fixSubject.click();
        Utils.waitMillis(1000);
        /* fix subject */

        /* update background */
        mUiDevice.drag(width / 2 - 200, height / 2, width / 2 + 200, height / 2, 20);
        Utils.waitMillis(1000);
        UiObject2 fixBackground = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fix_background_button"));
        assertThat(fixBackground, notNullValue());
        fixBackground.click();
        Utils.waitMillis(1000);
        /* update background */

        /* undo */
        mUiDevice.drag(width / 2 - 200, height / 2, width / 2 + 200, height / 2, 0);
        Utils.waitMillis(1000);
        UiObject2 undoButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "undo"));
        assertThat(undoButton, notNullValue());
        undoButton.click();
        Utils.waitMillis(1000);
        /* undo */

        UiObject2 helpButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "help"));
        assertThat(helpButton, notNullValue());
        helpButton.click();
        Utils.waitMillis(1000);
        mUiDevice.pressBack();

        Utils.waitMillis(1000);
        UiObject exitButton = mUiDevice.findObject(new UiSelector().descriptionContains("Navigate up"));
        assertThat(exitButton, notNullValue());
        exitButton.click();
        Utils.waitMillis(2000);
    }

    /* edit background */
    @Test
    public void testBackgroundReplaceEditAndSave() throws UiObjectNotFoundException {
        viewBackgroundReplace();
        waitMillis(1500);
        UiObject2 imageblending = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "imageblending"));
        if (imageblending == null) {
            Log.w("SpecialTypeImageTest", "not support imageblending");
            return;
        }
        imageblending.click();
        waitMillis(8000);
        UiObject confirmButton = mUiDevice.findObject(new UiSelector().text(getApplicationContext().getString(R.string.confirm)));
        assertThat(confirmButton, notNullValue());
        if (confirmButton.exists() && confirmButton.isEnabled()) {
            mUiDevice.pressBack();
        }
        waitMillis(1000);
        int width = mUiDevice.getDisplayWidth();
        int height = mUiDevice.getDisplayHeight();

        /* select foregound */
        mUiDevice.drag(width / 2 - 200, height / 2 - 300, width / 2 + 200, height / 2 + 300, 0);
        Utils.waitMillis(1000);

        UiObject2 selectButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok"));
        assertThat(selectButton, notNullValue());
        selectButton.click();
        Utils.waitMillis(3000);

        /* first select foreground,may show tip */
        UiObject tipObject = mUiDevice.findObject(new UiSelector().text(getApplicationContext().getString(R.string.confirm)));
        assertThat(tipObject, notNullValue());
        if (tipObject.exists()) {
            android.util.Log.d("jiaqiang", "tipObject ");
            mUiDevice.pressBack();
        }
        Utils.waitMillis(500);
        /* select foregound */
        UiObject2 selectButton1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok"));
        assertThat(selectButton1, notNullValue());
        selectButton1.click();
        Utils.waitMillis(2000);
        /* select punch */
        UiObject2 recycleview = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "recycleview"));
        assertThat(recycleview, notNullValue());
        onView(ViewMatchers.withId(R.id.recycleview)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(2000);

        /* save picture */
        UiObject2 saveButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "save"));
        assertThat(saveButton, notNullValue());
        long time = System.currentTimeMillis();
        saveButton.click();
        Utils.waitMillis(2000);
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= (time + 2000) / 1000);
    }

    /* edit background ,select background effect/exit/save*/
    @Test
    public void testBackgroundReplaceEditExitAndSave() throws UiObjectNotFoundException {
        viewBackgroundReplace();
        waitMillis(1500);
        UiObject2 imageblending = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "imageblending"));
        if (imageblending == null) {
            Log.w("SpecialTypeImageTest", "not support imageblending");
            return;
        }
        imageblending.click();
        waitMillis(8000);
        UiObject confirmButton = mUiDevice.findObject(new UiSelector().text(getApplicationContext().getString(R.string.confirm)));
        assertThat(confirmButton, notNullValue());
        if (confirmButton.exists() && confirmButton.isEnabled()) {
            mUiDevice.pressBack();
        }
        waitMillis(1000);
        int width = mUiDevice.getDisplayWidth();
        int height = mUiDevice.getDisplayHeight();

        /* select foregound */
        mUiDevice.drag(width / 2 - 200, height / 2 - 300, width / 2 + 200, height / 2 + 300, 0);
        Utils.waitMillis(1000);

        UiObject2 selectButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok"));
        assertThat(selectButton, notNullValue());
        selectButton.click();
        Utils.waitMillis(2000);
        /* select foregound */
        UiObject2 selectButton1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok"));
        assertThat(selectButton1, notNullValue());
        selectButton1.click();
        Utils.waitMillis(2000);
        /* select punch */
        UiObject2 recycleview = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "recycleview"));
        assertThat(recycleview, notNullValue());
        onView(ViewMatchers.withId(R.id.recycleview)).perform(RecyclerViewActions.actionOnItemAtPosition(1,
                click()));
        Utils.waitMillis(1000);

        /* press exit button */
        UiObject exitButton = mUiDevice.findObject(new UiSelector().descriptionContains("Navigate up"));
        assertThat(exitButton, notNullValue());
        exitButton.click();
        Utils.waitMillis(1000);

        /* save and exit */
        UiObject2 save_and_exit = mUiDevice.findObject(By.text("SAVE AND EXIT"));
        assertThat(save_and_exit, notNullValue());
        Utils.checkSdPermission(getApplicationContext(), mUiDevice);
        Utils.waitMillis(2000);
        long time = System.currentTimeMillis();
        save_and_exit.click();
        Utils.waitMillis(4000);
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= (time + 4000) / 1000);

        UiObject navigate_up = mUiDevice.findObject(new UiSelector().descriptionContains("Navigate up"));
        assertThat(navigate_up, notNullValue());
        Utils.waitMillis(2000);
        navigate_up.click();
    }


    private void viewBurstPic() throws UiObjectNotFoundException {
        updateBurstImages();
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        int sHeight = mUiDevice.getDisplayHeight();
        UiObject burstAlbum;
        int count = 100;
        do {
            burstAlbum = mUiDevice.findObject(new UiSelector().text("BurstImage"));
            count--;
            if (burstAlbum.exists()) {
                break;
            }
            mUiDevice.swipe(0, sHeight / 2, 0, sHeight / 2 - 500, 100);
        } while (count >= 0);

        assertTrue(burstAlbum.exists());

        burstAlbum.click();

        waitMillis(2000);

        UiObject2 burstImage = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "image_type_1"));
        assertThat(burstImage, notNullValue());
        burstImage.click();
    }

    /* select items and exit*/
    @Test
    public void testBurstViewSelectAndExit() throws UiObjectNotFoundException {
        viewBurstPic();
        waitMillis(2000);
        UiObject2 object1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        assertThat(object1, notNullValue());
        object1.click();
        waitMillis(2000);
        UiObject2 object2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "burst_filmstrip_view"));
        assertThat(object2, notNullValue());
        onView(withId(R.id.burst_filmstrip_view)).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        waitMillis(1000);
        onView(withId(R.id.burst_filmstrip_view)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        waitMillis(1000);
        UiObject uiObject3 = mUiDevice.findObject(new UiSelector().descriptionMatches("Navigate up"));
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        waitMillis(1000);
    }

    /* SaveAll*/
    @Test
    public void testBurstViewSaveAll() throws UiObjectNotFoundException {
        viewBurstPic();
        waitMillis(2000);
        UiObject2 object1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        object1.click();
        waitMillis(2000);
        UiObject2 object2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "burst_filmstrip_view"));
        assertThat(object2, notNullValue());
        onView(withId(R.id.burst_filmstrip_view)).perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));
        waitMillis(1000);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_complete"));
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        waitMillis(2000);
        String saveAll = getApplicationContext().getString(R.string.keep_all);
        UiObject uiObject4 = mUiDevice.findObject(new UiSelector().text(saveAll));
        uiObject4.click();
        waitMillis(1000);
        mUiDevice.pressBack();
    }

    /* save one */
    @Test
    public void testBurstViewSaveOne() throws UiObjectNotFoundException {
        viewBurstPic();
        waitMillis(2000);
        UiObject2 object1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        object1.click();
        waitMillis(2000);
        UiObject2 object2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "burst_filmstrip_view"));
        assertThat(object2, notNullValue());
        onView(withId(R.id.burst_filmstrip_view)).perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));
        waitMillis(1000);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_complete"));
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        waitMillis(2000);
        String saveOne = getApplicationContext().getResources().getQuantityString(R.plurals.keep_selected, 1, 1);
        UiObject uiObject4 = mUiDevice.findObject(new UiSelector().text(saveOne));
        uiObject4.click();
        waitMillis(1000);
        mUiDevice.pressBack();
    }

    /* delete one */
    @Test
    public void testBurstViewDeleteOne() throws UiObjectNotFoundException {
        viewBurstPic();
        waitMillis(2000);
        UiObject2 object1 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "icon_button"));
        object1.click();
        waitMillis(2000);
        UiObject2 object2 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "burst_filmstrip_view"));
        assertThat(object2, notNullValue());
        onView(withId(R.id.burst_filmstrip_view)).perform(RecyclerViewActions.actionOnItemAtPosition(2, click()));
        waitMillis(1000);
        UiObject2 uiObject3 = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_complete"));
        assertThat(uiObject3, notNullValue());
        uiObject3.click();
        waitMillis(2000);
        String saveAll = getApplicationContext().getResources().getQuantityString(R.plurals.delete_selected, 1, 1);
        UiObject uiObject4 = mUiDevice.findObject(new UiSelector().text(saveAll));
        uiObject4.click();
        waitMillis(1000);
    }

    /* test Gif Pic */
    public void viewGifPic() {
        Context context = getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(Utils.BASIC_PACKAGE);
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "IMG_GIF.gif";
        Uri uri = GalleryUtils.transFileToContentUri(context, new File(filePath));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.android.gallery3d");
        intent.setDataAndType(uri, "image/gif");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertThat(intent, notNullValue());
        context.startActivity(intent);
    }

    @Test
    public void testGifPic() {
        viewGifPic();
        waitMillis(2000);
        UiObject2 uiObject = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertThat(uiObject, notNullValue());
        waitMillis(1000);
    }

    public void dummy() {
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
