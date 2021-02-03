package com.sprd.gallery3d.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;

import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;

/**
 * SPRD : Add PermissionsActivity for check gallery permission
 */

public class PermissionsActivity extends Activity {
    private static final String TAG = "PermissionsActivity";

    private static int PERMISSION_REQUEST_CODE = 1;
    private int mIndexPermissionRequestStorage;
    private int mIndexPermissionRequestWriteStorage;
    private int mIndexPermissionRequestPhone;
    private int mIndexPermissionRequestLocation;
    private int mIndexPermissionRequestSms;
    private boolean mShouldRequestStoragePermission;
    private boolean mShouldRequestWriteStoragePermission;
    private boolean mShouldRequestPhonePermission;
    private boolean mShouldRequestLocationPermission;
    private boolean mShouldRequestSmsPermission;
    private boolean mFlagHasStoragePermission;
    private boolean mFlagHasWriteStoragePermission;
    private boolean mFlagHasPhonePermission;
    private boolean mFlagHasLocationPermission;
    private boolean mFlagHasSmsPermission;
    private static String PERMISSION_READ_PHONE = Manifest.permission.READ_PHONE_STATE;
    private static String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static String PERMISSION_ACCESS_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static String PERMISSION_RECEIVE_SMS = Manifest.permission.RECEIVE_SMS;

    private int mNumPermissionsToRequest;
    private boolean mDialogShow;

    /**
     * SPRD:Bug510007  check storage permission  @{
     */
    public static final String UI_START_BY = "permission-activity-start-by";
    /**
     * @}
     */
    private int mStartFrom;
    // 0:gallery; 1: video; -1;other app
    public static final int START_FROM_GALLERY = 0;
    public static final int START_FROM_VIDEO = 1;
    public static final int START_FROM_MOVIE = 2;
    public static final int START_FROM_NEW_VIDEO = 3;
    public static final int START_FROM_CROP = 4;
    public static final int START_FROM_FILTER_SHOW = 5;
    public static final int START_PERIMISSION_SUCESS = 6;
    public static final int START_PERIMISSION_FAIL = 7;
    public static final int START_FROM_OTHER = -1;
    //for result
    public static final int RESULT_FOR_MOVIE = 1;

    private Bundle mSavedInstanceState;
    private AlertDialog mDialog = null;
    // SPRD:Modify for bug592606, check access sms permission for Gallery
    private boolean mIsStreamUri = false;

