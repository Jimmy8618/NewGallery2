package com.android.gallery3d.v2.discover.db;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

public class DiscoverProvider extends ContentProvider {
    private static final String TAG = DiscoverProvider.class.getSimpleName();

    public static final String AUTHORITY = "com.android.gallery3d.v2.discover.db.DiscoverProvider";

    private SQLiteDatabase db;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    //match some items in table Thing
    private static final int CODE_THING_DIR = 1;
    //match some items in table VectorInfo
    private static final int CODE_VECTOR_INFO_DIR = 2;
    //match some items in table FaceInfo
    private static final int CODE_FACE_INFO_DIR = 3;
    //match some items in table LocationInfo
    private static final int CODE_LOCATION_INFO_DIR = 4;
    //match some items in table LocationGroup
    private static final int CODE_LOCATION_GROUP_DIR = 5;

    static {
        sUriMatcher.addURI(AUTHORITY, DiscoverStore.Things.Media.TABLE_NAME, CODE_THING_DIR);
        sUriMatcher.addURI(AUTHORITY, DiscoverStore.VectorInfo.Media.TABLE_NAME, CODE_VECTOR_INFO_DIR);
        sUriMatcher.addURI(AUTHORITY, DiscoverStore.FaceInfo.Media.TABLE_NAME, CODE_FACE_INFO_DIR);
        sUriMatcher.addURI(AUTHORITY, DiscoverStore.LocationInfo.Media.TABLE_NAME, CODE_LOCATION_INFO_DIR);
        sUriMatcher.addURI(AUTHORITY, DiscoverStore.LocationGroup.Media.TABLE_NAME, CODE_LOCATION_GROUP_DIR);
    }

    @Override
    public boolean onCreate() {
        DiscoverHelper sqlHelper = new DiscoverHelper(getContext());
        try {
            db = sqlHelper.getWritableDatabase();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed to open db.", e);
        }
        return db != null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        Uri ret = null;
        switch (sUriMatcher.match(uri)) {
            case CODE_THING_DIR: {
                long id = db.insert(DiscoverStore.Things.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(DiscoverStore.Things.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_VECTOR_INFO_DIR: {
                long id = db.insert(DiscoverStore.VectorInfo.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(DiscoverStore.VectorInfo.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_FACE_INFO_DIR: {
                long id = db.insert(DiscoverStore.FaceInfo.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(DiscoverStore.FaceInfo.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_INFO_DIR: {
                long id = db.insert(DiscoverStore.LocationInfo.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(DiscoverStore.LocationInfo.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_GROUP_DIR: {
                long id = db.insert(DiscoverStore.LocationGroup.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(DiscoverStore.LocationGroup.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            default:
                Log.e(TAG, "Insert UnSupported Uri " + uri);
                break;
        }
        return ret;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case CODE_THING_DIR: {
                count = db.delete(DiscoverStore.Things.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_VECTOR_INFO_DIR: {
                count = db.delete(DiscoverStore.VectorInfo.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_FACE_INFO_DIR: {
                count = db.delete(DiscoverStore.FaceInfo.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_INFO_DIR: {
                count = db.delete(DiscoverStore.LocationInfo.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_GROUP_DIR: {
                count = db.delete(DiscoverStore.LocationGroup.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            default:
                Log.e(TAG, "Delete UnSupported Uri " + uri);
                break;
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case CODE_THING_DIR: {
                if (values == null) {
                    getContext().getContentResolver().notifyChange(uri, null);
                    break;
                }
                count = db.update(DiscoverStore.Things.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_VECTOR_INFO_DIR: {
                if (values == null) {
                    getContext().getContentResolver().notifyChange(uri, null);
                    break;
                }
                count = db.update(DiscoverStore.VectorInfo.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_FACE_INFO_DIR: {
                if (values == null) {
                    getContext().getContentResolver().notifyChange(uri, null);
                    break;
                }
                count = db.update(DiscoverStore.FaceInfo.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_INFO_DIR: {
                if (values == null) {
                    getContext().getContentResolver().notifyChange(uri, null);
                    break;
                }
                count = db.update(DiscoverStore.LocationInfo.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            case CODE_LOCATION_GROUP_DIR: {
                if (values == null) {
                    getContext().getContentResolver().notifyChange(uri, null);
                    break;
                }
                count = db.update(DiscoverStore.LocationGroup.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
            }
            default:
                Log.e(TAG, "Update UnSupported Uri " + uri);
                break;
        }
        return count;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Cursor cursor = null;
        switch (sUriMatcher.match(uri)) {
            case CODE_THING_DIR: {
                cursor = db.query(DiscoverStore.Things.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            case CODE_VECTOR_INFO_DIR: {
                cursor = db.query(DiscoverStore.VectorInfo.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            case CODE_FACE_INFO_DIR: {
                cursor = db.query(DiscoverStore.FaceInfo.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            case CODE_LOCATION_INFO_DIR: {
                cursor = db.query(DiscoverStore.LocationInfo.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            case CODE_LOCATION_GROUP_DIR: {
                cursor = db.query(DiscoverStore.LocationGroup.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            default:
                Log.e(TAG, "Query UnSupported Uri " + uri);
                break;
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String type = null;
        switch (sUriMatcher.match(uri)) {
            case CODE_THING_DIR: {
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + DiscoverStore.Things.Media.TABLE_NAME;
                break;
            }
            case CODE_VECTOR_INFO_DIR: {
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + DiscoverStore.VectorInfo.Media.TABLE_NAME;
                break;
            }
            case CODE_FACE_INFO_DIR: {
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + DiscoverStore.FaceInfo.Media.TABLE_NAME;
                break;
            }
            case CODE_LOCATION_INFO_DIR: {
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + DiscoverStore.LocationInfo.Media.TABLE_NAME;
                break;
            }
            case CODE_LOCATION_GROUP_DIR: {
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + DiscoverStore.LocationGroup.Media.TABLE_NAME;
                break;
            }
            default:
                Log.e(TAG, "GetType UnSupported Uri " + uri);
                break;
        }
        return type;
    }

}
