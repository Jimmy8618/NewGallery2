
package com.android.gallery3d.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.gallery3d.R;
import com.sprd.frameworks.StandardFrameworks;

public class FloatPlayerService extends Service {

    FloatMoviePlayer mFloatMoviePlayer;
    private Uri mUri;
    private int mPosition;
    private ScreenBroadcastReceiver mScreenReceiver;
    public int mOldState;

    private static final String TAG = "FloatPlayerService";
    public static final String STOP_PLAY_VIDEO = "stop.play.video";
    private static String ACTION_SHUTFLOAT = "com.android.gallery3d.app.SHUT_FLOATWINDOW";
    private boolean mIsHeadSetMount = false;
    private boolean mIsUnmounted = false;//SPRD:modify by old bug487272
    /**
     * SPRD:Bug571356 the float window is not pause, when change to an other video @{
     */
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private static final String START_SCREEN_SAVER = "com.android.settingslib.dream.start";

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        Log.d(TAG, "onCreate");
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(getString(R.string.movie_view_label))
                .setContentText(getString(R.string.floatview_notification_text))
                .setSmallIcon(R.drawable.video_icon);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(TAG, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(TAG);
        }
        startForeground(R.id.video_title, builder.build());
        Log.d(TAG, "set channel");

        mScreenReceiver = new ScreenBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(ACTION_SHUTFLOAT);
        filter.addAction(STOP_PLAY_VIDEO);
        filter.addAction(START_SCREEN_SAVER);
        registerReceiver(mScreenReceiver, filter);

