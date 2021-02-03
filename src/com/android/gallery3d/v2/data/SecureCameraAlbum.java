package com.android.gallery3d.v2.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SecureCameraAlbum extends MediaSet {
    private static final String TAG = SecureCameraAlbum.class.getSimpleName();

    public static final Path PATH = Path.fromString("/secure/camera/album");

    private static final int INVALID_COUNT = -1;

    private final GalleryApp mApplication;
    private final ChangeNotifier mNotifier;

    private int mCachedCount = INVALID_COUNT;

    //保存Camera传过来的需要查看的媒体 _id
    private final List<Integer> mSecureItemIds = new ArrayList<>();
    //保存存在的媒体Item, 获取数据时, 从这个列表中读取
    private final List<Path> mExistItems = new ArrayList<>();

    //构造方法
    public SecureCameraAlbum(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mNotifier = new ChangeNotifier(this, new Uri[]{
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }, application);
    }

    //添加需要查看的媒体 _id 列表
    public synchronized void addSecureItems(List<Integer> secureItemIds) {
        Log.d(TAG, "addSecureItems");
        this.mSecureItemIds.clear();//清空
        if (secureItemIds != null) {
            this.mSecureItemIds.addAll(secureItemIds);//添加
            Collections.sort(this.mSecureItemIds, new Comparator<Integer>() {//排序
                @Override
                public int compare(Integer l, Integer r) {
                    return -Utils.compare(l, r);
                }
            });
        }
        //发送数据变化通知
        mNotifier.fakeChange();
    }

    //获取数据接口
    @Override
    public synchronized ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> list = new ArrayList<>();
        //判断若mCachedCount未知, 则查询一遍数据个数
        if (mCachedCount == INVALID_COUNT) {
            getMediaItemCount();
        }

        //若超出范围, 返回个数为0
        if (start < 0 || start >= mCachedCount) {
            return list;
        }

        //计算end位置
        int end = Math.min(start + count, mCachedCount);
        Log.d(TAG, "getMediaItem start = " + start + ", end = " + end + ", mediaCount = " + mExistItems.size());

        //从mExistItems中读取数据
        ArrayList<Path> sub = new ArrayList<>(mExistItems.subList(start, end));

        for (Path p : sub) {
            //创建获取MediaItem数据
            list.add((MediaItem) mApplication.getDataManager().getMediaObject(p));
        }

        //返回
        return list;
    }

    @Override
    public synchronized int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            this.mExistItems.clear();
            //若mSecureItemIds没有任何数据, 则返回个数为0
            if (mSecureItemIds.size() == 0) {
                mCachedCount = 0;
            } else {
                //查询包含的图片 _id
                ArrayList<Integer> images = queryExistItems(false, this.mSecureItemIds);
                //查询包含的视频 _id
                ArrayList<Integer> videos = queryExistItems(true, this.mSecureItemIds);
                //遍历, 将查询到的媒体文件依次放入mExistItems中
                for (int itemId : this.mSecureItemIds) {
                    if (images.contains(itemId)) {
                        this.mExistItems.add(LocalImage.ITEM_PATH.getChild(itemId));
                    } else if (videos.contains(itemId)) {
                        this.mExistItems.add(LocalVideo.ITEM_PATH.getChild(itemId));
                    }
                }
                //获取到个数
                mCachedCount = this.mExistItems.size();
            }
        }
        return mCachedCount;
    }

    @Override
    public String getName() {
        return "SecureCameraAlbum";
    }

    @Override
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
        }
        return mDataVersion;
    }

    //查询存在的媒体 _id
    private ArrayList<Integer> queryExistItems(boolean isVideo, List<Integer> secureItemIds) {
        ArrayList<Integer> exist = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int itemId : secureItemIds) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(itemId);
        }
        Uri uri = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = mApplication.getContentResolver().query(uri, new String[]{MediaStore.Images.ImageColumns._ID},
                MediaStore.Images.ImageColumns._ID + " in (" + sb.toString() + ")",
                null, MediaStore.Images.ImageColumns._ID + " DESC");
        if (cursor != null) {
            int _id;
            while (cursor.moveToNext()) {
                _id = cursor.getInt(0);
                exist.add(_id);
            }
        }
        Utils.closeSilently(cursor);
        return exist;
    }
}
