package com.sprd.gallery3d.burstphoto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;

/**
 * Created by apuser on 12/13/16.
 */

public class ImageCheck extends FrameLayout implements View.OnClickListener {
    private static final String TAG = ImageCheck.class.getSimpleName();

    private ImageView mImage;
    private ImageView mCheckBox;
    private TextView mSelectText;
    private OnImageCheckedListener mOnImageCheckedListener;

    public ImageCheck(Context context) {
        super(context);
    }

    public ImageCheck(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick --> ");
        if (mCheckBox.isSelected()) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public void setOnImageCheckedListener(OnImageCheckedListener l) {
        mOnImageCheckedListener = l;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImage = findViewById(R.id.image);
        mCheckBox = findViewById(R.id.checkbox);
        mSelectText = findViewById(R.id.select);

        mImage.setOnClickListener(this);
        mCheckBox.setOnClickListener(this);
    }

    @Override
    public void setSelected(boolean checked) {
        Log.d(TAG, "setSelected checked=" + checked);
        if (checked) {
            mCheckBox.setImageResource(R.drawable.burst_check_on);
            mSelectText.setTextColor(Color.parseColor("#4185F5"));
        } else {
            mCheckBox.setImageResource(R.drawable.burst_check_off);
            mSelectText.setTextColor(Color.parseColor("#FFFFFF"));
        }
        mCheckBox.setSelected(checked);
    }

    public boolean isChecked() {
        return mCheckBox.isSelected();
    }

    private void setChecked(boolean checked) {
        if (checked) {
            mCheckBox.setImageResource(R.drawable.burst_check_on);
            mSelectText.setTextColor(Color.parseColor("#4185F5"));
        } else {
            mCheckBox.setImageResource(R.drawable.burst_check_off);
            mSelectText.setTextColor(Color.parseColor("#FFFFFF"));
        }
        mCheckBox.setSelected(checked);
        if (mOnImageCheckedListener != null) {
            mOnImageCheckedListener.onImageChecked(checked);
        }
    }

    public void setImageBitmap(Bitmap bitmap) {
        mImage.setImageBitmap(bitmap);
    }

    public ImageView getImageView() {
        return mImage;
    }

    public interface OnImageCheckedListener {
        void onImageChecked(boolean checked);
    }
}
