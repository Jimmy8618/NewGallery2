package com.android.gallery3d.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtils {

    public static byte[] toByteArray(int[] ints, ByteOrder order) {
        final ByteBuffer buf = ByteBuffer.allocate(ints.length * 4).order(order);
        buf.asIntBuffer().put(ints);
        return buf.array();
    }

    public static int[] toIntArray(byte[] buf) {
        int[] array = new int[buf.length / 4];
        int offset = 0;
        for (int i = 0; i < array.length; i++) {
            array[i] = (buf[3 + offset] & 0xFF) | ((buf[2 + offset] & 0xFF) << 8)
                    | ((buf[1 + offset] & 0xFF) << 16) | ((buf[0 + offset] & 0xFF) << 24);
            offset += 4;
        }
        return array;
    }


}
