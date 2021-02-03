package com.android.gallery3d.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.data.DataManager;

import java.io.File;


public class GalleryStorageReceiver extends BroadcastReceiver {

    private static final String TAG = "GalleryStorageReceiver";
    /*
     *
     * <receiver android:name="test.spreadtrum.storagemodule.GalleryStorageReceiver" >
     * <intent-filter> <action android:name="android.intent.action.MEDIA_EJECT"
     * /> <action android:name="android.intent.action.MEDIA_MOUNTED" /> <action
     * android:name="android.intent.action.MEDIA_BAD_REMOVAL" /> <action
     * android:name="android.intent.action.MEDIA_UNMOUNTED" />
     *
     * <data android:scheme="file" /> </intent-filter> </receiver>
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive,  intent: " + intent);
        String action = intent.getAction();
        /* SPRD: Modify for bug612571. @{ */
        String storagePath = null;
        Uri intentUri = intent.getData();
        if (intentUri != null) {
            storagePath = intentUri.getPath();
        }
        if (Intent.ACTION_MEDIA_EJECT.equals(action)
                || Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                || Intent.ACTION_MEDIA_BAD_REMOVAL.equals(action)
                || Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            Log.d(TAG, "onReceive, notifyStorageChanged");
            DataManager.from(context).onContentDirty();
            GalleryStorageUtil.notifyStorageChanged(storagePath, action);
        }
    }

    private File whichStorage(Uri uri) {
        if (uri == null || uri.getPath() == null) {
            Log.e(TAG, "whichStorage, intent.data.path is null");
            return null;
        }
        String path = uri.getPath();
        Log.e(TAG, "whichStorage, path = " + path);
        if (GalleryStorageUtil.isInUSBVolume(path)) {
            return new File("/storage/usbdisk");
        } else {
            Log.e(TAG, "whichStorage, can not found the uri: " + uri + " in which storage");
            return null;
        }
    }
    /* @} */


    private File whichStorage(Uri uri, String action) {
        if (uri == null || uri.getPath() == null) {
            Log.e(TAG, "whichStorage(), uri or uri.getPath() is null");
            return null;
        }
        String path = uri.getPath();
        Log.e(TAG, "whichStorage(), path = " + path + "  uri = " + uri);
        if (GalleryStorageUtil.isInUSBVolume(path) && !GalleryStorageUtil.isStorageMounted(new File(path))) {
            return new File("/storage/usbdisk");
        } else {
            Log.e(TAG, "whichStorage(), can not found the uri: " + uri + " in which storage");
            return null;
        }
    }

}