        IntentFilter intentFilter = new IntentFilter("android.intent.action.MEDIA_UNMOUNTED");
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        /** modify by old Bug487272 @{ */
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        /**@}*/
        intentFilter.setPriority(1000);
        intentFilter.addDataScheme("file");
        registerReceiver(mExternalMountedReceiver, intentFilter);

        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        mSubscriptionManager = SubscriptionManager.from(this);
        Log.i(TAG, "oncreate");
    }

    PhoneStateListener mPhoneListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            Log.d(TAG, "onCallStateChanged   state = " + state);
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (mFloatMoviePlayer != null) {
                        // mFloatMoviePlayer.closeWindow();
                        mFloatMoviePlayer.setPhoneState(true);
                    }
                    break;
                // bug 350950 begin
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (mFloatMoviePlayer != null) {
                        // mFloatMoviePlayer.closeWindow();
                        mFloatMoviePlayer.setPhoneState(true);
                    }
                    break;
                // bug 350950 end
                case TelephonyManager.CALL_STATE_IDLE:
                    if (mFloatMoviePlayer != null) {
                        mFloatMoviePlayer.setPhoneState(false);
                    }
                    break;
                default:
                    break;
            }
        }

    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        } else {
            mUri = intent.getData();
            Log.d(TAG, "FloatPlayerService onStartCommand Uri = " + mUri);
            mPosition = intent.getIntExtra("position", 0);
            int state = intent.getIntExtra("currentstate", 0);
            createFloatView();
            mFloatMoviePlayer.setDataIntent(intent);
            Log.d(TAG, "onStartCommand setVideoUri state=" + state);
            mFloatMoviePlayer.setVideoUri(mUri);
            if (mPosition > 0) {
                Log.d(TAG, "onStartCommand seekTo");
                mFloatMoviePlayer.seekTo(mPosition - 2500);//SPRD:modify by old Bug490436
            }
            // if (state != FloatMoviePlayer.STATE_PAUSED) {
            Log.d(TAG, "onStartCommand start");
            mFloatMoviePlayer.start();
            // }
            mFloatMoviePlayer.initTitle();
        }
        /*
         * if(state == FloatMoviePlayer.STATE_PAUSED){ mFloatMoviePlayer.pause(); }
         */
        Log.d(TAG, "onStartCommand end");
        return super.onStartCommand(intent, flags, startId);
    }

    private void createFloatView() {
        if (mFloatMoviePlayer == null) {
            mFloatMoviePlayer = new FloatMoviePlayer(this);
        }
        if (mFloatMoviePlayer.mFloatLayout == null) {
            mFloatMoviePlayer.addToWindow();
        }
        /** SPRD:Bug571356 the float window is not pause, when change to an other video @{ */
        mFloatMoviePlayer.setSubscriptionManager(mSubscriptionManager);
        mFloatMoviePlayer.setTelePhoneManager(mTelephonyManager);
        /**@}*/
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(getString(R.string.movie_view_label))
                .setContentText(getString(R.string.floatview_notification_text))
                .setSmallIcon(R.drawable.video_icon);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(TAG, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(TAG);
        }
        startForeground(R.id.video_title, builder.build());
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mScreenReceiver);
        unregisterReceiver(mExternalMountedReceiver);
        if (mFloatMoviePlayer != null) {
            // SPRD：Add for bug613664 When changing from float window to normal play,the musicplayer will play
            mFloatMoviePlayer.setIsAbandonAudiofocus(true);
            mFloatMoviePlayer.stop();
            mFloatMoviePlayer.removeFromWindow();
            mFloatMoviePlayer = null;
        }
        //SPRD:bug642527 FloatPlayer maybe close when open other apps
        stopForeground(true);
        super.onDestroy();
    }

    private class ScreenBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mFloatMoviePlayer == null) {
                return;
            }
            int state = mFloatMoviePlayer.getCurrentState();
            Log.d(TAG, "onReceive action:" + action + " state=" + state);
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                /* SPRD:Add for bug575931 FloatMoviePlayer start playing when the screen is off @{*/
                mFloatMoviePlayer.setScreenOff(false);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action) || START_SCREEN_SAVER.equals(action)) {
                mFloatMoviePlayer.setScreenOff(true);
                /* Bug575931 End @} */
                if (state == FloatMoviePlayer.STATE_PLAYING) {
                    mOldState = state;
                    mFloatMoviePlayer.pause();
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                if (state == FloatMoviePlayer.STATE_PAUSED
                        && mOldState == FloatMoviePlayer.STATE_PLAYING) {
                    if (mFloatMoviePlayer.getPhoneState()) {
                        mFloatMoviePlayer.setState(mOldState);
                        mOldState = 0;
                        return;
                    }
                    mOldState = 0;
                    mFloatMoviePlayer.start();
                }
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                if (intent.getIntExtra("state", 0) == 1) {
                    mIsHeadSetMount = true;
                    Log.d(TAG, "intent.getIntExtra() " + 1 + " " + mIsHeadSetMount);
                } else if (intent.getIntExtra("state", 0) == 0) {
                    Log.d(TAG, "intent.getIntExtra() " + 0 + " " + mIsHeadSetMount);
                    if (mIsHeadSetMount == true) {
                        mIsHeadSetMount = false;
                        mFloatMoviePlayer.pause();
                    }
                }
            } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                mFloatMoviePlayer.closeWindow();
            } else if (STOP_PLAY_VIDEO.equals(action)) {
                mFloatMoviePlayer.closeWindow();
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                mFloatMoviePlayer.closeWindow();
                /**SPRD:Bug542760 the MovieActivity and the floatwindow play together@{*/
            } else if (ACTION_SHUTFLOAT.equals(action)) {
                mFloatMoviePlayer.closeWindow();
            }
            /**@}*/
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private BroadcastReceiver mExternalMountedReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            /** modify by old Bug487272 @{ */
            String action = intent.getAction();
            Log.d(TAG, action);
            String path = mFloatMoviePlayer.getFilePathFromUri(mUri);
            String sdPath = StandardFrameworks.getInstances().getExternalStoragePath(context).toString();
            String state = StandardFrameworks.getInstances().getExternalStorageState(context);
            String internalPath = StandardFrameworks.getInstances().getInternalStoragePath().toString();
            if (path == null || (path != null && path.startsWith(sdPath) && Environment.MEDIA_EJECTING.equals(state))
                    || (path != null && !path.startsWith(sdPath) && !path.startsWith(internalPath) && !Environment.MEDIA_EJECTING.equals(state)
                    && !Environment.MEDIA_BAD_REMOVAL.equals(state) && !Environment.MEDIA_UNMOUNTED.equals(state))) {
                if (!mIsUnmounted) {
                    mFloatMoviePlayer.stop();
                    Log.d(TAG, "float movie player has stopped");
                    mIsUnmounted = true;
                    mFloatMoviePlayer.closeWindow();
                }
            }
            /**@}*/
        }
    };
}
