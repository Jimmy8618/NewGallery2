
package com.android.gallery3d.sidebar;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;

import java.util.List;

/**
 * Created by apuser on 12/22/16.
 */

public class SideBarAdapter extends BaseAdapter {
    private Context mContext;
    private List<SideBarItem> mSideBarItems;
    private OnSideBarItemClickListener mOnSideBarItemClickListener;
    private OnSideBarItemChangeListener mOnSideBarItemChangeListener;

    public interface OnSideBarItemClickListener {
        void onSideBarClicked(String key, boolean changed);
    }

    public interface OnSideBarItemChangeListener {
        void onSideBarItemChanged(SideBarItem item);
    }

    public void setOnSideBarItemClickListener(OnSideBarItemClickListener l) {
        mOnSideBarItemClickListener = l;
    }

    public void setOnSideBarItemChangeListener(OnSideBarItemChangeListener l) {
        mOnSideBarItemChangeListener = l;
    }

    public SideBarAdapter(Context context, List<SideBarItem> items) {
        mContext = context;
        mSideBarItems = items;
    }

    public List<SideBarItem> getSideBarItems() {
        return mSideBarItems;
    }

    @Override
    public int getCount() {
        return mSideBarItems.size();
    }

    @Override
    public Object getItem(int i) {
        return mSideBarItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder = null;
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.side_bar_item, null);
            viewHolder = new ViewHolder((TextView) view.findViewById(R.id.title), (ImageView) view.findViewById(R.id.icon));
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.setSideBarItem(mSideBarItems.get(i));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                if (mOnSideBarItemClickListener != null) {
                    mOnSideBarItemClickListener.onSideBarClicked(mSideBarItems.get(i).getKey(), !mSideBarItems.get(i).isSelected());
                }
                for (SideBarItem item : mSideBarItems) {
                    item.setSelected(false);
                }
                mSideBarItems.get(i).setSelected(true);
                notifyDataSetChanged();
            }
        });
        return view;
    }

    public String getSelectedItemKey() {
        for (SideBarItem item : mSideBarItems) {
            if (item.isSelected()) {
                return item.getKey();
            }
        }
        return null;
    }

    class ViewHolder {
        private ImageView mIcon;
        private TextView mTextView;
        private SideBarItem mSideBarItem;

        public ViewHolder(TextView textView, ImageView icon) {
            mTextView = textView;
            mIcon = icon;
        }

        public void setSideBarItem(SideBarItem item) {
            mSideBarItem = item;
            setTitle(mSideBarItem.getTitle());
            if (mSideBarItem.isSelected()) {
                setDrawable(mSideBarItem.getSelectedDrawable());
                mTextView.setTextColor(Color.parseColor("#4185F5"));
                if (mOnSideBarItemChangeListener != null) {
                    mOnSideBarItemChangeListener.onSideBarItemChanged(mSideBarItem);
                }
            } else {
                setDrawable(mSideBarItem.getNormalDrawable());
                mTextView.setTextColor(Color.BLACK);
            }
        }

        private void setTitle(CharSequence text) {
            mTextView.setText(text);
        }

        private void setDrawable(int resid) {
            mIcon.setImageResource(resid);
        }

    }
}
