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

package com.mishiranu.dashchan.ui.navigator.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class BoardsAdapter extends BaseAdapter {
    public static final String KEY_TITLE = "title";
    public static final String KEY_BOARDS = "boards";

    private static final int TYPE_VIEW = 0;
    private static final int TYPE_HEADER = 1;

    private final String chanName;

    private final ArrayList<ListItem> listItems = new ArrayList<>();
    private final ArrayList<ListItem> filteredListItems = new ArrayList<>();

    private boolean filterMode = false;
    private String filterText;

    public BoardsAdapter(String chanName) {
        this.chanName = chanName;
    }

    // Returns true, if adapter isn't empty.
    public boolean applyFilter(String text) {
        filterText = text;
        filterMode = !StringUtils.isEmpty(text);
        filteredListItems.clear();
        if (filterMode) {
            text = text.toLowerCase(Locale.getDefault());
            for (ListItem listItem : listItems) {
                if (listItem.boardName != null) {
                    boolean add = false;
                    if (listItem.boardName.toLowerCase(Locale.US).contains(text)) {
                        add = true;
                    } else if (listItem.title != null && listItem.title.toLowerCase(Locale.getDefault())
                            .contains(text)) {
                        add = true;
                    }
                    if (add) {
                        filteredListItems.add(listItem);
                    }
                }
            }
        }
        notifyDataSetChanged();
        return !filterMode || filteredListItems.size() > 0;
    }

    public void update() {
        listItems.clear();
        ChanConfiguration configuration = ChanConfiguration.get(chanName);
        JSONArray jsonArray = configuration.getBoards();
        if (jsonArray != null) {
            try {
                for (int i = 0, length = jsonArray.length(); i < length; i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String title = CommonUtils.getJsonString(jsonObject, KEY_TITLE);
                    if (length > 1) {
                        listItems.add(new ListItem(null, title));
                    }
                    JSONArray boardsArray = jsonObject.getJSONArray(KEY_BOARDS);
                    for (int j = 0; j < boardsArray.length(); j++) {
                        String boardName = boardsArray.isNull(j) ? null : boardsArray.getString(j);
                        if (!StringUtils.isEmpty(boardName)) {
                            title = configuration.getBoardTitle(boardName);
                            listItems.add(new ListItem(boardName, StringUtils.formatBoardTitle(chanName,
                                    boardName, title)));
                        }
                    }
                }
            } catch (JSONException e) {
                // Invalid data, ignore exception
            }
        }
        notifyDataSetChanged();
        if (filterMode) {
            applyFilter(filterText);
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).boardName == null ? TYPE_HEADER : TYPE_VIEW;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItem(position).boardName != null;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ListItem listItem = getItem(position);
        if (convertView == null) {
            if (listItem.boardName != null) {
                float density = ResourceUtils.obtainDensity(parent);
                TextView textView = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
                textView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                convertView = textView;
            } else {
                convertView = ViewFactory.makeListTextHeader(parent, false);
            }
        }
        ((TextView) convertView).setText(listItem.title);
        return convertView;
    }

    @Override
    public int getCount() {
        return (filterMode ? filteredListItems : listItems).size();
    }

    @Override
    public ListItem getItem(int position) {
        return (filterMode ? filteredListItems : listItems).get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    public static class ListItem {
        public final String boardName, title;

        public ListItem(String boardName, String title) {
            this.boardName = boardName;
            this.title = title;
        }
    }
}
