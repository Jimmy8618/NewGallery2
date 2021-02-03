package com.android.gallery3d.v2.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

/**
 * @author baolin.li
 */
public class PermissionUtil {
    private static final String TAG = PermissionUtil.class.getSimpleName();

    public static final int PERMISSION_REQUEST_CODE = 101;

    public static boolean hasPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
            }, PERMISSION_REQUEST_CODE);
        } else {
            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            }, PERMISSION_REQUEST_CODE);
        }
    }

    public static AlertDialog showPermissionErrorDialog(final Activity context) {
        AlertDialog dialog = new AlertDialog.Builder(context).setCancelable(false)
                .setMessage(R.string.error_permissions)
                .setPositiveButton(R.string.refocus_quit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        context.finish();
                    }
                })
                .create();
        dialog.show();
        return dialog;
    }
}
