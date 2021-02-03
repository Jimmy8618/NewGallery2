package com.sprd.gallery3d.blending;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

public class RecycleAdapter extends RecyclerView.Adapter<RecycleAdapter.ThumbnailItem> {

    private static final String TAG = "RecycleAdapter";
    private Context mContext;
    private List<Thumbnail> mThumbnails = new ArrayList<>();
    private SelectedCallback mSelectedCallback;
    private int mSelectedNum = 0;

    RecycleAdapter(Context mContext) {
        this.mContext = mContext;
    }

    public void setData(List<Thumbnail> thumbnails) {
        if (thumbnails == null || thumbnails.size() < 0) {
            return;
        }
        this.mThumbnails.clear();
        this.mThumbnails = thumbnails;
        Log.d(TAG, "setData: " + thumbnails.size());
        notifyDataSetChanged();
    }

    public List<Thumbnail> getmThumbnails() {
        return mThumbnails;
    }

    @Override
    public ThumbnailItem onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item, parent, false);
        return new ThumbnailItem(view);
    }

    @Override
    public void onBindViewHolder(ThumbnailItem holder, int position) {
        int itemViewType = getItemViewType(position);
        if (mThumbnails.size() <= 0) {
            Log.d(TAG, "onBindViewHolder: mThumbnails.size() <= 0");
            return;
        }
        Thumbnail thumbnail = mThumbnails.get(position);
        if (thumbnail == null) {
            return;
        }
        if (position == mSelectedNum) {
            holder.imageViewFrame.setVisibility(View.VISIBLE);
        } else {
            holder.imageViewFrame.setVisibility(View.GONE);
        }
        holder.imageView.setTag(position);
        if (itemViewType == 1) {
            holder.imageView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.effect_replace_background));
            holder.textView.setText("");
        } else if (itemViewType == 2) {
            holder.imageView.setImageBitmap(thumbnail.bitmap);
            holder.textView.setText(thumbnail.name);
        }
    }

    @Override
    public int getItemCount() {
        return mThumbnails.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mThumbnails.get(position).getType();
    }


    static class Thumbnail {
        Bitmap bitmap;
        String name;
        int id;
        //2 is normal, 1 is add
        int type;

        public Thumbnail(Bitmap bitmap, String name, int id) {
            this.bitmap = bitmap;
            this.name = name;
            this.id = id;
            this.type = 2;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }
    /* @Bug 1226248 */
    public void setSelectnNum(int number){
        mSelectedNum = number;
        notifyDataSetChanged();
    }
    /* @ */

    class ThumbnailItem extends RecyclerView.ViewHolder {

        private ImageView imageView;
        private ImageView imageViewFrame;
        private TextView textView;

        public ThumbnailItem(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.effect);
            imageViewFrame = itemView.findViewById(R.id.frame);
            textView = itemView.findViewById(R.id.effect_name);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = (int) imageView.getTag();
                    Log.d(TAG, "onClick: " + position);
                    if (position < 0 || position > mThumbnails.size()) {
                        return;
                    }
                    mSelectedNum = position;
                    notifyDataSetChanged();
                    Thumbnail thumbnail = mThumbnails.get(position);
                    mSelectedCallback.OnSelected(thumbnail);
                }
            });
        }
    }

    public void setSelectedCallback(SelectedCallback selectedCallback) {
        this.mSelectedCallback = selectedCallback;
    }

    interface SelectedCallback {
        void OnSelected(Thumbnail thumbnail);
    }
}
