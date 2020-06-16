/*
 * Copyright 2014-2017 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui.gallery;

import android.content.Context;
import android.net.Uri;
import android.os.Parcelable;

import com.mishiranu.dashchan.content.model.GalleryItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import chan.content.ChanLocator;

public class GalleryInstance {
    public static final int FLAG_LOCKED_USER = 0x00000001;
    public static final int FLAG_LOCKED_GRID = 0x00000002;
    public static final int FLAG_LOCKED_ERROR = 0x00000004;

    public final Context context;
    public final Callback callback;

    public String chanName;
    public ChanLocator locator;
    public ArrayList<GalleryItem> galleryItems;

    public int actionBarColor;

    public GalleryInstance(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * Get sublist from gallery items.
     *
     * @param isOnlyVideos Get only videos?
     * @return Sublist of gallery items.
     */
    public ArrayList<GalleryItem> getGallerySubList(boolean isOnlyVideos) {
        ArrayList<GalleryItem> result = new ArrayList<>();
        for (GalleryItem item : galleryItems) {
            if (isOnlyVideos && !item.isVideo(locator)) {
                continue;
            }

            result.add(item);
        }
        return result;
    }

    /**
     * Get URIs of the all gallery items.
     *
     * @return URIs.
     */
    public LinkedHashMap<Uri, Parcelable> getGalleryURIs(ArrayList<GalleryItem> items) {
        LinkedHashMap<Uri, Parcelable> result = new LinkedHashMap<>();
        for (GalleryItem item : items) {
            result.put(item.getFileUri(locator), item);
        }
        return result;
    }

    public interface Callback {
        public void downloadGalleryItem(GalleryItem galleryItem);

        public void downloadGalleryItems(ArrayList<GalleryItem> galleryItems);

        public void modifyVerticalSwipeState(boolean ignoreIfGallery, float value);

        public void updateTitle();

        public void navigateGalleryOrFinish(boolean enableGalleryMode);

        public void navigatePageFromList(int position);

        public void navigatePost(GalleryItem galleryItem, boolean manually, boolean force);

        public boolean isAllowNavigatePostManually(boolean fromPager);

        public void invalidateOptionsMenu();

        public void setScreenOnFixed(boolean fixed);

        public boolean isGalleryWindow();

        public boolean isGalleryMode();

        public boolean isSystemUiVisible();

        public void modifySystemUiVisibility(int flag, boolean value);

        public void toggleSystemUIVisibility(int flag);
    }
}
