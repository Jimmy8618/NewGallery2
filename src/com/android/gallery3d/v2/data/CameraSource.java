package com.android.gallery3d.v2.data;

import android.os.Build;
import android.os.storage.StorageManager;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.LocalAlbumSet;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;
import com.android.gallery3d.util.BucketNames;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.StorageInfos;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author baolin.li
 */
public class CameraSource extends MediaSource {
    private static PathMatcher mPathMatcher;

    private static final int IMAGE_ALBUM = 0;
    private static final int VIDEO_ALBUM = 1;
    private static final int ALL = 2;

    public CameraSource(GalleryApp app) {
        super("camera");
    }

    static {
        mPathMatcher = new PathMatcher();
        mPathMatcher.add("/camera/all/image", IMAGE_ALBUM);
        mPathMatcher.add("/camera/all/video", VIDEO_ALBUM);
        mPathMatcher.add("/camera/all", ALL);
    }

    public static ArrayList<Integer> getCameraSources() {
        ArrayList<Integer> sources = new ArrayList<>();
        //Internal
        //1.DCIM/Camera
        String internalCamera = StorageInfos.getInternalStorageDirectory().toString() + "/" + BucketNames.CAMERA;
        sources.add(GalleryUtils.getBucketId(internalCamera));
        //2.DCIM
        if (StandardFrameworks.getInstances().isSupportMultiCameraSource()) {
            sources.add(GalleryUtils.getBucketId(StorageInfos.getInternalStorageDirectory().toString() + "/" + BucketNames.DCIM));
        }
        //External
        //1.DCIM/Camera
        String externalCamera = StorageInfos.getExternalStorageDirectory().toString() + "/" + BucketNames.CAMERA;
        sources.add(GalleryUtils.getBucketId(externalCamera));
        //2.DCIM
        if (StandardFrameworks.getInstances().isSupportMultiCameraSource()) {
            sources.add(GalleryUtils.getBucketId(StorageInfos.getExternalStorageDirectory().toString() + "/" + BucketNames.DCIM));
        }
        //Otg
        //1.DCIM/Camera
        ArrayList<Integer> otgSources = getOtgCameraSources();
        if (otgSources.size() > 0) {
            sources.addAll(otgSources);
        }
        return sources;
    }

    public static ArrayList<Integer> getOtgCameraSources() {
        ArrayList<Integer> otgIds = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StorageManager storageManager = GalleryAppImpl.getApplication().getSystemService(StorageManager.class);
            HashMap<String, String> otg = StandardFrameworks.getInstances().getOtgVolumesInfo(storageManager);
            for (String path : otg.values()) {
                otgIds.add(GalleryUtils.getBucketId(path + "/" + BucketNames.CAMERA));
            }
        }
        return otgIds;
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        return new CameraMergeAlbum(path, createCameraSources(path));
    }

    synchronized static MediaSet[] createCameraSources(Path path) {
        ArrayList<Integer> sources = getCameraSources();
        MediaSet[] mediaSets = new MediaSet[sources.size()];
        switch (mPathMatcher.match(path)) {
            case IMAGE_ALBUM:
                for (int i = 0; i < sources.size(); i++) {
                    mediaSets[i] = LocalAlbumSet.getLocalAlbum(LocalAlbumSet.MEDIA_TYPE_IMAGE, LocalAlbumSet.PATH_IMAGE,
                            sources.get(i), null);
                }
                break;
            case VIDEO_ALBUM:
                for (int i = 0; i < sources.size(); i++) {
                    mediaSets[i] = LocalAlbumSet.getLocalAlbum(LocalAlbumSet.MEDIA_TYPE_VIDEO, LocalAlbumSet.PATH_VIDEO,
                            sources.get(i), null);
                }
                break;
            case ALL:
                for (int i = 0; i < sources.size(); i++) {
                    mediaSets[i] = LocalAlbumSet.getLocalAlbum(LocalAlbumSet.MEDIA_TYPE_ALL, LocalAlbumSet.PATH_ALL,
                            sources.get(i), null);
                }
                break;
            default:
                throw new RuntimeException("bad path: " + path);
        }
        return mediaSets;
    }
}