    private boolean mIsResumCalled;
    private boolean mFailureBeforeResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.permissions_layout);
        // SPRD:Modify for bug592606,604970, check access sms permission for Gallery,nullpointerexception
        mIsStreamUri = (MovieActivity.getInstance() != null) && MovieActivity.getInstance().isStreamUri();
        mDialogShow = false;
        mIsResumCalled = false;
        mFailureBeforeResume = false;
        mSavedInstanceState = getIntent().getExtras();
        mStartFrom = getIntent().getIntExtra(UI_START_BY, START_FROM_OTHER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
        mNumPermissionsToRequest = 0;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            android.util.Log.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onResume() {
        GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        super.onResume();
        mIsResumCalled = true;
        Log.d(TAG, "onResume mFailureBeforeResume=" + mFailureBeforeResume);
        if (mFailureBeforeResume) {
            handlePermissionsFailure();
            mFailureBeforeResume = false;
        }
    }

    protected int getToastFlag() {
        return GalleryUtils.DONT_SUPPORT_VIEW_PHOTOS;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermissions() {
        // check read_external_storage permission first
        if (checkSelfPermission(PERMISSION_READ_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestStoragePermission = true;
        } else {
            mFlagHasStoragePermission = true;
        }

        if (checkSelfPermission(PERMISSION_WRITE_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestWriteStoragePermission = true;
        } else {
            mFlagHasWriteStoragePermission = true;
        }

        /* SPRD: Modify for bug576760, check access location permission for Gallery @{ */
        if (START_FROM_GALLERY == mStartFrom && !(Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction()))) {
            if (checkSelfPermission(PERMISSION_ACCESS_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                mNumPermissionsToRequest++;
                mShouldRequestLocationPermission = true;
            } else {
                mFlagHasLocationPermission = true;
            }
        }
        /* @} */

        if (START_FROM_MOVIE == mStartFrom || START_FROM_NEW_VIDEO == mStartFrom) {
            /** SPRD:Bug474639 check phone permission @{ */
            if (checkSelfPermission(PERMISSION_READ_PHONE) != PackageManager.PERMISSION_GRANTED) {
                mNumPermissionsToRequest++;
                mShouldRequestPhonePermission = true;
            } else {
                mFlagHasPhonePermission = true;
            }
            /**@}*/
        }

        /* SPRD: Modify for bug592606, check access sms permission for Gallery @{ */
        if (START_FROM_MOVIE == mStartFrom && mIsStreamUri) {
            if (checkSelfPermission(PERMISSION_RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                mNumPermissionsToRequest++;
                mShouldRequestSmsPermission = true;
            } else {
                mFlagHasSmsPermission = true;
            }
        }
        /* Bug592606 end @} */

        if (mNumPermissionsToRequest != 0) {
            if (!mDialogShow) {
                buildPermissionsRequest();
            } else {
                handlePermissionsFailure();
            }
        } else {
            handlePermissionsSuccess();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void buildPermissionsRequest() {
        String[] permissionsToRequest = new String[mNumPermissionsToRequest];
        int permissionsRequestIndex = 0;

        if (mShouldRequestStoragePermission) {
            permissionsToRequest[permissionsRequestIndex] = PERMISSION_READ_STORAGE;
            mIndexPermissionRequestStorage = permissionsRequestIndex;
            permissionsRequestIndex++;
        }

        if (mShouldRequestWriteStoragePermission) {
            permissionsToRequest[permissionsRequestIndex] = PERMISSION_WRITE_STORAGE;
            mIndexPermissionRequestWriteStorage = permissionsRequestIndex;
            permissionsRequestIndex++;
        }

        /**SPRD:Bug474639  check phone permission  @{*/
        if (mShouldRequestPhonePermission) {
            permissionsToRequest[permissionsRequestIndex] = PERMISSION_READ_PHONE;
            mIndexPermissionRequestPhone = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        /**@}*/

        /* SPRD: Modify for bug576760, check access location permission for Gallery @{ */
        if (mShouldRequestLocationPermission) {
            permissionsToRequest[permissionsRequestIndex] = PERMISSION_ACCESS_LOCATION;
            mIndexPermissionRequestLocation = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        /* @} */

        /* SPRD: Modify for bug592606, check access sms permission for Gallery @{ */
        if (mShouldRequestSmsPermission && mIsStreamUri) {
            permissionsToRequest[permissionsRequestIndex] = PERMISSION_RECEIVE_SMS;
            mIndexPermissionRequestSms = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        /* Bug592606 end @} */
        for (String permission : permissionsToRequest) {
            Log.d(TAG, "request permissions : " + permission);
        }
        requestPermissions(permissionsToRequest, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult permissions.length=" + permissions.length + ", grantResults.length=" + grantResults.length);
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, "onRequestPermissionsResult " + permissions[i] + " : " + grantResults[i]);
        }
        mDialogShow = true;
        if (mShouldRequestStoragePermission) {
            if (grantResults.length > mIndexPermissionRequestStorage && grantResults[mIndexPermissionRequestStorage] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFlagHasStoragePermission = true;
            } else {
                handlePermissionsFailure();
            }
        }

        if (mShouldRequestWriteStoragePermission) {
            if (grantResults.length > mIndexPermissionRequestWriteStorage && grantResults[mIndexPermissionRequestWriteStorage] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFlagHasWriteStoragePermission = true;
            } else {
                handlePermissionsFailure();
            }
        }

        /**SPRD:Bug474639  check phone permission  @{*/
        if (mShouldRequestPhonePermission) {
            if (grantResults.length > mIndexPermissionRequestPhone && grantResults[mIndexPermissionRequestPhone] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFlagHasPhonePermission = true;
            } else {
                handlePermissionsFailure();
            }
        }
        /**@}*/
        /* SPRD: Modify for bug576760, check access location permission for Gallery @{ */
        if (mShouldRequestLocationPermission) {
            if (grantResults.length > mIndexPermissionRequestLocation && grantResults[mIndexPermissionRequestLocation] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFlagHasLocationPermission = true;
            } else {
                handlePermissionsFailure();
            }
        }
        /* @} */
        /* SPRD: Modify for bug592606, check access sms permission for Gallery @{ */
        if (mShouldRequestSmsPermission) {
            if (grantResults.length > mIndexPermissionRequestSms && grantResults[mIndexPermissionRequestSms] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFlagHasSmsPermission = true;
            } else {
                handlePermissionsFailure();
            }
        }
        /* Bug592606 end @} */
        switch (mStartFrom) {
            case START_FROM_CROP:
            case START_FROM_VIDEO:
            case START_FROM_FILTER_SHOW:
                if (mFlagHasStoragePermission) {
                    handlePermissionsSuccess();
                }
                break;
            case START_FROM_GALLERY:
                if ((Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                        || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction())) && mFlagHasStoragePermission) {
                    handlePermissionsSuccess();
                } else if (mFlagHasStoragePermission && mFlagHasLocationPermission) {// Modify for bug576760, also check access location permission for Gallery
                    handlePermissionsSuccess();
                }
                break;
            case START_FROM_MOVIE:
            case START_FROM_NEW_VIDEO:
                // SPRD:Modify for bug592606, check access sms permission for Gallery
                if (mFlagHasStoragePermission && mFlagHasPhonePermission && (!mIsStreamUri || mFlagHasSmsPermission)) {
                    handlePermissionsSuccess();
                }
                break;
        }
    }

    private void handlePermissionsSuccess() {
        /*
         * SPRD: bug 597395 ,Contacts add pictures for the first time after add permission failed ,
         * and no tips
         */
        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction())
                || mStartFrom == START_FROM_CROP) {
            Toast.makeText(this, R.string.gallery_premission_change, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        /** SPRD:Bug510007 check storage permission @{ */
        Intent intent = null;
        switch (mStartFrom) {
            case START_FROM_GALLERY:
                intent = new Intent(this, GalleryActivity.class);
                break;
            case START_FROM_NEW_VIDEO:
                intent = new Intent(this, NewVideoActivity.class);
                break;
            case START_FROM_FILTER_SHOW:
                intent = new Intent(this, FilterShowActivity.class);
                break;
            case START_FROM_MOVIE:
                intent = new Intent();
                if (mSavedInstanceState != null) {
                    intent.putExtras(mSavedInstanceState);
                }
                setResult(RESULT_OK, intent);
                /* SPRD: Add for bug609761 The gallery crashed when first enter it @{ */
            default:
                finish();
                return;
            /* Bug609761 end @} */
        }
        /** @}*/
        /* SPRD: bug 517885 , lose of the read or write uri permissions*/
        if (StandardFrameworks.getInstances().getIntentisAccessUriMode(this)) {
            intent.setFlags(getIntent().getFlags());
        }
        /* @} */
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
        if (mStartFrom == START_FROM_GALLERY) {
            setResult(START_PERIMISSION_SUCESS, intent);
            finish();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        /* SPRD :bug 630329 file Non-existent,get permissions and open it crash. @{*/
        Uri uri = intent.getData();
        if (uri != null) {
            // uri not null,it get permissions to open a file.
            if (GalleryUtils.isValidUri(this, uri)) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.fail_to_load, Toast.LENGTH_SHORT).show();
            }
        } else {
            // uri is null, it get permissions to open application.
            startActivity(intent);
        }
        /* @} */
        finish();
    }

    private void handlePermissionsFailure() {
        if (!mIsResumCalled) {
            mFailureBeforeResume = true;
            Log.d(TAG, "can not handlePermissionsFailure, mIsResumCalled is false.");
            return;
        }
        mDialog = new AlertDialog.Builder(this)
                .setMessage(getResources().getString(R.string.error_permissions))
                .setCancelable(false)
                .setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            setResult(START_PERIMISSION_FAIL, null);
                            finish();
                        }
                        return true;
                    }
                })
                .setPositiveButton(getResources().getString(R.string.refocus_quit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setResult(START_PERIMISSION_FAIL, null);
                                finish();
                            }
                        }).create();
        switch (mStartFrom) {
            case START_FROM_GALLERY:
            case START_FROM_CROP:
            case START_FROM_FILTER_SHOW:
                /* SPRD: Modify for bug579275, add to avoid WindowLeaked exception @{ */
                if (!isFinishing()) {
                    mDialog.show();
                    mDialogShow = true;
                }
                /* @} */
                break;
            case START_FROM_VIDEO:
            case START_FROM_NEW_VIDEO:
            case START_FROM_MOVIE:
                // mDialog.setTitle(R.string.videoplayer_error_title);
                /* SPRD: Modify for bug579275, add to avoid WindowLeaked exception @{ */
                if (!isFinishing()) {
                    mDialog.show();
                    mDialogShow = true;
                }
                /* @} */
                break;
        }
    }

    /*SPRD: bug 532248,Can not perform this action after onSaveInstanceState @{ */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
    /* @} */

    @Override
    protected void onDestroy() {
        mSavedInstanceState = null;
        /* SPRD: Modify for bug579275, dismiss dialog to avoid WindowLeaked exception @{ */
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }
        /* @} */
        super.onDestroy();
    }
}
