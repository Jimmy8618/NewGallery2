
package com.sprd.gallery3d.app;

import com.android.gallery3d.app.GalleryStorageUtil;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.sprd.frameworks.StandardFrameworks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.util.Config;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.bumptech.glide.Glide;
import com.sprd.gallery3d.drm.SomePageUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;

public class VideosFragment extends Fragment implements OnItemClickListener,
        OnItemLongClickListener {
    private ListView mListView;
    private Context mContext;
    private VideoAdapter mAdapter;
    private static final String TAG = "VideosFragment";
    private ActionMode mActionMode;
    private Menu mMenu;
    private HashMap<Integer, Boolean> checkboxes = new HashMap<Integer, Boolean>();
    private Map<Integer, VideoItems> checkItem = new TreeMap<Integer, VideoItems>();
    private ArrayList<VideoItems> mVideoList = new ArrayList<VideoItems>();
    private String mFragmentName;
    private static final String FLAG_GALLERY = "startByGallery";
    private static final int REQUEST_TRIM = 6;
    public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
    private ImageView mImageView;
    private TextView mTextView;
    private String mOtgDevicePath;
    private static final String STATE_SAVE_IS_HIDDEN = "STATE_SAVE_IS_HIDDEN";
    private Activity mActivity;
    private DateFormat mFormater;
    private VideoItems mItem = null;
    private int mVideoId = 0;
    private String mTitle = null;
    private AlertDialog mDeleteDialog;
    private AlertDialog mDetailsDialog;
    private RefreshHandler mHandler = new RefreshHandler();
    private final int REFRESH_VIDEOLIST = 1;
    private final int REFRESH_VIDEOLIST_UI = 2;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private long mLastClickTime = -1;
    private ProgressDialog mProgressDialog;
    private static int THUMB_NAIL_WIDTH = 400;
    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
    private static final int SCOPED_REQUEST_CODE = 1;
    private boolean mRequestSdcardPermission;
    private ArrayList<String> mStorageList = new ArrayList<>();
    private static final int MAX_SHARE_VIDEO_NUM = 100;

    /*
     *  SPRD : add for self test: we must have a empty constructor here for instantiated
     * Unable to instantiate fragment com.sprd.gallery3d.app.VideosFragment @{
     */
    public VideosFragment() {
        Log.d(TAG, "Nullary constructor for VideosFragment");
    }
    /*@}*/

    public VideosFragment(ArrayList<VideoItems> videoList, String fragmentName) {
        // TODO Auto-generated constructor stub
        mVideoList = videoList;
        mFragmentName = fragmentName;
    }

    public VideosFragment(ArrayList<VideoItems> videoList, String otgdevicepath, String fragmentName) {
        mVideoList = videoList;
        mOtgDevicePath = otgdevicepath;
        mFragmentName = fragmentName;
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    class RefreshHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            mVideoList = (ArrayList<VideoItems>) msg.obj;
            switch (what) {
                case REFRESH_VIDEOLIST_UI:
                    if (mVideoList.size() == 0) {
                        mListView.setVisibility(View.GONE);
                        mImageView.setVisibility(View.VISIBLE);
                        mTextView.setVisibility(View.VISIBLE);
                        // SPRD:Add for bug588637 The Gallery will crash when you remove the OTG cable in video player view
                        mTextView.setText(R.string.no_videos);
                        mAdapter.notifyDataSetChanged();
                        updateActionModeIfNeed();
                    } else {
                        mListView.setVisibility(View.VISIBLE);
                        mImageView.setVisibility(View.GONE);
                        mTextView.setVisibility(View.GONE);
                        mAdapter.notifyDataSetChanged();
                        updateActionModeIfNeed();
                    }
                    break;
                default:
                    break;


            }
        }
    }

    public int getFragmentId() {
        return this.getId();
    }

    public String getOtgDevicePath() {
        return mOtgDevicePath;
    }

    public void notifyChange() {
        if (mAdapter == null || mListView == null) {
            return;
        }
        mChildHandler.sendEmptyMessage(REFRESH_VIDEOLIST);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            boolean isSupportHidden = savedInstanceState.getBoolean(STATE_SAVE_IS_HIDDEN);
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            if (isSupportHidden) {
                ft.hide(this);
            } else {
                ft.show(this);
            }
            ft.commit();
        }
        mHandlerThread = new HandlerThread("RefreshVideoList");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case REFRESH_VIDEOLIST:
                        mAdapter.refreshVideoList();
                        break;
                }
                return true;
            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SAVE_IS_HIDDEN, isHidden());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onCreateView()");
        if (isLowRam) {
            Resources r = getResources();
            DisplayMetrics metrics = r.getDisplayMetrics();
            int screenWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
            if (screenWidth <= 480) {
                THUMB_NAIL_WIDTH = 150;
            } else {
                THUMB_NAIL_WIDTH = 300;

            }
        }
        if (mContext == null) {
            mContext = inflater.getContext();
        }
        View view = inflater.inflate(R.layout.all_videos_list, container, false);
        mListView = view.findViewById(R.id.listView);
        mImageView = view.findViewById(R.id.imageView);
        mTextView = view.findViewById(R.id.textView);
        mAdapter = new VideoAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);

        String country = mContext.getResources().getConfiguration().locale.getCountry();
        if (country.equals("XA")) {
            mFormater = new SimpleDateFormat("M/dd/yyyy");
        } else {
            mFormater = new SimpleDateFormat(getResources().getString(R.string.date_format));
        }

        if (mVideoList.size() == 0) {
            mListView.setVisibility(View.GONE);
            mImageView.setVisibility(View.VISIBLE);
            mTextView.setVisibility(View.VISIBLE);
            mTextView.setText(getResources().getString(R.string.no_videos));
        }
        return view;
    }

    @Override
    public void onAttach(Context context) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onAttach()");
        mActivity = (Activity) context;
        mContext = context;
        mContext.getContentResolver().registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        super.onAttach(context);
    }

    @Override
    public void onResume() {
        // TODO Auto-generated method stub
        Log.d(TAG, "onResume()");
        super.onResume();
        if (GalleryUtils.checkStoragePermissions(mContext)) {
            mChildHandler.sendEmptyMessage(REFRESH_VIDEOLIST);
        }
    }

    private ContentObserver observer = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "onChange");
            /* SPRD:Add for bug595344 When clicking the gotonormalplay button,it shows wrong activity @{ */
            if (mAdapter == null) {
                return;
            }
            /* Bug595344 end @} */
            mChildHandler.removeMessages(REFRESH_VIDEOLIST);
            mChildHandler.sendEmptyMessage(REFRESH_VIDEOLIST);

        }
    };

    public void playVideo(Activity activity, Uri uri, String title) {
        try {
            Intent intent = new Intent(mContext, MovieActivity.class)
                    .setDataAndType(uri, "video/*")
                    .putExtra(Intent.EXTRA_TITLE, title)
                    .putExtra(FLAG_GALLERY, true)
                    .putExtra("mFragmentName", mFragmentName)
                    .putExtra("mOtgDevicePath", mOtgDevicePath)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // .putExtra(PermissionsActivity.UI_START_BY,startFrom);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public Uri getContentUri(int id) {
        Uri baseUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO Auto-generated method stub
        if (mActionMode == null && mVideoList.size() != 0) {
            Log.d(TAG, "mActionMode = " + mActionMode);
            long currentTime = System.currentTimeMillis();
            long intervalTime = currentTime - mLastClickTime;
            mLastClickTime = currentTime;
            if (Math.abs(intervalTime) < 1000) {
                return;
            }
            String path = mVideoList.get(position).getUrl();
            mTitle = mVideoList.get(position).getDisplayName();
            mVideoId = mVideoList.get(position).getId();
            /* SPRDL: Add for DRM feature @{ */
            AlertDialog.OnClickListener onClickListener = new AlertDialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    playVideo(getActivity(), getContentUri(mVideoId), mTitle);
                }
            };
            if (SomePageUtils.getInstance().newCheckPressedIsDrm(mActivity, path, onClickListener,
                    false)) {
                return;
            } else {
                playVideo(getActivity(), getContentUri(mVideoId), mTitle);
            }
            Log.d(TAG, "getContentUri(videoId) =" + getContentUri(mVideoId));
        } else {
            CheckBox checkBox = view.findViewById(R.id.checkBox);
            Log.d(TAG, "checkBox" + checkBox);
            if (null == checkBox) {
                return;
            }
            if (true == checkBox.isChecked()) {
                checkBox.setChecked(false);
            } else {
                checkBox.setChecked(true);
            }
            checkboxOnclick(position);
            /* SPRD: Delete for bug606821 Wrong logic in sharemenu showing @{ */
            if (checkItem.size() != 0) {
                for (VideoItems v : checkItem.values()) {
                    mItem = v;
                    mMenu.findItem(R.id.action_delete).setVisible(true);
                    /* SPRD: Delete for bug603592 Wrong logic in sharemenu showing @{ */
                    if (isDrmNotSupportShare(mItem.getUrl())) {
                        mMenu.findItem(R.id.action_share).setVisible(false);
                        break;
                    } else {
                        mMenu.findItem(R.id.action_share).setVisible(true);
                    }
                    /* Bug603592 end @} */
                }
            } else {
                mMenu.findItem(R.id.action_share).setVisible(false);
                mMenu.findItem(R.id.action_delete).setVisible(false);
            }
            /* Bug606821 end @} */
            checkIsDrmDetails();
            /* DRM feature end @} */
        }
    }

    /* SPRD: Add for drm new feature @{ */
    public void checkIsDrmDetails() {
        if (!SomePageUtils.getInstance().checkIsDrmFile(mItem.getUrl()) || checkItem.size() > 1 || checkItem.size() == 0) {
            mMenu.findItem(R.id.protection_information).setVisible(false);
        } else {
            mMenu.findItem(R.id.protection_information).setVisible(true);
        }
    }
    /* Drm new feature end @} */

    private void checkboxOnclick(int pos) {
        Boolean result = checkboxes.get(pos);
        if (result == null || result == false) {
            checkboxes.put(pos, true);
            VideoItems item = mVideoList.get(pos);
            checkItem.put(pos, item);
            Log.d(TAG, "the size of the checkItem=" + checkItem.size());
            mActionMode.setTitle(selectedNum());
        } else {
            checkboxes.put(pos, false);
            checkItem.remove(pos);
            Log.d(TAG, "the size of the checkItem=" + checkItem.size());
            mActionMode.setTitle(selectedNum());
        }
    }

    private String selectedNum() {
        String selectedText = getString(R.string.current_select, checkItem.size());
        return selectedText;
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                   long id) {
        // TODO Auto-generated method stub
        Log.d(TAG, "mActionMode = " + mActionMode);
        if (mActionMode == null && mAdapter != null) {
            VideoItems item = mVideoList.get(position);
            mItem = item;
            checkItem.put(position, item);
            getActivity().startActionMode(new MyActionModeCallback());
            CheckBox checkBox = view.findViewById(R.id.checkBox);
            checkBox.setChecked(true);
            checkboxes.put(position, true);
            Log.d(TAG, "checkItem=" + checkItem + "    position=" + position + "   item"
                    + item.getDisplayName() + "    number=" + checkItem.size());
            mActionMode.setTitle(selectedNum());
        } else {
            return false;
        }
        return true;
    }

    public class MyActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            mActionMode = mode;
            mMenu = menu;
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.actionmode_share_delete, mMenu);
            checkIsDrmDetails();// SPRD:Add for drm new feature
            /* SPRD: Add for bug599941 non-sd drm videos are not supported to share @{ */
            MenuItem shareMenu = menu.findItem(R.id.action_share);
            if (isDrmNotSupportShare(mItem.getUrl())) {
                shareMenu.setVisible(false);
            } else {
                shareMenu.setVisible(true);
            }
            /* Bug599941 end @}　*/
            mAdapter.setCheckboxHidden(false);
            mAdapter.notifyDataSetChanged();
            /*SPRD:Bug664144 In select mode ,the drawlayout still can slide @{*/
            if (mActivity instanceof NewVideoActivity) {
                ((NewVideoActivity) mActivity).setDrawerLockClosed();
            }
            /*end @}*/
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            if (checkItem.size() != mVideoList.size()) {
                menu.findItem(R.id.select_all).setTitle(getResources().getString(R.string.select_all));
            } else {
                menu.findItem(R.id.select_all).setTitle(getResources().getString(R.string.deselect_all));
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            if (item.getItemId() == R.id.action_share) {
                /* SPRD:Add for bug597680 When click the sharemenu several times,there will be many share dialogs @{ */
                if (VideoUtil.isFastClick()) {
                    return true;
                }
                /* Bug597680 @} */
                /* SPRD: Modify for subject tests @{ */
                Intent shareIntent = new Intent();
                Map<Integer, VideoItems> deleteItem = new TreeMap<Integer, VideoItems>(
                        checkItem);
                ArrayList<Uri> uris = new ArrayList<Uri>();
                for (Map.Entry<Integer, VideoItems> entry : deleteItem.entrySet()) {
                    int fileId = entry.getValue().getId();
                    uris.add(getContentUri(fileId));
                }
                // UNISOC added for bug 1162081, limit the number of videos that can be shared.
                if (checkItem.size() > MAX_SHARE_VIDEO_NUM) {
                    Toast.makeText(mContext, getString(R.string.share_max_warning),
                            Toast.LENGTH_SHORT).show();
                    return true;
                } else if (checkItem.size() > 1 && checkItem.size() <= MAX_SHARE_VIDEO_NUM) {
                    shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                } else {
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                }
                shareIntent.setType("video/*");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                /* Subject tests modification @} */
            } else if (item.getItemId() == R.id.action_delete) {
                /* SPRD:Add for bug597680 When click the sharemenu several times,there will be many share dialogs @{ */
                if (VideoUtil.isFastClick()) {
                    return true;
                }
                /* Bug597680 @} */
                AlertDialog.Builder dialog = new Builder(mContext);
                dialog.setMessage(getResources().getQuantityString(R.plurals.delete_confirm, checkItem.size()))
                        .setPositiveButton(R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Bug 913573:add for delete file on SD card
                                if (needRequestPermission()) {
                                    requestScopedDirectoryAccess();
                                } else {
                                    deleteFileAsync();
                                }
                                dialog.dismiss();
                            }
                        }).setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        dialog.dismiss();
                    }
                });
                // SPRD:Add for bug605694 The dialog still exists when the actionmode is destroyed
                mDeleteDialog = dialog.show();
                mDeleteDialog.setCanceledOnTouchOutside(false);
            } else if (item.getItemId() == R.id.select_all) {
                if (item.getTitle().equals(getResources().getString(R.string.select_all))) {
                    for (int i = 0; i < mVideoList.size(); i++) {
                        checkboxes.put(i, true);
                        VideoItems videoItem = mVideoList.get(i);
                        checkItem.put(i, videoItem);
                    }
                    // SPRD:Delete for bug606821 Wrong logic in sharemenu showing
                    mMenu.findItem(R.id.action_delete).setVisible(true);
                } else {
                    for (int i = 0; i < mVideoList.size(); i++) {
                        checkboxes.clear();
                        checkItem.clear();
                    }
                    // SPRD:Delete for bug606821 Wrong logic in sharemenu showing
                    mMenu.findItem(R.id.action_delete).setVisible(false);
                    //SPRD:Bug609412 Select all then cancel select all and click share_button,the VideoPlayer is crash
                    mMenu.findItem(R.id.action_share).setVisible(false);
                }
                mActionMode.setTitle(selectedNum());
                mAdapter.notifyDataSetChanged();
                /* SPRD:Add for drm new feature @{ */
                checkIsDrmDetails();
                /* SPRD: Delete for bug606821 Wrong logic in sharemenu showing @{ */
                for (VideoItems v : checkItem.values()) {
                    mItem = v;
                    if (isDrmNotSupportShare(mItem.getUrl())) {
                        mMenu.findItem(R.id.action_share).setVisible(false);
                        break;
                    } else {
                        mMenu.findItem(R.id.action_share).setVisible(true);
                    }
                }
                /* Bug606821 end @} */
            } else if (item.getItemId() == R.id.protection_information) {
                boolean isDrmVideoRightsValidity = SomePageUtils.getInstance().checkIsDrmFileValid(mItem.getUrl());
                boolean isDrmSupportTransfer = SomePageUtils.getInstance().isDrmSupportTransfer(mItem.getUrl());
                DrmManagerClient client = SomePageUtils.getInstance().getDrmManagerClient();
                ContentValues value = client.getConstraints(mItem.getUrl(), DrmStore.Action.PLAY);
                byte[] clickTime = value.getAsByteArray(DrmStore.ConstraintsColumns.EXTENDED_METADATA);

                Object drmStartTime = null;
                Object drmEndTime = null;
                Long startTime = null;
                Long endTime = null;
                if (value != null) {
                    startTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_START_TIME);
                    endTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_EXPIRY_TIME);
                }
                drmStartTime = SomePageUtils.getInstance().newTransferDate(startTime, NewVideoActivity.getNewVideoActivity());
                drmEndTime = SomePageUtils.getInstance().newTransferDate(endTime, NewVideoActivity.getNewVideoActivity());
                Object drmExpirationTime = SomePageUtils.getInstance().newCompareDrmExpirationTime(value.get(DrmStore.ConstraintsColumns.LICENSE_AVAILABLE_TIME), clickTime, NewVideoActivity.getNewVideoActivity());
                Object drmRemain = SomePageUtils.getInstance().newCompareDrmRemainRight(mItem.getUrl(), value.get(DrmStore.ConstraintsColumns.REMAINING_REPEAT_COUNT), NewVideoActivity.getNewVideoActivity());
                View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_view, null);
                TextView fileName = view.findViewById(R.id.fileName);
                TextView rightsValidity = view.findViewById(R.id.rightsValidity);
                TextView isTransferAllowed = view.findViewById(R.id.isTransferAllowed);
                TextView startTimeView = view.findViewById(R.id.startTime);
                TextView endTimeView = view.findViewById(R.id.endTime);
                TextView expirationTimeView = view.findViewById(R.id.expirationTime);
                TextView remainTime = view.findViewById(R.id.remainTime);

                fileName.setText(getDrmString(R.string.file_name) + " " + mItem.getDisplayName());
                rightsValidity.setText(getDrmString(R.string.rights_validity) + (isDrmVideoRightsValidity ? getDrmString(R.string.rights_validity_valid) : getDrmString(R.string.rights_validity_invalid)));
                isTransferAllowed.setText(getDrmString(R.string.rights_status) + (isDrmSupportTransfer ? getDrmString(R.string.rights_status_share) : getDrmString(R.string.rights_status_not_share)));
                startTimeView.setText(getDrmString(R.string.start_time) + drmStartTime);
                endTimeView.setText(getDrmString(R.string.end_time) + drmEndTime);
                expirationTimeView.setText(getDrmString(R.string.expiration_time) + drmExpirationTime);
                remainTime.setText(getDrmString(R.string.remain_times) + drmRemain);

                AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
                dialog.setTitle(R.string.drm_info).setView(view).setNegativeButton(R.string.close, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        dialog.dismiss();
                    }
                }).create();
                // SPRD:Add for bug605694 The dialog still exists when the actionmode is destroyed
                mDetailsDialog = dialog.show();
                /* Drm new feature end @} */
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            VideoUtil.updateStatusBarColor(NewVideoActivity.getNewVideoActivity(), true);
            if (mAdapter != null) {
                mAdapter.setCheckboxHidden(true);
                mAdapter.notifyDataSetChanged();
                if (mMenu != null) {
                    mMenu.clear();
                    mMenu.close();
                }
                if (mDeleteDialog != null) {
                    mDeleteDialog.dismiss();
                    mDeleteDialog = null;
                }
                clearContainer();
                mActionMode = null;
            }
            /*SPRD:Bug664144 In select mode ,the drawlayout still can slide @{*/
            if (mActivity instanceof NewVideoActivity) {
                ((NewVideoActivity) mActivity).setDrawerUnLocked();
            }
            /*end @}*/
        }
    }

    private boolean needRequestPermission() {
        for (VideoItems v : checkItem.values()) {
            String path = v.getUrl();
            Log.d(TAG, "needRequestPermission() path = " + path);
            if (!GalleryStorageUtil.isInInternalStorage(path)
                    && !SdCardPermission.hasStoragePermission(path)) {
                String storageName = SdCardPermission.getStorageName(path);
                if (mStorageList != null && !mStorageList.contains(storageName)) {
                    mStorageList.add(storageName);
                }
            }
        }
        return (mStorageList != null && !mStorageList.isEmpty()) ? true : false;
    }

    public void requestScopedDirectoryAccess() {
        if (mStorageList != null && !mStorageList.isEmpty()) {
            StorageVolume storageVolume = null;
            for (StorageVolume volume : getVolumes()) {
                File volumePath = StandardFrameworks.getInstances().getVolumePathFile(volume);

                if (!volume.isPrimary() && volumePath != null
                        && Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED)
                        && volumePath.getAbsolutePath().equals(mStorageList.get(0))) {
                    // UNISOC added for bud 1111804, use new SAF issue to get SD write permission
                    storageVolume = volume;
                    break;
                }
            }
            if (storageVolume != null) {
                Intent intent;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    intent = storageVolume.createAccessIntent(null);
                } else {
                    intent = storageVolume.createOpenDocumentTreeIntent();
                }

                if (intent != null) {
                    mRequestSdcardPermission = true;
                    startActivityForResult(intent, SCOPED_REQUEST_CODE);
                }
            }
        }
    }

    private List<StorageVolume> getVolumes() {
        StorageManager sm = (StorageManager)
                mContext.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = sm.getStorageVolumes();

        return volumes;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, " requestCode = " + requestCode + " resultCode = " + resultCode
                + " data = " + data);
        if (requestCode == SCOPED_REQUEST_CODE) {
            mRequestSdcardPermission = false;
            if (resultCode == Activity.RESULT_CANCELED){
                mStorageList.clear();
                AlertDialog.Builder alterDialog = new AlertDialog.Builder(mContext);
                alterDialog.setMessage(R.string.no_sd_write_permission)
                           .setPositiveButton(R.string.confirm, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).setCancelable(false)
                        .create()
                        .show();
            } else if (resultCode == Activity.RESULT_OK) {
                Uri uri = data != null ? data.getData() : null;
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                mActivity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
                if (mStorageList != null && !mStorageList.isEmpty()) {
                    Config.setPref(mStorageList.get(0), uri.toString().substring(uri.toString().lastIndexOf("/") + 1));
                    mStorageList.remove(0);
                    if (!mStorageList.isEmpty()) {
                        requestScopedDirectoryAccess();
                    } else {
                        deleteFileAsync();
                    }
                }
            }
        }
    }

    private void deleteFile(VideoItems item) {
        //Bug 913573/1181720:add for delete file on SD card or OTG devices
        String filePath = item.getUrl();
        boolean isInInternalStorage = GalleryStorageUtil.isInInternalStorage(filePath);
        Log.d(TAG, " filePath = " + filePath + "isInInternalStorage = " + isInInternalStorage);
        if (!isInInternalStorage) {
            Uri treeUri = SdCardPermission.getAccessStorageUri(filePath);
            String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            String[] s = treeDocumentId.split(":");
            String relativePath = filePath.substring(filePath.indexOf(s[0]) + s[0].length());
            String documentId = treeDocumentId.concat(relativePath);
            Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            Log.d(TAG, "treeUri = " + treeUri.toString() + " treeDocumentId = " + treeDocumentId
                    + " index = " + filePath.indexOf(s[0]) + " length = " + s[0].length()
                    + " relativePath = " + relativePath + " documentId = " + documentId);
            try {
                DocumentsContract.deleteDocument(mContext.getContentResolver(), uri);
            } catch (Exception e) {
                Log.e(TAG, "deleteDocument()", e);
            }
        } else {
            Log.d(TAG, "isExternalStorageMounted = " + StorageInfos.isExternalStorageMounted()
                    + " isInternalStorageSupported = " + StorageInfos.isInternalStorageSupported());
            if (StorageInfos.isExternalStorageMounted() || !StorageInfos.isInternalStorageSupported()) {
                mContext.getContentResolver().delete(
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                item.getId()), null, null);
            } else {
                mContext.getContentResolver().delete(
                        ContentUris.withAppendedId(MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                                item.getId()), null, null);
            }
            File del = new File(filePath);
            Log.d(TAG, "!del.exists()=" + !del.exists() + "        !del.delete()=" + !del.delete());
        }
    }

    private void clearContainer() {
        if (checkboxes != null) {
            checkboxes.clear();
        }
        if (checkItem != null) {
            checkItem.clear();
        }
    }

    private void invalidateCheckbox(CheckBox box, int pos) {
        Log.d(TAG, "checkboxes=" + checkboxes);
        Boolean result = checkboxes.get(pos);
        if (result == null || result == false) {
            box.setChecked(false);
        } else {
            VideoItems item = mVideoList.get(pos);
            checkItem.put(pos, item);
            box.setChecked(true);
        }
    }

    private void deleteFileAsync() {
        AsyncTask<Void, Integer, Void> task = new AsyncTask<Void, Integer, Void>() {

            private Map<Integer, VideoItems> deleteItem = new TreeMap<Integer, VideoItems>(
                    checkItem);

            protected void createDialog() {
                if (null != mContext) {
                    mProgressDialog = new ProgressDialog(mContext);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setTitle(R.string.wait_for_deleting);
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.setMax(deleteItem.size());
                    mProgressDialog.show();
                }
            }

            @Override
            protected Void doInBackground(Void... params) {
                Integer i = 0;
                Log.d(TAG, "the size of the checkItem=" + checkItem.size());
                for (Map.Entry<Integer, VideoItems> entry : deleteItem.entrySet()) {
                    Log.d(TAG, "delete the video: " + entry.getValue().getUrl());
                    deleteFile(entry.getValue());
                    publishProgress(++i);
                }
                return null;
            }

            @Override
            protected void onPreExecute() {
                // TODO Auto-generated method stub
                super.onPreExecute();
                if (deleteItem.size() > 1) {
                    createDialog();
                } else {
                    for (Map.Entry<Integer, VideoItems> entry : deleteItem.entrySet()) {
                        Log.d(TAG, "remove the video: " + entry.getValue());
                        mVideoList.remove(entry.getValue());
                    }
                    deleteFinish();
                }
            }

            @Override
            protected void onPostExecute(Void result) {
                // TODO Auto-generated method stub
                super.onPostExecute(result);
                if (deleteItem.size() > 1) {
                    if (null != mProgressDialog) {
                        mProgressDialog.cancel();
                    }
                    deleteFinish();
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                // TODO Auto-generated method stub
                super.onProgressUpdate(values);
                if (deleteItem.size() > 1 && null == mProgressDialog) {
                    createDialog();
                }
                if (mProgressDialog != null) {
                    mProgressDialog.setProgress(values[0]);
                }
            }

        };
        task.execute((Void[]) null);
    }

    private void deleteFinish() {
        // TODO Auto-generated method stub
        if (mActionMode != null) {
            mActionMode.finish();
        }
        clearContainer();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.d(TAG, "onDestroy()");
        if (null != mProgressDialog) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        mContext.getContentResolver().unregisterContentObserver(observer);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        // TODO Auto-generated method stub
        Log.d(TAG, "onStop()");
        // SPRD added for bug 1003702, the video can be deleted normally after the screen is locked.
        if (mActionMode != null && !mRequestSdcardPermission) {
            mAdapter.setCheckboxHidden(true);
            mAdapter.notifyDataSetChanged();
            clearContainer();
            mActionMode.finish();
            mActionMode = null;
            /* SPRD:Add for bug605694 The dialog still exists when the actionmode is destroyed @{ */
            if (mDeleteDialog != null) {
                mDeleteDialog.dismiss();
                mDeleteDialog = null;
            }
            if (mDetailsDialog != null) {
                mDetailsDialog.dismiss();
                mDetailsDialog = null;
            }
            /* Bug605694 end @} */
        }
        super.onStop();
    }

    class VideoAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private String imageUrl;
        private boolean isCheckboxHidden = true;
        private String mCurrentFragment;
        private static final String ALL_VIDEOS_FRAGMENT = "AllVideosFragment";
        private static final String LOCAL_VIDEOS_FRAGMENT = "LocalVideosFragment";
        private static final String FILMED_VIDEOS_FRAGMENT = "FilmedVideosFragment";
        private static final String OTG_VIDEOS_FRAGMENT = "OtgVideosFragment";

        public void setCheckboxHidden(boolean flag) {
            isCheckboxHidden = flag;
        }

        public String getStringTime(int duration) {
            return VideoUtil.calculatTime(duration);
        }

        public VideoAdapter() {
            inflater = LayoutInflater.from(mContext);
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return mVideoList.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return mVideoList.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.videos_list_item, parent,
                        false);
                holder = new ViewHolder();
                holder.mDisplayName = convertView
                        .findViewById(R.id.title);
                holder.mDuration = convertView
                        .findViewById(R.id.duration);
                holder.mVideoSize = convertView.findViewById(R.id.size);
                holder.mTimeModified = convertView.findViewById(R.id.date_modified);
                holder.mThumbnail = convertView
                        .findViewById(R.id.imageView);
                holder.mDrmLock = convertView.findViewById(R.id.drmlock);
                holder.mCheckBox = convertView.findViewById(R.id.checkBox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String displayName = mVideoList.get(position).getDisplayName();
            String duration = mVideoList.get(position).getDuration();
            long videoSize = mVideoList.get(position).getSize();
            String dateModified = mVideoList.get(position).getDate_modified();
            holder.mDisplayName.setText(displayName);
            if (duration == null) {
                duration = getResources().getString(R.string.unknown);
            } else {
                duration = getStringTime(Integer.parseInt(duration));
            }
            holder.mDuration.setText(duration);
            holder.mTimeModified.setText(mFormater.format(new Date(Long.parseLong(dateModified) * 1000)));
            holder.mVideoSize.setText(VideoUtil.getStringVideoSize(mContext, videoSize));
            imageUrl = mVideoList.get(position).getUrl();
            Log.d(TAG, "imageUrl=" + imageUrl);
            RequestOptions requestOptions = new RequestOptions()
                    .override(THUMB_NAIL_WIDTH)
                    .fitCenter();
            RequestBuilder requestBuilder = Glide.with(mContext).load(imageUrl).apply(requestOptions);
            requestBuilder.into(holder.mThumbnail);
            /* SPRD: Add for DRM feature @{ */
            if (SomePageUtils.getInstance().checkIsDrmFile(imageUrl)) {
                holder.mDrmLock.setVisibility(View.VISIBLE);
                if (SomePageUtils.getInstance().checkIsDrmFileValid(imageUrl)) {
                    holder.mDrmLock.setImageResource(R.drawable.ic_drm_unlock);
                } else {
                    holder.mDrmLock.setImageResource(R.drawable.ic_drm_lock);
                }
            } else {
                holder.mDrmLock.setVisibility(View.GONE);
            }
            /* DRM feature end @} */
            invalidateCheckbox(holder.mCheckBox, position);
            if (!isCheckboxHidden) {
                holder.mCheckBox.setVisibility(View.VISIBLE);
            } else {
                holder.mCheckBox.setChecked(false);
                holder.mCheckBox.setVisibility(View.GONE);
            }
            return convertView;
        }

        private void refreshVideoList() {
            /* SPRD: Add for bug593851 boolean java.lang.String.equals(java.lang.Object) @{ */
            if (mFragmentName == null) {
                return;
            }
            /* Bug593851 end @} */
            try {
                ArrayList<VideoItems> mNewVideoList = new ArrayList<VideoItems>();
                if (mFragmentName.equals(ALL_VIDEOS_FRAGMENT)) {
                    Log.d(TAG, "refreshVideoList----AllVideosFragment");
                    mNewVideoList = VideoUtil.getVideoList(mContext);
                } else if (mFragmentName.equals(LOCAL_VIDEOS_FRAGMENT)) {
                    Log.d(TAG, "refreshVideoList----LocalVideosFragment");
                    mNewVideoList = VideoUtil.getLocalVideos(VideoUtil.getVideoList(mContext));
                } else if (mFragmentName.equals(FILMED_VIDEOS_FRAGMENT)) {
                    Log.d(TAG, "refreshVideoList----FilmedVideosFragment");
                    mNewVideoList = VideoUtil.getFilmedVideos(VideoUtil.getVideoList(mContext));
                } else if (mFragmentName.equals(OTG_VIDEOS_FRAGMENT)) {
                    mNewVideoList = VideoUtil.getOtgVideos(VideoUtil.getVideoList(mContext), mOtgDevicePath);
                } else if (mFragmentName.equals("HistoryVideosFragment")) {
                }
                Message message = new Message();
                message.what = REFRESH_VIDEOLIST_UI;
                message.obj = mNewVideoList;
                mHandler.sendMessage(message);
            } catch (RejectedExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ViewHolder {
        TextView mDisplayName;
        TextView mDuration;
        TextView mVideoSize;
        TextView mTimeModified;
        ImageView mThumbnail;
        ImageView mDrmLock;
        CheckBox mCheckBox;
    }

    public void updateActionModeIfNeed() {
        if (mVideoList.size() == 0 && mActionMode != null && mAdapter != null) {
            clearContainer();
            mActionMode.finish();
            mActionMode = null;
        } else {
            if (mActionMode != null) {
                mActionMode.setTitle(selectedNum());
            }
        }
    }

    public int getIntFromDimens(int index) {
        int result = this.getResources().getDimensionPixelSize(index);
        return result;
    }

    /* SPRD: Add for drm new feature @{ */
    public String getDrmString(int id) {
        return mContext.getResources().getString(id);
    }
    /* Drm new feature end @}*/

    /* SPRD: Delete for bug603592 Wrong logic in sharemenu showing @{ */
    public boolean isDrmNotSupportShare(String filePath) {
        return SomePageUtils.getInstance().checkIsDrmFile(filePath) && !SomePageUtils.getInstance().newIsSupportShare(filePath);
    }
    /* Bug603592 end @} */
}
