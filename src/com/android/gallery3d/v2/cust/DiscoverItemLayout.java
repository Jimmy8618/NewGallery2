package com.android.gallery3d.v2.cust;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.v2.data.DiscoverItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.MediaStoreSignature;

public class DiscoverItemLayout extends ViewGroup {
    private static final String TAG = DiscoverItemLayout.class.getSimpleName();
    private static final int THUMB_SIZE = 400;

    private static final int TITLE_MARGIN_TOP = 5;
    private static final int IMAGE_GAP = 6;

    private TextView mTitleView;

    private ImageView[] mImageViews;

    private View mBg;

    public DiscoverItemLayout(Context context) {
        super(context);
    }

    public DiscoverItemLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DiscoverItemLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DiscoverItemLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageViews = new ImageView[4];
        mTitleView = findViewById(R.id.title);
        mImageViews[0] = findViewById(R.id.image1);
        mImageViews[1] = findViewById(R.id.image2);
        mImageViews[2] = findViewById(R.id.image3);
        mImageViews[3] = findViewById(R.id.image4);
        mBg = findViewById(R.id.bg);
    }

    public void bind(DiscoverItem item) {
        mTitleView.setText(item.getTitleResId());

        loadImage(0, item.getType(), item.getUri1(), item.getOrientation1(), item.getDateModified1(), item.getPlaceHolderId1());
        loadImage(1, item.getType(), item.getUri2(), item.getOrientation2(), item.getDateModified2(), item.getPlaceHolderId2());
        loadImage(2, item.getType(), item.getUri3(), item.getOrientation3(), item.getDateModified3(), item.getPlaceHolderId3());
        loadImage(3, item.getType(), item.getUri4(), item.getOrientation4(), item.getDateModified4(), item.getPlaceHolderId4());
    }

    private void loadImage(int index, int type, Uri uri, int rotation,
                           long dateModified, Integer placeHolderId) {
        RequestOptions options = RequestOptions.overrideOf(THUMB_SIZE)
                .signature(new MediaStoreSignature(null, dateModified, rotation));

        RequestBuilder builder = Glide.with(getContext())
                .asBitmap()
                .apply(options);
        if (uri != null) {
            mImageViews[index].setScaleType(ImageView.ScaleType.CENTER_CROP);
            builder.load(uri).into(mImageViews[index]);
        } else if (placeHolderId != null) {
            mImageViews[index].setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            builder.load(placeHolderId).into(mImageViews[index]);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int imgW = mImageViews[0].getMeasuredWidth();
        mBg.layout(0, 0, mBg.getMeasuredWidth(), mBg.getMeasuredHeight());
        mImageViews[0].layout(IMAGE_GAP, IMAGE_GAP, IMAGE_GAP + imgW, IMAGE_GAP + imgW);
        mImageViews[1].layout(IMAGE_GAP * 2 + imgW, IMAGE_GAP, IMAGE_GAP * 2 + imgW * 2, IMAGE_GAP + imgW);
        mImageViews[2].layout(IMAGE_GAP, IMAGE_GAP * 2 + imgW, IMAGE_GAP + imgW, IMAGE_GAP * 2 + imgW * 2);
        mImageViews[3].layout(IMAGE_GAP * 2 + imgW, IMAGE_GAP * 2 + imgW, IMAGE_GAP * 2 + imgW * 2, IMAGE_GAP * 2 + imgW * 2);
        mTitleView.layout(0, IMAGE_GAP * 3 + TITLE_MARGIN_TOP + imgW * 2, mTitleView.getMeasuredWidth(), IMAGE_GAP * 3 + TITLE_MARGIN_TOP + imgW * 2 + mTitleView.getMeasuredHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        //measure title height
        measureChild(mTitleView, widthMeasureSpec, heightMeasureSpec);

        int imgW = (widthSize - IMAGE_GAP * 3) / 2;

        mImageViews[0].getLayoutParams().width = imgW;
        mImageViews[0].getLayoutParams().height = imgW;
        measureChild(mImageViews[0], widthMeasureSpec, heightMeasureSpec);

        mImageViews[1].getLayoutParams().width = imgW;
        mImageViews[1].getLayoutParams().height = imgW;
        measureChild(mImageViews[1], widthMeasureSpec, heightMeasureSpec);

        mImageViews[2].getLayoutParams().width = imgW;
        mImageViews[2].getLayoutParams().height = imgW;
        measureChild(mImageViews[2], widthMeasureSpec, heightMeasureSpec);

        mImageViews[3].getLayoutParams().width = imgW;
        mImageViews[3].getLayoutParams().height = imgW;
        measureChild(mImageViews[3], widthMeasureSpec, heightMeasureSpec);

        mBg.getLayoutParams().width = widthSize;
        mBg.getLayoutParams().height = widthSize;
        measureChild(mBg, widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(widthSize, widthSize + TITLE_MARGIN_TOP + mTitleView.getMeasuredHeight());
    }
}
