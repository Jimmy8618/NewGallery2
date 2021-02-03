package com.sprd.ui.test;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.trash.db.TrashStore;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CropTest {
    private static final String TAG = ImageLoaderTest.class.getSimpleName();
    private UiDevice mUiDevice;

    @Before
    public void launchCropTest() {
        mUiDevice = UiDevice.getInstance(getInstrumentation());

        String filePath = Utils.RES_DIR + "/FilterShow/0.jpg";
        Log.d(TAG, "launchCropTest: filePath=" + filePath);
        Uri uri = Uri.fromFile(new File(filePath));
        Log.d(TAG, "launchCropTest: uri=" + uri);
        Intent intent = new Intent(CropActivity.CROP_ACTION);
        intent.setClass(getApplicationContext(), CropActivity.class);
        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("from_photo_page", true);
        intent.putExtra("crop_in_gallery", true);
        getApplicationContext().startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(Utils.BASIC_PACKAGE).depth(0)), Utils.LAUNCH_TIMEOUT);
    }

    @Test
    public void cropTest() {
        Utils.waitMillis(2000);
        UiObject2 saveButton = mUiDevice.findObject(By.res(Utils.BASIC_PACKAGE, "filtershow_save"));
        assertNotNull(saveButton);
        long time = System.currentTimeMillis();
//        String expectedName = new SimpleDateFormat("_yyyyMMdd_HHmmss").
//                format(new Date(time))+".jpg";
        saveButton.click();
        Utils.waitMillis(2000);

        //判断实际的文件名是否与期望的一致
        long date_added = Utils.getLatestImageAddedTime();
        assertTrue(date_added <= (time + 2000) / 1000);
    }
}
