package com.sprd.ui.test;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.android.gallery3d.filtershow.cache.ImageLoader;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertTrue;

public class ImageLoaderTest {
    private static final String TAG = ImageLoaderTest.class.getSimpleName();

    @Test
    public void getMimeTypeTest() {
        String filePath = Utils.RES_DIR + "/FilterShow/0.jpg";
        Uri uri = Uri.fromFile(new File(filePath));
        Log.d(TAG, "getMimeTypeTest: mimeType = " + uri);
        Assert.assertNotNull(ImageLoader.getMimeType(uri));
    }

    @Test
    public void getLocalPathFromUriTest() {
        String filePath = Utils.RES_DIR + "/FilterShow/0.jpg";
        File file = new File(filePath);
        assertTrue(filePath + " is not exists", file.exists());
        Uri uri = Utils.getUriFromFilePath(filePath);
        Log.d(TAG, "getLocalPathFromUriTest: uri = " + uri);
        Assert.assertNotNull(ImageLoader.getLocalPathFromUri(getApplicationContext(), uri));
    }

    @Test
    public void getMetadataOrientationTest() {
        String filePath = Utils.RES_DIR + "/FilterShow/0.jpg";
        File file = new File(filePath);
        assertTrue(filePath + " is not exists", file.exists());
        Uri uri = Utils.getUriFromFilePath(filePath);
        Assert.assertNotNull(ImageLoader.getMetadataOrientation(getApplicationContext(), uri));
    }
}
