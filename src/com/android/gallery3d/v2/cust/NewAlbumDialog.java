package com.android.gallery3d.v2.cust;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;

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
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NewAlbumDialog extends DialogFragment {
    private static final int NAME_MAX_LENGTH = 60;

    private Toast mToast;
    private static String mRootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + SaveImage.NEW_ALBUM_SAVE_DIRECTORY;
    static List<Character> sInvalidCharOfFilename;

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

    private OnNewAlbumCreatedListener mOnNewAlbumCreatedListener;

    public interface OnNewAlbumCreatedListener {
        void onNewAlbumCreated(String dir);
    }

    public void setOnNewAlbumCreatedListener(OnNewAlbumCreatedListener l) {
        this.mOnNewAlbumCreatedListener = l;
    }

    public static boolean isMyAlbum(String dir) {
        if (TextUtils.isEmpty(dir)) {
            return false;
        }
        return mRootDir.equals(new File(dir).getParent());
    }

    private boolean hasInvalidChar(String dirName) {
        boolean hasInvalidChar = false;
        for (char ch : dirName.toCharArray()) {
            if (sInvalidCharOfFilename.contains(ch)) {
                hasInvalidChar = true;
            }
        }
        if (hasInvalidChar) {
            mToast = ToastUtil.showMessage(getContext(), mToast, R.string.illegal_chars_of_foldername, Toast.LENGTH_SHORT);
        }
        return hasInvalidChar;
    }

    /* @Bug 1214972 */
    private boolean isExistsOrEmptyFolder(String filePath){
        File newAlbumFile = new File(mRootDir + "/" + filePath);
        if(!newAlbumFile.exists()){
            return false;
        }else{
            File[] files = newAlbumFile.listFiles();
            if(files == null ){
                return false;
            }
            if(files.length == 0){
                return false;
            }
        }
        return true;
    }
    /* @ */

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_new_album, null);
        final EditText editText = view.findViewById(R.id.edit_text);

        String newDirName = getString(R.string.new_album);
        int copy = 1;
        /* @Bug 1214972
         * Empty album won't be shown in list,but it exists
         * If there is an empty album, allow to use the duplicate name to create
         * It's more kindly to user
         * */
        while (isExistsOrEmptyFolder(newDirName)) {
        /* @ */
            newDirName = getString(R.string.new_album) + "-" + (copy++);
        }
        editText.setText(newDirName);
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
                if (TextUtils.isEmpty(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.msg_folder_input_empty, Toast.LENGTH_SHORT);
                    return;
                } else if ("..".equals(name) || ".".equals(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.badname_as_point, Toast.LENGTH_SHORT);
                    return;
                } else if (name.endsWith(".")) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.badname_endwith_point, Toast.LENGTH_SHORT);
                    return;
                } else if (hasInvalidChar(name)) {
                    mToast = ToastUtil.showMessage(getContext(), mToast, R.string.illegal_chars_of_foldername, Toast.LENGTH_SHORT);
                    return;
                }

                String dirPath = mRootDir + "/" + name;
                File dir = new File(dirPath);
                /* @Bug 1214972 */
                dismiss();
                if (mOnNewAlbumCreatedListener != null) {
                    mOnNewAlbumCreatedListener.onNewAlbumCreated(dirPath);
                }
                /* @ */
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

    @Override
    public void onPause() {
        super.onPause();
        dismissAllowingStateLoss();
    }
}
