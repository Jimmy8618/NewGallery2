package com.android.gallery3d.v2.media.extras;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class MediaExtrasHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "media_store_extras.db";
    private static final int DB_VERSION = 1;

    public MediaExtrasHelper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String local = "CREATE TABLE IF NOT EXISTS " + MediaExtras.Extension.Media.TABLE_NAME
                + "("
                + MediaExtras.Extension.Columns._ID + " INTEGER PRIMARY KEY,"
                + MediaExtras.Extension.Columns.TIMEZONE_OFFSET + " INTEGER DEFAULT -1"
                + ")";
        db.execSQL(local);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
