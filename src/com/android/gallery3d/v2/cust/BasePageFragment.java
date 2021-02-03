package com.android.gallery3d.v2.cust;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.Menu;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.interact.OnTabSelectedCallback;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.page.DetailsPageFragment;
import com.android.gallery3d.v2.tab.TabItemView;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.FileCopyTask;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public abstract class BasePageFragment extends Fragment {
    private static final String TAG = BasePageFragment.class.getSimpleName();
    private static final int MSG_RUN_ON_UI_THREAD = 1;
    protected static final int LOAD_DELAY = 400;

    private boolean mIsNextPage;
    private DataManager mDataManager;
    private FileCopyTask mFileCopyTask;
    private Handler mMainHandler;
    private boolean mPaused;

    private PageDataBackListener mDataBackListener;

    public interface PageDataBackListener {
        void onDataBack(Bundle data);
    }

    public void setDataBackListener(PageDataBackListener dataBackListener) {
        mDataBackListener = dataBackListener;
    }
    /* Bug 1184608 */
    protected HideMenuListener mHideMenuListener;

    public interface HideMenuListener {
        void onMenuShow(boolean visible);
    }

    public void setHideMenuListener(HideMenuListener hideMenuListener) {
        mHideMenuListener = hideMenuListener;
    }
    /* @ */
    void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_RUN_ON_UI_THREAD:
                ((Runnable) msg.obj).run();
                break;
            default:
                break;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            mIsNextPage = bundle.getBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, false);
        }
        if (getGalleryActivity2() != null) {
            mDataManager = getGalleryActivity2().getDataManager();
        }
        mMainHandler = new MainHandler(this);
    }

    public GalleryActivity2 getGalleryActivity2() {
        return (GalleryActivity2) getActivity();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!isAdded()) {
            return;
        }
        if (hidden) {
            onHide();
            mPaused = true;
        } else {
            onShow();
            mPaused = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
    }

    public abstract void onShow();

    public abstract void onHide();

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFileCopyTask != null) {
            mFileCopyTask.stop();
            mFileCopyTask = null;
        }
    }

    public void setDataBack(Bundle data) {
        if (mDataBackListener != null) {
            mDataBackListener.onDataBack(data);
        }
    }

    protected void checkDataBackValid(@StringRes int resId) {
        if (mDataBackListener == null) {
            Toast.makeText(GalleryAppImpl.getApplication(), resId, Toast.LENGTH_LONG).show();
        }
    }

    public boolean isNextPage() {
        return mIsNextPage;
    }

    public DataManager getDataManager() {
        return mDataManager;
    }

    public void setStatusBarColor(int resId) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setStatusBarColor(resId);
        }
    }

    public void setNavigationTitle(String title) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setNavigationTitle(title);
        }
    }

    public void setNavigationTitle(String title, int drawableId) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setNavigationTitle(title, drawableId);
        }
    }

    public CharSequence getNavigationTitle() {
        if (getGalleryActivity2() != null) {
            return getGalleryActivity2().getNavigationTitle();
        }
        return "";
    }

    public void setNavigationTitleOnly(String title) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setNavigationTitleOnly(title);
        }
    }

    public void resetNavigationAppearance() {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().resetNavigationAppearance();
        }
    }

    public void setTabsVisible(boolean visible) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setTabsVisible(visible);
        }
    }

    public void setNavigationVisible(boolean visible) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setNavigationVisible(visible);
        }
    }

    public void onBackPressed() {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().onBackPressed();
        }
    }

    public boolean isMainIntent() {
        return getGalleryActivity2() == null || getGalleryActivity2().isMainIntent();
    }

    public boolean isGetContentIntent() {
        return getGalleryActivity2() != null && getGalleryActivity2().isGetContentIntent();
    }

    public boolean isWidgetGetAlbumIntent() {
        return getGalleryActivity2() != null && getGalleryActivity2().isWidgetGetAlbumIntent();
    }

    public void setStatusBarVisibleLight() {//设置状态栏字体为黑色, 设置状态栏可见
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setStatusBarVisibleLight();
        }
    }

    public void setStatusBarVisibleWhite() {//设置状态栏字体为白色, 设置状态栏可见
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setStatusBarVisibleWhite();
        }
    }

    public void setStatusBarHideWhite() {//设置状态栏字体为白色, 设置状态栏不可见
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setStatusBarHideWhite();
        }
    }

    public void setCoverVisible(boolean visible) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setCoverVisible(visible);
        }
    }

    public void keepScreenOn(boolean on) {
        if (on) {
            if (getGalleryActivity2() != null) {
                getGalleryActivity2().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } else {
            if (getGalleryActivity2() != null) {
                getGalleryActivity2().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    public void lockScreen() {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().lockScreen();
        }
    }

    protected void addToAlbumTask(final String dir, final List<String> itemList) {
        Log.d(this.getClass().getSimpleName(), "addToAlbumTask dir = " + dir + ", itemList size = " + (itemList == null ? 0 : itemList.size()));
        if (itemList == null || itemList.size() <= 0) {
            Toast.makeText(getContext(), R.string.failed_add_to_album, Toast.LENGTH_LONG).show();
            return;
        }
        if (!GalleryStorageUtil.isInInternalStorage(dir)
                && !SdCardPermission.hasStoragePermission(dir)) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    mFileCopyTask = new FileCopyTask.Build(getContext()).setDir(dir).setCopyFiles(itemList).create();
                    mFileCopyTask.start();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(getGalleryActivity2(), null);
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(dir);
            SdCardPermission.requestSdcardPermission(getGalleryActivity2(), storagePaths,
                    getGalleryActivity2(), sdCardPermissionListener);
        } else {
            mFileCopyTask = new FileCopyTask.Build(getContext()).setDir(dir).setCopyFiles(itemList).create();
            mFileCopyTask.start();
        }
    }

    protected boolean isDetailsPageShown() {
        if (getGalleryActivity2() != null) {
            Fragment f = getGalleryActivity2().getSupportFragmentManager().findFragmentById(R.id.fragment_full_container);
            return f instanceof DetailsPageFragment;
        }
        return false;
    }

    public int getTabPosition() {
        if (getGalleryActivity2() != null) {
            return getGalleryActivity2().getTabPosition();
        }
        return 0;
    }

    public void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setMenuItemVisible(menu, itemId, visible);
        }
    }

    public void setPageFragment(BasePageFragment page) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().setPageFragment(page);
        }
    }

    public boolean isBackConsumed() {
        return false;
    }

    public void registerTabSelectedCallback(OnTabSelectedCallback callback) {
        if (getGalleryActivity2() != null) {
            getGalleryActivity2().registerTabSelectedCallback(callback);
        }
    }

    public TabItemView getCurrentTab() {
        if (getGalleryActivity2() != null) {
            return getGalleryActivity2().getCurrentTab();
        }
        return null;
    }

    public void runOnUIThread(Runnable runnable, long delayMillis) {
        Message msg = mMainHandler.obtainMessage(MSG_RUN_ON_UI_THREAD, runnable);
        mMainHandler.sendMessageDelayed(msg, delayMillis);
    }

    private static class MainHandler extends Handler {
        private WeakReference<BasePageFragment> mPage;

        MainHandler(BasePageFragment page) {
            this.mPage = new WeakReference<>(page);
        }

        @Override
        public void handleMessage(Message msg) {
            BasePageFragment page = this.mPage.get();
            if (page != null) {
                page.handleMessage(msg);
            }
        }
    }

    public boolean isPaused() {
        return mPaused;
    }
}
