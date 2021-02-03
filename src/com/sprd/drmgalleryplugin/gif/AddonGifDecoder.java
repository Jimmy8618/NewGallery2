
package com.sprd.drmgalleryplugin.gif;

import android.content.Context;
import android.database.Cursor;
import android.drm.DrmManagerClient;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.GifDecoderUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AddonGifDecoder extends GifDecoderUtils {
    private DrmManagerClient mClient = null;
    private Object mDecryptHandle = null;
    private boolean mIsDrmValid = false;
    private String mFilePath = null;
    private static final String TAG = "AddonGalleryDrm";

    @Override
    public void initDrm(Uri uri, Context context) {
        // SPRD: bug 519230,Can not get File path by some uri
        mFilePath = getFilePathByUri(uri, context);
        mIsDrmValid = DrmUtil.isDrmValid(mFilePath);
        if (mClient == null) {
            mClient = DrmUtil.getDrmManagerClient();
        }
        if (mIsDrmValid) {
            mDecryptHandle = StandardFrameworks.getInstances().openDecryptSession(mClient, mFilePath);
        }
        Log.d(TAG, "AddonGifDecoder.initDrm---end+mFilePath=" + mFilePath);
    }

    /* SPRD: bug 519230,Can not get File path by some uri @{ */
    private String getFilePathByUri(Uri uri, Context context) {
        String filePath = null;
        if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
            // Gets the absolute path of the image in the internal storage
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            final String selection = "_id=?";
            final String[] selectionArgs = new String[]{
                    split[1]
            };
            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else {
                Log.w(TAG, "Type is not image");
                return null;
            }
            filePath = getDataColumn(context, contentUri, selection, selectionArgs);
        } else if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            // Gets the absolute path of the image in the external storage
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            filePath = "/storage/" + split[0] + split[1];
        } else {
            filePath = DrmUtil.getFilePathByUri(uri, context);
        }
        return filePath;
    }

    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /* @} */

    @Override
    public boolean isReadDrmUri() {
        return mIsDrmValid && mClient != null && mDecryptHandle != null;
    }

    @Override
    public InputStream readDrmUri() {
        InputStream is = null;
        int fileSize = 0;
        FileInputStream fis = null;
        try {
            File file = new File(mFilePath);
            if (file.exists()) {
                fis = new FileInputStream(file);
                fileSize = fis.available();
            }
        } catch (Exception e) {
            Log.d(TAG, "readDrmUri.file error");
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "readDrmUri.file close error");
                e.printStackTrace();
            }
        }
        byte[] ret = StandardFrameworks.getInstances().preadDrmData(mClient, mDecryptHandle, fileSize, 0);
        Log.d(TAG, "Drm loadGif pread ret = " + ret);
        //SPRD : fixbug 613687 rename the "DrmDownload",DRM file cant open.
        StandardFrameworks.getInstances().closeDecryptSession(mClient, mDecryptHandle);
        // SPRD: Modify for bug607036, gallery will if gif drm image is removed while viewing
        if (ret == null) {
            return null;
        }
        is = new ByteArrayInputStream(ret);
        return is;
    }
}
