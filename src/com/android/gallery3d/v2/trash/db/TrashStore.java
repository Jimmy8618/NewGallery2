package com.android.gallery3d.v2.trash.db;

import android.net.Uri;

public class TrashStore {

    public static final class Local {
        public interface Media {
            String TABLE_NAME = "local";
            Uri CONTENT_URI = Uri.parse("content://" + TrashProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";
            String LOCAL_PATH = "local_path";
            String TRASH_FILE_PATH = "trash_file_path";
            String DELETED_TIME = "deleted_time";
            String DATE_TAKEN = "datetaken";
            String IS_IMAGE = "is_image";
            String FILE_FLAG = "file_flag";
            String MEDIA_STORE_VALUES = "media_store_values";
            String IS_PENDING = "is_pending";
        }
    }

}
