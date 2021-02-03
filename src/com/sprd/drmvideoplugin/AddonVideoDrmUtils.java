package com.sprd.drmvideoplugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.drm.DrmManagerClient;
import android.net.Uri;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.FloatMoviePlayer;
import com.android.gallery3d.app.MoviePlayer;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.VideoDrmUtils;

public class AddonVideoDrmUtils extends VideoDrmUtils {

    private static final String TAG = "AddonVideoDrmUtils";
    private static DrmManagerClient sDrmManagerClient = null;
    private Context mPluginContext;

    public AddonVideoDrmUtils(Context context) {
        sDrmManagerClient = StandardFrameworks.getInstances().
                getDrmManagerClientEx(context);
        mPluginContext = context;
    }

    public static DrmManagerClient getDrmManagerClient() {
        return sDrmManagerClient;
    }

    /**
     * SPRD: Update mFilePath in VideoDrmUtils
     * <p>
     * A protected member is used to indicate absolut file path
     * which is playing now. For the purpose of isolating all drm
     * implementation, this path is maintained in addon class and
     * original code can not access it. This method is used to update
     * mFilePath at appropriate place. It is an empty function in
     * VideoDrmUtils.
     */
    @Override
    public void getFilePathByUri(Uri uri, Context context) {
        mFilePath = DrmUtil.getFilePathByUri(uri, context);
        Log.d(TAG, "getFilePathByUri: " + mFilePath);
    }

    /**
     * SPRD: This method is used to control the share menu item
     * <p>
     * Return value represents current state of share menu item.
     * trur for enable and false for disable. This return value
     * should be changed when it is used in plugin apk. If current file
     * can be transfered, enable the item and return false. And if not, disable
     * the item and return true.
     */
    @Override
    public boolean disableShareMenu(MenuItem shareItem) {
        if (!DrmUtil.isDrmSupportTransfer(mFilePath)) {
            shareItem.setVisible(false);
            Log.d(TAG, "share menu item is disabled");
            return true;
        } else {
            shareItem.setVisible(true);
            return false;
        }
    }

    /**
     * SPRD: Set mConsumeForPause Flag
     * mConsumeForPause is used to indicate consume or not when suspend is invoked
     * If back is pressed or playing finish, just suspend and consume
     * If home key is pressed, suspend but not consume(push to background and will be back later)
     */
    @Override
    public void needToConsume(boolean consume) {
        mConsumeForPause = consume;
    }

    /**
     * SPRD: Get mConsumeForPause Flag
     * <p>
     * isFinishedByUser means user do not want to stop playing but just want to leave for a while.
     * When home is pressed, maybe user just want to push the playing activity to background.
     * So in this scenario, isFinishedByUser will return true and drm file does not need to be
     * consumed right.
     */
    @Override
    public boolean isConsumed() {
        Log.d(TAG, "isConsumed: " + !mConsumeForPause);
        return !mConsumeForPause;
    }

    @Override
    public boolean isDrmFile(String filePath, String mimeType) {
        return DrmUtil.isDrmFile(filePath, mimeType);
    }

    @Override
    public boolean isDrmFile() {
        Log.d(TAG, "isDrmFile");
        return DrmUtil.isDrmFile(mFilePath, null);
    }

