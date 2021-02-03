package com.android.gallery3d.v2.data;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.adobe.xmp.XMPMeta;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.util.XmpReader;

import java.io.InputStream;

public class MotionMeta {
    private static final String TAG = MotionMeta.class.getSimpleName();

    private static final String VIDEO_SEMANTIC = "MotionPhoto";
    private static final String XMP_CAMERA = "http://ns.google.com/photos/1.0/camera/";
    private static final String XMP_CONTAINER = "http://ns.google.com/photos/1.0/container/";

    private int mMotionPhoto;
    private int mMotionPhotoVersion;
    private long mMotionPhotoPresentationTimestampUs;

    private String mVideoDirectoryItemSemantic;
    private String mVideoDirectoryItemMime;
    private long mVideoDirectoryItemLength;

    public static MotionMeta parse(Context context, Uri uri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            return new MotionMeta(XmpReader.read(is));
        } catch (Exception e) {
            e.printStackTrace();
            return new MotionMeta();
        } finally {
            Utils.closeSilently(is);
        }
    }

    private MotionMeta() {
    }

    private MotionMeta(@Nullable XMPMeta meta) throws Exception {
        if (meta == null) {
            throw new Exception("meta is null.");
        }
        try {
            mMotionPhoto = meta.getPropertyInteger(XMP_CAMERA, "GCamera:MotionPhoto");
            mMotionPhotoVersion = meta.getPropertyInteger(XMP_CAMERA, "GCamera:MotionPhotoVersion");
            mMotionPhotoPresentationTimestampUs = meta.getPropertyLong(XMP_CAMERA, "GCamera:MotionPhotoPresentationTimestampUs");
        } catch (Exception e) {
            mMotionPhoto = meta.getPropertyInteger(XMP_CAMERA, "Camera:MotionPhoto");
            mMotionPhotoVersion = meta.getPropertyInteger(XMP_CAMERA, "Camera:MotionPhotoVersion");
            mMotionPhotoPresentationTimestampUs = meta.getPropertyLong(XMP_CAMERA, "Camera:MotionPhotoPresentationTimestampUs");
        }

        int itemCount = meta.countArrayItems(XMP_CONTAINER, "Container:Directory");
        for (int i = 1; i <= itemCount; i++) {
            String semanticPath = "Container:Directory[" + i + "]/Container:Item/Item:Semantic";
            String mimePath = "Container:Directory[" + i + "]/Container:Item/Item:Mime";
            String lengthPath = "Container:Directory[" + i + "]/Container:Item/Item:Length";

            String semantic = meta.getPropertyString(XMP_CONTAINER, semanticPath);
            if (semantic == null) {
                Log.d(TAG, "semantic = null, redict path");
                semanticPath = "Container:Directory[" + i + "]/Item:Semantic";
                mimePath = "Container:Directory[" + i + "]/Item:Mime";
                lengthPath = "Container:Directory[" + i + "]/Item:Length";

                semantic = meta.getPropertyString(XMP_CONTAINER, semanticPath);
            }
            Log.d(TAG, "semantic = " + semantic);
            if (VIDEO_SEMANTIC.equals(semantic)) {
                mVideoDirectoryItemSemantic = semantic;
                mVideoDirectoryItemMime = meta.getPropertyString(XMP_CONTAINER, mimePath);
                mVideoDirectoryItemLength = meta.getPropertyLong(XMP_CONTAINER, lengthPath);
                break;
            }
        }
        Log.d(TAG, "motion photo info : " + toString());
    }

    public boolean isMotionPhoto() {
        return mMotionPhoto == 1 && mVideoDirectoryItemLength > 0;
    }

    public long getVideoLength() {
        return mVideoDirectoryItemLength;
    }

    public long getMotionPhotoPresentationTimestampUs() {
        return mMotionPhotoPresentationTimestampUs;
    }

    @Override
    public String toString() {
        return "MotionMeta{" +
                "mMotionPhoto=" + mMotionPhoto +
                ", mMotionPhotoVersion=" + mMotionPhotoVersion +
                ", mMotionPhotoPresentationTimestampUs=" + mMotionPhotoPresentationTimestampUs +
                ", mVideoDirectoryItemSemantic='" + mVideoDirectoryItemSemantic + '\'' +
                ", mVideoDirectoryItemMime='" + mVideoDirectoryItemMime + '\'' +
                ", mVideoDirectoryItemLength=" + mVideoDirectoryItemLength +
                '}';
    }
}
