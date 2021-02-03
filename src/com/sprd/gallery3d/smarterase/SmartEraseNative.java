package com.sprd.gallery3d.smarterase;

import android.util.Log;

public class SmartEraseNative {


    static {
        Log.d("SmartEraseNative", "static initializer: load jni_sprd_smarterase start");
        System.loadLibrary("jni_sprd_smarterase");
        Log.d("SmartEraseNative", "static initializer: load jni_sprd_smarterase end");
    }

    public native int init();

    public native int getVersion();

    public native int deinit();

    public native byte[] process(byte[] image, byte[] mask, int image_width, int image_height);
}
