package com.android.gallery3d.v2.page;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.DetailsAddressResolver;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.media.extras.MediaExtras;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.DetailsLocationResolver;
import com.android.gallery3d.v2.util.PermissionUtil;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DetailsPageFragment extends BasePageFragment implements DetailsHelper.ResolutionResolvingListener2,
        DetailsAddressResolver.AddressResolvingListener, DetailsLocationResolver.LocationResolvingListener {
    private static final String TAG = DetailsPageFragment.class.getSimpleName();

    private MediaItem mMediaItem;
    private MediaDetails mMediaDetails;
    private Adapter mAdapter;

    private DecimalFormat mDecimalFormat;
    private DecimalFormat sMegaPixelFormat;
    private Locale mDefaultLocale;

    private List<DetailsItem> mDetailsItemList;

    private int mResolutionIndex;
    private boolean mResolutionIsValid;
    private String mFileSize;
    private String mFileTitle;

    private int mLocationIndex;
    private double[] mLatlng;

    private Toolbar mToolbar;
    private View mToolbarLine;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        mDecimalFormat = new DecimalFormat(".####");
        sMegaPixelFormat = new DecimalFormat("##0.0");
        mDefaultLocale = Locale.getDefault();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_details_page, container, false);
        mToolbar = v.findViewById(R.id.toolbar);
        mToolbarLine = v.findViewById(R.id.toolbar_line);
        RecyclerView recyclerView = v.findViewById(R.id.detail_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new Adapter();
        recyclerView.setAdapter(mAdapter);
        if (isNextPage()) {//隐藏底部Tab
            setTabsVisible(false);
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (PermissionUtil.hasPermissions(getContext())) {
            if (mMediaItem == null) {
                String path = getArguments().getString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH);
                if (TextUtils.isEmpty(path)) {
                    throw new RuntimeException("MediaItem path not set");
                }
                mMediaItem = (MediaItem) getDataManager().getMediaObject(path);
                if (mMediaItem == null) {
                    Log.d(TAG, "onResume: mMediaItem is null.");
                    getActivity().getSupportFragmentManager().popBackStack();
                    return;
                }
            }
            load();
            startQueryLocation();
        }

        if (isNextPage()) {
            setNavigationTitle(getString(R.string.detail_pop_title));
        }

        if (getArguments().getInt(Constants.KEY_BUNDLE_CONTAINER_ID) == R.id.fragment_full_container) {
            mToolbar.setVisibility(View.VISIBLE);
            mToolbarLine.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mToolbar.getLayoutParams();
            params.topMargin = GalleryUtils.getStatusBarHeight(getActivity());
            mToolbar.setLayoutParams(params);
            mToolbar.setTitle(getString(R.string.detail_pop_title));
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
            setStatusBarVisibleLight();
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        DetailsHelper.pause();

        if (getArguments().getInt(Constants.KEY_BUNDLE_CONTAINER_ID) == R.id.fragment_full_container) {
            setStatusBarVisibleWhite();
        }
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    private void startQueryLocation() {
        if (mMediaItem == null || GalleryUtils.isValidLocation(((LocalMediaItem) mMediaItem).latitude,
                ((LocalMediaItem) mMediaItem).longitude)) {
            Log.d(TAG, "not need to query location from exif info.");
            return;
        }
        Log.d(TAG, "start to query location from exif info.");
        boolean isImage = mMediaItem instanceof LocalImage;
        DetailsHelper.resolveLocation(getActivity(), mMediaItem.getContentUri(), this, isImage);
    }

    private synchronized void load() {
        mMediaDetails = mMediaItem.getDetails();
        Log.d(TAG, "load mMediaItem(" + mMediaItem + "), mMediaDetails(" + mMediaDetails + ")");
        initDetailsData();
    }

    @Override
    public void onLocationAvailable(double latitude, double longitude) {
        Log.d(TAG, "onLocationAvailable latitude = " + latitude + ", longitude = " + longitude);
        if (mMediaItem == null || isPaused()
                || !GalleryUtils.isValidLocation(latitude,longitude)) {
            return;
        }
        ((LocalMediaItem) mMediaItem).latitude = latitude;
        ((LocalMediaItem) mMediaItem).longitude = longitude;
        Log.d(TAG, "onLocationAvailable call load again.");
        load();
    }

    @Override
    public void onAddressAvailable(String address) {
        Log.d(TAG, "onAddressAvailable address = " + address);
        if (address == null || TextUtils.isEmpty(address)) {
            return;
        }
        mDetailsItemList.get(mLocationIndex).setSubTitle(address);
        mAdapter.notifyItemChanged(mLocationIndex);
    }

    @Override
    public void onResolutionAvailable(int width, int height, String size) {
        if (width == 0 || height == 0) {
            return;
        }
        String p1 = sMegaPixelFormat.format((width * height) / 1e6) + "MP";
        String p2 = width + "x" + height;
        mDetailsItemList.get(mResolutionIndex).setSubTitle(p1 + "   " + p2 + "   " + size);
        mAdapter.notifyItemChanged(mResolutionIndex);
    }

    private void initDetailsData() {
        mDetailsItemList = new ArrayList<>();

        //Date
        DetailsItem dateItem = dateInfo();
        if (dateItem != null) {
            mDetailsItemList.add(dateItem);
        }

        //Path
        DetailsItem pathItem = pathInfo();
        if (pathItem != null) {
            mDetailsItemList.add(pathItem);
            if (!mResolutionIsValid) {
                DetailsHelper.resolveResolution2(mFileTitle, this, mFileSize);
            }
        }

        //Maker
        DetailsItem makerItem = makerInfo();
        if (makerItem != null) {
            mDetailsItemList.add(makerItem);
        }

        //Location
        DetailsItem loactionItem = locationInfo();
        if (loactionItem != null) {
            mDetailsItemList.add(loactionItem);
            DetailsHelper.resolveAddress(getActivity(), mLatlng, this);
        }

        //More
        DetailsItem moreItem = moreInfo();
        if (moreItem != null) {
            mDetailsItemList.add(moreItem);
        }

        mAdapter.setData(mDetailsItemList);
    }

    private DetailsItem dateInfo() {
        Object valueObj = detailValue(mMediaDetails, MediaDetails.INDEX_DATETIME);
        DetailsItem item = null;
        if (valueObj == null) {
            // Log.d(TAG, "dateInfo valueObj = null");
        } else {
            Cursor cursor = null;
            try {
                //
                int timezone_offset = -1;
                if (mMediaItem instanceof LocalMediaItem && getContext() != null) {
                    Uri uri = MediaExtras.Extension.Media.CONTENT_URI.buildUpon().appendPath(String.valueOf(((LocalMediaItem) mMediaItem).id)).build();
                    cursor = getContext().getContentResolver().query(uri, new String[]{MediaExtras.Extension.Columns.TIMEZONE_OFFSET}, null, null, null);
                    if (cursor != null && cursor.moveToNext()) {
                        timezone_offset = cursor.getInt(0);
                    }
                }
                Log.d(TAG, "dateInfo timezone_offset = " + timezone_offset);
                //
                DateFormat dateFormat = DateFormat.getDateTimeInstance();
                String dateFormatString = dateFormat.format(new Date((Long) valueObj));
                Date date = dateFormat.parse(dateFormatString);
                SimpleDateFormat titleFormat = new SimpleDateFormat(getString(R.string.detail_date_info_format));
                boolean is24Hour = android.text.format.DateFormat.is24HourFormat(getContext());
                SimpleDateFormat subtitleFormat = new SimpleDateFormat(is24Hour ? "EEEE HH:mm" : "EEEE hh:mm aa");
                if (timezone_offset != -1) {
                    date = new Date(date.getTime() + timezone_offset);
                    titleFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    subtitleFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                item = new DetailsItem(titleFormat.format(date), subtitleFormat.format(date), R.drawable.detail_date);
            } catch (Exception e) {
                Log.d(TAG, "dateInfo exception :" + e.toString());
            } finally {
                Utils.closeSilently(cursor);
            }
        }
        return item;
    }

    private DetailsItem pathInfo() {
        DetailsItem item = null;
        String title = "";

        Object pathObj = detailValue(mMediaDetails, MediaDetails.INDEX_PATH);
        if (pathObj == null) {
            // Log.d(TAG, "pathInfo pathObj = null");
        } else {
            title = pathObj.toString();
        }

        item = new DetailsItem(title, pathSubtitle(title), R.drawable.detail_path);
        return item;
    }

    private String pathSubtitle(String path) {
        mResolutionIsValid = true;
        mResolutionIndex = mDetailsItemList.size();
        String width = "0";
        String height = "0";

        Object widthObj = detailValue(mMediaDetails, MediaDetails.INDEX_WIDTH);
        if (widthObj.toString().equalsIgnoreCase("0")) {
            width = "0";
            mResolutionIsValid = false;
        } else {
            width = toLocalInteger(widthObj);
        }

        Object heightObj = detailValue(mMediaDetails, MediaDetails.INDEX_HEIGHT);
        if (heightObj.toString().equalsIgnoreCase("0")) {
            height = "0";
            mResolutionIsValid = false;
        } else {
            height = toLocalInteger(heightObj);
        }

        String p1 = sMegaPixelFormat.format((Integer.parseInt(width) * Integer.parseInt(height)) / 1e6) + "MP";
        String p2 = width + "x" + height;
        Object sizeObj = detailValue(mMediaDetails, MediaDetails.INDEX_SIZE);
        String p3 = Formatter.formatFileSize(getContext(), sizeObj != null ? (Long) sizeObj : 0);

        mFileSize = p3;
        mFileTitle = path;

        return p1 + "   " + p2 + "   " + p3;
    }

    private DetailsItem makerInfo() {
        DetailsItem item = null;
        String maker = "";
        String model = "";
        String aperture = "";
        String focusLength = "";
        String iso = "";

        Object makerObj = detailValue(mMediaDetails, MediaDetails.INDEX_MAKE);
        if (makerObj == null) {
            // Log.d(TAG, "makerInfo makerObj = null");
        } else {
            maker = makerObj.toString();
        }

        Object modelObj = detailValue(mMediaDetails, MediaDetails.INDEX_MODEL);
        if (modelObj == null) {
            // Log.d(TAG, "makerInfo modelObj = null");
        } else {
            model = modelObj.toString();
        }

        Object apertureObj = detailValue(mMediaDetails, MediaDetails.INDEX_APERTURE);
        if (apertureObj == null) {
            // Log.d(TAG, "makerInfo apertureObj = null");
        } else {
            aperture = apertureObj.toString();
        }

        Object focalLengthObj = detailValue(mMediaDetails, MediaDetails.INDEX_FOCAL_LENGTH);
        if (focalLengthObj == null) {
            // Log.d(TAG, "makerInfo focalLengthObj = null");
        } else {
            double focalLength = Double.parseDouble(focalLengthObj.toString());
            focusLength = toLocalNumber(focalLength);
        }

        Object isoObj = detailValue(mMediaDetails, MediaDetails.INDEX_ISO);
        if (isoObj == null) {
            // Log.d(TAG, "makerInfo isoObj = null");
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

    private DetailsItem locationInfo() {
        mLocationIndex = mDetailsItemList.size();
        DetailsItem item = null;
        double[] latlng = (double[]) detailValue(mMediaDetails, MediaDetails.INDEX_LOCATION);
        if (latlng == null || !GalleryUtils.isValidLocation(latlng[0], latlng[1])) {
            // Log.d(TAG, "locationInfo latlng = " + latlng);
        } else {
            mLatlng = latlng;
            DecimalFormat df = new DecimalFormat("#.000000");
            String subtitle = df.format(latlng[0]) + ", " + df.format(latlng[1]);
            item = new DetailsItem(getString(R.string.detail_location), subtitle, R.drawable.detail_location);
        }
        return item;
    }

    private DetailsItem moreInfo() {
        DetailsItem item = null;
        String flash = "";
        String whitebalance = "";
        String orientation = "";
        String exposureTime = "";

        Map.Entry<Integer, Object> flashDetail = detail(mMediaDetails, MediaDetails.INDEX_FLASH);
        if (flashDetail == null) {
            // Log.d(TAG, "makerInfo flashDetail = null");
        } else {
            MediaDetails.FlashState flashState = (MediaDetails.FlashState) flashDetail.getValue();
            if (flashState.isFlashFired()) {
                flash = getString(R.string.flash_on);
            } else {
                flash = getString(R.string.flash_off);
            }
        }

        Map.Entry<Integer, Object> whitebalanceDetail = detail(mMediaDetails, MediaDetails.INDEX_WHITE_BALANCE);
        if (whitebalanceDetail == null) {
            // Log.d(TAG, "makerInfo whitebalanceDetail = null");
        } else {
            whitebalance = DetailsHelper.getDetailsName(getContext(), MediaDetails.INDEX_WHITE_BALANCE) + ": " + ("1".equals(whitebalanceDetail.getValue()) ? getString(R.string.manual) : getString(R.string.auto));
        }

        Map.Entry<Integer, Object> orientationDetail = detail(mMediaDetails, MediaDetails.INDEX_ORIENTATION);
        if (orientationDetail == null) {
            // Log.d(TAG, "makerInfo orientationDetail = null");
        } else {
            orientation = DetailsHelper.getDetailsName(getContext(), MediaDetails.INDEX_ORIENTATION) + ": " + toLocalInteger(orientationDetail.getValue());
        }

        Map.Entry<Integer, Object> exposureTimeDetail = detail(mMediaDetails, MediaDetails.INDEX_EXPOSURE_TIME);
        if (exposureTimeDetail == null) {
            // Log.d(TAG, "makerInfo exposureTimeDetail = null");
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
            exposureTime = DetailsHelper.getDetailsName(getContext(), MediaDetails.INDEX_EXPOSURE_TIME) + ": " + exposureTimeDetailValue;
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
            item = new DetailsItem(getString(R.string.detail_more), subtitle, R.drawable.detail_other);
        }

        return item;
    }

    private Object detailValue(MediaDetails details, int key) {
        for (Map.Entry<Integer, Object> detail : details) {
            if (detail.getKey() == key) {
                return detail.getValue();
            }
        }
        return null;
    }

    private Map.Entry<Integer, Object> detail(MediaDetails details, int key) {
        for (Map.Entry<Integer, Object> detail : details) {
            if (detail.getKey() == key) {
                return detail;
            }
        }
        return null;
    }

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

    private String toLocalNumber(int n) {
        return String.format(mDefaultLocale, "%d", n);
    }

    private String toLocalNumber(double n) {
        return mDecimalFormat.format(n);
    }

    private static class DetailsItem {
        private String title;
        private String subTitle;
        private int iconId;

        public DetailsItem(String title, String subTitle, int iconId) {
            this.title = title;
            this.subTitle = subTitle;
            this.iconId = iconId;
        }

        public String getTitle() {
            return title;
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
    }

    private static class Holder extends RecyclerView.ViewHolder {
        private ImageView mIcon;
        private TextView mTitle;
        private TextView mSubTitle;

        public Holder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.image);
            mTitle = itemView.findViewById(R.id.title);
            mSubTitle = itemView.findViewById(R.id.subtitle);
        }

        public void bind(DetailsItem item) {
            //Image
            mIcon.setImageResource(item.getIconId());
            //Title
            mTitle.setText(item.getTitle());
            //SubTitle
            mSubTitle.setText(item.getSubTitle());
        }
    }

    private static class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<DetailsItem> mData;

        public Adapter() {
            this.mData = new ArrayList<>();
        }

        public void setData(List<DetailsItem> data) {
            this.mData.clear();
            this.mData.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_details_list, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(this.mData.get(position));
        }

        @Override
        public int getItemCount() {
            return this.mData.size();
        }
    }
}
