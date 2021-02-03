package com.android.gallery3d.v2.media.extras;

import android.net.Uri;

public class MediaExtras {

    public static final class Extension {
        public interface Media {
            String TABLE_NAME = "media_store_extension";
            Uri CONTENT_URI = Uri.parse("content://" + MediaExtrasProvider.AUTHORITY + "/" + TABLE_NAME);
        }

        public interface Columns {
            String _ID = "_id";
            String TIMEZONE_OFFSET = "timezone_offset";
        }
    }
}
