/**
 * Created By Spreadst
 */

package com.sprd.gallery3d.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.MovieActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.SomePageUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class HistoryVideosFragment extends Fragment implements OnChildClickListener,
        OnGroupClickListener {
    private Context mContext;
    private ExpandableListView mExpandableListView;
    private RecentVideoAdapter mAdapter;
    private boolean mTodayRecordShow = false;
    private boolean mYesterdayRecordShow = false;
    private boolean mFurtherRecordShow = false;
    private ArrayList<RecentVideoInfo> mTodayPlayList = new ArrayList<RecentVideoInfo>();
    private ArrayList<RecentVideoInfo> mYesterdayPlayList = new ArrayList<RecentVideoInfo>();
    private ArrayList<RecentVideoInfo> mFurtherPlayList = new ArrayList<RecentVideoInfo>();
    private DataSetObserver mDataSetObserver;
    private static final String TAG = "HistoryVideosFragment";
    private static final String FLAG_GALLERY = "startByGallery";
    private static final String DATABASE_TABLE = "content://com.sprd.gallery3d.app.VideoBookmarkProvider2/bookmarks";
    private static final String HISTORY_VIDEOS_FRAGMENT = "HistoryVideosFragment";
    private static final String FRAGMENTNAME = "mFragmentName";
    private static final int START_GET_VIDEO_LIST = 1;
    private static final int UPDATE_VIEW = 2;
    private ImageView mImageView;
    private TextView mTextView;
    private static final String STATE_SAVE_IS_HIDDEN = "STATE_SAVE_IS_HIDDEN";
    private Handler mHandler = new MyHandler(this);
    private int mVideoId = -1;
    private String mTitle = null;
    private int mPosition;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private Dialog mDialog;
    private long mLastClickTime = -1;
    private static final int MIN_CLICK_DELAY_TIME = 1800;
    private static int THUMB_NAIL_WIDTH = 400;
    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();

    /* SPRD : add for self test: we must have a empty constructor here for instantiated
     * Unable to instantiate fragment com.sprd.gallery3d.app.VideosFragment @{
     */
    public HistoryVideosFragment() {
        Log.d(TAG, "Nullary constructor for HistoryVideosFragment");
    }
    /*@}*/

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        Log.d(TAG, "onCreateView");
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
        View view = inflater.inflate(R.layout.recent_videos_list, container, false);
        mExpandableListView = view.findViewById(R.id.expandableListView);
        mAdapter = new RecentVideoAdapter();
        mExpandableListView.setAdapter(mAdapter);
        mExpandableListView.setOnChildClickListener(this);
        mExpandableListView.setOnGroupClickListener(this);
        mImageView = view.findViewById(R.id.imageView);
        mTextView = view.findViewById(R.id.textView);
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
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
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                VideoUtil.getDatabaseTableName(), true, observer);
        mHandlerThread = new HandlerThread("RefreshVideoList");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_VIEW:
                        refreshVideoList();
                        break;
                    default:
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
    public void onResume() {
        Log.d(TAG, "onResume()");
        updateView();
        super.onResume();
    }

    private void handleStartGetVideoList(Message msg) {
        ArrayList arrayList;
        arrayList = (ArrayList) msg.obj;
        mTodayPlayList = (ArrayList<RecentVideoInfo>) arrayList.get(0);
        mYesterdayPlayList = (ArrayList<RecentVideoInfo>) arrayList.get(1);
        mFurtherPlayList = (ArrayList<RecentVideoInfo>) arrayList.get(2);
        if (mTodayPlayList.size() == 0 && mYesterdayPlayList.size() == 0
                && mFurtherPlayList.size() == 0) {
            mExpandableListView.setVisibility(View.INVISIBLE);
            mImageView.setVisibility(View.VISIBLE);
            mTextView.setVisibility(View.VISIBLE);
            mTextView.setText(R.string.no_video_play_records);
        } else {
            mExpandableListView.setVisibility(View.VISIBLE);
            mImageView.setVisibility(View.INVISIBLE);
            mTextView.setVisibility(View.INVISIBLE);
        }
        mAdapter.notifyDataSetChanged();
        for (int i = 0; i < mAdapter.getGroupCount(); i++) {
            mExpandableListView.collapseGroup(i);
            mExpandableListView.expandGroup(i);
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<HistoryVideosFragment> mHistoryVideosFragment;

        public MyHandler(HistoryVideosFragment historyVideosFragment) {
            mHistoryVideosFragment = new WeakReference<HistoryVideosFragment>(historyVideosFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            HistoryVideosFragment historyVideosFragment = mHistoryVideosFragment.get();
            if (historyVideosFragment == null) {
                return;
            }
            switch (msg.what) {
                case START_GET_VIDEO_LIST:
                    historyVideosFragment.handleStartGetVideoList(msg);
                    break;
                default:
                    break;
            }
        }
    }

    private void updateView() {
        Log.d(TAG, "updateView()");
        mChildHandler.sendEmptyMessage(UPDATE_VIEW);
    }

    private void initRecentVideoList() {
        VideoUtil.getRecentVideos(mContext, mTodayPlayList, mYesterdayPlayList, mFurtherPlayList);
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach()");
        mContext = context;
        super.onAttach(context);
    }

    //Bug765304 Add observer to abserve database change
    private ContentObserver observer = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "onChange");
            if (mAdapter == null) {
                return;
            }
            mChildHandler.removeMessages(UPDATE_VIEW);
            mChildHandler.sendEmptyMessage(UPDATE_VIEW);

        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.clear_all, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_all_records:
                Log.d(TAG, "clear all video records");
                if (mTodayPlayList.size() == 0 && mYesterdayPlayList.size() == 0
                        && mFurtherPlayList.size() == 0) {
                    long mCurrentClickTime = System.currentTimeMillis();
                    if (Math.abs(mCurrentClickTime - mLastClickTime) > MIN_CLICK_DELAY_TIME) {
                        mLastClickTime = mCurrentClickTime;
                        Toast.makeText(mContext, getResources().getString(R.string.no_video_play_records), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    int totalSize = mTodayPlayList.size() + mYesterdayPlayList.size() + mFurtherPlayList.size();
                    if (mDialog == null) {
                        mDialog = new AlertDialog.Builder(mContext)
                                .setMessage(getResources().getQuantityString(R.plurals.records_delete_confirm, totalSize))
                                .setCancelable(false)
                                .setPositiveButton(getResources().getString(R.string.ok),
                                        new OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                getActivity()
                                                        .getContentResolver()
                                                        .delete(Uri
                                                                        .parse(DATABASE_TABLE),
                                                                null, null);
                                                updateView();
                                                dialog.dismiss();
                                                mDialog = null;
                                            }
                                        })
                                .setNegativeButton(getResources().getString(R.string.cancel),
                                        new OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                mDialog = null;
                                            }
                                        }).create();
                    }
                    mDialog.setCanceledOnTouchOutside(false);
                    if (!mDialog.isShowing()) {
                        mDialog.show();
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    private class RecentVideoAdapter extends BaseExpandableListAdapter {
        private String[] mainTypes = new String[]{
                getResources().getString(R.string.today),
                getResources().getString(R.string.yesterday),
                getResources().getString(R.string.earlier)
        };
        private LayoutInflater inflater;

        public RecentVideoAdapter() {
            inflater = LayoutInflater.from(mContext);
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public int getGroupCount() {
            return mainTypes.length;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            if (groupPosition == 0) {
                return mTodayPlayList.size();
            } else if (groupPosition == 1) {
                return mYesterdayPlayList.size();
            } else {
                return mFurtherPlayList.size();
            }
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mainTypes[groupPosition];
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            if (groupPosition == 0) {
                return mTodayPlayList.get(childPosition);
            } else if (groupPosition == 1) {
                return mYesterdayPlayList.get(childPosition);
            } else {
                return mFurtherPlayList.get(childPosition);
            }
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            LinearLayout ll = new LinearLayout(mContext);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            TextView textView = getTextView();
            textView.setText(getGroup(groupPosition).toString());
            if (getGroup(groupPosition).toString().equals(getGroup(0))) {
                textView.setTextColor(getResources().getColor(R.color.sprd_main_theme_color));
            } else {
                textView.setTextColor(getResources().getColor(R.color.sprd_earlier_record_color));
            }
            if (getChildrenCount(groupPosition) != 0) {
                ll.addView(textView);
            }
            return ll;
        }

        private TextView getTextView() {
            AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, getIntFromDimens(R.dimen.sprd_expandablelistview_height));
            TextView textView = new TextView(mContext);
            textView.setLayoutParams(lp);
            textView.setGravity(Gravity.CENTER_VERTICAL);
            textView.setPadding(36, 0, 0, 0);
            textView.setTextSize(14);
            return textView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.history_videos_list, parent, false);
                holder = new ViewHolder();
                holder.mDisplayName = convertView.findViewById(R.id.title);
                holder.mLast_play_date = convertView.findViewById(R.id.duration);
                holder.mThumbnail = convertView.findViewById(R.id.imageView);
                holder.mDrmLock = convertView.findViewById(R.id.drmlock);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            String displayName = null;
            String lastPlayDate = null;
            String uri = null;
            ImageView cutoffLine = convertView.findViewById(R.id.cutoffline);
            try {
                if (groupPosition == 0) {
                    displayName = mTodayPlayList.get(childPosition).getTitle();
                    lastPlayDate = mTodayPlayList.get(childPosition).getBookmark();
                    uri = mTodayPlayList.get(childPosition).getUri();
                    if (childPosition == mTodayPlayList.size() - 1) {
                        cutoffLine.setVisibility(View.GONE);
                    } else {
                        cutoffLine.setVisibility(View.VISIBLE);
                    }
                } else if (groupPosition == 1) {
                    displayName = mYesterdayPlayList.get(childPosition).getTitle();
                    lastPlayDate = mYesterdayPlayList.get(childPosition).getBookmark();
                    uri = mYesterdayPlayList.get(childPosition).getUri();
                    if (childPosition == mYesterdayPlayList.size() - 1) {
                        cutoffLine.setVisibility(View.GONE);
                    } else {
                        cutoffLine.setVisibility(View.VISIBLE);
                    }
                } else {
                    displayName = mFurtherPlayList.get(childPosition).getTitle();
                    lastPlayDate = mFurtherPlayList.get(childPosition).getBookmark();
                    uri = mFurtherPlayList.get(childPosition).getUri();
                    if (childPosition == mFurtherPlayList.size() - 1) {
                        cutoffLine.setVisibility(View.GONE);
                    } else {
                        cutoffLine.setVisibility(View.VISIBLE);
                    }
                }
                /* SPRD:Add for bug602678 com.android.gallery3d happens JavaCrash,log:java.lang.NumberFormatException @{ */
                holder.mDisplayName.setText(displayName);
                holder.mLast_play_date.setText(getResources().getString(R.string.last_play)
                        + VideoUtil.calculatTime(Integer.parseInt(lastPlayDate)));
                RequestOptions requestOptions = new RequestOptions()
                        .override(THUMB_NAIL_WIDTH)
                        .fitCenter();
                RequestBuilder requestBuilder = Glide.with(mContext).load(uri).apply(requestOptions);
                requestBuilder.into(holder.mThumbnail);

                /* SPRD: Add for DRM feature @{ */
                if (SomePageUtils.getInstance().checkIsDrmFile(uri)) {
                    holder.mDrmLock.setVisibility(View.VISIBLE);
                    if (SomePageUtils.getInstance().checkIsDrmFileValid(uri)) {
                        holder.mDrmLock.setImageResource(R.drawable.ic_drm_unlock);
                    } else {
                        holder.mDrmLock.setImageResource(R.drawable.ic_drm_lock);
                    }
                } else {
                    holder.mDrmLock.setVisibility(View.GONE);
                }
                /* DRM feature end @} */
                /* Bug 602678 end@}*/
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void onGroupExpanded(int groupPosition) {

        }

        @Override
        public void onGroupCollapsed(int groupPosition) {

        }

        @Override
        public long getCombinedChildId(long groupId, long childId) {
            return 0;
        }

        @Override
        public long getCombinedGroupId(long groupId) {
            return 0;
        }
    }

    public void refreshVideoList() {
        ArrayList<RecentVideoInfo> mNewTodayPlayList = new ArrayList<RecentVideoInfo>();
        ArrayList<RecentVideoInfo> mNewYesterdayPlayList = new ArrayList<RecentVideoInfo>();
        ArrayList<RecentVideoInfo> mNewFurtherPlayList = new ArrayList<RecentVideoInfo>();
        ArrayList arrayList = new ArrayList();

        VideoUtil.getRecentVideos(mContext, mNewTodayPlayList, mNewYesterdayPlayList, mNewFurtherPlayList);
        arrayList.add(mNewTodayPlayList);
        arrayList.add(mNewYesterdayPlayList);
        arrayList.add(mNewFurtherPlayList);
        Message msg = new Message();
        msg.what = START_GET_VIDEO_LIST;
        msg.obj = arrayList;
        mHandler.sendMessage(msg);

    }

    static class ViewHolder {
        TextView mDisplayName;
        TextView mLast_play_date;
        ImageView mThumbnail;
        String mLast_Play_Time;
        ImageView mDrmLock;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        Log.d(TAG, "onChildClick");
        String path = null;
        /* SPRD: Add for drm new feature @{ */
        //String title = null;
        //int position = 0;
        try {
            if (groupPosition == 0) {
                path = mTodayPlayList.get(childPosition).getUri();
                mTitle = mTodayPlayList.get(childPosition).getTitle();
                mPosition = Integer.parseInt(mTodayPlayList.get(childPosition).getBookmark());
            } else if (groupPosition == 1) {
                path = mYesterdayPlayList.get(childPosition).getUri();
                mTitle = mYesterdayPlayList.get(childPosition).getTitle();
                mPosition = Integer.parseInt(mYesterdayPlayList.get(childPosition).getBookmark());
            } else {
                Log.d(TAG, "mFurtherPlayList");
                path = mFurtherPlayList.get(childPosition).getUri();
                mTitle = mFurtherPlayList.get(childPosition).getTitle();
                mPosition = Integer.parseInt(mFurtherPlayList.get(childPosition).getBookmark());
            }
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        mVideoId = VideoUtil.getIdFromPath(path, mContext);
        Log.d(TAG, "videoId=" + mVideoId);
        /* SPRD: Add for new DRM feature @{ */
        if (mVideoId != -1) {
            AlertDialog.OnClickListener onClickListener = new AlertDialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    playVideo(getActivity(), getContentUri(mVideoId), mTitle, mPosition);
                }
            };
            /* SPRD:Add for bug592616 Video can't play successfully when playing recent list videos @{ */
            if (SomePageUtils.getInstance().newCheckPressedIsDrm(mContext, path, onClickListener,
                    false)) {
                return true;
            } else {
                playVideo(getActivity(), getContentUri(mVideoId), mTitle, mPosition);
            }
            /* Bug592616 end @} */
            /* DRM feature end @} */
        } else {
            long mCurrentClickTime = System.currentTimeMillis();
            if (Math.abs(mCurrentClickTime - mLastClickTime) > MIN_CLICK_DELAY_TIME) {
                mLastClickTime = mCurrentClickTime;
                Toast.makeText(mContext, R.string.video_not_exist, Toast.LENGTH_SHORT).show();
            }
        }
        return true;
        /* Drm new feature end @} */
    }

    public void playVideo(Activity activity, Uri uri, String title, int position) {
        try {
            Intent intent = new Intent(mContext, MovieActivity.class)
                    .setDataAndType(uri, "video/*")
                    .putExtra(Intent.EXTRA_TITLE, title)
                    .putExtra(FLAG_GALLERY, true)
                    .putExtra("position", position)
                    .putExtra("mIsBookmark", false)
                    .putExtra(FRAGMENTNAME, HISTORY_VIDEOS_FRAGMENT)
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

    private void ClearList() {
        mTodayPlayList.clear();
        mYesterdayPlayList.clear();
        mFurtherPlayList.clear();
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        return true;
    }

    public int getIntFromDimens(int index) {
        int result = this.getResources().getDimensionPixelSize(index);
        return result;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GalleryAppImpl.getApplication().getContentResolver().unregisterContentObserver(observer);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }
}
