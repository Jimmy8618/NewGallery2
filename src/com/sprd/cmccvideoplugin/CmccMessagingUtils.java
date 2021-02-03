
package com.sprd.cmccvideoplugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MoviePlayer;

import java.lang.ref.WeakReference;

public class CmccMessagingUtils {

    private static final String TAG = "CmccMessagingUtils";

    /**
     * SPRD:Bug 474641 add reciver MMS&SMS Control @{
     */
    private static final String TRANSACTION_COMPLETED_ACTION = "android.intent.action.TRANSACTION_COMPLETED_ACTION";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final int MOVIEVIEW_END_MMS_CONNECTIVITY = 4;
    private static final int MOVIEVIEW_SMS_RECEIVED = 7;
    private static final String MMS_RECEIVED = "android.provider.Telephony.WAP_PUSH_RECEIVED";
    private static final String MMS_MESSAGE_TYPE = "application/vnd.wap.mms-message";
    private final Handler mHandler = new MovePlayerHandler(this);
    private AlertDialog mMMSCompletedDialog;
    private AlertDialog mSMSReceivedDialog;
    private MessageBroadcastReceiver mMessageReceiver;
    private Context mPluginContext;
    private Activity mActivity;
    private MoviePlayer mPlayer;
    private static CmccMessagingUtils mInstance;
    private boolean mIsMessageReceiverRegisted;

    /**
     * @}
     */

    public static CmccMessagingUtils getInstance() {
        if (mInstance == null) {
            mInstance = new CmccMessagingUtils();
        }
        return mInstance;
    }

    public void initMessagingUtils(final Activity activity, Context context) {
        mPluginContext = context;
        mActivity = activity;
        if (mMessageReceiver == null) {
            mMessageReceiver = new MessageBroadcastReceiver();
        }
        IntentFilter messageFilter = new IntentFilter();
        /** SPRD:Bug 474641 add reciver MMS&SMS Control @{ */
        messageFilter.addAction(TRANSACTION_COMPLETED_ACTION);
        messageFilter.addAction(SMS_RECEIVED);
        /* SPRD:Add for bug592606 When receving mms,no notice @{ */
        IntentFilter mmsFilter = new IntentFilter();
        mmsFilter.addAction(TRANSACTION_COMPLETED_ACTION);
        mmsFilter.addAction(MMS_RECEIVED);
        try {
            mmsFilter.addDataType(MMS_MESSAGE_TYPE);
        } catch (MalformedMimeTypeException e) {
            e.printStackTrace();
            Log.d(TAG, "" + e);
        }
        /** @} */
        if (!mIsMessageReceiverRegisted) {
            Log.d(TAG, "initMessagingUtils regist broadcast");
            mIsMessageReceiverRegisted = true;
            mActivity.registerReceiver(mMessageReceiver, messageFilter);
            mActivity.registerReceiver(mMessageReceiver, mmsFilter);
        }
        /* Bug592606 end @} */
    }

    public void releaseMessagingUtils() {
        /** SPRD:Bug 474641 add reciver MMS&SMS Control @{ */
        if (mMessageReceiver != null && mIsMessageReceiverRegisted) {
            mIsMessageReceiverRegisted = false;
            //Bug 951554, registering receiver fails cause the problem.
            try {
                mActivity.unregisterReceiver(mMessageReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                Log.e(TAG, "unregistering receiver failed");
            }
        }
        /** @} */
    }

    public void initPlayer(MoviePlayer player) {
        mPlayer = player;
    }

    public void destoryMessagingDialog() {
        /** SPRD:Bug 474641 add reciver MMS&SMS Control @{ */
        if (mMMSCompletedDialog != null) {
            mMMSCompletedDialog.cancel();
            mMMSCompletedDialog = null;
        }
        if (mSMSReceivedDialog != null) {
            mSMSReceivedDialog.cancel();
            mSMSReceivedDialog = null;
        }
    }

    private AlertDialog initCompleteDialog(int flag) {
        int titleId = flag == MOVIEVIEW_END_MMS_CONNECTIVITY ?
                R.string.movie_view_mms_process_title : R.string.movie_view_sms_process_title;
        return new AlertDialog.Builder(mActivity)
                .setTitle(mPluginContext.getText(titleId))
                .setCancelable(false)
                .setPositiveButton(mPluginContext.getText(R.string.movie_view_mms_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                dialog.dismiss();
                                mPlayer.onPlayPause();
                            }
                        }).create();
    }

    /**
     * @}
     */

    private void handleMovePlayerHandlerMsg(Message msg) {
        switch (msg.what) {
            case MOVIEVIEW_END_MMS_CONNECTIVITY:
                if (mMMSCompletedDialog == null) {
                    mMMSCompletedDialog = initCompleteDialog(MOVIEVIEW_END_MMS_CONNECTIVITY);
                } else {
                    mMMSCompletedDialog.dismiss();
                }
                pauseIfNeed();
                mMMSCompletedDialog.setMessage(mPluginContext.getText(R.string.movie_view_mms_processing_ok));
                mMMSCompletedDialog.show();
                break;
            case MOVIEVIEW_SMS_RECEIVED:
                if (mSMSReceivedDialog == null) {
                    mSMSReceivedDialog = initCompleteDialog(MOVIEVIEW_SMS_RECEIVED);
                } else {
                    mSMSReceivedDialog.dismiss();
                }
                pauseIfNeed();
                mSMSReceivedDialog.setMessage(mPluginContext.getText(R.string.movie_view_sms_received_ok));
                mSMSReceivedDialog.show();
                break;
            default:
                break;

        }
    }

    private static class MovePlayerHandler extends Handler {
        private final WeakReference<CmccMessagingUtils> mCmccMessagingUtils;

        public MovePlayerHandler(CmccMessagingUtils cmccMessagingUtils) {
            mCmccMessagingUtils = new WeakReference<>(cmccMessagingUtils);
        }

        @Override
        public void handleMessage(Message msg) {
            CmccMessagingUtils cmccMessagingUtils = mCmccMessagingUtils.get();
            if (cmccMessagingUtils != null) {
                cmccMessagingUtils.handleMovePlayerHandlerMsg(msg);
            }
        }
    }

    private class MessageBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "MessageBroadcastReceiver onReceive " + intent);
            if (mPlayer == null || mHandler == null || !mPlayer.isStreamUri()) {
                return;
            }

            if (mPlayer.misLiveStreamUri) {
                Log.d(TAG, "When mms receiving, can't stop live streaming");
                return;
            }
            /* SPRD:Add for bug592606 When receving mms,no notice @{ */
            if (intent.getAction().equals(MMS_RECEIVED) &&
                    intent.getType().equals(MMS_MESSAGE_TYPE)) {
                /* Bug592606 end @} */
                Message handldermsg = mHandler.obtainMessage();
                handldermsg.what = MOVIEVIEW_END_MMS_CONNECTIVITY;
                handldermsg.arg1 = intent.getIntExtra("state", 0);
                mHandler.sendMessage(handldermsg);
            } else if (intent.getAction().equals(SMS_RECEIVED)) {
                Message handldermsg = mHandler.obtainMessage();
                handldermsg.what = MOVIEVIEW_SMS_RECEIVED;
                mHandler.sendMessage(handldermsg);
            }
        }
    }

    private boolean pauseIfNeed() {
        if (mPlayer == null) {
            return false;
        }
        if (!mPlayer.isPlaying()) {
            mPlayer.pause();
            return false;
        }
        mPlayer.onPlayPause();
        return true;
    }
}
