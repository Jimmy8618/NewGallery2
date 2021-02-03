package com.sprd.robolectric.test;

import android.content.Context;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.util.ToastUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = GalleryAppImpl.class)
public class ToastUtilTest {

    @Test
    public void showMessage() {
        Context context = GalleryAppImpl.getApplication().getAndroidContext();
        assertNotNull(context);

        Toast toast = null;
        toast = ToastUtil.showMessage(context, toast, "show toast", Toast.LENGTH_LONG);
        assertNotNull(toast);

        assertTrue(ShadowToast.showedToast("show toast"));

    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void showMessageInt() {
        Context context = GalleryAppImpl.getApplication().getAndroidContext();
        assertNotNull(context);

        Toast toast = null;
        toast = ToastUtil.showMessage(context, toast, R.string.no_thumbnail, Toast.LENGTH_LONG);
        assertNotNull(toast);

        assertTrue(ShadowToast.showedToast("无缩略图"));
    }
}
