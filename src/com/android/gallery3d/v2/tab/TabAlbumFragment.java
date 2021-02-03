package com.android.gallery3d.v2.tab;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.R;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.page.AlbumSetPageFragment;
import com.android.gallery3d.v2.util.Constants;

public class TabAlbumFragment extends TabBaseFragment implements BasePageFragment.HideMenuListener{
    private static final String TAG = TabAlbumFragment.class.getSimpleName();
    /* Bug 1184608 */
    private BasePageFragment.HideMenuListener mHideMenuListener;

    public void setHideMenuListener(BasePageFragment.HideMenuListener listener){
        mHideMenuListener = listener;
    }
    /* @ */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_album_tab, container, false);
        return v;
    }

    @Override
    public void loadData() {
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.tab_album_fragment_container);
        Log.d(TAG, "loadData fragment(" + fragment + "), fm(" + fm + ").");
        if (fragment == null) {
            /* Bug 1184608 */
            AlbumSetPageFragment albumSetPageFragment = new AlbumSetPageFragment();
            albumSetPageFragment.setHideMenuListener(this);
            fragment = albumSetPageFragment;
            /* @ */
            //set arguments
            Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
            bundle.putInt(Constants.KEY_BUNDLE_CONTAINER_ID, R.id.tab_album_fragment_container);
            fragment.setArguments(bundle);
            //
            fm.beginTransaction()
                    .add(R.id.tab_album_fragment_container, fragment)
                    .commit();
        }
    }
    /* Bug 1184608 */
    @Override
    public void onMenuShow(boolean visible) {
        Log.d(TAG, "onMenuShow:"+visible);
        if(mHideMenuListener != null){
            mHideMenuListener.onMenuShow(visible);
        }
    }
    /* @ */
}
