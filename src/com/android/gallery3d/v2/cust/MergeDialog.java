package com.android.gallery3d.v2.cust;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class MergeDialog extends DialogFragment {

    public interface OnItemSelectListener {
        void onItemSelected(String text);
    }

    private OnItemSelectListener mOnItemSelectListener;

    private String mTitle;

    private List<String> mNames;

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public void setNames(List<String> names) {
        mNames = names;
    }

    public void setOnItemSelectListener(OnItemSelectListener onItemSelectListener) {
        mOnItemSelectListener = onItemSelectListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_merge, null);
        final TextView textView = view.findViewById(R.id.title);
        textView.setText(this.mTitle);
        final RecyclerView mContent = view.findViewById(R.id.content_list);
        mContent.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        mContent.setAdapter(new Adapter(this.mNames));

        view.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        view.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return new AlertDialog.Builder(getContext())
                .setView(view)
                .create();
    }

    private class Holder extends RecyclerView.ViewHolder {
        private String mName;
        private TextView mTextView;

        public Holder(View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    if (mOnItemSelectListener != null) {
                        mOnItemSelectListener.onItemSelected(mName);
                    }
                }
            });
        }

        public void bind(String name) {
            mName = name;
            mTextView.setText(name);
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private List<String> mData;

        public Adapter(List<String> data) {
            this.mData = new ArrayList<>();
            if (data != null) {
                this.mData.addAll(data);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_merge_face_dialog, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(this.mData.get(position));
        }

        @Override
        public int getItemCount() {
            return this.mData.size();
        }
    }
}
