package com.android.gallery3d.v2.cust;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;

public class SingleEditTextDialog extends DialogFragment {
    private static final String TAG = SingleEditTextDialog.class.getSimpleName();
    private static final int NAME_MAX_LENGTH = 60;

    private static List<Character> sInvalidCharOfFilename;

    static {
        sInvalidCharOfFilename = new ArrayList<>();
        sInvalidCharOfFilename.add('*');
        sInvalidCharOfFilename.add('\"');
        sInvalidCharOfFilename.add('/');
        sInvalidCharOfFilename.add('\\');
        sInvalidCharOfFilename.add('?');
        sInvalidCharOfFilename.add('|');
        sInvalidCharOfFilename.add('>');
        sInvalidCharOfFilename.add('<');
        sInvalidCharOfFilename.add(':');
    }

    private Toast mToast;

    private String mTitle;
    private String mTint;

    private OnPositiveButtonClickedListener mOnPositiveButtonClickedListener;

    public interface OnPositiveButtonClickedListener {
        void onPositiveButtonClicked(String text);
    }

    public void setOnPositiveButtonClickedListener(OnPositiveButtonClickedListener onPositiveButtonClickedListener) {
        mOnPositiveButtonClickedListener = onPositiveButtonClickedListener;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public void setTint(String tint) {
        this.mTint = tint;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_editor, null);
        final EditText editText = view.findViewById(R.id.edit_text);
        final TextView textView = view.findViewById(R.id.title);

        textView.setText(this.mTitle);
        editText.setText(this.mTint);
        editText.requestFocus();

        editText.selectAll();
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > NAME_MAX_LENGTH) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.input_reached_max_length, Toast.LENGTH_SHORT);
                    s.delete(NAME_MAX_LENGTH, s.length());
                }
            }
        });
        view.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        view.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = editText.getText().toString().trim();

                dismiss();

                if (TextUtils.isEmpty(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.none_input_tips, Toast.LENGTH_SHORT);
                } else if ("..".equals(name) || ".".equals(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.badname_as_point, Toast.LENGTH_SHORT);
                } else if (hasInvalidChar(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.illegal_chars_of_name, Toast.LENGTH_SHORT);
                } else if (mOnPositiveButtonClickedListener != null) {
                    mOnPositiveButtonClickedListener.onPositiveButtonClicked(name);
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        return dialog;
    }

    private boolean hasInvalidChar(String dirName) {
        boolean hasInvalidChar = false;
        for (char ch : dirName.toCharArray()) {
            if (sInvalidCharOfFilename.contains(ch)) {
                hasInvalidChar = true;
            }
        }
        return hasInvalidChar;
    }

    @Override
    public void onPause() {
        super.onPause();
        dismissAllowingStateLoss();
    }
}
