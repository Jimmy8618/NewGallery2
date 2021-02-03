
package com.sprd.gallery3d.burstphoto;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.GalleryPermissionsActivity;
import com.sprd.gallery3d.app.PermissionsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by apuser on 12/15/16.
 */

public class BurstActivity extends Activity {
    private static final String TAG = BurstActivity.class.getSimpleName();
    private static final boolean DEBUG_BURST = false;
    private List<BurstImageItem> mImageItems = null;
    private View mBurstMainView;
    private ThumbNailBurstRecycleView mThumbNailListView;
    private FilmStripRecyclerView mBurstFilmStripView;

    private MenuItem mComplete;

    private int mSelectNumbers;
    private PopupWindow mPopupWindow;

    private static Activity sLastActivity;
    private LoadImageTask mLoadImageTask;
    private DealImageTask mDealImageTask;

    private boolean mIntentHandled;

    private final int SCROLL_END = 0;
    private final int SCROLL_FROM_CARD = 1;
    private final int SCROLL_FROM_THUMBNAIL = 2;
    private int mScrollFrom = SCROLL_END;

    private int mImageWidth;
    private int mImageHeight;

    private static final int MSG_SET_HIGHLIGHT = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_HIGHLIGHT:
                    clearThumbnailHighLight();
                    int thumbCenter = findCenterItemPosition(mThumbNailListView);
                    Log.d(TAG, "handleMessage recv MSG_SET_HIGHLIGHT thumbCenterItem=" + thumbCenter);
                    if (thumbCenter != RecyclerView.NO_POSITION) {
                        setThumbnailHighLight(thumbCenter);
                    }
                    break;
            }
        }
    };

    FilmStripRecyclerView.SizeChangedListener mBurstFilmStripSizeChangeListener = new FilmStripRecyclerView.SizeChangedListener() {


        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            Log.d(TAG, "FilmStrip onSizeChanged w=" + w + ", h=" + h);
            initData();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        if (GalleryUtils.isMonkey()) {
            if (sLastActivity != null) {
                Log.e(TAG, "BurstActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
                sLastActivity = null;
            }
            sLastActivity = this;
        }

        setContentView(R.layout.burst_activity_layout);

        mSelectNumbers = 0;

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_SHOW_TITLE /*| ActionBar.DISPLAY_SHOW_HOME*/
                        | ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_USE_LOGO);
        setSelectNumbers(mSelectNumbers);

        mBurstMainView = findViewById(R.id.burst_activity_main);
        mThumbNailListView = findViewById(R.id.thumb_nail_list_view);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mThumbNailListView.setLayoutManager(linearLayoutManager);
        mThumbNailListView.setFlingXRatio(0.3);
        mThumbNailListView.addOnScrollListener(new ThumbNailListScrollListener());


        mBurstFilmStripView = findViewById(R.id.burst_filmstrip_view);
        mBurstFilmStripView.setClickable(true);
        mBurstFilmStripView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mBurstFilmStripView.addOnScrollListener(new BurstFilmStripScrollListener());

        if (!GalleryUtils.checkStoragePermissions(this)) {
            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION}, 0);
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
        } else {
            handleIntent();
        }
        mBurstFilmStripView.setOnSizeChangedListener(mBurstFilmStripSizeChangeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mLoadImageTask != null && mLoadImageTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.d(TAG, "onDestroy cancel mLoadImageTask");
            mLoadImageTask.cancel(true);
        }
        if (mDealImageTask != null && mDealImageTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.d(TAG, "onDestroy cancel mDealImageTask ");
            mDealImageTask.cancel(true);
        }
        mBurstFilmStripView.setOnSizeChangedListener(null);
        mThumbNailListView.clearOnScrollListeners();
        mBurstFilmStripView.clearOnScrollListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (GalleryUtils.checkStoragePermissions(this)) {
            if (handleIntent() == 0) {
                int thumbCenter = findCenterItemPosition(mThumbNailListView);
                if (thumbCenter != RecyclerView.NO_POSITION) {
                    setThumbnailHighLight(thumbCenter);
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeMessages(MSG_SET_HIGHLIGHT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < grantResults.length; i++) {
            if (PackageManager.PERMISSION_GRANTED != grantResults[i]) {
                Log.e(TAG, "onRequestPermissionsResult: Missing critical permissions: " + permissions[i]);
                finish();
            }
        }
    }

    private boolean checkPermissions() {
        boolean hasCriticalPermissions;

        hasCriticalPermissions = GalleryUtils.checkStoragePermissions(this);
        if (!hasCriticalPermissions) {
            Intent intent = new Intent(this, GalleryPermissionsActivity.class);
            if (StandardFrameworks.getInstances().getIntentisAccessUriMode(this)) {
                intent.setFlags(getIntent().getFlags());
            }
            if (getIntent().getAction() != null) {
                intent.setAction(getIntent().getAction());
            }
            if (getIntent().getType() != null) {
                intent.setType(getIntent().getType());
            }
            if (getIntent().getData() != null) {
                intent.setData(getIntent().getData());
            }
            if (getIntent().getExtras() != null) {
                intent.putExtras(getIntent().getExtras());
            }
            intent.putExtra(PermissionsActivity.UI_START_BY, PermissionsActivity.START_FROM_GALLERY);
            if (!(sLastActivity != null && sLastActivity.isFinishing()) || !isFinishing()) {
                startActivity(intent);
                finish();
            }
        }
        return hasCriticalPermissions;
    }

    private int handleIntent() {
        if (mIntentHandled) {
            return 0;
        }
        mIntentHandled = true;
        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            String type = intent.getType();
            int mediaType = intent.getIntExtra("media_type", 0);
            Log.d(TAG, "handleIntent uri = " + uri + " type = " + type + " mediaType = " + mediaType);
            if (uri == null || !"image/jpeg".equals(type) || mediaType != MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
                ArrayList<String> pathList = intent
                        .getStringArrayListExtra(BurstImageItem.EXTRA_BURST_IMAGE_ITEMS);
                if (DEBUG_BURST && null != pathList) {
                    mImageItems = new ArrayList<BurstImageItem>();
                    for (String path : pathList) {
                        mImageItems.add(new BurstImageItem(path));
                    }
                    onImagesLoaded();
                }
                finish();
                return -1;
            }
            Cursor cursor = null;
            String dataTaken = null;
            try {
                cursor = getContentResolver().query(uri, new String[]{
                        MediaStore.Images.ImageColumns.DATE_TAKEN
                }, null, null, null);
                if (cursor != null && cursor.moveToNext()) {
                    dataTaken = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN));
                } else {
                    finish();
                    return -1;
                }
            } catch (Exception e) {
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (dataTaken != null) {
                mLoadImageTask = new LoadImageTask(getContentResolver(), dataTaken);
                mLoadImageTask.execute();
            }
        }
        return 1;
    }

    private void keepAllUnSelectedPhotos() {
        /** TODO keep all unselected photos **/
        Log.d(TAG, "keepAllUnSelectedPhotos");
        mDealImageTask = new DealImageTask(this, mImageItems.size() - mSelectNumbers,
                R.string.burst_progress_dialog_title,
                DealImageTask.SAVE_ALL_UN_SELECTED_PHOTOS);
        mDealImageTask.execute();
    }

    private void keepOnlySelectedPhotos() {
        /** TODO keep all selected photos **/
        Log.d(TAG, "keepOnlySelectedPhotos");
        mDealImageTask = new DealImageTask(this, mImageItems.size(),
                R.string.burst_progress_dialog_title,
                DealImageTask.SAVE_ONLY_SELECTED_PHOTOS_AND_DELETE_BURST);
        mDealImageTask.execute();
    }

    private void keepAllPhotos() {
        /** TODO keep all photos **/
        Log.d(TAG, "keepAllPhotos");
        mDealImageTask = new DealImageTask(this, mImageItems.size(),
                R.string.burst_progress_dialog_title,
                DealImageTask.SAVE_ALL_SELECTED_PHOTOS_AND_RESERVE_BURST);
        mDealImageTask.execute();
    }

    private void deleteSelectedPhotos() {
        Log.d(TAG, "deleteSelectedPhotos");
        mDealImageTask = new DealImageTask(this, mImageItems.size(),
                R.string.burst_progress_dialog_title,
                DealImageTask.DELETE_SELECTED_PHOTOS_AND_RESERVE_BURST);
        mDealImageTask.execute();
    }

    class DealImageTask extends AsyncTask<Void, Integer, Void> {

        public static final int SAVE_ALL_UN_SELECTED_PHOTOS = 0;
        public static final int SAVE_ONLY_SELECTED_PHOTOS_AND_DELETE_BURST = 1;
        public static final int SAVE_ALL_SELECTED_PHOTOS_AND_RESERVE_BURST = 2;
        public static final int DELETE_SELECTED_PHOTOS_AND_RESERVE_BURST = 3;

        private ProgressDialog mDialog;
        private Context mContext;
        private int mMax;
        private int mTitleResId;
        private int mAction;

        public DealImageTask(Context context, int max, int titleResId, int action) {
            mContext = context;
            mMax = max;
            mTitleResId = titleResId;
            mAction = action;
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mDialog = new ProgressDialog(mContext);
            mDialog.setTitle(mTitleResId);
            mDialog.setMax(mMax);
            mDialog.setProgress(0);
            mDialog.setMessage(0 + "/" + mMax);
            mDialog.setCancelable(false);
            mDialog.setCanceledOnTouchOutside(false);
            mDialog.show();
            dismissPopupWindow();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            // TODO Auto-generated method stub
            //super.onProgressUpdate(values);
            //handler.post(updateDialog);
            mDialog.setProgress(values[0]);
            mDialog.setMessage(values[0] + "/" + mMax);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            // TODO Auto-generated method stub
            String dstFilePath = null;
            File dstFile = null;
            List<String> scanList = new ArrayList<String>();
            String[] scanArray = null;
            int progress = 0;
            Log.d(TAG, "DealImageTask.doInBackground mAction=" + mAction);
            if (mAction == SAVE_ALL_UN_SELECTED_PHOTOS) {
                for (BurstImageItem item : mImageItems) {
                    if (isCancelled()) {
                        break;
                    }
                    if (!item.isSelected()) {
                        // save file with a copy
                        progress++;
                        publishProgress(progress);

                        dstFilePath = generateName(item.getPath());

                        dstFile = new File(dstFilePath);
                        if (copy(item.getFile(), dstFile)) {// copy
                            scanList.add(dstFilePath);
                        } else {
                            if (dstFile.exists()) {
                                dstFile.delete();
                            }
                        }
                    }
                }
            } else if (mAction == SAVE_ONLY_SELECTED_PHOTOS_AND_DELETE_BURST) {
                StringBuilder updateItems = new StringBuilder("_id in (");
                StringBuilder deleteItems = new StringBuilder("_id in (");
                for (BurstImageItem item : mImageItems) {
                    if (isCancelled()) {
                        break;
                    }
                    if (item.isSelected()) {
                        updateItems.append(item.getId());
                        updateItems.append(',');
                        progress++;
                        publishProgress(progress);
                    } else {
                        deleteItems.append(item.getId());
                        deleteItems.append(',');
                    }

                }
                updateItems.deleteCharAt(updateItems.length() - 1);
                updateItems.append(')');
                ContentValues values = new ContentValues(1);
                values.put("file_flag", 0);
                mContext.getContentResolver()
                        .update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values, updateItems.toString(), null);

                deleteItems.deleteCharAt(deleteItems.length() - 1);
                deleteItems.append(')');
                getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, deleteItems.toString(), null);

            } else if (mAction == SAVE_ALL_SELECTED_PHOTOS_AND_RESERVE_BURST) {
                for (BurstImageItem item : mImageItems) {
                    if (isCancelled()) {
                        break;
                    }
                    if (item.isSelected()) {
                        // save selected file with a copy
                        progress++;
                        publishProgress(progress);

                        dstFilePath = generateName(item.getPath());

                        dstFile = new File(dstFilePath);
                        if (copy(item.getFile(), dstFile)) {// copy

                            ExifInterface exifInterface = new ExifInterface();
                            ParcelFileDescriptor fd = null;
                            try {
                                exifInterface.readExif(dstFilePath);
                                exifInterface.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(0));
                                if (!GalleryStorageUtil.isInInternalStorage(dstFilePath)) {
                                    fd = SdCardPermission.createExternalFileDescriptor(dstFilePath, "rw");
                                }
                                exifInterface.forceRewriteExif(dstFilePath, fd);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                Utils.closeSilently(fd);
                            }
                            scanList.add(dstFilePath);
                        } else {
                            if (dstFile.exists()) {
                                dstFile.delete();
                            }
                        }
                    }
                }
            } else if (mAction == DELETE_SELECTED_PHOTOS_AND_RESERVE_BURST) {
                StringBuilder deleteItems = new StringBuilder("_id in (");
                List<BurstImageItem> imageItems = new ArrayList<BurstImageItem>();
                for (BurstImageItem item : mImageItems) {
                    if (isCancelled()) {
                        break;
                    }
                    if (item.isSelected()) {
                        deleteItems.append(item.getId());
                        deleteItems.append(',');
                        progress++;
                        publishProgress(progress);
                    } else {
                        imageItems.add(item);
                    }
                }
                deleteItems.deleteCharAt(deleteItems.length() - 1);
                deleteItems.append(')');
                Log.d(TAG, "DealImageTask delete_selected delete = " + deleteItems.toString());
                getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, deleteItems.toString(), null);
            }
            Log.d(TAG, "DealImageTask.doInBackground scanList.size = " + scanList.size());
            if (scanList.size() > 0) {
                scanArray = new String[scanList.size()];
                for (int i = 0; i < scanList.size(); i++) {
                    scanArray[i] = scanList.get(i);
                }
                MediaScannerConnection.scanFile(mContext, scanArray, null, null);
            }
            return null;
        }

        private String generateName(String oriName) {// /sdcard/DCIM/Camera/IMG_xxx_xx.jpg
            String outName = subfileName(oriName) + lastAppendName() + ".jpg";
            if (new File(outName).exists()) {
                return generateName(outName);
            } else {
                return outName;
            }
        }

        private String subfileName(String path) {
            return path.substring(0, path.indexOf("."));
        }

        private String lastAppendName() {
            String timeString = String.valueOf(SystemClock.currentThreadTimeMillis());
            return "_" + timeString.substring(timeString.length() - 1);
        }

        private boolean copy(File from, File to) {
            boolean success = true;
            FileInputStream in = null;
            FileOutputStream out = null;
            try {
                in = new FileInputStream(from);
                out = new FileOutputStream(to);
                byte[] buf = new byte[4096];
                int len = 0;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
            } catch (Exception e) {
                success = false;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (Exception ex) {
                    success = false;
                }
            }
            return success;
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
            if (BurstActivity.this.isDestroyed()) {
                return;
            }
            mDialog.cancel();
            finish();
        }

    }

    class LoadImageTask extends AsyncTask<Void, Void, ArrayList<BurstImageItem>> {
        private String dataTaken;
        private ContentResolver mContentResolver;

        public LoadImageTask(ContentResolver c, String dataTaken) {
            this.mContentResolver = c;
            this.dataTaken = dataTaken;
        }

        @Override
        protected ArrayList<BurstImageItem> doInBackground(Void... arg0) {
            // TODO Auto-generated method stub
            Cursor cursor = null;
            ArrayList<BurstImageItem> items = new ArrayList<BurstImageItem>();
            if (isCancelled()) {
                return items;
            }
            try {
                cursor = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{
                                MediaStore.Images.ImageColumns._ID,
                                MediaStore.Images.ImageColumns.DATA,
                                MediaStore.Images.ImageColumns.WIDTH,
                                MediaStore.Images.ImageColumns.HEIGHT
                        }, " datetaken = ? AND (file_flag = ? OR file_flag = ?) ", new String[]{
                                dataTaken,
                                String.valueOf(LocalImage.IMG_TYPE_MODE_BURST),
                                String.valueOf(LocalImage.IMG_TYPE_MODE_BURST_COVER)
                        }, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        if (isCancelled()) {
                            return items;
                        }
                        String path = cursor.getString(cursor
                                .getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                        int _id = cursor.getInt(cursor
                                .getColumnIndex(MediaStore.Images.ImageColumns._ID));
                        mImageWidth = cursor.getInt(2);
                        mImageHeight = cursor.getInt(3);
                        BurstImageItem item = new BurstImageItem(path, _id);
                        items.add(item);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "caught exception " + e.toString());
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return items;
        }

        @Override
        protected void onPostExecute(ArrayList<BurstImageItem> result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
            Log.d(TAG, "LoadImageTask onPostExecute images size = " + result.size());
            mImageItems = result;
            onImagesLoaded();
        }

    }

    private void onImagesLoaded() {
        if (mImageItems.size() <= 0) {
            finish();
            return;
        }
        initData();
    }

    private void initData() {
        //init thumbnail data
        if (mImageItems == null || mImageItems.size() <= 0) {
            return;
        }
        if (mBurstMainView.getHeight() <= 0) {
            return;
        }
        clearThumbnailHighLight();
        final ThumbNailAdapter thumbNailAdapter = new ThumbNailAdapter(this, mImageItems);
        mThumbNailListView.setAdapter(thumbNailAdapter);

        final GestureDetector gestureDetector = new GestureDetector(this, new ThumbNailGestureListener());
        mThumbNailListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        });

        // init filmstrip data
        Log.d(TAG, "initData main height:" + mBurstMainView.getHeight() + ", thumbnail height:" + thumbNailAdapter.getItemSize());
        FilmStripAdapterHelper helper = new FilmStripAdapterHelper(this, mBurstFilmStripView.getWidth(),
                mBurstMainView.getHeight() - thumbNailAdapter.getItemSize(), mImageWidth, mImageHeight);
        FilmStripAdapter filmStripAdapter = new FilmStripAdapter(this, mImageItems, helper);
        filmStripAdapter.setOnItemClickListener(new FilmStripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                mImageItems.get(position).setSelected(!mImageItems.get(position).isSelected());
                thumbNailAdapter.notifyDataSetChanged();
                mSelectNumbers = 0;
                for (BurstImageItem item : mImageItems) {
                    if (item.isSelected()) {
                        mSelectNumbers++;
                    }
                }

                Log.d(TAG, "onItemClick --> mComplete = " + mComplete + ", mSelectNumbers = " + mSelectNumbers);
                if (mComplete != null) {
                    mComplete.setVisible(mSelectNumbers > 0);
                }
                setSelectNumbers(mSelectNumbers);
            }
        });
        mBurstFilmStripView.setAdapter(filmStripAdapter);
    }

    private void dismissPopupWindow() {
        if (mPopupWindow != null) {
            mPopupWindow.dismiss();
            mPopupWindow = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        getMenuInflater().inflate(R.menu.burst_operation, menu);
        mComplete = menu.findItem(R.id.action_complete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case R.id.action_complete: {
//                showPopupWindow();
                showMenuDialog();
                return true;
            }
            case android.R.id.home: {
                finish();
                return true;
            }
        }
        return false;
    }

    private void moveThumbNailToPosition(int targetPosition) {
        stopScroll();
        int firstItem = ((LinearLayoutManager) mThumbNailListView.getLayoutManager())
                .findFirstCompletelyVisibleItemPosition();
        int lastItem = ((LinearLayoutManager) mThumbNailListView.getLayoutManager())
                .findLastCompletelyVisibleItemPosition();
        int centerItem = findCenterItemPosition(mThumbNailListView);
        Log.d(TAG, "moveThumbNailToPosition targetPosition=" + targetPosition + ", firstItem=" + firstItem + ", lastItem=" + lastItem + ", centerItem=" + centerItem + ", childCount=" + mThumbNailListView.getLayoutManager().getChildCount());
        if (centerItem == RecyclerView.NO_POSITION || targetPosition == centerItem) {
            Log.d(TAG, "moveThumbNailToPosition centerItem is invalid or center is target, don't need move.");
            return;
        }

        if (targetPosition < firstItem) {
            mThumbNailListView.smoothScrollToPosition(targetPosition);
        } else if (targetPosition <= lastItem) {
            int centerPositionLeft = mThumbNailListView.getLayoutManager().findViewByPosition(centerItem).getLeft();
            int clickPositionLeft = mThumbNailListView.getLayoutManager().findViewByPosition(targetPosition).getLeft();
            Log.d(TAG, "moveThumbNailToPosition move " + (clickPositionLeft - centerPositionLeft));
            mThumbNailListView.smoothScrollBy((clickPositionLeft - centerPositionLeft), 0);
        } else {
            mThumbNailListView.smoothScrollToPosition(targetPosition);
        }
    }

    class ThumbNailListScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            Log.d(TAG, "ThumbNailListScrollListener.onScrolled  mScrollFrom=" + mScrollFrom);
            if (mScrollFrom == SCROLL_FROM_THUMBNAIL) {
                int thumbCenterItem = findCenterItemPosition(recyclerView);
                if (thumbCenterItem != RecyclerView.NO_POSITION) {
                    int toCenterItem = thumbCenterItem - ThumbNailAdapter.NUM_OF_EMPTY_ITEM_ON_HEAD;
                    int offset = (mBurstFilmStripView.getWidth() - ((FilmStripAdapter) mBurstFilmStripView.getAdapter())
                            .getItemWidth()) / 2;
                    Log.d(TAG, "ThumbNailListScrollListener.onScrolled filmstrip scroll to toCenterItem=" +
                            toCenterItem + ", offset=" + offset);
                    ((LinearLayoutManager) mBurstFilmStripView.getLayoutManager()).scrollToPositionWithOffset(toCenterItem, offset);

                }
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            Log.d(TAG, "ThumbNailListScrollListener.onScrollStateChanged newState=" + newState + ", mScrollFrom=" + mScrollFrom);
            switch (newState) {
                case RecyclerView.SCROLL_STATE_IDLE: {
                    if (mScrollFrom == SCROLL_FROM_THUMBNAIL) {
                        mScrollFrom = SCROLL_END;
                    }
                    needSetHighLight();
                }
                break;

                case RecyclerView.SCROLL_STATE_DRAGGING:
                case RecyclerView.SCROLL_STATE_SETTLING: {
                    if (mScrollFrom == SCROLL_FROM_CARD) {
                        mBurstFilmStripView.stopScroll();
                    }
                    mScrollFrom = SCROLL_FROM_THUMBNAIL;
                    clearThumbnailHighLight();
                }
                break;
            }
        }
    }

    /*private void showPopupWindow() {
        View view = LayoutInflater.from(this).inflate(R.layout.burst_popup_menu, null);
        view.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissPopupWindow();
            }
        });
        TextView title = (TextView) (view.findViewById(R.id.title));
        TextView keep_all = (TextView) view.findViewById(R.id.keep_all);
        TextView keepSelectedTextView = ((TextView) (view.findViewById(R.id.keep_selected)));
        TextView deleteSelectedTextView = ((TextView) (view.findViewById(R.id.delete_selected)));
        if (mImageItems.size() == mSelectNumbers) {
            title.setText(R.string.popup_title_save_all_selected);
            keepSelectedTextView.setVisibility(View.GONE);
            keep_all.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // keep all
                    keepAllPhotos();
                }
            });
            deleteSelectedTextView.setText(R.string.delete_all);
        } else {
            int unSelectNums = mImageItems.size() - mSelectNumbers;
            title.setText(getResources().
                    getQuantityString(R.plurals.popup_title_save_unselected, unSelectNums, unSelectNums));
            keep_all.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // keep all selected and reserve burst
                    keepAllPhotos();
                }
            });
            keepSelectedTextView.setText(getResources().getQuantityString(R.plurals.keep_selected, mSelectNumbers, mSelectNumbers));
            deleteSelectedTextView.setText(getResources().getQuantityString(R.plurals.delete_selected, mSelectNumbers, mSelectNumbers));
        }
        keepSelectedTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // keep all selected and delete burst
                keepOnlySelectedPhotos();
            }
        });
        deleteSelectedTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelectedPhotos();
            }
        });
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - 80, getResources().getDisplayMetrics().heightPixels - 80);
        mPopupWindow = new PopupWindow(view, width, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        mPopupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
    }*/

    private abstract class OptionItem {
        String string;

        OptionItem(String str) {
            string = str;
        }

        public String getString() {
            return string;
        }

        abstract public void execute();
    }

    private class MenuOptions {
        ArrayList<OptionItem> items = new ArrayList<>();

        public void add(OptionItem item) {
            items.add(item);
        }

        CharSequence[] getMenuText() {
            ArrayList<CharSequence> c = new ArrayList<>();
            for (OptionItem item : items) {
                c.add(item.getString());
            }
            return c.toArray(new CharSequence[items.size()]);
        }

        public void execute(int id) {
            items.get(id).execute();
        }
    }

    private void showMenuDialog() {
        Log.d(TAG, "showMenuDialog mSelectNumbers = " + mSelectNumbers);
        String title;
        final MenuOptions menu = new MenuOptions();
        if (mImageItems.size() == mSelectNumbers) {
            title = getString(R.string.popup_title_save_all_selected);

            menu.add(new OptionItem(String.valueOf(getText(R.string.keep_all))) {
                @Override
                public void execute() {
                    keepAllPhotos();
                }
            });
            menu.add(new OptionItem(String.valueOf(getText(R.string.delete_all))) {
                @Override
                public void execute() {
                    deleteSelectedPhotos();
                }
            });

        } else {
            int unSelectNums = mImageItems.size() - mSelectNumbers;
            title = getResources().
                    getQuantityString(R.plurals.popup_title_save_unselected, unSelectNums, unSelectNums);

            menu.add(new OptionItem(getString(R.string.keep_all)) {
                @Override
                public void execute() {
                    keepAllPhotos();
                }
            });
            menu.add(new OptionItem(getResources().getQuantityString(R.plurals.keep_selected, mSelectNumbers, mSelectNumbers)) {
                @Override
                public void execute() {
                    keepOnlySelectedPhotos();
                }
            });
            menu.add(new OptionItem(getResources().getQuantityString(R.plurals.delete_selected, mSelectNumbers, mSelectNumbers)) {
                @Override
                public void execute() {
                    deleteSelectedPhotos();
                }
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "menu dialog selected : " + which);
                menu.execute(which);
            }
        };
        builder.setTitle(title)
                .setItems(menu.getMenuText(), onClickListener)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        Log.d(TAG, "menu dialog dismiss");
                        mBurstFilmStripView.setClickable(true);
                    }
                });
        builder.show();
        mBurstFilmStripView.setClickable(false);
    }

    class ThumbNailGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            View child = mThumbNailListView.findChildViewUnder(e.getX(), e.getY());
            int position = mThumbNailListView.getChildAdapterPosition(child);
            Log.d(TAG, "ThumbNailGestureListener.onSingleTapUp --> position = " + position);
            if (position != RecyclerView.NO_POSITION) {
                ThumbNailAdapter.ViewHolder holder = (ThumbNailAdapter.ViewHolder) mThumbNailListView
                        .findViewHolderForAdapterPosition(position);
                if (holder.getViewType() == ThumbNailAdapter.VIEW_TYPE_IMAGE) {
                    onThumbnailItemClick(child, position);
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        Log.d(TAG, "onPause");
        dismissPopupWindow();
        clearThumbnailHighLight();
    }


    private void onThumbnailItemClick(View v, int clickPosition) {
        moveThumbNailToPosition(clickPosition);
    }

    private void setThumbnailHighLight(int position) {
        Log.d(TAG, "setThumbnailHighLight position=" + position);
        if (mThumbNailListView.getAdapter() != null) {
            ((ThumbNailAdapter) mThumbNailListView.getAdapter()).setHighLight(mThumbNailListView, position);
        }
    }

    private void clearThumbnailHighLight() {
        if (mThumbNailListView.getAdapter() != null) {
            ((ThumbNailAdapter) mThumbNailListView.getAdapter()).clearAllHighLight(mThumbNailListView);
        }
    }

    class BurstFilmStripScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            Log.d(TAG, "BurstFilmStripScrollListener.onScrollStateChanged newState = " + newState + ", mScrollFrom=" + mScrollFrom);
            switch (newState) {
                case RecyclerView.SCROLL_STATE_SETTLING:
                case RecyclerView.SCROLL_STATE_DRAGGING: {
                    if (mScrollFrom == SCROLL_FROM_THUMBNAIL) {
                        mThumbNailListView.stopScroll();
                    }
                    mScrollFrom = SCROLL_FROM_CARD;
                    clearThumbnailHighLight();
                }
                break;

                case RecyclerView.SCROLL_STATE_IDLE: {
                    if (mScrollFrom == SCROLL_FROM_CARD) {
                        mScrollFrom = SCROLL_END;
                        needSetHighLight();
                    }
                }
                break;
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            if (mScrollFrom == SCROLL_FROM_CARD) {
                int filmstripCenterItem = findCenterItemPosition(recyclerView);
                if (filmstripCenterItem != RecyclerView.NO_POSITION) {
                    int thumbCenterItem = filmstripCenterItem + ThumbNailAdapter.NUM_OF_EMPTY_ITEM_ON_HEAD;
                    int offset = (mThumbNailListView.getWidth() - ((ThumbNailAdapter) mThumbNailListView.getAdapter())
                            .getItemSize()) / 2;
                    Log.d(TAG, "BurstFilmStripScrollListener.onScrolled thumbnail scroll to thumbCenterItem=" +
                            thumbCenterItem + ", offset=" + offset);
                    ((LinearLayoutManager) mThumbNailListView.getLayoutManager()).scrollToPositionWithOffset
                            (thumbCenterItem, offset);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mIntentHandled) {
            return;
        }
        stopScroll();
        mBurstFilmStripView.removeAllViews();
        mThumbNailListView.removeAllViews();
        if (mImageItems != null && !mImageItems.isEmpty()) {
            for (BurstImageItem item : mImageItems) {
                item.setHighLight(false);
            }
        }
    }

    public int findCenterItemPosition(RecyclerView recyclerView) {
        LinearLayoutManager layoutManager = ((LinearLayoutManager) recyclerView.getLayoutManager());
        if (layoutManager.getChildCount() == 0) {
            Log.d(TAG, "findCenterItemPosition the number of child views is 0");
            return RecyclerView.NO_POSITION;
        }
        int firstItem = layoutManager.findFirstVisibleItemPosition();
        int lastItem = layoutManager.findLastVisibleItemPosition();
        int layoutCenterX = (recyclerView.getLeft() + recyclerView.getRight()) / 2;
        int minDiff = Integer.MAX_VALUE;
        int target = 0;
        for (int i = firstItem; i <= lastItem; i++) {
            View view = layoutManager.findViewByPosition(i);
            int itemCenterX = (view.getLeft() + view.getRight()) / 2;
            int dx = Math.abs(itemCenterX - layoutCenterX);
            if (dx < minDiff) {
                minDiff = dx;
                target = i;
            }
        }
        return target;
    }

    private void stopScroll() {
        mBurstFilmStripView.stopScroll();
        mThumbNailListView.stopScroll();
    }

    private void needSetHighLight() {
        if (mHandler.hasMessages(MSG_SET_HIGHLIGHT)) {
            mHandler.removeMessages(MSG_SET_HIGHLIGHT);
        }
        mHandler.sendEmptyMessageDelayed(MSG_SET_HIGHLIGHT, 200);
    }

    private void setSelectNumbers(int selectNumbers) {
        ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setTitle(getResources().getQuantityString(R.plurals.selected_images,
                selectNumbers < 1 ? 1 : selectNumbers, selectNumbers));
    }
}
