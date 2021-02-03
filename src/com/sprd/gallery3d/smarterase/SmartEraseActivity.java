package com.sprd.gallery3d.smarterase;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.v2.util.SDCardPermissionHandler;

import java.io.File;

public class SmartEraseActivity extends Activity {

    private static final String TAG = "SmartEraseActivity";

    private Uri mUri;
    private EraseManager mEraseManager;
    private UIControl mUIControl;
    private SDCardPermissionHandler mPermissionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mUri = intent.getData();
        Log.d(TAG, "onCreate: mUri=" + mUri);
//        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
//        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_layout_smart_erase);
        mEraseManager = new EraseManager(this, mUri);
        mUIControl = new UIControl(this, mEraseManager);
        mEraseManager.setUIControl(mUIControl);
        mPermissionHandler = new SDCardPermissionHandler(this);
        initActionBar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUIControl.dismissProgressDialog();
        mEraseManager.release();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_smart_erase, menu);
        mUIControl.setOptionsMenu(menu);
        return true;
    }

    private void initActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_HOME_AS_UP);
            actionBar.setTitle(R.string.smarterase);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_smart_erase_quit);
        } else {
            Log.d(TAG, "initActionbar: actionBar is null");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPermissionHandler.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void startLoadBitmap() {
        File file = SmartEraseUtils.getLocalFileFromUri(this, mUri);
        Log.d(TAG, "startLoadBitmap: file = " + file);
        if (file == null) {
            Log.d(TAG, "startLoadBitmap: file is null, finish activity.");
            finish();
            return;
        }
        String filePath = file.getAbsolutePath();
        mPermissionHandler.requestPermissionIfNeed(filePath, new SDCardPermissionHandler.PermissionCallback() {
            @Override
            public void onAllowed() {
                mEraseManager.startLoadBitmap();
            }

            @Override
            public void onDenied() {
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mUIControl.getState() == EraseManager.State.STATE_ERASE_FINISH) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.unsaved);
            builder.setPositiveButton(R.string.save_and_exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    mEraseManager.save();
                }
            });
            builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    finish();
                }
            });
            builder.show();
        } else {
            super.onBackPressed();
        }
    }
}
