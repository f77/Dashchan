/*
 * Copyright 2014-2016 Fukurou Mishiranu
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

package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.adapter.HistoryAdapter;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

import chan.util.StringUtils;

public class HistoryPage extends ListPage<HistoryAdapter> {
    @Override
    protected void onCreate() {
        PullableListView listView = getListView();
        PageHolder pageHolder = getPageHolder();
        HistoryAdapter adapter = new HistoryAdapter();
        initAdapter(adapter, null);
        listView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
        if (updateConfiguration(true)) {
            showScaleAnimation();
            if (pageHolder.position != null) {
                pageHolder.position.apply(getListView());
            }
        }
    }

    @Override
    protected void onResume() {
        updateConfiguration(false);
    }

    private boolean updateConfiguration(boolean create) {
        PageHolder pageHolder = getPageHolder();
        HistioryExtra extra = getExtra();
        HistoryAdapter adapter = getAdapter();
        String chanName = pageHolder.chanName;
        boolean mergeChans = Preferences.isMergeChans();
        boolean update = !mergeChans && !chanName.equals(extra.chanName) || mergeChans != extra.mergeChans;
        if (create || update) {
            adapter.updateConfiguraion(mergeChans ? null : chanName);
        }
        if (update) {
            extra.chanName = chanName;
            extra.mergeChans = mergeChans;
            pageHolder.position = null;
        }
        if (adapter.isEmpty()) {
            switchView(ViewType.ERROR, R.string.message_empty_history);
            return false;
        } else {
            switchView(ViewType.LIST, null);
            return true;
        }
    }

    @Override
    public String obtainTitle() {
        return getString(R.string.action_history);
    }

    @Override
    public void onItemClick(View view, int position, long id) {
        HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
        if (historyItem != null) {
            getUiManager().navigator().navigatePosts(historyItem.chanName, historyItem.boardName,
                    historyItem.threadNumber, null, null, 0);
        }
    }

    private static final int OPTIONS_MENU_CLEAR_HISTORY = 0;

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        menu.add(0, OPTIONS_MENU_CLEAR_HISTORY, 0, R.string.action_clear_history);
        menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTIONS_MENU_CLEAR_HISTORY: {
                new AlertDialog.Builder(getActivity()).setMessage(R.string.message_clear_history_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which1) -> {
                            HistioryExtra extra = getExtra();
                            HistoryDatabase.getInstance().clearAllHistory(extra.mergeChans ? null : extra.chanName);
                            getAdapter().clear();
                            switchView(ViewType.ERROR, R.string.message_empty_history);
                        }).show();
                return true;
            }
        }
        return false;
    }

    private static final int CONTEXT_MENU_COPY_LINK = 0;
    private static final int CONTEXT_MENU_ADD_FAVORITES = 1;
    private static final int CONTEXT_MENU_REMOVE_FROM_HISTORY = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView) {
        HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
        if (historyItem != null) {
            menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
            if (!FavoritesStorage.getInstance().hasFavorite(historyItem.chanName,
                    historyItem.boardName, historyItem.threadNumber)) {
                menu.add(0, CONTEXT_MENU_ADD_FAVORITES, 0, R.string.action_add_to_favorites);
            }
            menu.add(0, CONTEXT_MENU_REMOVE_FROM_HISTORY, 0, R.string.action_remove_from_history);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item, int position, View targetView) {
        HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
        if (historyItem != null) {
            switch (item.getItemId()) {
                case CONTEXT_MENU_COPY_LINK: {
                    Uri uri = getChanLocator().safe(true).createThreadUri(historyItem.boardName,
                            historyItem.threadNumber);
                    if (uri != null) {
                        StringUtils.copyToClipboard(getActivity(), uri.toString());
                    }
                    return true;
                }
                case CONTEXT_MENU_ADD_FAVORITES: {
                    FavoritesStorage.getInstance().add(historyItem.chanName, historyItem.boardName,
                            historyItem.threadNumber, historyItem.title, 0);
                    return true;
                }
                case CONTEXT_MENU_REMOVE_FROM_HISTORY: {
                    if (HistoryDatabase.getInstance().remove(historyItem.chanName, historyItem.boardName,
                            historyItem.threadNumber)) {
                        getAdapter().remove(historyItem);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onSearchQueryChange(String query) {
        getAdapter().applyFilter(query);
    }

    public static class HistioryExtra implements PageHolder.Extra {
        private String chanName;
        private boolean mergeChans = false;
    }

    private HistioryExtra getExtra() {
        PageHolder pageHolder = getPageHolder();
        if (!(pageHolder.extra instanceof HistioryExtra)) {
            pageHolder.extra = new HistioryExtra();
        }
        return (HistioryExtra) pageHolder.extra;
    }
}
