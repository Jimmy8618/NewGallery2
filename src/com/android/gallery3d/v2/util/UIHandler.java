package com.android.gallery3d.v2.util;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

public class UIHandler<T extends UIMessageHandler> extends Handler {
    private final WeakReference<T> ref;

    public UIHandler(T a) {
        ref = new WeakReference<>(a);
    }

    @Override
    public void handleMessage(Message msg) {
        T a = ref.get();
        if (a != null) {
            a.handleUIMessage(msg);
        }
    }
}