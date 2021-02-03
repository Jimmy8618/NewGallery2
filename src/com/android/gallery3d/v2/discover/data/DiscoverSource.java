package com.android.gallery3d.v2.discover.data;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;

public class DiscoverSource extends MediaSource {
    private GalleryApp mApplication;
    private PathMatcher mPathMatcher;

    private static final int PLACEHOLDER_ITEM = 0;

    private static final int THINGS_ALBUMSET = 1;
    private static final int THINGS_ALBUM = 2;
    private static final int THINGS_PLACEHOLDER_ALBUM = 3;

    private static final int PEOPLE_ALBUMSET = 4;
    private static final int PEOPLE_ALBUM = 5;
    private static final int PEOPLE_MERGE_ALBUM = 6;
    private static final int PEOPLE_PLACEHOLDER_ALBUM = 7;
    private static final int PEOPLE_ITEM = 8;

    private static final int LOCATION_ALBUMSET = 9;
    private static final int LOCATION_ALBUM = 10;
    private static final int LOCATION_PLACEHOLDER_ALBUM = 11;

    private static final int STORY_ALBUMSET = 12;
    private static final int STORY_ALBUM = 13;
    private static final int STORY_PLACEHOLDER_ALBUM = 14;

    public DiscoverSource(GalleryApp app) {
        super("discover");
        this.mApplication = app;
        mPathMatcher = new PathMatcher();
        mPathMatcher.add("/discover/things/albumset", THINGS_ALBUMSET);
        mPathMatcher.add("/discover/things/album/*", THINGS_ALBUM);
        mPathMatcher.add("/discover/things/album/placeholder/*", THINGS_PLACEHOLDER_ALBUM);

        mPathMatcher.add("/discover/people/albumset", PEOPLE_ALBUMSET);
        mPathMatcher.add("/discover/people/album/*", PEOPLE_ALBUM);
        mPathMatcher.add("/discover/people/merge/album/*", PEOPLE_MERGE_ALBUM);
        mPathMatcher.add("/discover/people/merge/album/placeholder/*", PEOPLE_PLACEHOLDER_ALBUM);
        mPathMatcher.add("/discover/people/item/*", PEOPLE_ITEM);

        mPathMatcher.add("/discover/all/album/placeholder/item/*", PLACEHOLDER_ITEM);

        mPathMatcher.add("/discover/location/albumset", LOCATION_ALBUMSET);
        mPathMatcher.add("/discover/location/album/*", LOCATION_ALBUM);
        mPathMatcher.add("/discover/location/album/placeholder/*", LOCATION_PLACEHOLDER_ALBUM);

        mPathMatcher.add("/discover/story/albumset", STORY_ALBUMSET);
        mPathMatcher.add("/discover/story/album/*", STORY_ALBUM);
        mPathMatcher.add("/discover/story/album/placeholder/*", STORY_PLACEHOLDER_ALBUM);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        switch (mPathMatcher.match(path)) {
            case THINGS_ALBUMSET:
                return new ThingsAlbumSet(path, mApplication);
            case THINGS_ALBUM:
                return new ThingsAlbum(path, mApplication, mPathMatcher.getIntVar(0), false);
            case THINGS_PLACEHOLDER_ALBUM:
                return new ThingsAlbum(path, mApplication, mPathMatcher.getIntVar(0), true);
            case PLACEHOLDER_ITEM:
                return new PlaceHolder(path, mPathMatcher.getIntVar(0));
            case PEOPLE_ALBUMSET:
                return new PeopleAlbumSet(path, mApplication);
            case PEOPLE_ALBUM:
                return new PeopleAlbum(path, mApplication, mPathMatcher.getIntVar(0));
            case PEOPLE_MERGE_ALBUM:
                return createPeopleMergeAlbum(path, mPathMatcher.getVar(0));
            case PEOPLE_PLACEHOLDER_ALBUM:
                return new PeopleMergeAlbum(path, new MediaSet[]{}, mPathMatcher.getIntVar(0), true);
            case PEOPLE_ITEM:
                return createPeopleItem(path, mPathMatcher.getVar(0));
            case LOCATION_ALBUMSET:
                return new LocationAlbumSet(path, mApplication);
            case LOCATION_ALBUM:
                return new LocationAlbum(path, mApplication, mPathMatcher.getIntVar(0), false);
            case LOCATION_PLACEHOLDER_ALBUM:
                return new LocationAlbum(path, mApplication, mPathMatcher.getIntVar(0), true);
            case STORY_ALBUMSET:
                return new StoryAlbumSet(path, mApplication);
            case STORY_ALBUM:
                return new StoryAlbum(path, mApplication, mPathMatcher.getVar(0), false);
            case STORY_PLACEHOLDER_ALBUM:
                return new StoryAlbum(path, mApplication, mPathMatcher.getVar(0), true);
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }

    private MediaSet createPeopleMergeAlbum(Path path, String ids) {
        String id[] = ids.split("-");
        MediaSet[] mediaSets = new MediaSet[id.length];
        for (int i = 0; i < id.length; i++) {
            mediaSets[i] = mApplication.getDataManager().getMediaSet(PeopleAlbum.PATH_ITEM.getChild(id[i]));
        }
        return new PeopleMergeAlbum(path, mediaSets, 0, false);
    }

    private MediaItem createPeopleItem(Path path, String imgId_faceId) {
        String id[] = imgId_faceId.split("-");
        return new PeopleItem(path, mApplication, Integer.parseInt(id[0]), Integer.parseInt(id[1]));
    }
}
