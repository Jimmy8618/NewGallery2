package com.sprd.robolectric.test;

import android.content.Context;
import android.content.Intent;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.util.IntentHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowIntent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = GalleryAppImpl.class)
public class IntentHelperTest {

    @Test
    public void getGalleryIntent() {
        Context context = GalleryAppImpl.getApplication().getAndroidContext();
        assertNotNull(context);

        Intent intent = IntentHelper.getGalleryIntent(context);
        assertNotNull(intent);

        ShadowIntent shadowIntent = Shadows.shadowOf(intent);
        assertEquals("GalleryActivity2", shadowIntent.getIntentClass().getSimpleName());
    }
}
