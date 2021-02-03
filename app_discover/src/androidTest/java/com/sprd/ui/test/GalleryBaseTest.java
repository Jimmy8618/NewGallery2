package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
import org.junit.runner.RunWith;

import java.util.List;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryBaseTest {
    private static final String TAG = GalleryBaseTest.class.getSimpleName();

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

    //点击 "照片" Tab测试
    @Test
    public void pressPhotoTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_photo")).click();
        Utils.waitMillis(3000);
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览
    @Test
    public void enterPhotoView() {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //点击 position 为 1 位置的图片, 应该进入浏览界面
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(3000);
        //判断是否存在gl_root_view, 表示成功进入了浏览界面
        UiObject2 glRoot = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "gl_root_view"));
        assertNotNull(glRoot);
    }

    //点击 "照片" Tab, 长按一张图片, 看是否会弹出 Action Mode Bar
    @Test
    public void longClickPhoto() {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片, 应该弹出 Action Mode Bar
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //判断是否存在action_mode_bar
        UiObject2 actionMode = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_mode_bar"));
        assertNotNull(actionMode);
    }

    //点击 "照片" Tab, 长按一张图片, 看是否存在删除菜单
    @Test
    public void longClickPhotoShowDeleteMenu() {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片, 应该存在删除菜单
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //判断是否存在删除菜单
        UiObject2 deleteMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_delete"));
        assertNotNull(deleteMenu);
    }

    //点击 "照片" Tab, 长按一张图片, 看是否存在详细信息菜单
    @Test
    public void longClickPhotoShowDetailInfoMenu() {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片, 应该存在删除菜单
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在详细信息菜单
        String detailTitle = getApplicationContext().getString(R.string.details);
        UiObject detailMenu = mUiDevice.findObject(new UiSelector().text(detailTitle));
        assertTrue(detailMenu.exists());
    }

    //点击 "照片" Tab, 长按一张图片, 若存在 "分享" 菜单,点击可以正常分享
    @Test
    public void clickPhotoPageShareMenu() {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片, 应该存在删除菜单
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //判断是否存在 "分享" 菜单
        UiObject2 shareMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_share"));

        if (shareMenu != null) {
            shareMenu.click();
        }

        Utils.waitMillis(3000);

        mUiDevice.pressHome();
    }

    //点击 "照片" Tab, 长按一张图片, 点击 "删除" 菜单, 可以正常删除
    @Test
    public void clickPhotoPageDeleteMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片, 应该存在删除菜单
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //点击 "删除" 菜单
        UiObject2 deleteMenu = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_delete"));
        deleteMenu.click();
        Utils.waitMillis(3000);

        String okString = getApplicationContext().getString(R.string.ok);
        UiObject ok = mUiDevice.findObject(new UiSelector().text(okString));

        assertTrue(ok.exists());

        ok.click();

        Utils.checkSdPermission(getApplicationContext(), mUiDevice);
        Utils.waitMillis(3000);
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"添加到相册"菜单, 点击菜单
    @Test
    public void longClickPhotoShowAddToAlbumMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在添加到相册菜单
        String addToAlbumTitle = getApplicationContext().getString(R.string.add_to_album);
        UiObject addToAlbumMenu = mUiDevice.findObject(new UiSelector().text(addToAlbumTitle));

        if (addToAlbumMenu.exists()) {
            addToAlbumMenu.click();
            Utils.waitMillis(3000);
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"编辑"菜单, 则点击进入编辑
    @Test
    public void longClickPhotoClickEditMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在编辑菜单
        String editTitle = getApplicationContext().getString(R.string.edit);
        UiObject editMenu = mUiDevice.findObject(new UiSelector().text(editTitle));

        if (editMenu.exists()) {
            editMenu.click();
            Utils.waitMillis(3000);
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"旋转"菜单, 则点击旋转
    @Test
    public void longClickPhotoClickRotateCWMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在旋转菜单
        String rotateTitle = getApplicationContext().getString(R.string.rotate_right);
        UiObject rotateMenu = mUiDevice.findObject(new UiSelector().text(rotateTitle));

        if (rotateMenu.exists()) {
            rotateMenu.click();
            Utils.waitMillis(3000);
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"旋转"菜单, 则点击旋转
    @Test
    public void longClickPhotoClickRotateCCWMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在旋转菜单
        String rotateTitle = getApplicationContext().getString(R.string.rotate_left);
        UiObject rotateMenu = mUiDevice.findObject(new UiSelector().text(rotateTitle));

        if (rotateMenu.exists()) {
            rotateMenu.click();
            Utils.waitMillis(3000);
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"裁剪"菜单, 则点击裁剪
    @Test
    public void longClickPhotoClickCropMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在裁剪菜单
        String cropTitle = getApplicationContext().getString(R.string.crop_action);
        UiObject cropMenu = mUiDevice.findObject(new UiSelector().text(cropTitle));

        if (cropMenu.exists()) {
            cropMenu.click();
            Utils.waitMillis(3000);
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"将图片设置为"菜单, 则点击
    @Test
    public void longClickPhotoClickSetAsMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在将图片设置为菜单
        String setAsTitle = getApplicationContext().getString(R.string.set_image);
        UiObject setAsMenu = mUiDevice.findObject(new UiSelector().text(setAsTitle));

        if (setAsMenu.exists()) {
            setAsMenu.click();
            Utils.waitMillis(3000);

            mUiDevice.pressHome();
        }
    }

    //点击 "照片" Tab, 长按一张图片, 若存在"打印"菜单, 则点击
    @Test
    public void longClickPhotoClickPrintMenu() throws UiObjectNotFoundException {
        //点击进入 "照片" 界面
        pressPhotoTab();
        //长按 position 为 1 位置的图片
        onView(ViewMatchers.withId(R.id.recycler_view_album_page_v2))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, longClick()));
        Utils.waitMillis(3000);
        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);
        //判断是否存在打印菜单
        String printTitle = getApplicationContext().getString(R.string.print_image);
        UiObject printMenu = mUiDevice.findObject(new UiSelector().text(printTitle));

        if (printMenu.exists()) {
            printMenu.click();
            Utils.waitMillis(3000);

            mUiDevice.pressHome();
        }
    }

    //点击 "相册" Tab测试
    @Test
    public void pressAlbumTab() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);
    }

    //点击 "相册" Tab, 存在"所有图片" 和 "最近删除"
    @Test
    public void hasAllAndRecentDelete() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        String allTitle = getApplicationContext().getString(R.string.all_medias);
        UiObject all = mUiDevice.findObject(new UiSelector().text(allTitle));

        assertTrue(all.exists());

        int sHeight = mUiDevice.getDisplayHeight();
        String recentDeleteTitle = getApplicationContext().getString(R.string.recently_deleted);
        UiObject recentDelete;
        int count = 100;

        do {
            mUiDevice.swipe(0, sHeight / 2, 0, sHeight / 2 - 500, 100);
            recentDelete = mUiDevice.findObject(new UiSelector().text(recentDeleteTitle));
            count--;
            if (recentDelete.exists()) {
                break;
            }
        } while (count >= 0);

        assertTrue(recentDelete.exists());

        Utils.waitMillis(2000);
    }

    //点击 "相册" Tab, 存在"新建相册"按钮
    @Test
    public void hasNewAlbumButton() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        UiObject2 fab = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fab_add_new_album"));
        assertNotNull(fab);
    }

    //点击 "相册" Tab, 点击右上角菜单, 存在"隐藏相册"菜单
    @Test
    public void hasHideAlbumMenu() throws UiObjectNotFoundException {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        //获取屏幕宽度
        int sWidth = mUiDevice.getDisplayWidth();
        //点击右上角三个点, 展开菜单
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);

        String hideAlbumTitle = getApplicationContext().getString(R.string.hide_albums_v2);
        UiObject hideAlbum = mUiDevice.findObject(new UiSelector().text(hideAlbumTitle));

        assertTrue(hideAlbum.exists());
        hideAlbum.click();
        Utils.waitMillis(3000);
    }

    //通过相册名称查找某个相册
    public UiObject findAlbum(String name, int count) {
        int sHeight = mUiDevice.getDisplayHeight();
        UiObject album;
        do {
            mUiDevice.swipe(0, sHeight / 2, 0, sHeight / 2 - 500, 100);
            album = mUiDevice.findObject(new UiSelector().text(name));
            count--;
            if (album.exists()) {
                return album;
            }
        } while (count >= 0);

        return album;
    }

    //点击 "相册" Tab, 点击右上角菜单, 点击"隐藏菜单"，打开第一个相册的switch，返回相册列表，不显示该相册
    @Test
    public void testHideAlbum() throws UiObjectNotFoundException {
        hasHideAlbumMenu();
        UiObject2 hide_album_switch = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "hide_album_switch"));
        assertNotNull(hide_album_switch);
        String hide_album_onoff = hide_album_switch.getText();
        if (hide_album_onoff.equalsIgnoreCase("OFF")) {
            hide_album_switch.click();
        }
        UiObject2 album_name = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "album_name"));
        assertNotNull(album_name);
        String hide_album = album_name.getText();
        Utils.waitMillis(1000);

        UiObject2 toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "toolbar"));
        assertNotNull(toolbar);
        List<UiObject2> children = toolbar.getChildren();
        assertTrue(children.size() > 0);
        children.get(0).click();
        Utils.waitMillis(1000);

        UiObject hideAlbum = findAlbum(hide_album, 50);
        assertFalse(hideAlbum.exists());
    }

    //点击 "相册" Tab, 点击"新建相册"
    @Test
    public void clickNewAlbumMenu() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        UiObject2 fab = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fab_add_new_album"));
        assertNotNull(fab);
        fab.click();

        Utils.waitMillis(2000);

        UiObject2 okButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok_button"));
        assertNotNull(okButton);
        okButton.click();

        Utils.waitMillis(2000);

        String selectImageTitle = getApplicationContext().getString(R.string.select_images_or_videos);
        UiObject selectImage = mUiDevice.findObject(new UiSelector().text(selectImageTitle));
        assertTrue(selectImage.exists());
    }

    //点击 "相册" Tab, 点击"新建相册"，选择图片，新建相册，在相册列表存在新建的相册
    @Test
    public void testNewAlbum() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(1500);

        UiObject2 fab = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "fab_add_new_album"));
        assertNotNull(fab);
        fab.click();

        Utils.waitMillis(1500);

        UiObject2 okButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "ok_button"));
        UiObject2 ablum_edit = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "edit_text"));
        assertNotNull(ablum_edit);
        String ablum_name = ablum_edit.getText();
        assertNotNull(okButton);
        okButton.click();
        Utils.waitMillis(1500);

        onView(allOf(withId(R.id.recycler_view_albumset_page), isDisplayed())).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        Utils.waitMillis(1500);

        onView(allOf(withId(R.id.recycler_view_album_page_v2), isDisplayed())).perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(1000);

        UiObject2 action_done = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "action_done"));
        assertNotNull(action_done);
        action_done.click();
        Utils.waitMillis(1500);

        UiObject newAlbum = findAlbum(ablum_name, 100);
        assertTrue(newAlbum.exists());
    }

    //点击 "相册" Tab, 点击"所有图片"
    @Test
    public void enterAllAlbum() {
        mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_album")).click();
        Utils.waitMillis(3000);

        onView(ViewMatchers.withId(R.id.recycler_view_albumset_page))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        Utils.waitMillis(3000);

        String allTitle = getApplicationContext().getString(R.string.all_medias);
        UiObject all = mUiDevice.findObject(new UiSelector().text(allTitle));
        assertTrue(all.exists());
    }

    //点击 "发现" Tab测试
    public boolean pressDiscoverTab() {
        UiObject2 tab = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_discover"));
        if (tab == null) {
            return false;
        }
        tab.click();
        Utils.waitMillis(3000);
        return true;
    }

    //点击 "发现" Tab测试
    @Test
    public void pressDiscoverTabTest() {
        pressDiscoverTab();
    }

    //点击 "发现" Tab, 存在 "事物" 和 "人物"
    @Test
    public void hasThingsAndFaceDetect() {
        if (!pressDiscoverTab()) {
            return;
        }

        String thingsTitle = getApplicationContext().getString(R.string.tf_discover_things);
        UiObject things = mUiDevice.findObject(new UiSelector().text(thingsTitle));
        assertTrue(things.exists());

        String peopleTitle = getApplicationContext().getString(R.string.tf_discover_people);
        UiObject people = mUiDevice.findObject(new UiSelector().text(peopleTitle));
        assertTrue(people.exists());
    }

    //点击 "发现" Tab, 进入 "事物"
    @Test
    public void enterThings() {
        if (!pressDiscoverTab()) {
            return;
        }

        onView(ViewMatchers.withId(R.id.recycler_view_discover_page))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        Utils.waitMillis(3000);

        String thingsTitle = getApplicationContext().getString(R.string.tf_discover_things);
        UiObject things = mUiDevice.findObject(new UiSelector().text(thingsTitle));
        assertTrue(things.exists());
    }

    //点击 "发现" Tab, 进入 "人物"
    @Test
    public void enterPeople() {
        if (!pressDiscoverTab()) {
            return;
        }

        onView(ViewMatchers.withId(R.id.recycler_view_discover_page))
                .perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        Utils.waitMillis(3000);

        String peopleTitle = getApplicationContext().getString(R.string.tf_discover_people);
        UiObject people = mUiDevice.findObject(new UiSelector().text(peopleTitle));
        assertTrue(people.exists());
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 显示隐藏toolbar
    @Test
    public void showHideToolbarInPhotoView() {
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
            assertNotNull(toolbar);
        } else {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
            assertNull(toolbar);
        }

        if (toolbar == null) {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
            assertNotNull(toolbar);
        } else {
            mUiDevice.click(sWidth / 2, sHeight / 2 - 200);
            Utils.waitMillis(2000);
            toolbar = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "photo_view_toolbar"));
            assertNull(toolbar);
        }
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击toolbar上返回箭头
    @Test
    public void pressBackInToolbarInPhotoView() {
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

        List<UiObject2> children = toolbar.getChildren();

        assertTrue(children.size() > 0);

        children.get(0).click();

        Utils.waitMillis(3000);

        assertNotNull(mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "v2_photo")));
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击底部"分享"图标
    @Test
    public void pressShareMenuInPhotoView() {
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

        UiObject2 share = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "share"));

        if (share != null) {
            share.click();
            Utils.waitMillis(2000);
            mUiDevice.pressBack();
        }
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击底部"编辑"图标
    @Test
    public void pressEditMenuInPhotoView() {
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

        UiObject2 edit = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "edit"));

        if (edit != null) {
            edit.click();
            Utils.waitMillis(2000);
            UiObject2 imageShow = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "imageShow"));
            assertNotNull(imageShow);
            Utils.waitMillis(2000);
        }
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击底部"删除"图标
    @Test
    public void pressDeleteMenuInPhotoView() throws UiObjectNotFoundException {
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

        UiObject2 delete = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "delete"));
        assertNotNull(delete);

        delete.click();
        Utils.waitMillis(3000);

        String okString = getApplicationContext().getString(R.string.ok);
        UiObject ok = mUiDevice.findObject(new UiSelector().text(okString));

        assertTrue(ok.exists());
        ok.click();

        Utils.checkSdPermission(getApplicationContext(), mUiDevice);
        Utils.waitMillis(3000);
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击底部"详情"图标
    @Test
    public void pressDetailsMenuInPhotoView() {
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

        UiObject2 details = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "details"));
        assertNotNull(details);

        details.click();
        Utils.waitMillis(3000);

        UiObject2 detail_list = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "detail_list"));
        assertNotNull(detail_list);
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击菜单中"播放幻灯片"
    @Test
    public void pressPlaySlideViewMenuInPhotoView() throws UiObjectNotFoundException {
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
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);

        String playSlideViewTitle = getApplicationContext().getString(R.string.slideshow);
        UiObject slideshow = mUiDevice.findObject(new UiSelector().text(playSlideViewTitle));
        assertTrue(slideshow.exists());

        slideshow.click();

        Utils.waitMillis(3000);
    }

    //点击 "照片" Tab, 点击一张图片, 进入浏览, 点击菜单中"设置幻灯片音乐"
    @Test
    public void pressSetSlideMusicMenuInPhotoView() throws UiObjectNotFoundException {
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
        mUiDevice.click(sWidth - GalleryUtils.dpToPixel(16), GalleryUtils.dpToPixel(52));
        Utils.waitMillis(3000);

        String setSlideMusic = getApplicationContext().getString(R.string.slideshow_music);
        UiObject slideMusic = mUiDevice.findObject(new UiSelector().text(setSlideMusic));
        assertTrue(slideMusic.exists());

        slideMusic.click();

        Utils.waitMillis(3000);


        String dialogTitle = getApplicationContext().getString(R.string.slideshow_music);
        UiObject dialog = mUiDevice.findObject(new UiSelector().text(dialogTitle));
        assertTrue(dialog.exists());
    }

    //"设置幻灯片音乐"对话框，选择None
    @Test
    public void testSelectMusicNone() throws UiObjectNotFoundException {
        pressSetSlideMusicMenuInPhotoView();
        Utils.waitMillis(1000);

        //选择None，设置的music的uri为null
        Context context = getApplicationContext();
        UiObject selectNone = mUiDevice.findObject(new UiSelector().text(context.getString(R.string.none)));
        assertTrue(selectNone.exists());
        selectNone.click();
        Utils.waitMillis(1000);

        //设置铃声为null
        String uri = GalleryUtils.getSlideMusicUri(context);
        assertTrue(uri == null);
    }

    //"设置幻灯片音乐"对话框，选择Select music，选择铃声为铃声列表中第一个铃声
    @Test
    public void testSelectMusic() throws UiObjectNotFoundException {
        pressSetSlideMusicMenuInPhotoView();
        Utils.waitMillis(1000);

        Context context = getApplicationContext();
        UiObject select_music = mUiDevice.findObject(new UiSelector().text(context.getString(R.string.select_music)));
        assertTrue(select_music.exists());
        select_music.click();
        Utils.waitMillis(1000);

        UiObject media_storage = mUiDevice.findObject(new UiSelector().text("Media Storage"));
        if (media_storage.exists()) {
            media_storage.click();
            Utils.waitMillis(1000);
        }

        UiObject just_once = mUiDevice.findObject(new UiSelector().text("JUST ONCE"));
        if (just_once.exists()) {
            just_once.click();
            Utils.waitMillis(1000);
        }

        //查找铃声列表中第一个铃声的uri
        String select_uri = null;
        RingtoneManager manager = new RingtoneManager(context);
        manager.setType(RingtoneManager.TYPE_RINGTONE);
        Cursor cur = manager.getCursor();
        select_uri = manager.getRingtoneUri(0).toString();

        //选择铃声列表中第一个铃声
        UiObject2 first_ring = mUiDevice.findObject(By.res("com.android.providers.media", "checked_text_view"));
        assertNotNull(first_ring);
        first_ring.click();
        Utils.waitMillis(1000);

        UiObject ok_button = mUiDevice.findObject(new UiSelector().text("OK"));
        assertTrue(ok_button.exists());
        ok_button.click();
        Utils.waitMillis(1000);

        //设置的铃声为铃声列表中第一个铃声
        String musicUri = GalleryUtils.getSlideMusicUri(context);
        assertTrue(musicUri.equalsIgnoreCase(select_uri));
    }
}
