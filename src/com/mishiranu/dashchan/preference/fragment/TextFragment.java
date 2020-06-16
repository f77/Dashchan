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

package com.mishiranu.dashchan.preference.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.CommentTextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import chan.content.ChanMarkup;
import io.noties.markwon.Markwon;

public class TextFragment extends Fragment implements View.OnClickListener {
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_CONTENT = "content";
    private static final String EXTRA_IS_MARKDOWN = "is_markdown";

    public static final int TYPE_LICENSES = 0;
    public static final int TYPE_CHANGELOG = 1;

    private CommentTextView textView;

    public TextFragment() {
    }

    public static Bundle createArguments(int type, String content) {
        return createArguments(type, content, false);
    }

    public static Bundle createArguments(int type, String content, boolean isMarkdown) {
        Bundle args = new Bundle();
        args.putInt(EXTRA_TYPE, type);
        args.putString(EXTRA_CONTENT, content);
        args.putBoolean(EXTRA_IS_MARKDOWN, isMarkdown);
        return args;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = getArguments();
        int type = args.getInt(EXTRA_TYPE);
        String content = args.getString(EXTRA_CONTENT);
        boolean isMarkdown = args.getBoolean(EXTRA_IS_MARKDOWN);
        switch (type) {
            case TYPE_LICENSES: {
                InputStream input = null;
                try {
                    input = getActivity().getAssets().open("licenses.txt");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    IOUtils.copyStream(input, output);
                    content = new String(output.toByteArray()).replaceAll("\r?\n", "<br/>");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    IOUtils.close(input);
                }
                break;
            }
        }
        float density = ResourceUtils.obtainDensity(this);
        textView = new CommentTextView(getActivity(), null, android.R.attr.textAppearanceLarge);
        int padding = (int) (16f * density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (isMarkdown) {
            final Markwon markwon = Markwon.builder(getActivity().getApplicationContext()).build();
            markwon.setMarkdown(textView, content);
        } else {
            CharSequence text = HtmlParser.spanify(content, new Markup(), null, null);
            new ColorScheme(getActivity()).apply(text);
            textView.setText(text);
        }
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.setId(android.R.id.list);
        FrameLayout frameLayout = new FrameLayout(getActivity());
        frameLayout.setOnClickListener(this);
        scrollView.addView(frameLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.addView(textView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return scrollView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!C.API_MARSHMALLOW) {
            ((View) getView().getParent()).setPadding(0, 0, 0, 0);
        }
        switch (getArguments().getInt(EXTRA_TYPE)) {
            case TYPE_LICENSES: {
                getActivity().setTitle(R.string.preference_licenses);
                break;
            }
            case TYPE_CHANGELOG: {
                getActivity().setTitle(R.string.preference_changelog);
                break;
            }
        }
    }

    private long lastClickTime;

    @Override
    public void onClick(View v) {
        long time = System.currentTimeMillis();
        if (time - lastClickTime < ViewConfiguration.getDoubleTapTimeout()) {
            lastClickTime = 0L;
            textView.startSelection();
        } else {
            lastClickTime = time;
        }
    }

    private static class Markup extends ChanMarkup {
        public Markup() {
            super(false);
            addTag("h1", TAG_HEADING);
            addTag("h2", TAG_HEADING);
            addTag("h3", TAG_HEADING);
            addTag("h4", TAG_HEADING);
            addTag("h5", TAG_HEADING);
            addTag("h6", TAG_HEADING);
            addTag("strong", TAG_BOLD);
            addTag("em", TAG_ITALIC);
            addTag("pre", TAG_CODE);
        }
    }
}
