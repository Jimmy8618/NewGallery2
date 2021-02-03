package com.android.gallery3d.util;

import android.content.Context;
import android.widget.Toast;

public class ToastUtil {

    public static Toast showMessage(Context context, Toast toast, String msg, int duration) {
        if (toast != null) {
            toast.cancel();
        }

        Toast newToast = null;
        newToast = Toast.makeText(context, msg, duration);
        newToast.show();
        return newToast;
    }

    public static Toast showMessage(Context context, Toast toast, int msg, int duration) {
        if (toast != null) {
            toast.cancel();
        }

        Toast newToast = null;
        newToast = Toast.makeText(context, msg, duration);
        newToast.show();
        return newToast;
    }
}