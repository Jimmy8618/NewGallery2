package com.android.gallery3d.v2.data;

import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import java.util.ArrayList;

/**
 * @author baolin.li
 */
public class CameraMergeAlbum extends LocalMergeAlbum {
    private static final String TAG = CameraMergeAlbum.class.getSimpleName();

    public static final Path PATH = Path.fromString("/camera/all");
    public static final Path IMAGE_PATH = Path.fromString("/camera/all/image");
    public static final Path VIDEO_PATH = Path.fromString("/camera/all/video");

    public CameraMergeAlbum(Path path, MediaSet[] sources) {
        super(path, sources, 0);
    }

    @Override
    public Uri getContentUri() {
        throw new UnsupportedOperationException("this method should never called in CameraMergeAlbum");
    }

    @Override
    public String getName() {
        return GalleryAppImpl.getApplication().getString(R.string.folder_camera);
    }

    @Override
    public long reload() {
        //获取最新的 MediaSet Source BucketId
        ArrayList<Integer> sources = CameraSource.getCameraSources();
        //获取当前的 MediaSet Source
        MediaSet[] mediaSets = getSources();
        //用于判断 Source 是否已改变
        boolean changed = sources.size() != mediaSets.length;
        //遍历
        if (!changed) {
            for (MediaSet mediaSet : mediaSets) {
                if (mediaSet instanceof LocalAlbum) {
                    //判断 MediaSet 是否已改变
                    if (isMediaSetChanged((LocalAlbum) mediaSet, sources)) {
                        changed = true;
                        break;
                    }
                } else if (mediaSet instanceof LocalMergeAlbum) {
                    //获取子的 MediaSet Source
                    MediaSet[] subMediaSets = ((LocalMergeAlbum) mediaSet).getSources();
                    //遍历
                    for (MediaSet subMediaSet : subMediaSets) {
                        if (subMediaSet instanceof LocalAlbum) {
                            //判断 MediaSet 是否已改变
                            if (isMediaSetChanged((LocalAlbum) subMediaSet, sources)) {
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        //内部 Source 已改变
        if (changed) {
            Log.d(TAG, "reload , Camera Source Changed!");
            //重新设置下Camera Source
            setSources(CameraSource.createCameraSources(mPath));
        }

        return super.reload();
    }

    private boolean isMediaSetChanged(LocalAlbum mediaSet, ArrayList<Integer> sources) {
        return !sources.contains(mediaSet.getBucketId());
    }
}
