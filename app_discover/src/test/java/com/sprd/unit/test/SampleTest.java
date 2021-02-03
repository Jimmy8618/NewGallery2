package com.sprd.unit.test;

import com.android.gallery3d.util.GalleryUtils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SampleTest {

    @Test
    public void motion_photo_name_valid() {
        assertTrue(GalleryUtils.isMotionPhoto("IMG_ModelVersion2_10_MP.jpg", 0));
    }

    @Test
    public void motion_photo_name_invalid() {
        assertFalse(GalleryUtils.isMotionPhoto("IMG_ModelVersion2_10.jpg", 0));
    }
}
