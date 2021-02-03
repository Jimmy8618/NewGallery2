package com.android.gallery3d.v2.discover.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.discover.data.PeopleAlbum;
import com.android.gallery3d.v2.discover.people.FaceDetector;
import com.android.gallery3d.v2.discover.things.AbstractClassifier;
import com.android.gallery3d.v2.location.LocationResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class DiscoverStore {

    //Table Thing
    public static final class Things {
        public interface Media {
            String TABLE_NAME = "thing";

            Uri CONTENT_URI = Uri.parse("content://" + DiscoverProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";

            String IMAGE_ID = "image_id";

            String CLASSIFICATION = "classification";
        }
    }

    //Table VectorInfo
    public static final class VectorInfo {
        public interface Media {
            String TABLE_NAME = "vector_info";

            Uri CONTENT_URI = Uri.parse("content://" + DiscoverProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";

            String IMAGE_ID = "image_id";

            String FACE_ID = "face_id";

            String IS_MATCHED = "is_matched";

            String X = "x";

            String Y = "y";

            String WIDTH = "width";

            String HEIGHT = "height";

            String YAW_ANGLE = "yawAngle";

            String ROLL_ANGLE = "rollAngle";

            String FDSCORE = "fdScore";

            String FASCORE = "faScore";

            String VECTOR = "vector";
        }
    }

    //Table FaceInfo
    public static final class FaceInfo {
        public interface Media {
            String TABLE_NAME = "face_info";

            Uri CONTENT_URI = Uri.parse("content://" + DiscoverProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";

            String NAME = "name";

            String HEAD = "head";

            String SAMPLES = "samples";
        }
    }

    //Table LocationInfo
    public static final class LocationInfo {
        public interface Media {
            String TABLE_NAME = "location_info";

            Uri CONTENT_URI = Uri.parse("content://" + DiscoverProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";

            String IMAGE_ID = "image_id";                   //图片_id

            String DATE_STRING = "date";                    //日期

            String DATE_TAKEN = "date_taken";               //日期

            String LOCATION_GROUP = "location_group_id";    //LocationGroup _id

            String LATITUDE = "latitude";                   //纬度

            String LONGITUDE = "longitude";                 //经度

            String COUNTRY_CODE = "country_code";           //国家代码          CN

            String COUNTRY_NAME = "country_name";           //国家名称          中国

            String ADMIN_AREA = "admin_area";               //行政区            江苏省

            String LOCALITY = "locality";                   //地方性            苏州市

            String SUB_LOCALITY = "sub_locality";           //下一级---地方性    昆山市

            String THOROUGHFARE = "thoroughfare";           //大道, 大街        长江南路

            String SUB_THOROUGHFARE = "sub_thoroughfare";   //下一级---大道      24号

            String FEATURE_NAME = "feature_name";            //特征名            玉山镇长江南路24号新城域
        }
    }

    //Table LocationGroup
    public static final class LocationGroup {
        public interface Media {
            String TABLE_NAME = "location_group";

            Uri CONTENT_URI = Uri.parse("content://" + DiscoverProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";

            String LOCALE = "locale";

            String US_COUNTRY_CODE = "us_country_code";

            String US_COUNTRY_NAME = "us_country_name";

            String US_ADMIN_AREA = "us_admin_area";

            String US_LOCALITY = "us_locality";

            String LOCALE_COUNTRY_NAME = "locale_country_name";

            String LOCALE_ADMIN_AREA = "locale_admin_area";

            String LOCALE_LOCALITY = "locale_locality";

            String LATITUDE = "latitude";

            String LONGITUDE = "longitude";
        }
    }

    /**
     * 移除事务, 将对应图片id的classification设置为-1,变为未知
     *
     * @param imageId 图片id
     */
    public static void moveOutThings(int imageId) {
        ContentValues values = new ContentValues();
        values.put(DiscoverStore.Things.Columns.CLASSIFICATION, AbstractClassifier.TF_UNKNOWN);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.Things.Media.CONTENT_URI, values,
                DiscoverStore.Things.Columns.IMAGE_ID + "=" + imageId, null);
    }

    /**
     * 获取事务Uri
     *
     * @param classification 事务类别
     * @return uri
     */
    public static Uri getThingsUriWithClassification(int classification) {
        return DiscoverStore.Things.Media.CONTENT_URI.buildUpon()
                .appendQueryParameter(DiscoverStore.Things.Columns.CLASSIFICATION,
                        String.valueOf(classification)).build();
    }

    /**
     * 获取对应分类的事务的图片id
     *
     * @param classification 事务类别
     * @return image ids
     */
    public static String getImageIdsWithClassification(int classification) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.Things.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.Things.Columns.IMAGE_ID
                }, DiscoverStore.Things.Columns.CLASSIFICATION + "=" + classification,
                null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(cursor.getInt(cursor.getColumnIndex(DiscoverStore.Things.Columns.IMAGE_ID)));
            }
        }
        Utils.closeSilently(cursor);
        return sb.toString();
    }

    /**
     * 获取所有已分类的图片
     *
     * @return key imgId, value classification
     */
    public static SparseIntArray getClassifiedThings() {
        SparseIntArray things = new SparseIntArray();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.Things.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.Things.Columns.IMAGE_ID,
                        DiscoverStore.Things.Columns.CLASSIFICATION
                }, DiscoverStore.Things.Columns.CLASSIFICATION + ">=" + 0,
                null, null);
        if (cursor != null) {
            int imageId;
            int classification;
            while (cursor.moveToNext()) {
                imageId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.Things.Columns.IMAGE_ID));
                classification = cursor.getInt(cursor.getColumnIndex(DiscoverStore.Things.Columns.CLASSIFICATION));
                things.put(imageId, classification);
            }
        }
        Utils.closeSilently(cursor);
        return things;
    }

    /**
     * 获取事务表中所有的图片, 包括未知的分类
     *
     * @return key imgId, value imgId
     */
    public static SparseIntArray getAllThings() {
        SparseIntArray things = new SparseIntArray();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.Things.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.Things.Columns.IMAGE_ID
                }, null, null, null);
        if (cursor != null) {
            int key;
            while (cursor.moveToNext()) {
                key = cursor.getInt(cursor.getColumnIndex(DiscoverStore.Things.Columns.IMAGE_ID));
                things.put(key, key);
            }
        }
        Utils.closeSilently(cursor);
        return things;
    }

    /**
     * 判断对应图片id是否已存在事务中
     *
     * @param imageId 图片id
     * @return true if exist
     */
    public static boolean isThingExist(int imageId) {
        boolean exist;
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.Things.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.Things.Columns._ID
                }, DiscoverStore.Things.Columns.IMAGE_ID + "=" + imageId, null, null);
        exist = cursor != null && cursor.getCount() > 0;
        Utils.closeSilently(cursor);
        return exist;
    }

    /**
     * 添加事务记录
     *
     * @param imageId        图片id
     * @param classification 类别
     */
    public static void insertThing(int imageId, int classification) {
        ContentValues values = new ContentValues();
        values.put(DiscoverStore.Things.Columns.IMAGE_ID, imageId);
        values.put(DiscoverStore.Things.Columns.CLASSIFICATION, classification);
        GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.Things.Media.CONTENT_URI, values);
    }

    /**
     * 判断对应图片id是否已存在VectorInfo表中
     *
     * @param imageId 图片id
     * @return true if exist
     */
    public static boolean isVectorExist(int imageId) {
        boolean exist;
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.VectorInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.VectorInfo.Columns._ID
                }, DiscoverStore.VectorInfo.Columns.IMAGE_ID + "=" + imageId, null, null);
        exist = cursor != null && cursor.getCount() > 0;
        Utils.closeSilently(cursor);
        return exist;
    }

    /**
     * 获取VectorInfo表中所有的图片, 包括已分类的人脸
     *
     * @return key imgId, value imgId
     */
    public static SparseIntArray getAllVectors() {
        SparseIntArray vectors = new SparseIntArray();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.VectorInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.VectorInfo.Columns.IMAGE_ID
                }, null, null, null);
        if (cursor != null) {
            int key;
            while (cursor.moveToNext()) {
                key = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
                vectors.put(key, key);
            }
        }
        Utils.closeSilently(cursor);
        return vectors;
    }

    /**
     * 添加Vector记录
     *
     * @param imageId 图片id
     * @param faces   人脸信息
     */
    public static void insertVector(int imageId, @NonNull FaceDetector.Face[] faces) {
        if (faces.length == 0) {
            ContentValues values = new ContentValues();
            values.put(DiscoverStore.VectorInfo.Columns.IMAGE_ID, imageId);
            values.put(DiscoverStore.VectorInfo.Columns.IS_MATCHED, 1);
            values.put(DiscoverStore.VectorInfo.Columns.FACE_ID, 0);
            GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.VectorInfo.Media.CONTENT_URI, values);
            return;
        }
        for (FaceDetector.Face face : faces) {
            ContentValues values = new ContentValues();
            values.put(DiscoverStore.VectorInfo.Columns.IMAGE_ID, imageId);
            values.put(DiscoverStore.VectorInfo.Columns.X, face.getX());
            values.put(DiscoverStore.VectorInfo.Columns.Y, face.getY());
            values.put(DiscoverStore.VectorInfo.Columns.WIDTH, face.getWidth());
            values.put(DiscoverStore.VectorInfo.Columns.HEIGHT, face.getHeight());
            values.put(DiscoverStore.VectorInfo.Columns.YAW_ANGLE, face.getYawAngle());
            values.put(DiscoverStore.VectorInfo.Columns.ROLL_ANGLE, face.getRollAngle());
            values.put(DiscoverStore.VectorInfo.Columns.FDSCORE, face.getFdScore());
            values.put(DiscoverStore.VectorInfo.Columns.FASCORE, face.getFaScore());
            values.put(DiscoverStore.VectorInfo.Columns.VECTOR, face.getFeatureVector());
            GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.VectorInfo.Media.CONTENT_URI, values);
        }
    }

    /**
     * 获取人物Uri
     *
     * @param faceId faceId
     * @return uri
     */
    public static Uri getPeopleUriWithFaceId(int faceId) {
        return DiscoverStore.VectorInfo.Media.CONTENT_URI.buildUpon()
                .appendQueryParameter(DiscoverStore.VectorInfo.Columns.FACE_ID,
                        String.valueOf(faceId)).build();
    }

    /**
     * 获取对应Faced的图片id
     *
     * @param faceId faceId
     * @return image ids
     */
    public static String getImageIdsWithFaceId(int faceId) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.VectorInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.VectorInfo.Columns.IMAGE_ID
                }, DiscoverStore.VectorInfo.Columns.FACE_ID + "=" + faceId,
                null, null);
        if (cursor != null) {
            int id;
            while (cursor.moveToNext()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
                sb.append(id);
            }
        }
        Utils.closeSilently(cursor);
        return sb.toString();
    }

    /**
     * 移除人物
     *
     * @param imageId 图片id
     * @param faceId  faceId
     */
    public static void moveOutPeople(int imageId, int faceId) {
        ContentValues values = new ContentValues();
        values.put(DiscoverStore.VectorInfo.Columns.FACE_ID, 0);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.VectorInfo.Media.CONTENT_URI, values,
                DiscoverStore.VectorInfo.Columns.IMAGE_ID + "=? AND " + DiscoverStore.VectorInfo.Columns.FACE_ID + "=?",
                new String[]{
                        String.valueOf(imageId),
                        String.valueOf(faceId)
                });
    }

    /**
     * 获取已命名的人物名称, 数量大于0
     *
     * @param ignoreName ignoreName
     * @param jc         jc
     * @return named face names
     */
    public static List<String> getNamedFaces(String ignoreName, ThreadPool.JobContext jc) {
        List<String> names = new ArrayList<>();
        SparseArray<String> faces = new SparseArray<>();

        Cursor faceCursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.FaceInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.FaceInfo.Columns._ID,
                        DiscoverStore.FaceInfo.Columns.NAME
                }, DiscoverStore.FaceInfo.Columns.NAME + " != ?",
                new String[]{ignoreName}, null);

        if (faceCursor != null) {
            int faceId;
            String faceName;
            while (faceCursor.moveToNext()) {
                if (jc != null && jc.isCancelled()) {
                    break;
                }
                faceId = faceCursor.getInt(faceCursor.getColumnIndex(DiscoverStore.FaceInfo.Columns._ID));
                faceName = faceCursor.getString(faceCursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.NAME));
                if (TextUtils.isEmpty(faceName)) {
                    continue;
                }
                faces.put(faceId, faceName);
            }
        }
        Utils.closeSilently(faceCursor);

        if (faces.size() <= 0 || (jc != null && jc.isCancelled())) {
            return names;
        }

        List<MediaSet> peopleAlbumList = new ArrayList<>();

        for (int i = 0; i < faces.size(); i++) {
            if (jc != null && jc.isCancelled()) {
                break;
            }
            peopleAlbumList.add(GalleryAppImpl.getApplication().getDataManager()
                    .getMediaSet(PeopleAlbum.PATH_ITEM.getChild(faces.keyAt(i))));
        }

        HashMap<String, Integer> map = new HashMap<>();
        int count;
        for (MediaSet mediaSet : peopleAlbumList) {
            if (jc != null && jc.isCancelled()) {
                break;
            }
            count = mediaSet.getMediaItemCount();
            if (map.get(mediaSet.getName()) == null) {
                map.put(mediaSet.getName(), count);
            } else {
                map.put(mediaSet.getName(), map.get(mediaSet.getName()) + count);
            }
        }

        for (String key : map.keySet()) {
            if (jc != null && jc.isCancelled()) {
                break;
            }
            if (map.get(key) > 0) {
                names.add(key);
            }
        }

        return names;
    }

    /**
     * 获取所有已查询了位置信息的图片
     *
     * @return image ids
     */
    public static SparseIntArray getAllLocations() {
        SparseIntArray locations = new SparseIntArray();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns.IMAGE_ID
                }, null, null, null);
        if (cursor != null) {
            int key;
            while (cursor.moveToNext()) {
                key = cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.IMAGE_ID));
                locations.put(key, key);
            }
        }
        Utils.closeSilently(cursor);
        return locations;
    }

    /**
     * 判断对应图片id是否已存在LocationInfo表中
     *
     * @param imageId 图片id
     * @return true if exist
     */
    public static boolean isLocationExist(int imageId) {
        boolean exist;
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns._ID
                }, DiscoverStore.LocationInfo.Columns.IMAGE_ID + "=" + imageId, null, null);
        exist = cursor != null && cursor.getCount() > 0;
        Utils.closeSilently(cursor);
        return exist;
    }

    /**
     * 插入LocationInfo数据库
     *
     * @param imageId 图片id
     * @param result  LocationResult
     */
    public static void insertLocationInfo(int imageId, @NonNull LocationResult result) {
        ContentValues values = new ContentValues();
        values.put(LocationInfo.Columns.IMAGE_ID, imageId);
        values.put(LocationInfo.Columns.LATITUDE, result.latitude);
        values.put(LocationInfo.Columns.LONGITUDE, result.longitude);
        values.put(LocationInfo.Columns.COUNTRY_CODE, result.countryCode);
        values.put(LocationInfo.Columns.COUNTRY_NAME, result.countryName);
        values.put(LocationInfo.Columns.ADMIN_AREA, result.adminArea);
        values.put(LocationInfo.Columns.LOCALITY, result.locality);
        values.put(LocationInfo.Columns.SUB_LOCALITY, result.subLocality);
        values.put(LocationInfo.Columns.THOROUGHFARE, result.thoroughfare);
        values.put(LocationInfo.Columns.SUB_THOROUGHFARE, result.subThoroughfare);
        values.put(LocationInfo.Columns.FEATURE_NAME, result.featureName);
        if (result.date > 0) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String date = format.format(new Date(result.date));
            values.put(LocationInfo.Columns.DATE_STRING, date);
            values.put(LocationInfo.Columns.DATE_TAKEN, result.date);
        }
        GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.LocationInfo.Media.CONTENT_URI, values);
    }

    /**
     * 获取LocationGroupUri
     *
     * @param locationGroupId locationGroupId
     * @return uri
     */
    public static Uri getLocationGroupUriWithLocationGroupId(String locationGroupId) {
        return DiscoverStore.LocationGroup.Media.CONTENT_URI.buildUpon()
                .appendQueryParameter(LocationGroup.Columns._ID,
                        locationGroupId).build();
    }

    /**
     * 获取对应LocationGroup的图片id
     *
     * @param locationGroupId 事务类别
     * @return image ids
     */
    public static String getImageIdsWithLocationGroupId(int locationGroupId) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns.IMAGE_ID
                }, LocationInfo.Columns.LOCATION_GROUP + "=" + locationGroupId,
                null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.IMAGE_ID)));
            }
        }
        Utils.closeSilently(cursor);
        return sb.toString();
    }
}
