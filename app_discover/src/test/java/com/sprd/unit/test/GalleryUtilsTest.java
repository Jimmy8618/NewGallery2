package com.sprd.unit.test;

import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.util.GalleryUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GalleryUtilsTest {

    @Test
    public void isCropAvailableTest() {
        assertTrue(GalleryUtils.isCropAvailable(null, "image/jpeg"));
        assertFalse(GalleryUtils.isCropAvailable(null, "image/gif"));
    }

    @Test
    public void isValidLocationTest() {
        assertTrue(GalleryUtils.isValidLocation(100, 100));
        assertFalse(GalleryUtils.isValidLocation(0, 0));
    }

    @Test
    public void getBucketIdTest() {
        int bucketId = GalleryUtils.getBucketId("/storage/emulated/0/DCIM/Camera");
        assertEquals(-1739773001, bucketId);
    }

    @Test
    public void isMotionPhotoTest() {
        assertTrue(GalleryUtils.isMotionPhoto("IMG_ModelVersion2_10_MP.jpg", 0));
        assertTrue(GalleryUtils.isMotionPhoto("", LocalImage.IMG_TYPE_MODE_MOTION_PHOTO));
    }

    @Test
    public void meterToPixel() {
        assertEquals(-15748, GalleryUtils.meterToPixel(2.5f));
    }

    @Test
    public void fastDistanceMeters() {
        assertEquals(1.4505402318719978E7, GalleryUtils.fastDistanceMeters(30f, 32f, 40f, 42f), 0.1f);
    }

    @Test
    public void accurateDistanceMeters() {
        assertEquals(1.4505402318719978E7, GalleryUtils.accurateDistanceMeters(30f, 32f, 40f, 42f), 0.1f);
    }

    @Test
    public void toMile() {
        assertEquals(621.5040397762585, GalleryUtils.toMile(1000000f), 0.1f);
    }
}