    @Override
    public void checkRightBeforePlay(final Activity activity, final MoviePlayer mp) {
        if (DrmUtil.isDrmValid(mFilePath)) {
            if (mIsStopped && !DrmUtil.getDrmFileType(mFilePath).equals(DrmUtil.FL_DRM_FILE)) {
                Dialog dialog = new AlertDialog.Builder(activity).
                        setTitle(mPluginContext.getString(R.string.drm_consume_title)).
                        setMessage(mPluginContext.getString(R.string.drm_consume_hint)).
                        setPositiveButton(mPluginContext.getString(R.string.channel_setting_ok),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mp.playVideoWrapper();
                                        mIsStopped = false;
                                    }
                                }).
                        setNegativeButton(mPluginContext.getString(R.string.channel_setting_cancel), null).create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            activity.finish();
                        }
                        return false;
                    }
                });
                dialog.show();
            } else {
                mp.playVideoWrapper();
            }
        } else {
            //Context context = activity.getApplicationContext();
            Toast.makeText(mPluginContext, mPluginContext.getString(R.string.drm_file_invalid), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public AlertDialog checkRightBeforeChange(final Activity activity, final MoviePlayer mp) {
        if (DrmUtil.isDrmValid(mFilePath)) {
            if (!DrmUtil.getDrmFileType(mFilePath).equals(DrmUtil.FL_DRM_FILE)) {
                AlertDialog dialog = new AlertDialog.Builder(activity).
                        setTitle(mPluginContext.getString(R.string.drm_consume_title)).
                        setMessage(mPluginContext.getString(R.string.drm_consume_hint)).
                        setPositiveButton(mPluginContext.getString(R.string.channel_setting_ok),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mp.changeVideoWrapper();
                                    }
                                }).
                        setNegativeButton(mPluginContext.getString(R.string.channel_setting_cancel),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        activity.finish();
                                    }
                                }).create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            activity.finish();
                        }
                        return false;
                    }
                });
                dialog.show();
                return dialog;
            } else {
                mp.changeVideoWrapper();
            }
        } else {
            //Context context = activity.getApplicationContext();
            Toast.makeText(mPluginContext, mPluginContext.getString(R.string.drm_file_invalid), Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        return null;
    }

    @Override
    public AlertDialog checkRightBeforeLoopPlayVideo(final Activity activity, final MoviePlayer mp) {
        if (DrmUtil.isDrmValid(mFilePath)) {
            if (!DrmUtil.getDrmFileType(mFilePath).equals(DrmUtil.FL_DRM_FILE)) {
                AlertDialog dialog = new AlertDialog.Builder(activity).
                        setTitle(mPluginContext.getString(R.string.drm_consume_title)).
                        setMessage(mPluginContext.getString(R.string.drm_consume_hint)).
                        setPositiveButton(mPluginContext.getString(R.string.channel_setting_ok),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mp.loopPlayVideoWrapper();
                                    }
                                }).
                        setNegativeButton(mPluginContext.getString(R.string.channel_setting_cancel),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        activity.finish();
                                    }
                                }).create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            activity.finish();
                        }
                        return false;
                    }
                });
                dialog.show();
                return dialog;
            } else {
                mp.loopPlayVideoWrapper();
            }
        } else {
            //Context context = activity.getApplicationContext();
            Toast.makeText(mPluginContext, mPluginContext.getString(R.string.drm_file_invalid), Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        return null;
    }

    @Override
    public AlertDialog checkRightBeforeChangeInService(final Context context, final FloatMoviePlayer fmp) {
        if (DrmUtil.isDrmValid(mFilePath)) {
            if (!DrmUtil.getDrmFileType(mFilePath).equals(DrmUtil.FL_DRM_FILE)) {
                AlertDialog dialog = new AlertDialog.Builder(context).
                        setTitle(mPluginContext.getString(R.string.drm_consume_title)).
                        setMessage(mPluginContext.getString(R.string.drm_consume_hint)).
                        setPositiveButton(mPluginContext.getString(R.string.channel_setting_ok),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        fmp.changeVideoWrapper();
                                    }
                                }).
                        setNegativeButton(mPluginContext.getString(R.string.channel_setting_cancel),
                                new AlertDialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        fmp.closeWindow();
                                    }
                                }).create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            fmp.closeWindow();
                        }
                        return false;
                    }
                });
                dialog.show();
                return dialog;
            } else {
                fmp.changeVideoWrapper();
            }
        } else {
            //Context context = activity.getApplicationContext();
            Toast.makeText(mPluginContext, mPluginContext.getString(R.string.drm_file_invalid), Toast.LENGTH_SHORT).show();
            fmp.closeWindow();
        }
        return null;
    }

    @Override
    public void setStopState(boolean state) {
        mIsStopped = state;
    }
}
