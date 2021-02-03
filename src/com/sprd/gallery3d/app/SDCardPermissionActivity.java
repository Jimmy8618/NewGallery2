package com.sprd.gallery3d.app;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.android.gallery3d.R;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.util.ArrayList;

public class SDCardPermissionActivity extends Activity implements SdCardPermissionAccessor {

    private static final String TAG = "SDPermissionActivity";
    public static String EXTRA_REQUEST_STORAGE_PATHS = "request_storage_paths";
    private SdCardPermissionListener mSdCardPermissionListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.permissions_layout);

        ArrayList<String> storagePaths = getIntent().getStringArrayListExtra(EXTRA_REQUEST_STORAGE_PATHS);
        Log.d(TAG, "onCreate: storagePaths=" + storagePaths);
        SdCardPermissionListener listener = new SdCardPermissionListener() {
            @Override
            public void onSdCardPermissionAllowed() {
                finish();
            }

            @Override
            public void onSdCardPermissionDenied() {
                SdCardPermission.showSdcardPermissionErrorDialog(SDCardPermissionActivity.this,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.i(TAG, " access permission failed");
                                finish();
                            }
                        });
            }
        };
        SdCardPermission.requestSdcardPermission(this, storagePaths, this, listener);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
                        return;
                    }
                    //
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);

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
                            startActivityForResult(accessIntent, SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE);
                        }
                    } else {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionAllowed();
                            mSdCardPermissionListener = null;
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }
}
