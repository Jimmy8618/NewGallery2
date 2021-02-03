package com.android.gallery3d.v2.media.extras;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MediaExtrasProvider extends ContentProvider {
    private static final String TAG = MediaExtrasProvider.class.getSimpleName();

    private static final String PARAM_LIMIT = "limit";

    public static final String AUTHORITY = "com.android.gallery3d.v2.media.extras.MediaExtrasProvider";

    private SQLiteDatabase db;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int CODE_EXTENSION_DIR = 1;
    private static final int CODE_EXTENSION_ITEM = 2;

    static {
        sUriMatcher.addURI(AUTHORITY, MediaExtras.Extension.Media.TABLE_NAME, CODE_EXTENSION_DIR);
        sUriMatcher.addURI(AUTHORITY, MediaExtras.Extension.Media.TABLE_NAME + "/#", CODE_EXTENSION_ITEM);
    }

    @Override
    public boolean onCreate() {
        MediaExtrasHelper sqlHelper = new MediaExtrasHelper(getContext());
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
            case CODE_EXTENSION_DIR:
                long id = db.insert(MediaExtras.Extension.Media.TABLE_NAME, null, values);
                if (id > 0) {
                    ret = ContentUris.withAppendedId(MediaExtras.Extension.Media.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
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
            case CODE_EXTENSION_DIR:
                count = db.delete(MediaExtras.Extension.Media.TABLE_NAME, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
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
            case CODE_EXTENSION_DIR:
                count = db.update(MediaExtras.Extension.Media.TABLE_NAME, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                break;
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
            case CODE_EXTENSION_DIR:
                //we need find "limit" param
                String limit = uri.getQueryParameter(PARAM_LIMIT);
                //
                cursor = db.query(MediaExtras.Extension.Media.TABLE_NAME, projection, selection,
                        selectionArgs, null, null, sortOrder, limit);
                break;
            case CODE_EXTENSION_ITEM:
                long id = ContentUris.parseId(uri);
                cursor = db.query(MediaExtras.Extension.Media.TABLE_NAME, projection,
                        MediaExtras.Extension.Columns._ID + " = " + id,
                        null, null, null, null);
                break;
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
            case CODE_EXTENSION_DIR:
                type = "vnd.android.cursor.dir/" + AUTHORITY + "." + MediaExtras.Extension.Media.TABLE_NAME;
                break;
            case CODE_EXTENSION_ITEM:
                type = "vnd.android.cursor.item/" + AUTHORITY + "." + MediaExtras.Extension.Media.TABLE_NAME;
                break;
            default:
                Log.e(TAG, "GetType UnSupported Uri " + uri);
                break;
        }
        return type;
    }
}
