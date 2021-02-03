package com.android.gallery3d.v2.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;

import java.util.ArrayList;

public class SDCardPermissionHandler implements SdCardPermissionAccessor {
    private static final String TAG = "Abs.PermissionActivity";

    private Activity mActivity;
    private SdCardPermissionListener mSdCardPermissionListener;

    public SDCardPermissionHandler(Activity activity) {
        mActivity = activity;
    }

    public interface PermissionCallback {
        void onAllowed();

        void onDenied();
    }

    public class SimplePermissionCallback implements PermissionCallback {

        @Override
        public void onAllowed() {

        }

        @Override
        public void onDenied() {

        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE:
                if (data == null || data.getData() == null) {
                    if (mSdCardPermissionListener != null) {
                        mSdCardPermissionListener.onSdCardPermissionDenied();
                        mSdCardPermissionListener = null;
                    }
                } else {
                    Uri uri = data.getData();
                    //
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    if (!documentId.endsWith(":") || "primary:".equals(documentId)) {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionDenied();
                            mSdCardPermissionListener = null;
                        }
                        return false;
                    }
                    //
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    mActivity.getContentResolver().takePersistableUriPermission(uri, takeFlags);

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        String path = SdCardPermission.getInvalidatePermissionStoragePath(0);
                        SdCardPermission.saveStorageUriPermission(path, uri.toString());
                        SdCardPermission.removeInvalidatePermissionStoragePath(0);
                        Log.d(TAG, "onActivityResult uri = " + uri + ", storage = " + path);
                    }

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        Intent accessIntent = SdCardPermission.getAccessStorageIntent(
                                SdCardPermission.getInvalidatePermissionStoragePath(0)
                        );
                        if (accessIntent == null) {
                            if (mSdCardPermissionListener != null) {
                                mSdCardPermissionListener.onSdCardPermissionDenied();
                                mSdCardPermissionListener = null;
                            }
                        } else {
                            mActivity.startActivityForResult(accessIntent, SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE);
                        }
                    } else {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionAllowed();
                            mSdCardPermissionListener = null;
                        }
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }

    public void requestPermissionIfNeed(String filePath, final PermissionCallback callback) {
        if (!GalleryStorageUtil.isInInternalStorage(filePath)
                && !SdCardPermission.hasStoragePermission(filePath)) {
            SdCardPermissionListener permissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    Log.d(TAG, "onSdCardPermissionAllowed: ");
                    callback.onAllowed();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(mActivity, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            callback.onDenied();
                        }
                    });
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            Log.d(TAG, "requestSDCardPermission: filePath=" + filePath);
            SdCardPermission.requestSdcardPermission(mActivity, storagePaths,
                    SDCardPermissionHandler.this, permissionListener);
        } else {
            callback.onAllowed();
        }
    }
}
