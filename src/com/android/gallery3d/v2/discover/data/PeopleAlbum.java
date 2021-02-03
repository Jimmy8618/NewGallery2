package com.android.gallery3d.v2.discover.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.discover.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class PeopleAlbum extends MediaSet {
    private static final String TAG = PeopleAlbum.class.getSimpleName();

    public static final Path PATH_ITEM = Path.fromString("/discover/people/album");

    private static final int INVALID_COUNT = -1;
    private final Uri mBaseUri;
    private final String mOrderClause;
    private final String[] mProjection;
    private final Path mItemPath;

    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final int mFaceId;
    private final ChangeNotifier mNotifier;

    private int mCachedCount = INVALID_COUNT;
    private List<Integer> mImageIds = new ArrayList<>();

    private String mName = "";
    private String mHead;

    public PeopleAlbum(Path path, GalleryApp application, int faceId) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mFaceId = faceId;
        mNotifier = new ChangeNotifier(this, new Uri[]{
                DiscoverStore.VectorInfo.Media.CONTENT_URI,
                DiscoverStore.FaceInfo.Media.CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }, application);

        mOrderClause = MediaStore.Images.ImageColumns.DATE_MODIFIED + " DESC, "
                + MediaStore.Images.ImageColumns._ID + " DESC";
        mBaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        mProjection = LocalImage.PROJECTION;
        mItemPath = PeopleItem.ITEM_PATH;

        initFaceInfo();
    }

    @Override
    public Uri getContentUri() {
        return DiscoverStore.getPeopleUriWithFaceId(mFaceId);
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> list = new ArrayList<>();
        if (mCachedCount == INVALID_COUNT) {
            getMediaItemCount();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + count; i++) {
            if (i >= 0 && i < mImageIds.size()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(mImageIds.get(i));
            }
        }

        String where = MediaStore.Images.ImageColumns._ID + " in (" + sb.toString() + ")";

        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("nonotify", "1")
                .build();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    uri, mProjection, where, null, mOrderClause);
        } catch (Exception e) {
            Log.w(TAG, "Gallery permissions is error,can't get data.", e);
            if (cursor != null) {
                cursor.close();
            }
            return list;
        }
        if (cursor == null) {
            Log.w(TAG, "query fail: " + uri);
            return list;
        }

        try {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column
                Path childPath = mItemPath.getChild(id + "-" + mFaceId);
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager, mApplication, mFaceId);
                list.add(item);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor,
                                              DataManager dataManager, GalleryApp app, int faceId) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new PeopleItem(path, app, cursor, faceId);
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            mImageIds = getImageIds();
            mCachedCount = mImageIds.size();
        }
        return mCachedCount;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
            initFaceInfo();
            checkHead();
        }
        return mDataVersion;
    }

    private void initFaceInfo() {
        Cursor cursor = mResolver.query(DiscoverStore.FaceInfo.Media.CONTENT_URI, new String[]{
                DiscoverStore.FaceInfo.Columns.NAME,
                DiscoverStore.FaceInfo.Columns.HEAD
        }, DiscoverStore.FaceInfo.Columns._ID + "=" + mFaceId, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            mName = cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.NAME));
            mHead = cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.HEAD));
        }
        Utils.closeSilently(cursor);
    }

    private List<Integer> getImageIds() {
        List<Integer> images = new ArrayList<>();
        Cursor cursor = mResolver.query(mBaseUri, new String[]{
                MediaStore.Images.ImageColumns._ID
        }, MediaStore.Images.ImageColumns._ID
                + " in (" + DiscoverStore.getImageIdsWithFaceId(mFaceId) + ")" +
                " ) AND (" + hidedAlbumsClause(), null, mOrderClause);
        if (cursor != null) {
            int id;
            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                images.add(id);
            }
        }
        Utils.closeSilently(cursor);
        return images;
    }

    @Override
    public String getHead() {
        return mHead;
    }

    public int getFaceId() {
        return mFaceId;
    }

    //检测是否需要重新生成head头像
    private void checkHead() {
        if (TextUtils.isEmpty(mHead)) {
            return;
        }
        int index1 = mHead.lastIndexOf("/");
        int index2 = mHead.lastIndexOf(".");
        String vecId = mHead.substring(index1 + 1, index2);

        Cursor cursor = mResolver.query(DiscoverStore.VectorInfo.Media.CONTENT_URI, new String[]{
                DiscoverStore.VectorInfo.Columns._ID,
                DiscoverStore.VectorInfo.Columns.IMAGE_ID,
                DiscoverStore.VectorInfo.Columns.FACE_ID
        }, DiscoverStore.VectorInfo.Columns._ID + "=" + vecId, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String imgId = cursor.getString(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
            int faceId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.FACE_ID));

            if (faceId != mFaceId) {
                createNewHead();
            } else {
                Cursor imgCursor = mResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                                MediaStore.Images.ImageColumns.DATA
                        }, MediaStore.Images.ImageColumns._ID + "=" + imgId, null, null
                );

                if ((imgCursor != null && imgCursor.getCount() == 0)) {
                    createNewHead();
                }

                Utils.closeSilently(imgCursor);
            }
        }

        Utils.closeSilently(cursor);
    }

    private void createNewHead() {
        Log.d(TAG, "createNewHead mFaceId = " + mFaceId);
        SparseArray<VectorBean> vecs = new SparseArray<>();//key imgId : value VectorBean
        StringBuilder sb = new StringBuilder();

        Cursor cursor = mResolver.query(DiscoverStore.VectorInfo.Media.CONTENT_URI, new String[]{
                DiscoverStore.VectorInfo.Columns._ID,
                DiscoverStore.VectorInfo.Columns.IMAGE_ID,
                DiscoverStore.VectorInfo.Columns.X,
                DiscoverStore.VectorInfo.Columns.Y,
                DiscoverStore.VectorInfo.Columns.WIDTH,
                DiscoverStore.VectorInfo.Columns.HEIGHT,
        }, DiscoverStore.VectorInfo.Columns.FACE_ID + "=" + mFaceId, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int imgId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
                vecs.put(imgId, new VectorBean(cursor));
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(imgId);
            }
        }

        Utils.closeSilently(cursor);

        if (vecs.size() > 0) {
            Cursor imgCursor = mResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                            MediaStore.Images.ImageColumns._ID,
                            MediaStore.Images.ImageColumns.DATA,
                            MediaStore.Images.ImageColumns.ORIENTATION
                    }, MediaStore.Images.ImageColumns._ID + " in (" + sb.toString() + ")", null, null
            );

            if (imgCursor != null && imgCursor.moveToFirst()) {
                int imgId = imgCursor.getInt(imgCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                String data = imgCursor.getString(imgCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                int orientation = imgCursor.getInt(imgCursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION));
                VectorBean vectorBean = vecs.get(imgId, null);
                if (vectorBean != null) {
                    //创建头像
                    String head = ImageUtils.saveHead(data, orientation,
                            vectorBean.x, vectorBean.y,
                            vectorBean.width, vectorBean.height, vectorBean._id);
                    //更新头像
                    ContentValues faceValues = new ContentValues();
                    faceValues.put(DiscoverStore.FaceInfo.Columns.HEAD, head);
                    mResolver.update(DiscoverStore.FaceInfo.Media.CONTENT_URI, faceValues,
                            DiscoverStore.FaceInfo.Columns._ID + "=" + mFaceId, null);
                }
            }

            Utils.closeSilently(imgCursor);
        }
    }

    private class VectorBean {
        int _id;
        int image_id;
        int x;
        int y;
        int width;
        int height;

        VectorBean(Cursor cursor) {
            _id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns._ID));
            image_id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
            x = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.X));
            y = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.Y));
            width = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.WIDTH));
            height = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.HEIGHT));
        }
    }
}
