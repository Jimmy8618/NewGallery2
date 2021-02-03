package com.android.gallery3d.v2.trash.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TrashHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "local_trash.db";
    private static final int DB_VERSION = 1;

    public TrashHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String local = "CREATE TABLE IF NOT EXISTS " + TrashStore.Local.Media.TABLE_NAME
                + "("
                + TrashStore.Local.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TrashStore.Local.Columns.LOCAL_PATH + " TEXT,"
                + TrashStore.Local.Columns.TRASH_FILE_PATH + " TEXT,"
                + TrashStore.Local.Columns.DELETED_TIME + " INTEGER,"
                + TrashStore.Local.Columns.DATE_TAKEN + " INTEGER,"
                + TrashStore.Local.Columns.IS_IMAGE + " INTEGER DEFAULT 0,"
                + TrashStore.Local.Columns.FILE_FLAG + " INTEGER DEFAULT 0,"
                + TrashStore.Local.Columns.MEDIA_STORE_VALUES + " BLOB,"
                + TrashStore.Local.Columns.IS_PENDING + " INTEGER DEFAULT 0"
                + ")";
        db.execSQL(local);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
