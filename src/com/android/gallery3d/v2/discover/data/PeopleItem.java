package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

public class PeopleItem extends LocalImage {
    private static final String TAG = PeopleItem.class.getSimpleName();

    public static final Path ITEM_PATH = Path.fromString("/discover/people/item");

    private final int mFaceId;

    public PeopleItem(Path path, GalleryApp application, Cursor cursor, int faceId) {
        super(path, application, cursor);
        this.mFaceId = faceId;
    }

    public PeopleItem(Path path, GalleryApp application, int id, int faceId) {
        super(path, application, id);
        this.mFaceId = faceId;
    }

    @Override
    public void moveOutPeople() {
        Log.d(TAG, "moveOutPeople imgId = " + id + ", faceId = " + mFaceId);
        DiscoverStore.moveOutPeople(id, mFaceId);
    }
}
