package com.android.gallery3d.v2.discover;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.v2.discover.things.Recognition;

import com.unisoc.sdk.ai.AIEngineListener;
import com.unisoc.sdk.ai.ImageClassifier;
import com.unisoc.sdk.ai.IUscAIListener;
import com.unisoc.sdk.ai.RecResult;
import com.unisoc.sdk.ai.Scene;
import com.unisoc.sdk.ai.UscAiManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class SprdDiscoverManager extends DiscoverManager {
    private static final String TAG = SprdDiscoverManager.class.getSimpleName();

    private Handler mMainHandler = new Handler();

    private com.unisoc.sdk.ai.Recognition mRecognition =
            new com.unisoc.sdk.ai.Recognition(1, Scene.IMAGE, false);

    private ImageClassifier mUSCAIClassifier;

    private Context mContext = GalleryAppImpl.getApplication().getAndroidContext();
    private UscAiManager mUscAiManager = new UscAiManager(mContext);

    private IUscAIListener mListener = new AIEngineListener() {

        @Override
        public void onResult(final com.unisoc.sdk.ai.Recognition rec) throws RemoteException {
            Log.d(TAG, "onResult rec: " + rec);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (rec != null) {
                        List<RecResult> recResultList = rec.getResults();
                        List<Recognition> recognitionList = new ArrayList<>();

                        for (RecResult recResult : recResultList) {
                            recognitionList.add(new Recognition(recResult.getTitle(), recResult.getConfidence(), null));
                        }

                        for (OnThingsResultListener l : mThingsListener) {
                            l.onResult(rec.getId(), recognitionList);
                        }
                    }
                }
            });
        }

        @Override
        public void onEngineConnect() throws RemoteException {
            Log.d(TAG, "onEngineConnect.");
            mEngineConnected = true;
            onDirty();
        }
    };

    public SprdDiscoverManager() {
        super();
        mEngineConnected = false;
    }

    @Override
    public void register(int scene, @NonNull Listener listener) {
        super.register(scene, listener);
        if (scene == SCENE_THINGS) {
            if (mUscAiManager != null) {
                mEngineConnected = false;
                mUscAiManager.registerListener(mListener, Scene.IMAGE);
            }
        }
    }

    @Override
    public void unregister(int scene, @NonNull Listener listener) {
        super.unregister(scene, listener);
        if (scene == SCENE_THINGS) {
            if (mUscAiManager != null) {
                mEngineConnected = false;
                mUscAiManager.unregisterListener(mListener, Scene.IMAGE);
            }
        }
    }

    private ImageClassifier getUSCAIClassifier() {
        if (mUSCAIClassifier == null) {
            mUSCAIClassifier = new ImageClassifier(mContext, mUscAiManager);
        }
        return mUSCAIClassifier;
    }

    @Override
    protected void classifyImage(ImageBean image) {
        mRecognition.setId(image.imageId);
        mRecognition.setMedialocation(image.path);
        getUSCAIClassifier().classifyFrame(mListener, mRecognition);
    }

    @Override
    protected void closeClassifier() {
        //do nothing
    }
}
