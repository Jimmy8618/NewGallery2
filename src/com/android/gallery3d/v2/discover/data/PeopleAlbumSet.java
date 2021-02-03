package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PeopleAlbumSet extends MediaSet implements FutureListener<ArrayList<MediaSet>> {
    private static final String TAG = PeopleAlbumSet.class.getSimpleName();

    private static final int AT_LEAST_COUNT = 3;

    private static final int[] placeHolderId = new int[]{
            R.drawable.blank_person1,
            R.drawable.blank_person2,
            R.drawable.blank_person3,
            R.drawable.blank_person4,
            R.drawable.blank_person5,
            R.drawable.blank_person6,
            R.drawable.blank_person7,
            R.drawable.blank_more
    };

    public static final Path PATH_ALBUM_SET = Path.fromString("/discover/people/albumset");

    private static final Uri[] mWatchUris = {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            DiscoverStore.VectorInfo.Media.CONTENT_URI,
            DiscoverStore.FaceInfo.Media.CONTENT_URI
    };

    private final GalleryApp mApplication;
    private ArrayList<MediaSet> mAlbums = new ArrayList<MediaSet>();
    private final ChangeNotifier mNotifier;
    private boolean mIsLoading;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    private FaceNameComparator mFaceNameComparator;
    private FaceIdComparator mFaceIdComparator;

    public PeopleAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
        mFaceNameComparator = new FaceNameComparator();
        mFaceIdComparator = new FaceIdComparator();
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        if (mAlbums == null) {
            return 0;
        }
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mApplication.getResources().getString(
                R.string.tf_discover_people);
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        public ArrayList<MediaSet> run(ThreadPool.JobContext jc) {
            DataManager dataManager = mApplication.getDataManager();
            ArrayList<MediaSet> albums = new ArrayList<>();
            List<ImageBean> imageBeanList = getImages(jc);

            List<ImageBean> unNamed = new ArrayList<>();
            List<ImageBean> named = new ArrayList<>();

            for (ImageBean imageBean : imageBeanList) {
                if (jc.isCancelled()) {
                    break;
                }
                if (TextUtils.isEmpty(imageBean.face_name)) {
                    unNamed.add(imageBean);
                } else {
                    named.add(imageBean);
                }
            }

            Collections.sort(named, mFaceNameComparator);
            Collections.sort(unNamed, mFaceIdComparator);

            List<List<ImageBean>> setList = new ArrayList<>();
            compute(setList, named, unNamed);
            List<List<Integer>> albumSet = computeAlbumSet(setList);

            for (List<Integer> set : albumSet) {
                if (jc.isCancelled()) {
                    break;
                }
                StringBuilder sb = new StringBuilder();
                for (Integer id : set) {
                    if (sb.length() > 0) {
                        sb.append("-");
                    }
                    sb.append(id);
                }
                MediaSet mediaSet = dataManager.getMediaSet(PeopleMergeAlbum.PATH_ITEM.getChild(sb.toString()));
                if (mediaSet.getMediaItemCount() < AT_LEAST_COUNT) {
                    continue;
                }
                albums.add(mediaSet);
            }

            int size = albums.size();
            for (int i = size; i < placeHolderId.length; i++) {
                albums.add(dataManager.getMediaSet(PeopleMergeAlbum.PATH_PLACE_HOLDER_ITEM.getChild(placeHolderId[i])));
            }

            return albums;
        }
    }

    private void compute(List<List<ImageBean>> setList, List<ImageBean> named, List<ImageBean> unNamed) {
        computeNamed(setList, named);
        computeUnNamed(setList, unNamed);
    }

    private void computeNamed(List<List<ImageBean>> setList, List<ImageBean> named) {
        List<ImageBean> cluster = new ArrayList<>();
        if (named.size() == 1) {
            cluster.add(named.get(0));
            setList.add(cluster);
        } else if (named.size() > 1) {
            ImageBean item0 = named.get(0);
            cluster.add(item0);
            setList.add(cluster);

            ImageBean itemEnu;
            for (int i = 1; i < named.size(); i++) {
                itemEnu = named.get(i);
                if (itemEnu.face_name.equals(item0.face_name)) {
                    cluster.add(itemEnu);
                } else {
                    item0 = itemEnu;
                    cluster = new ArrayList<>();
                    cluster.add(item0);
                    setList.add(cluster);
                }
            }
        }
    }

    private void computeUnNamed(List<List<ImageBean>> setList, List<ImageBean> unNamed) {
        List<ImageBean> cluster = new ArrayList<>();
        if (unNamed.size() == 1) {
            cluster.add(unNamed.get(0));
            setList.add(cluster);
        } else if (unNamed.size() > 1) {
            ImageBean item0 = unNamed.get(0);
            cluster.add(item0);
            setList.add(cluster);

            ImageBean itemEnu;
            for (int i = 1; i < unNamed.size(); i++) {
                itemEnu = unNamed.get(i);
                if (itemEnu.faceId == (item0.faceId)) {
                    cluster.add(itemEnu);
                } else {
                    item0 = itemEnu;
                    cluster = new ArrayList<>();
                    cluster.add(item0);
                    setList.add(cluster);
                }
            }
        }
    }

    private List<List<Integer>> computeAlbumSet(List<List<ImageBean>> setList) {
        List<List<Integer>> albumSet = new ArrayList<>();
        for (List<ImageBean> set : setList) {
            if (set.size() <= 0) {
                continue;
            }
            SparseIntArray ids = new SparseIntArray();
            for (ImageBean imageBean : set) {
                ids.put(imageBean.faceId, imageBean.faceId);
            }
            List<Integer> faceIds = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                faceIds.add(ids.keyAt(i));
            }
            albumSet.add(faceIds);
        }
        return albumSet;
    }

    @Override
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    @Override
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) {
                mLoadTask.cancel();
            }
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), this);
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public void onFutureDone(Future<ArrayList<MediaSet>> future) {
        synchronized (this) {
            if (mLoadTask != future) {
                return; // ignore, wait for the latest task
            }
            mLoadBuffer = future.get();
            mIsLoading = false;
            if (mLoadBuffer == null) {
                mLoadBuffer = new ArrayList<MediaSet>();
            }
        }
        Log.d(TAG, "onFutureDone notifyContentChanged");
        notifyContentChanged(Uri.parse("content://com.android.gallery3d.v2.discover.data.PeopleAlbumSet/onFutureDone"));
    }

    private List<ImageBean> getImages(ThreadPool.JobContext jc) {
        List<ImageBean> imageBeanList = new ArrayList<>();
        try {
            SparseArray<VectorInfo> vectorInfoSparseArray = getVectorInfo(jc);
            SparseArray<FaceInfo> faceInfoSparseArray = getFaceInfo(jc);
            SparseArray<ImageInfo> imageInfoSparseArray = getImageInfo(jc);

            if (jc.isCancelled()) {
                return imageBeanList;
            }

            int vectorId;
            int imageId;
            VectorInfo vectorInfo;
            ImageInfo imageInfo;
            FaceInfo faceInfo;
            for (int i = 0; i < vectorInfoSparseArray.size(); i++) {
                if (jc.isCancelled()) {
                    break;
                }
                vectorId = vectorInfoSparseArray.keyAt(i);
                vectorInfo = vectorInfoSparseArray.get(vectorId);
                imageId = vectorInfo.imageId;
                imageInfo = imageInfoSparseArray.get(imageId);
                if (imageInfo == null) {
                    continue;
                }
                faceInfo = faceInfoSparseArray.get(vectorInfo.faceId);
                if (faceInfo == null) {
                    continue;
                }
                imageBeanList.add(new ImageBean(imageId, vectorInfo.faceId, faceInfo.faceName, faceInfo.faceHead, vectorInfo.vectorId,
                        vectorInfo.x, vectorInfo.y, vectorInfo.width, vectorInfo.height, imageInfo.orientation, imageInfo.path));
            }

            Collections.sort(imageBeanList, mFaceIdComparator);
        } catch (Exception e) {
            Log.d(TAG, "getImages error", e);
        }
        return imageBeanList;
    }

    private SparseArray<ImageInfo> getImageInfo(ThreadPool.JobContext jc) {
        SparseArray<ImageInfo> imageInfoSparseArray = new SparseArray<>();
        Cursor cursor = mApplication.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.ORIENTATION,
                        MediaStore.Images.ImageColumns.DATA
                }, hidedAlbumsClause(), null, null);
        if (cursor != null) {
            int imageId;
            while (cursor.moveToNext()) {
                if (jc.isCancelled()) {
                    break;
                }
                imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                imageInfoSparseArray.put(imageId, new ImageInfo(cursor));
            }
        }
        Utils.closeSilently(cursor);
        return imageInfoSparseArray;
    }

    private SparseArray<VectorInfo> getVectorInfo(ThreadPool.JobContext jc) {
        SparseArray<VectorInfo> allVectors = new SparseArray<>();
        Cursor vectorCursor = mApplication.getContentResolver().query(DiscoverStore.VectorInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.VectorInfo.Columns._ID,
                        DiscoverStore.VectorInfo.Columns.FACE_ID,
                        DiscoverStore.VectorInfo.Columns.IMAGE_ID,
                        DiscoverStore.VectorInfo.Columns.X,
                        DiscoverStore.VectorInfo.Columns.Y,
                        DiscoverStore.VectorInfo.Columns.WIDTH,
                        DiscoverStore.VectorInfo.Columns.HEIGHT
                }, DiscoverStore.VectorInfo.Columns.FACE_ID + ">0", null, null);
        if (vectorCursor != null) {
            int vectorId;
            while (vectorCursor.moveToNext()) {
                if (jc.isCancelled()) {
                    break;
                }
                vectorId = vectorCursor.getInt(vectorCursor.getColumnIndex(DiscoverStore.VectorInfo.Columns._ID));
                allVectors.put(vectorId, new VectorInfo(vectorCursor));
            }
        }
        Utils.closeSilently(vectorCursor);
        return allVectors;
    }

    private SparseArray<FaceInfo> getFaceInfo(ThreadPool.JobContext jc) {
        SparseArray<FaceInfo> infoSparseArray = new SparseArray<>();
        Cursor cursor = mApplication.getContentResolver().query(DiscoverStore.FaceInfo.Media.CONTENT_URI,
                null, null, null, null);
        if (cursor != null) {
            int faceId;
            while (cursor.moveToNext()) {
                if (jc.isCancelled()) {
                    break;
                }
                faceId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns._ID));
                infoSparseArray.put(faceId, new FaceInfo(cursor));
            }
        }
        Utils.closeSilently(cursor);
        return infoSparseArray;
    }

    private class ImageBean {
        int vectorId;
        int x;
        int y;
        int width;
        int height;
        int imageId;
        int faceId;
        int orientation;
        String path;

        String face_name;
        String face_head;

        ImageBean(int imageId, int faceId, String faceName, String faceHead,
                  int vectorId, int x, int y, int width, int height, int orientation, String path) {
            this.imageId = imageId;
            this.faceId = faceId;
            this.face_name = faceName;
            this.face_head = faceHead;
            this.vectorId = vectorId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.orientation = orientation;
            this.path = path;
        }
    }

    private class FaceInfo {
        int faceId;
        String faceName;
        String faceHead;

        FaceInfo(Cursor cursor) {
            faceId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns._ID));
            faceName = cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.NAME));
            faceHead = cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.HEAD));
        }
    }

    private class VectorInfo {
        int vectorId;
        int imageId;
        int faceId;
        int x;
        int y;
        int width;
        int height;

        VectorInfo(Cursor cursor) {
            vectorId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns._ID));
            imageId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
            faceId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.FACE_ID));
            x = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.X));
            y = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.Y));
            width = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.WIDTH));
            height = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.HEIGHT));
        }
    }

    private class ImageInfo {
        int imageId;
        int orientation;
        String path;

        ImageInfo(Cursor cursor) {
            imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
            orientation = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION));
            path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
        }
    }

    private static final class FaceNameComparator implements Comparator<ImageBean> {
        private Collator mCollator;

        public FaceNameComparator() {
            mCollator = Collator.getInstance();
        }

        @Override
        public int compare(ImageBean o1, ImageBean o2) {
            return mCollator.getCollationKey(o1.face_name).compareTo(mCollator.getCollationKey(o2.face_name));
        }
    }

    private static final class FaceIdComparator implements Comparator<ImageBean> {
        @Override
        public int compare(ImageBean o1, ImageBean o2) {
            return Utils.compare(o1.faceId, o2.faceId);
        }
    }
}
