package com.android.gallery3d.v2.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.cust.DiscoverItemLayout;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.DiscoverItem;
import com.android.gallery3d.v2.page.DiscoverPageFragment;

import java.util.ArrayList;
import java.util.List;

public class DiscoverPageUI extends FrameLayout {
    private static final String TAG = DiscoverPageUI.class.getSimpleName();

    private static final int mColumnPort = 2;
    private static final int mColumnLand = 3;
    private static final int M_SLOT_GAP = 10;

    private int mColumns;

    private RecyclerView mRecyclerView;
    private Adapter mAdapter;

    private DiscoverPageFragment mDiscoverPageFragment;

    private DiscoverItemClickListener mDiscoverItemClickListener;

    public interface DiscoverItemClickListener {
        void onDiscoverItemClicked(DiscoverItem item);
    }

    public DiscoverPageUI(@NonNull Context context) {
        super(context);
    }

    public DiscoverPageUI(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DiscoverPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DiscoverPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setDiscoverItemClickListener(DiscoverItemClickListener discoverItemClickListener) {
        mDiscoverItemClickListener = discoverItemClickListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mColumns = Utils.getMinMultiple(mColumnPort, mColumnLand);
        this.mRecyclerView = findViewById(R.id.recycler_view_discover_page);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), mColumns);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ?
                        (mColumns / mColumnPort)
                        : (mColumns / mColumnLand);
            }
        });
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = M_SLOT_GAP;
                outRect.top = M_SLOT_GAP;
                outRect.right = M_SLOT_GAP;
                outRect.bottom = M_SLOT_GAP;
            }
        });

        mAdapter = new Adapter();
        mRecyclerView.setAdapter(mAdapter);
    }

    public void bind(DiscoverPageFragment discoverPageFragment) {
        this.mDiscoverPageFragment = discoverPageFragment;
        mAdapter.addItems(this.mDiscoverPageFragment.getData());
    }

    public void loadStart(int type) {
        Log.d(TAG, "loadStart type = " + type);
    }

    public void loading(int type, int index, int size, AlbumSetItem[] item) {
        DiscoverItem discoverItem = mAdapter.findItem(type);
        if (discoverItem == null || index >= 4) {
            return;
        }
        Log.d(TAG, "loading B (" + index + ", " + size + ", " + item[item.length - 1].getName() + ").");
        for (AlbumSetItem albumSetItem : item) {
            switch (index) {
                case 0:
                    discoverItem.setUri1(albumSetItem.getCoverItemUri(),
                            albumSetItem.getCoverOrientation(), albumSetItem.getCoverDateModified(),
                            albumSetItem.getCoverPlaceHolderId());
                    break;
                case 1:
                    discoverItem.setUri2(albumSetItem.getCoverItemUri(),
                            albumSetItem.getCoverOrientation(), albumSetItem.getCoverDateModified(),
                            albumSetItem.getCoverPlaceHolderId());
                    break;
                case 2:
                    discoverItem.setUri3(albumSetItem.getCoverItemUri(),
                            albumSetItem.getCoverOrientation(), albumSetItem.getCoverDateModified(),
                            albumSetItem.getCoverPlaceHolderId());
                    break;
                case 3:
                    discoverItem.setUri4(albumSetItem.getCoverItemUri(),
                            albumSetItem.getCoverOrientation(), albumSetItem.getCoverDateModified(),
                            albumSetItem.getCoverPlaceHolderId());
                    break;
                default:
                    break;
            }
        }
        Log.d(TAG, "loading E (" + index + ", " + size + ", " + item[item.length - 1].getName() + ").");
    }

    public void loadEnd(int type) {
        Log.d(TAG, "loadEnd type = " + type);
        DiscoverItem item = mAdapter.findItem(type);
        if (item == null) {
            return;
        }
        if (item.isUpdated()) {
            mAdapter.notifyItemChanged(item.getPosition());
        }
    }

    public void loadEmpty(int type) {
        Log.d(TAG, "loadEmpty");
        DiscoverItem item = mAdapter.findItem(type);
        if (item == null) {
            return;
        }
        item.setUriNull();
        if (item.isUpdated()) {
            mAdapter.notifyItemChanged(item.getPosition());
        }
    }

    private class Holder extends RecyclerView.ViewHolder {
        private DiscoverItemLayout mDiscoverItemLayout;
        private DiscoverItem mDiscoverItem;

        public Holder(View itemView) {
            super(itemView);
            mDiscoverItemLayout = (DiscoverItemLayout) itemView;
            mDiscoverItemLayout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mDiscoverItemClickListener != null) {
                        mDiscoverItemClickListener.onDiscoverItemClicked(mDiscoverItem);
                    }
                }
            });
        }

        public void bind(DiscoverItem item) {
            mDiscoverItemLayout.bind(item);
            mDiscoverItem = item;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private List<DiscoverItem> mData = new ArrayList<>();

        synchronized void addItems(List<DiscoverItem> items) {
            mData.clear();
            mData.addAll(items);
            notifyDataSetChanged();
        }

        synchronized DiscoverItem findItem(int type) {
            DiscoverItem item = null;
            for (DiscoverItem discoverItem : mData) {
                if (discoverItem.getType() == type) {
                    item = discoverItem;
                    break;
                }
            }
            return item;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.discover_item_layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(mData.get(position));
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }
    }
}
