package com.sprd.unit.test;

import com.android.gallery3d.common.BitmapUtils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitmapUtilsTest {

    @Test
    public void isSupportedByRegionDecoderTest() {
        assertTrue(BitmapUtils.isSupportedByRegionDecoder("image/jpeg"));
        assertFalse(BitmapUtils.isSupportedByRegionDecoder("image/gif"));
    }

    @Test
    public void isRotationSupportedTest() {
        assertTrue(BitmapUtils.isRotationSupported("image/jpeg"));
        assertFalse(BitmapUtils.isRotationSupported("image/gif"));
    }
}
