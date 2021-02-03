
package com.sprd.gallery3d.burstphoto;

import android.util.Log;

import java.io.File;
import java.io.Serializable;

/**
 * Created by apuser on 12/15/16.
 */

public class BurstImageItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String TAG = BurstImageItem.class.getSimpleName();
    public static final String EXTRA_BURST_IMAGE_ITEMS = "extra_burst_image_items";
    private String path;
    private int _id;
    private boolean selected;
    private boolean isHighLight;

    public BurstImageItem(String path) {
        this.path = path;
    }

    public BurstImageItem(String path, int id) {
        this.path = path;
        _id = id;
    }

    public File getFile() {
        if (null == path || path.trim().length() == 0) {
            Log.d(TAG, "getFile path = " + path);
            return null;
        }
        return new File(path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setId(int id) {
        _id = id;
    }

    public int getId() {
        return _id;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isHighLight() {
        return isHighLight;
    }

    public void setHighLight(boolean highLight) {
        isHighLight = highLight;
    }
}
