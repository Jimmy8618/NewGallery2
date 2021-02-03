package com.android.gallery3d.app;

import android.Manifest;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import android.os.Build;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

public class FlipSettingsActivity extends Activity {
    private Switch mSwitch;
    private ImageButton mCancelButton;
    private final String TAG = " FlipSettingsActivity";
    private SharedPreferences mPref;
    private PopupWindow mPopupWindow;
    private boolean mFirstUse = false;
    private boolean mIsFlip = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        View customView = LayoutInflater.from(this).inflate(R.layout.actionbar_style, null);
        if (customView != null) {
            actionBar.setCustomView(customView);
        }
        setContentView(R.layout.flip_item_list);
        mPref = this.getSharedPreferences("flip_values", Context.MODE_PRIVATE);
        mIsFlip = mPref.getBoolean("flip_values", false);
        mFirstUse = mPref.getBoolean("first_use_function", false);
        mSwitch = findViewById(R.id.button);
        mCancelButton = findViewById(R.id.cancel_btn);
        if (mIsFlip) {
            mSwitch.setChecked(true);
        } else {
            mSwitch.setChecked(false);
        }
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                finish();
            }
        });
        mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub
                mFirstUse = mPref.getBoolean("first_use_function", false);
                if (!mFirstUse) {
                    if (mSwitch != null) {
                        mSwitch.setChecked(false);
                    }
                    showPopupWindow();
                }
                if (isChecked) {
                    mPref.edit().putBoolean("flip_values", true).apply();
                } else {
                    mPref.edit().putBoolean("flip_values", false).apply();
                }
            }
        });

        if (!GalleryUtils.checkCameraPermissions(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 0);
            }
        }

    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private void showPopupWindow() {
        View view = LayoutInflater.from(this).inflate(R.layout.summary_popup_menu, null);
        mPopupWindow = new PopupWindow(view, (getResources().getDisplayMetrics().widthPixels - 80),
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#414141")));
        mPopupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        TextView button = view.findViewById(R.id.save_button);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                mPref.edit().putBoolean("first_use_function", true).apply();
                dismissPopupWindow();
                if (mSwitch != null) {
                    mSwitch.setChecked(true);
                }
            }
        });
    }

    private void dismissPopupWindow() {
        if (mPopupWindow != null) {
            mPopupWindow.dismiss();
            mPopupWindow = null;
        }
    }
}
