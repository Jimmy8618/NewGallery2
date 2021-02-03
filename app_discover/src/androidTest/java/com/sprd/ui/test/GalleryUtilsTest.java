package com.sprd.ui.test;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Size;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.data.MotionMeta;
import com.android.gallery3d.v2.discover.utils.ImageUtils;
import com.sprd.frameworks.StandardFrameworks;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GalleryUtilsTest {
    private static final String TAG = GalleryUtilsTest.class.getSimpleName();

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
    public void isEditorAvailableTest() {
        assertTrue(GalleryUtils.isEditorAvailable(getApplicationContext(), "image/jpeg"));
        assertFalse(GalleryUtils.isEditorAvailable(getApplicationContext(), "image/gif"));
        assertFalse(GalleryUtils.isEditorAvailable(getApplicationContext(), "image/vnd.wap.wbmp"));
    }

    @Test
    public void formatDurationTest() {
        String time = GalleryUtils.formatDuration(getApplicationContext(), 224212 / 1000);
        assertEquals("03:44", time);
    }

    @Test
    public void isValidUriTest() {
        File img = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
        assertTrue(GalleryUtils.isValidUri(getApplicationContext(), Uri.fromFile(img)));

        File img2 = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP2_xxx.jpg");
        assertFalse(GalleryUtils.isValidUri(getApplicationContext(), Uri.fromFile(img2)));
    }

    @Test
    public void getImageSizeTest() {
        Size size = GalleryUtils.getImageSize(getApplicationContext(), R.drawable.ic_add_black_24dp);
        assertEquals(GalleryUtils.dpToPixel(24), size.getWidth());
        assertEquals(GalleryUtils.dpToPixel(24), size.getHeight());
    }

    @Test
    public void isFileUriTest() {
        File img = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
        assertTrue(GalleryUtils.isFileUri(Uri.fromFile(img)));

        assertFalse(GalleryUtils.isFileUri(Uri.parse("content://com.android.externalstorage.documents/document/E216-0DFF:DCIM/Camera/00001IMG_00001_BURST20180702162754.jpg")));
    }

    @Test
    public void transFileToContentTypeTest() {
        File img = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
        Uri uri = GalleryUtils.transFileToContentType(Uri.fromFile(img), getApplicationContext());
        assertEquals("content://com.android.gallery3d.fileprovider2/external_files/SpecialImage/IMG_motion_photo_MP.jpg",
                uri.toString());
    }

    @Test
    public void getFilePathTest() {
        File img = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
        String file = GalleryUtils.getFilePath(Uri.fromFile(img), getApplicationContext());
        assertEquals(img.getAbsolutePath(), file);
    }

    @Test
    public void isSprdPlatformTest() {
        assertTrue(GalleryUtils.isSprdPlatform());
    }

    @Test
    public void motionMetaTest() {
        File img = new File(Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg");
        MotionMeta meta = MotionMeta.parse(getApplicationContext(), Uri.fromFile(img));
        assertTrue(meta.isMotionPhoto());
    }

    @Test
    public void createBitmapTest() {
        String path = Utils.RES_DIR + "/SpecialImage/" + "IMG_motion_photo_MP.jpg";
        Bitmap bitmap = ImageUtils.createBitmap(path, ImageUtils.getOptions(path, 1000, 1000));
        assertNotNull(bitmap);
    }

    @Test
    public void getImageMediaDataBaseUri() {
        Uri uri = com.android.gallery3d.common.Utils.getImageMediaDataBaseUri(getApplicationContext(), "/storage/emulated/0/DCIM/Camera/1.jpg");
        assertEquals("content://media/external_primary/images/media", uri.toString());

        File sdcard = StandardFrameworks.getInstances().getExternalStoragePath(getApplicationContext());
        Uri uri2 = com.android.gallery3d.common.Utils.getImageMediaDataBaseUri(getApplicationContext(), sdcard.getAbsolutePath() + "/DCIM/Camera/1.jpg");
        assertEquals("content://media/" + sdcard.getName().toLowerCase() + "/images/media", uri2.toString());
    }

    @Test
    public void getVideoMediaDataBaseUri() {
        Uri uri = com.android.gallery3d.common.Utils.getVideoMediaDataBaseUri(getApplicationContext(), "/storage/emulated/0/DCIM/Camera/1.mp4");
        assertEquals("content://media/external_primary/video/media", uri.toString());

        File sdcard = StandardFrameworks.getInstances().getExternalStoragePath(getApplicationContext());
        Uri uri2 = com.android.gallery3d.common.Utils.getVideoMediaDataBaseUri(getApplicationContext(), sdcard.getAbsolutePath() + "/DCIM/Camera/1.mp4");
        assertEquals("content://media/" + sdcard.getName().toLowerCase() + "/video/media", uri2.toString());
    }
}
