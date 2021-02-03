package com.android.gallery3d.filtershow;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.imageshow.ImageShow;

import java.util.Vector;

public class EditorPlaceHolder {
    private static final String LOGTAG = "EditorPlaceHolder";

    private FilterShowActivity mActivity = null;
    private FrameLayout mContainer = null;
    //private HashMap<Integer, Editor> mEditors = new HashMap<Integer, Editor>();
    private SparseArray<Editor> mEditors = new SparseArray<Editor>();
    private Vector<ImageShow> mOldViews = new Vector<ImageShow>();

    public EditorPlaceHolder(FilterShowActivity activity) {
        mActivity = activity;
    }

    public void setContainer(FrameLayout container) {
        mContainer = container;
    }

    public void addEditor(Editor c) {
        mEditors.put(c.getID(), c);
    }

    public boolean contains(int type) {
        return mEditors.get(type) != null;
    }

    public Editor showEditor(int type) {
        Editor editor = mEditors.get(type);
        if (editor == null) {
            return null;
        }

        editor.createEditor(mActivity, mContainer);
        editor.getImageShow().attach();
        mContainer.setVisibility(View.VISIBLE);
        mContainer.removeAllViews();
        View eview = editor.getTopLevelView();
        ViewParent parent = eview.getParent();

        if (parent != null && parent instanceof FrameLayout) {
            ((FrameLayout) parent).removeAllViews();
        }

        mContainer.addView(eview);
        hideOldViews();
        editor.setVisibility(View.VISIBLE);
        return editor;
    }

    public void setOldViews(Vector<ImageShow> views) {
        mOldViews = views;
    }

    public void hide() {
        // SPRD: fix bug 500011,the mContainer maybe null in monkeytest
        if (mContainer == null) {
            return;
        }
        mContainer.setVisibility(View.GONE);
    }

    public void hideOldViews() {
        for (View view : mOldViews) {
            view.setVisibility(View.GONE);
        }
    }

    public Editor getEditor(int editorId) {
        return mEditors.get(editorId);
    }

}
