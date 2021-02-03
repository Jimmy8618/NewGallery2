package com.android.fw;

import android.graphics.YuvImageEx;

public class YuvCodec {
    private YuvImageEx mYuvImageEx;

    YuvCodec(Object obj) {
        if (obj instanceof YuvImageEx) {
            mYuvImageEx = (YuvImageEx) obj;
        }
    }

    public byte[] getYuvData() {
        if (mYuvImageEx == null) {
            return null;
        }
        return mYuvImageEx.getYuvData();
    }
}
