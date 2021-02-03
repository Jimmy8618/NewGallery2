package com.android.gallery3d.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.ui.DetailsAddressResolver;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.gallery3d.drm.MenuExecutorUtils;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Created by apuser on 1/11/17.
 */

public class DetailsActivity extends Activity {
    private static final String TAG = "DetailsActivity";
    //private static AbstractGalleryActivity mGalleryActivity;
    private /*static*/ DetailsHelper.DetailsSource mSource;
    private static boolean mIsDrmDetails = false;

    private RecyclerView mDetailsList;
    private static Activity sLastActivity;

    /*public static void setAbstractGalleryActivity(AbstractGalleryActivity activity) {
        mGalleryActivity = activity;
    }*/

    /*public static void setDetailsSource(DetailsHelper.DetailsSource source) {
        mSource = source;
        try {
            mSource.setIndex();
        } catch (Exception e) {
        }
    }*/

    private static class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int size;
        private int index;
        private MediaDetails mediaDetails;

        public MyDetailsSource(int size, int index, MediaDetails mediaDetails) {
            this.size = size;
            this.index = index;
            this.mediaDetails = mediaDetails;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int setIndex() {
            return index;
        }

        @Override
        public MediaDetails getDetails() {
            return mediaDetails;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (GalleryUtils.isMonkey()) {
            if (sLastActivity != null) {
                Log.e(TAG, "DetailsActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
                sLastActivity = null;
            }
            sLastActivity = this;
        }

        setContentView(R.layout.detail_pop_layout);

        Intent intent = getIntent();
        if (intent != null) {
            mSource = new MyDetailsSource(intent.getIntExtra("Size", -1),
                    intent.getIntExtra("Index", -1),
                    (MediaDetails) intent.getSerializableExtra("Details"));
        }
        Log.d(TAG, "onCreate mSource : " + (mSource != null ? ("Size = "
                + mSource.size()
                + ", Index = " + mSource.setIndex()
                + " , Details = " + mSource.getDetails()) : "null"));

        setToolBar();

        mDetailsList = findViewById(R.id.detail_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mDetailsList.setLayoutManager(layoutManager);

        if (mSource != null && mSource.getDetails() != null) {
            mDetailsList.setAdapter(new DetailsAdapter2(this, mSource.getDetails()));
        } else {
            Toast.makeText(this, R.string.show_details_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void setToolBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setVisibility(View.VISIBLE);
        toolbar.setTitle(R.string.detail_pop_title);
        toolbar.setNavigationIcon(R.drawable.ic_back_gray);
        toolbar.setTitleTextAppearance(this, R.style.DetailsToolbarStyle);
        setActionBar(toolbar);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false)) {
            setShowWhenLocked(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        DetailsHelper.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* Bug 664105 not need to set null
        mGalleryActivity = null;
        mSource = null;
        mIsDrmDetails = false;
        */
    }

    class DetailsHolder extends RecyclerView.ViewHolder {
        private ImageView mIcon;
        private TextView mTitleView;
        private TextView mSubTitleView;

        public DetailsHolder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.image);
            mTitleView = itemView.findViewById(R.id.title);
            mSubTitleView = itemView.findViewById(R.id.subtitle);
        }

        public void setDetailsItem(DetailsItem item) {
            mTitleView.setText(item.getTitle());
            mSubTitleView.setText(item.getSubTitle());
            mIcon.setImageResource(item.getIconId());
        }
    }

    class DetailsItem {
        private String title;
        private String subTitle;
        private int iconId;

        public DetailsItem(String title) {
            this.title = title;
        }

        public DetailsItem(String title, String subTitle) {
            this(title);
            this.subTitle = subTitle;
        }

        public DetailsItem(String title, String subTitle, int iconId) {
            this(title, subTitle);
            this.iconId = iconId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSubTitle() {
            return subTitle;
        }

        public void setSubTitle(String subTitle) {
            this.subTitle = subTitle;
        }

        public int getIconId() {
            return iconId;
        }

        public void setIconId(int iconId) {
            this.iconId = iconId;
        }
    }

    class DetailsAdapter extends RecyclerView.Adapter<DetailsHolder> implements DetailsAddressResolver.AddressResolvingListener, DetailsHelper.ResolutionResolvingListener {
        private final ArrayList<DetailsItem> mDetailsItems;
        private int mLocationIndex;
        private final Locale mDefaultLocale = Locale.getDefault();
        private final DecimalFormat mDecimalFormat = new DecimalFormat(".####");
        private int mWidthIndex = -1;
        private int mHeightIndex = -1;

        public DetailsAdapter(Context context, MediaDetails details) {
            mDetailsItems = new ArrayList<DetailsItem>(details.size());
            mLocationIndex = -1;
            if (MenuExecutorUtils.getInstance()
                    .setDrmDetails(context, details, new ArrayList<String>(details.size()), mIsDrmDetails)) {
                return;
            }
            setDetails(context, details);
        }

        private void setDetails(Context context, MediaDetails details) {
            boolean resolutionIsValid = true;
            String path = null;
            for (Map.Entry<Integer, Object> detail : details) {
                String value;
                int imageResId = R.drawable.all_photos_icon;
                switch (detail.getKey()) {
                    case MediaDetails.INDEX_LOCATION: {
                        double[] latlng = (double[]) detail.getValue();
                        mLocationIndex = mDetailsItems.size();
                        value = DetailsHelper.resolveAddress(/*mGalleryActivity*/DetailsActivity.this, latlng, this);
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_SIZE: {
                        value = Formatter.formatFileSize(
                                context, (Long) detail.getValue());
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_WHITE_BALANCE: {
                        value = "1".equals(detail.getValue())
                                ? context.getString(R.string.manual)
                                : context.getString(R.string.auto);
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_FLASH: {
                        MediaDetails.FlashState flash =
                                (MediaDetails.FlashState) detail.getValue();
                        if (flash.isFlashFired()) {
                            value = context.getString(R.string.flash_on);
                        } else {
                            value = context.getString(R.string.flash_off);
                        }
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_EXPOSURE_TIME: {
                        value = (String) detail.getValue();
                        double time = Double.valueOf(value);
                        if (time < 1.0f) {
                            value = String.format(mDefaultLocale, "%d/%d", 1,
                                    (int) (0.5f + 1 / time));
                        } else {
                            int integer = (int) time;
                            time -= integer;
                            value = String.valueOf(integer) + "''";
                            if (time > 0.0001) {
                                value += String.format(mDefaultLocale, " %d/%d", 1,
                                        (int) (0.5f + 1 / time));
                            }
                        }
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_WIDTH:
                        mWidthIndex = mDetailsItems.size();
                        if (detail.getValue().toString().equalsIgnoreCase("0")) {
                            value = context.getString(R.string.unknown);
                            resolutionIsValid = false;
                        } else {
                            value = toLocalInteger(detail.getValue());
                        }
                        //imageResId = R.drawable.detail_date;
                        break;
                    case MediaDetails.INDEX_HEIGHT: {
                        mHeightIndex = mDetailsItems.size();
                        if (detail.getValue().toString().equalsIgnoreCase("0")) {
                            value = context.getString(R.string.unknown);
                            resolutionIsValid = false;
                        } else {
                            value = toLocalInteger(detail.getValue());
                        }
                        //imageResId = R.drawable.detail_date;
                        break;
                    }
                    case MediaDetails.INDEX_PATH:
                        if (detail.getValue() != null) {
                            value = "\n" + detail.getValue().toString();
                            path = detail.getValue().toString();
                        } else {
                            value = "\n" + "";
                            path = "";
                        }
                        //imageResId = R.drawable.detail_date;
                        break;
                    case MediaDetails.INDEX_ISO:
                        value = toLocalNumber(Integer.parseInt((String) detail.getValue()));
                        //imageResId = R.drawable.detail_date;
                        break;
                    case MediaDetails.INDEX_FOCAL_LENGTH:
                        double focalLength = Double.parseDouble(detail.getValue().toString());
                        value = toLocalNumber(focalLength);
                        //imageResId = R.drawable.detail_date;
                        break;
                    case MediaDetails.INDEX_ORIENTATION:
                        value = toLocalInteger(detail.getValue());
                        //imageResId = R.drawable.detail_date;
                        break;
                    default: {
                        Object valueObj = detail.getValue();
                        if (valueObj == null) {
                            if (MenuExecutorUtils.getInstance().keyMatchDrm(detail.getKey())) {
                                valueObj = " ";
                                value = valueObj.toString();
                                break;
                            }
                            Log.d(TAG, DetailsHelper.getDetailsName(context, detail.getKey())
                                    + "'s value is Null");
                            valueObj = "";
                        }
                        value = valueObj.toString();
                        //imageResId = R.drawable.detail_date;
                    }
                }
                int key = detail.getKey();

                if (MenuExecutorUtils.getInstance().keyMatchDrm(key)) {
                    continue;
                }

                DetailsItem item = new DetailsItem(DetailsHelper.getDetailsName(context, key));

                value = value.trim();
                if (details.hasUnit(key)) {
                    value = String.format("%s %s", value, context.getString(details.getUnit(key)));
                }

                item.setSubTitle(value);
                item.setIconId(imageResId);

                mDetailsItems.add(item);
            }
            if (!resolutionIsValid) {
                DetailsHelper.resolveResolution(path, this);
            }
        }

        @Override
        public DetailsHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new DetailsHolder(LayoutInflater.from(DetailsActivity.this).inflate(R.layout.detail_list_item_layout, null, false));
        }

        @Override
        public void onBindViewHolder(DetailsHolder detailsHolder, int i) {
            detailsHolder.setDetailsItem(mDetailsItems.get(i));
        }

        @Override
        public int getItemCount() {
            return mDetailsItems.size();
        }

        @Override
        public void onAddressAvailable(String address) {
            mDetailsItems.get(mLocationIndex).setSubTitle(address);
            notifyDataSetChanged();
        }

        @Override
        public void onResolutionAvailable(int width, int height) {
            if (width == 0 || height == 0) {
                return;
            }
            mDetailsItems.get(mWidthIndex).setSubTitle(String.valueOf(width));
            mDetailsItems.get(mHeightIndex).setSubTitle(String.valueOf(height));
            notifyDataSetChanged();
        }

        /**
         * Converts the given integer (given as String or Integer object) to a
         * localized String version.
         */
        private String toLocalInteger(Object valueObj) {
            if (valueObj instanceof Integer) {
                return toLocalNumber((Integer) valueObj);
            } else {
                String value = valueObj.toString();
                try {
                    value = toLocalNumber(Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    // Just keep the current "value" if we cannot
                    // parse it as a fallback.
                }
                return value;
            }
        }

        /**
         * Converts the given integer to a localized String version.
         */
        private String toLocalNumber(int n) {
            return String.format(mDefaultLocale, "%d", n);
        }

        /**
         * Converts the given double to a localized String version.
         */
        private String toLocalNumber(double n) {
            return mDecimalFormat.format(n);
        }
    }

    class DetailsAdapter2 extends RecyclerView.Adapter<DetailsHolder> implements DetailsHelper.ResolutionResolvingListener2 {
        private final ArrayList<DetailsItem> mDetailsItems;
        private final Locale mDefaultLocale = Locale.getDefault();
        private final DecimalFormat mDecimalFormat = new DecimalFormat(".####");
        private final DecimalFormat sMegaPixelFormat = new DecimalFormat("##0.0");

        private boolean resolutionIsValid = true;
        private int resolutionIndex;
        private String mFileSize;
        private String mFileTitle;

        public DetailsAdapter2(Context context, MediaDetails details) {
            mDetailsItems = new ArrayList<DetailsItem>();
            setDetails(context, details);
        }

        private void setDetails(Context context, MediaDetails details) {
            DetailsItem date = dateInfo(context, details);
            if (date != null) {
                mDetailsItems.add(date);
            }

            DetailsItem path = pathInfo(context, details);
            if (path != null) {
                mDetailsItems.add(path);
                if (!resolutionIsValid) {
                    DetailsHelper.resolveResolution2(mFileTitle, this, mFileSize);
                }
            }

            DetailsItem maker = makerInfo(context, details);
            if (maker != null) {
                mDetailsItems.add(maker);
            }

            DetailsItem location = locationInfo(context, details);
            if (location != null) {
                mDetailsItems.add(location);
            }

            DetailsItem more = moreInfo(context, details);
            if (more != null) {
                mDetailsItems.add(more);
            }
        }

        private DetailsItem dateInfo(Context context, MediaDetails details) {
            Object valueObj = getDetailValueByKey(details, MediaDetails.INDEX_DATETIME);
            DetailsItem item = null;
            if (valueObj == null) {
                Log.d(TAG, "dateInfo valueObj = null");
            } else {
                try {
                    DateFormat dateFormat = DateFormat.getDateTimeInstance();
                    String dateFormatString = dateFormat.format(new Date((long) valueObj));
                    Date date = dateFormat.parse(dateFormatString);
                    Log.d(TAG, "dateInfo valueObj=" + valueObj + ", formatted: " + dateFormatString + ", parsed: " + date);
                    SimpleDateFormat titleFormat = new SimpleDateFormat(context.getString(R.string.detail_date_info_format));
                    boolean is24Hour = android.text.format.DateFormat.is24HourFormat(DetailsActivity.this);
                    SimpleDateFormat subtitleFormat = new SimpleDateFormat(is24Hour ? "EEEE HH:mm" : "EEEE hh:mm aa");
                    item = new DetailsItem(titleFormat.format(date), subtitleFormat.format(date), R.drawable.detail_date);
                } catch (Exception e) {
                    Log.d(TAG, "dateInfo exception :" + e.toString());
                }
            }
            return item;
        }

        private DetailsItem pathInfo(Context context, MediaDetails details) {
            DetailsItem item = null;
            String title = "";

            Object pathObj = getDetailValueByKey(details, MediaDetails.INDEX_PATH);
            if (pathObj == null) {
                Log.d(TAG, "pathInfo pathObj = null");
            } else {
                title = pathObj.toString();
            }

            item = new DetailsItem(title, pathSubtitle(context, details, title), R.drawable.detail_path);
            return item;
        }

        private String pathSubtitle(Context context, MediaDetails details, String path) {
            resolutionIndex = mDetailsItems.size();
            String width = "0";
            String height = "0";

            Object widthObj = getDetailValueByKey(details, MediaDetails.INDEX_WIDTH);
            if (widthObj.toString().equalsIgnoreCase("0")) {
                width = "0";
                resolutionIsValid = false;
            } else {
                width = toLocalInteger(widthObj);
            }

            Object heightObj = getDetailValueByKey(details, MediaDetails.INDEX_HEIGHT);
            if (heightObj.toString().equalsIgnoreCase("0")) {
                height = "0";
                resolutionIsValid = false;
            } else {
                height = toLocalInteger(heightObj);
            }

            String p1 = sMegaPixelFormat.format((Integer.parseInt(width) * Integer.parseInt(height)) / 1e6) + "MP";
            String p2 = width + "x" + height;
            Object sizeObj = getDetailValueByKey(details, MediaDetails.INDEX_SIZE);
            String p3 = Formatter.formatFileSize(context, sizeObj != null ? (Long) sizeObj : 0);

            mFileSize = p3;
            mFileTitle = path;

            return p1 + "   " + p2 + "   " + p3;
        }

        private DetailsItem makerInfo(Context context, MediaDetails details) {
            DetailsItem item = null;
            String maker = "";
            String model = "";
            String aperture = "";
            String focusLength = "";
            String iso = "";

            Object makerObj = getDetailValueByKey(details, MediaDetails.INDEX_MAKE);
            if (makerObj == null) {
                Log.d(TAG, "makerInfo makerObj = null");
            } else {
                maker = makerObj.toString();
            }

            Object modelObj = getDetailValueByKey(details, MediaDetails.INDEX_MODEL);
            if (modelObj == null) {
                Log.d(TAG, "makerInfo modelObj = null");
            } else {
                model = modelObj.toString();
            }

            Object apertureObj = getDetailValueByKey(details, MediaDetails.INDEX_APERTURE);
            if (apertureObj == null) {
                Log.d(TAG, "makerInfo apertureObj = null");
            } else {
                aperture = apertureObj.toString();
            }

            Object focalLengthObj = getDetailValueByKey(details, MediaDetails.INDEX_FOCAL_LENGTH);
            if (focalLengthObj == null) {
                Log.d(TAG, "makerInfo focalLengthObj = null");
            } else {
                double focalLength = Double.parseDouble(focalLengthObj.toString());
                focusLength = toLocalNumber(focalLength);
            }

            Object isoObj = getDetailValueByKey(details, MediaDetails.INDEX_ISO);
            if (isoObj == null) {
                Log.d(TAG, "makerInfo isoObj = null");
            } else {
                iso = toLocalNumber(Integer.parseInt((String) isoObj));
            }

            String title = maker + "   " + model;
            String subtitle = "";

            if (!aperture.trim().equals("")) {
                subtitle += "f/" + aperture + "   ";
            }

            if (!focusLength.trim().equals("")) {
                subtitle += focusLength + "mm" + "   ";
            }

            if (!iso.trim().equals("")) {
                subtitle += "ISO" + iso;
            }

            if (!title.trim().equals("") || !subtitle.trim().equals("")) {
                item = new DetailsItem(title, subtitle, R.drawable.detail_aperture);
            }

            return item;
        }

        private DetailsItem locationInfo(Context context, MediaDetails details) {
            DetailsItem item = null;
            double[] location = (double[]) getDetailValueByKey(details, MediaDetails.INDEX_LOCATION);
            if (location == null || location.length != 2) {
                Log.d(TAG, "locationInfo location[] is invalid");
            } else {
                DecimalFormat df = new DecimalFormat("#.000000");
                String subtitle = df.format(location[0]) + ", " + df.format(location[1]);
                item = new DetailsItem(context.getString(R.string.detail_location), subtitle, R.drawable.detail_location);
            }
            return item;
        }

        private DetailsItem moreInfo(Context context, MediaDetails details) {
            DetailsItem item = null;
            String flash = "";
            String whitebalance = "";
            String orientation = "";
            String exposureTime = "";

            Map.Entry<Integer, Object> flashDetail = getDetailByKey(details, MediaDetails.INDEX_FLASH);
            if (flashDetail == null) {
                Log.d(TAG, "makerInfo flashDetail = null");
            } else {
                MediaDetails.FlashState flashState = (MediaDetails.FlashState) flashDetail.getValue();
                if (flashState.isFlashFired()) {
                    flash = context.getString(R.string.flash_on);
                } else {
                    flash = context.getString(R.string.flash_off);
                }
            }

            Map.Entry<Integer, Object> whitebalanceDetail = getDetailByKey(details, MediaDetails.INDEX_WHITE_BALANCE);
            if (whitebalanceDetail == null) {
                Log.d(TAG, "makerInfo whitebalanceDetail = null");
            } else {
                whitebalance = DetailsHelper.getDetailsName(context, MediaDetails.INDEX_WHITE_BALANCE) + ": " + ("1".equals(whitebalanceDetail.getValue()) ? context.getString(R.string.manual) : context.getString(R.string.auto));
            }

            Map.Entry<Integer, Object> orientationDetail = getDetailByKey(details, MediaDetails.INDEX_ORIENTATION);
            if (orientationDetail == null) {
                Log.d(TAG, "makerInfo orientationDetail = null");
            } else {
                orientation = DetailsHelper.getDetailsName(context, MediaDetails.INDEX_ORIENTATION) + ": " + toLocalInteger(orientationDetail.getValue());
            }

            Map.Entry<Integer, Object> exposureTimeDetail = getDetailByKey(details, MediaDetails.INDEX_EXPOSURE_TIME);
            if (exposureTimeDetail == null) {
                Log.d(TAG, "makerInfo exposureTimeDetail = null");
            } else {
                String exposureTimeDetailValue = (String) exposureTimeDetail.getValue();
                double time = Double.valueOf(exposureTimeDetailValue);
                if (time < 1.0f) {
                    exposureTimeDetailValue = String.format(mDefaultLocale, "%d/%d", 1,
                            (int) (0.5f + 1 / time));
                } else {
                    int integer = (int) time;
                    time -= integer;
                    exposureTimeDetailValue = String.valueOf(integer) + "''";
                    if (time > 0.0001) {
                        exposureTimeDetailValue += String.format(mDefaultLocale, " %d/%d", 1,
                                (int) (0.5f + 1 / time));
                    }
                }
                exposureTime = DetailsHelper.getDetailsName(context, MediaDetails.INDEX_EXPOSURE_TIME) + ": " + exposureTimeDetailValue;
            }

            String subtitle = "";

            if (!flash.trim().equals("")) {
                subtitle += flash + "   ";
            }

            if (!whitebalance.trim().equals("")) {
                subtitle += whitebalance + "   ";
            }

            if (!orientation.trim().equals("")) {
                subtitle += orientation + "   ";
            }

            if (!exposureTime.trim().equals("")) {
                subtitle += exposureTime;
            }

            if (!subtitle.trim().equals("")) {
                item = new DetailsItem(context.getString(R.string.detail_more), subtitle, R.drawable.detail_other);
            }

            return item;
        }

        private Object getDetailValueByKey(MediaDetails details, int key) {
            for (Map.Entry<Integer, Object> detail : details) {
                if (detail.getKey() == key) {
                    return detail.getValue();
                }
            }
            return null;
        }

        private Map.Entry<Integer, Object> getDetailByKey(MediaDetails details, int key) {
            for (Map.Entry<Integer, Object> detail : details) {
                if (detail.getKey() == key) {
                    return detail;
                }
            }
            return null;
        }

        @Override
        public DetailsHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new DetailsHolder(LayoutInflater.from(DetailsActivity.this).inflate(R.layout.detail_list_item_layout, null, false));
        }

        @Override
        public void onBindViewHolder(DetailsHolder detailsHolder, int i) {
            detailsHolder.setDetailsItem(mDetailsItems.get(i));
        }

        @Override
        public int getItemCount() {
            return mDetailsItems.size();
        }

        @Override
        public void onResolutionAvailable(int width, int height, String size) {
            if (width == 0 || height == 0) {
                return;
            }
            String p1 = sMegaPixelFormat.format((width * height) / 1e6) + "MP";
            String p2 = width + "x" + height;
            mDetailsItems.get(resolutionIndex).setSubTitle(p1 + "   " + p2 + "   " + size);
            notifyDataSetChanged();
        }

        /**
         * Converts the given integer (given as String or Integer object) to a
         * localized String version.
         */
        private String toLocalInteger(Object valueObj) {
            if (valueObj instanceof Integer) {
                return toLocalNumber((Integer) valueObj);
            } else {
                String value = valueObj.toString();
                try {
                    value = toLocalNumber(Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    // Just keep the current "value" if we cannot
                    // parse it as a fallback.
                }
                return value;
            }
        }

        /**
         * Converts the given integer to a localized String version.
         */
        private String toLocalNumber(int n) {
            return String.format(Locale.ENGLISH, "%d", n);
        }

        /**
         * Converts the given double to a localized String version.
         */
        private String toLocalNumber(double n) {
            return mDecimalFormat.format(n);
        }
    }
}
