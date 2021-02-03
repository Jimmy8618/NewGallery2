package com.android.gallery3d.v2.discover.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DiscoverHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "discover.db";
    private static final int DB_VERSION = 1;

    public DiscoverHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String thing = "CREATE TABLE IF NOT EXISTS " + DiscoverStore.Things.Media.TABLE_NAME
                + "("
                + DiscoverStore.Things.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DiscoverStore.Things.Columns.IMAGE_ID + " INTEGER,"
                + DiscoverStore.Things.Columns.CLASSIFICATION + " INTEGER DEFAULT -2"
                + ")";

        String vectorInfo = "CREATE TABLE IF NOT EXISTS " + DiscoverStore.VectorInfo.Media.TABLE_NAME
                + "("
                + DiscoverStore.VectorInfo.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DiscoverStore.VectorInfo.Columns.IMAGE_ID + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.FACE_ID + " INTEGER DEFAULT -1,"
                + DiscoverStore.VectorInfo.Columns.IS_MATCHED + " INTEGER DEFAULT -1,"
                + DiscoverStore.VectorInfo.Columns.X + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.Y + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.WIDTH + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.HEIGHT + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.YAW_ANGLE + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.ROLL_ANGLE + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.FDSCORE + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.FASCORE + " INTEGER,"
                + DiscoverStore.VectorInfo.Columns.VECTOR + " BLOB"
                + ")";

        String faceInfo = "CREATE TABLE IF NOT EXISTS " + DiscoverStore.FaceInfo.Media.TABLE_NAME
                + "("
                + DiscoverStore.FaceInfo.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DiscoverStore.FaceInfo.Columns.NAME + " TEXT,"
                + DiscoverStore.FaceInfo.Columns.HEAD + " TEXT,"
                + DiscoverStore.FaceInfo.Columns.SAMPLES + " TEXT"
                + ")";

        String locationInfo = "CREATE TABLE IF NOT EXISTS " + DiscoverStore.LocationInfo.Media.TABLE_NAME
                + "("
                + DiscoverStore.LocationInfo.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DiscoverStore.LocationInfo.Columns.IMAGE_ID + " INTEGER,"
                + DiscoverStore.LocationInfo.Columns.DATE_STRING + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " INTEGER DEFAULT -1,"
                + DiscoverStore.LocationInfo.Columns.DATE_TAKEN + " LONG DEFAULT 0,"
                + DiscoverStore.LocationInfo.Columns.LATITUDE + " DOUBLE,"
                + DiscoverStore.LocationInfo.Columns.LONGITUDE + " DOUBLE,"
                + DiscoverStore.LocationInfo.Columns.COUNTRY_CODE + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.COUNTRY_NAME + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.ADMIN_AREA + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.LOCALITY + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.SUB_LOCALITY + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.THOROUGHFARE + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.SUB_THOROUGHFARE + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationInfo.Columns.FEATURE_NAME + " TEXT DEFAULT \"\""
                + ")";

        String locationGroup = "CREATE TABLE IF NOT EXISTS " + DiscoverStore.LocationGroup.Media.TABLE_NAME
                + "("
                + DiscoverStore.LocationGroup.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DiscoverStore.LocationGroup.Columns.LOCALE + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.US_COUNTRY_CODE + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.US_COUNTRY_NAME + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.US_ADMIN_AREA + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.US_LOCALITY + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.LOCALE_COUNTRY_NAME + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.LOCALE_ADMIN_AREA + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.LOCALE_LOCALITY + " TEXT DEFAULT \"\","
                + DiscoverStore.LocationGroup.Columns.LATITUDE + " DOUBLE,"
                + DiscoverStore.LocationGroup.Columns.LONGITUDE + " DOUBLE"
                + ")";

        db.execSQL(thing);
        db.execSQL(vectorInfo);
        db.execSQL(faceInfo);
        db.execSQL(locationInfo);
        db.execSQL(locationGroup);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
