/**
 * Created by Spreadst
 */

package com.sprd.gallery3d.app;

import com.android.gallery3d.app.LoadingListener;

public interface AlbumSetLoadingListener extends LoadingListener {

    /**
     * Call when find some data need loading. Just for SprdAlbumSetPage to update
     * CameraButton status.
     */
    void onLoadingWill();
}
