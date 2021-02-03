package com.sprd.unit.test;

import com.android.gallery3d.common.Utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

    @Test
    public void compareTest() {
        assertEquals(Utils.compare(3, 4), -1);
        assertEquals(Utils.compare(2, 2), 0);
        assertEquals(Utils.compare(4, 3), 1);
    }

    @Test
    public void getMaxDivisorTest() {
        assertEquals(Utils.getMaxDivisor(4, 6), 2);
    }

    @Test
    public void getMinMultipleTest() {
        assertEquals(Utils.getMinMultiple(4, 6), 12);
    }
}
