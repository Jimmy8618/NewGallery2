/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.filtershow.presets;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.ui.EditTextLengthFilter;
import com.android.gallery3d.util.ToastUtil;

public class PresetManagementDialog extends DialogFragment implements View.OnClickListener {
    private UserPresetsAdapter mAdapter;
    private EditText mEditText;
    private Toast toast = null;
    private Context mContext = null;
    private static final int MAX_TEXT_LENGTH = 20;

    private TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            int length = s.length();
            if (length >= MAX_TEXT_LENGTH) {
                toast = ToastUtil.showMessage(PresetManagementDialog.this.getContext(), toast,
                        R.string.name_length_limited, Toast.LENGTH_SHORT);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mContext = this.getContext();
        View view = inflater.inflate(R.layout.filtershow_presets_management_dialog, container);

        FilterShowActivity activity = (FilterShowActivity) getActivity();
        mAdapter = activity.getUserPresetsAdapter();
        mEditText = view.findViewById(R.id.editView);
        mEditText.setFilters(new InputFilter[]{new EditTextLengthFilter(mContext,
                R.string.name_length_limited, MAX_TEXT_LENGTH)});
        mEditText.addTextChangedListener(mTextWatcher);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        view.findViewById(R.id.ok).setOnClickListener(this);
        getDialog().setTitle(getString(R.string.filtershow_preset_name));
        return view;
    }

    @Override
    public void onClick(View v) {
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        /* SPRD: fix bug 395376,crash when change system language @{ */
        if (mAdapter == null) {
            mAdapter = activity.getUserPresetsAdapter();
        }
        /* @} */
        switch (v.getId()) {
            case R.id.cancel:
                mAdapter.clearChangedRepresentations();
                mAdapter.clearDeletedRepresentations();
                activity.updateUserPresetsFromAdapter(mAdapter);
                dismiss();
                break;
            case R.id.ok:
                String text = String.valueOf(mEditText.getText()).trim();
                if (TextUtils.isEmpty(text)) {
                    toast = ToastUtil.showMessage(getActivity(), toast, R.string.none_input_tips, Toast.LENGTH_SHORT);
                    return;
                }
                activity.saveCurrentImagePreset(text);
                mAdapter.updateCurrent();
                activity.updateUserPresetsFromAdapter(mAdapter);
                dismiss();
                break;
        }
    }
}
