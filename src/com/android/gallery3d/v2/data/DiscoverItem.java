package com.android.gallery3d.v2.data;

import android.net.Uri;

import androidx.annotation.StringRes;

import com.android.gallery3d.common.Utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoverItem {
    public static final int TYPE_THINGS = 0;
    public static final int TYPE_PEOPLE = 1;
    public static final int TYPE_LOCATION = 2;
    public static final int TYPE_STORY = 3;

    private int position;

    private final int type;
    private final int titleResId;

    private Uri uri1;
    private Uri uri2;
    private Uri uri3;
    private Uri uri4;

    private int orientation1;
    private int orientation2;
    private int orientation3;
    private int orientation4;

    private long dateModified1;
    private long dateModified2;
    private long dateModified3;
    private long dateModified4;

    private Integer placeHolderId1;
    private Integer placeHolderId2;
    private Integer placeHolderId3;
    private Integer placeHolderId4;

    private AtomicBoolean isUpdated;

    public DiscoverItem(int type, @StringRes int strResId, int position) {
        this.type = type;
        this.titleResId = strResId;
        this.position = position;
        this.isUpdated = new AtomicBoolean(false);
    }

    public int getTitleResId() {
        return this.titleResId;
    }

    public int getType() {
        return type;
    }

    public boolean isUpdated() {
        return isUpdated.getAndSet(false);
    }

    public void setUri1(Uri uri1, int orientation1, long dateModified1, Integer placeHolderId1) {
        if (!Utils.equals(this.uri1, uri1)) {
            this.isUpdated.set(true);
        }
        if (this.orientation1 != orientation1) {
            this.isUpdated.set(true);
        }
        if (this.placeHolderId1 != placeHolderId1) {
            this.isUpdated.set(true);
        }
        this.uri1 = uri1;
        this.orientation1 = orientation1;
        this.dateModified1 = dateModified1;
        this.placeHolderId1 = placeHolderId1;
    }

    public void setUri2(Uri uri2, int orientation2, long dateModified2, Integer placeHolderId2) {
        if (!Utils.equals(this.uri2, uri2)) {
            this.isUpdated.set(true);
        }
        if (this.orientation2 != orientation2) {
            this.isUpdated.set(true);
        }
        if (this.placeHolderId2 != placeHolderId2) {
            this.isUpdated.set(true);
        }
        this.uri2 = uri2;
        this.orientation2 = orientation2;
        this.dateModified2 = dateModified2;
        this.placeHolderId2 = placeHolderId2;
    }

    public void setUri3(Uri uri3, int orientation3, long dateModified3, Integer placeHolderId3) {
        if (!Utils.equals(this.uri3, uri3)) {
            this.isUpdated.set(true);
        }
        if (this.orientation3 != orientation3) {
            this.isUpdated.set(true);
        }
        if (this.placeHolderId3 != placeHolderId3) {
            this.isUpdated.set(true);
        }
        this.uri3 = uri3;
        this.orientation3 = orientation3;
        this.dateModified3 = dateModified3;
        this.placeHolderId3 = placeHolderId3;
    }

    public void setUri4(Uri uri4, int orientation4, long dateModified4, Integer placeHolderId4) {
        if (!Utils.equals(this.uri4, uri4)) {
            this.isUpdated.set(true);
        }
        if (this.orientation4 != orientation4) {
            this.isUpdated.set(true);
        }
        if (this.placeHolderId4 != placeHolderId4) {
            this.isUpdated.set(true);
        }
        this.uri4 = uri4;
        this.orientation4 = orientation4;
        this.dateModified4 = dateModified4;
        this.placeHolderId4 = placeHolderId4;
    }

    public DiscoverItem setUriNull() {
        setUri1(null, 0, 0, null);
        setUri2(null, 0, 0, null);
        setUri3(null, 0, 0, null);
        setUri4(null, 0, 0, null);
        return this;
    }

    public Uri getUri1() {
        return uri1;
    }

    public Uri getUri2() {
        return uri2;
    }

    public Uri getUri3() {
        return uri3;
    }

    public Uri getUri4() {
        return uri4;
    }

    public int getOrientation1() {
        return orientation1;
    }

    public int getOrientation2() {
        return orientation2;
    }

    public int getOrientation3() {
        return orientation3;
    }

    public int getOrientation4() {
        return orientation4;
    }

    public long getDateModified1() {
        return dateModified1;
    }

    public long getDateModified2() {
        return dateModified2;
    }

    public long getDateModified3() {
        return dateModified3;
    }

    public long getDateModified4() {
        return dateModified4;
    }

    public int getPosition() {
        return position;
    }

    public Integer getPlaceHolderId1() {
        return placeHolderId1;
    }

    public Integer getPlaceHolderId2() {
        return placeHolderId2;
    }

    public Integer getPlaceHolderId3() {
        return placeHolderId3;
    }

    public Integer getPlaceHolderId4() {
        return placeHolderId4;
    }
}
