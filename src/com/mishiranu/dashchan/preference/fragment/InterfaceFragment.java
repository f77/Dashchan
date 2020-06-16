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

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ToastUtils;

public class InterfaceFragment extends BasePreferenceFragment {
    private CheckBoxPreference advancedSearchPreference;
    private CheckBoxPreference fastSearchPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        makeSeekBar(null, Preferences.KEY_TEXT_SCALE, Preferences.DEFAULT_TEXT_SCALE,
                R.string.preference_text_scale, R.string.preference_text_scale_summary_format, 75, 200, 5, 1f);
        makeSeekBar(null, Preferences.KEY_THUMBNAILS_SCALE, Preferences.DEFAULT_THUMBNAILS_SCALE,
                R.string.preference_thumbnails_scale, R.string.preference_thumbnails_scale_summary_format,
                100, 200, 10, 1f);
        makeCheckBox(null, true, Preferences.KEY_CUT_THUMBNAILS, Preferences.DEFAULT_CUT_THUMBNAILS,
                R.string.preference_cut_thumbnails, R.string.preference_cut_thumbnails_summary);
        makeCheckBox(null, true, Preferences.KEY_ACTIVE_SCROLLBAR, Preferences.DEFAULT_ACTIVE_SCROLLBAR,
                R.string.preference_active_scrollbar, 0);
        makeCheckBox(null, true, Preferences.KEY_SCROLL_THREAD_GALLERY, Preferences.DEFAULT_SCROLL_THREAD_GALLERY,
                R.string.preference_scroll_thread_gallery, 0);

        PreferenceCategory navigationDrawerCategory = makeCategory(R.string.preference_category_navigation_drawer);
        makeList(navigationDrawerCategory, Preferences.KEY_PAGES_LIST, Preferences.VALUES_PAGES_LIST,
                Preferences.DEFAULT_PAGES_LIST, R.string.preference_pages_list, R.array.preference_pages_list_choices);
        makeList(navigationDrawerCategory, Preferences.KEY_DRAWER_INITIAL_POSITION,
                Preferences.VALUES_DRAWER_INITIAL_POSITION, Preferences.DEFAULT_DRAWER_INITIAL_POSITION,
                R.string.preference_drawer_initial_position, R.array.preference_drawer_initial_position_choices);

        PreferenceCategory threadsCategory = makeCategory(R.string.preference_category_threads);
        makeCheckBox(threadsCategory, true, Preferences.KEY_PAGE_BY_PAGE, Preferences.DEFAULT_PAGE_BY_PAGE,
                R.string.preference_page_by_page, R.string.preference_page_by_page_summary);
        makeCheckBox(threadsCategory, true, Preferences.KEY_DISPLAY_HIDDEN_THREADS,
                Preferences.DEFAULT_DISPLAY_HIDDEN_THREADS, R.string.preference_display_hidden_threads, 0);

        PreferenceCategory postsCategory = makeCategory(R.string.preference_category_posts);
        makeEditText(postsCategory, Preferences.KEY_POST_MAX_LINES, Preferences.DEFAULT_POST_MAX_LINES,
                R.string.preference_post_max_lines, R.string.preference_post_max_lines_summary, null,
                InputType.TYPE_CLASS_NUMBER, false);
        makeCheckBox(postsCategory, true, Preferences.KEY_ALL_ATTACHMENTS, Preferences.DEFAULT_ALL_ATTACHMENTS,
                R.string.preference_all_attachments, R.string.preference_all_attachments_summary);
        makeList(postsCategory, Preferences.KEY_HIGHLIGHT_UNREAD, Preferences.VALUES_HIGHLIGHT_UNREAD,
                Preferences.DEFAULT_HIGHLIGHT_UNREAD, R.string.preference_highlight_unread,
                R.array.preference_highlight_unread_choices);

        PreferenceCategory searchCategory = makeCategory(R.string.preference_category_search);
        advancedSearchPreference = makeCheckBox(searchCategory, true, Preferences.KEY_ADVANCED_SEARCH,
                Preferences.DEFAULT_ADVANCED_SEARCH, R.string.preference_advanced_search,
                R.string.preference_advanced_search_summary);
        fastSearchPreference = makeCheckBox(searchCategory, true, Preferences.KEY_FAST_SEARCH,
                Preferences.DEFAULT_FAST_SEARCH, R.string.preference_fast_search,
                R.string.preference_fast_search_summary);
        makeList(searchCategory, Preferences.KEY_FAST_SEARCH_SOURCE, Preferences.VALUES_FAST_SEARCH_SOURCE,
                Preferences.DEFAULT_FAST_SEARCH_SOURCE, R.string.preference_fast_search_source,
                R.array.preference_fast_search_source_choices);
        makeCheckBox(searchCategory, true, Preferences.KEY_FAST_SEARCH_SMOOTH_SCROLL,
                Preferences.DEFAULT_FAST_SEARCH_SMOOTH_SCROLL, R.string.preference_fast_search_smooth_scroll,
                R.string.preference_fast_search_smooth_scroll_summary);

        makeCheckBox(postsCategory, true, Preferences.KEY_DISPLAY_ICONS, Preferences.DEFAULT_DISPLAY_ICONS,
                R.string.preference_display_icons, R.string.preference_display_icons_summary);

        PreferenceCategory submissionCategory = makeCategory(R.string.preference_category_submission);
        makeCheckBox(submissionCategory, true, Preferences.KEY_HIDE_PERSONAL_DATA,
                Preferences.DEFAULT_HIDE_PERSONAL_DATA, R.string.preference_hide_personal_data, 0);
        makeCheckBox(submissionCategory, true, Preferences.KEY_HUGE_CAPTCHA, Preferences.DEFAULT_HUGE_CAPTCHA,
                R.string.preference_huge_captcha, 0);
    }

    @Override
    public void onPreferenceAfterChange(Preference preference) {
        super.onPreferenceAfterChange(preference);
        if (preference == advancedSearchPreference && advancedSearchPreference.isChecked()) {
            SpannableStringBuilder builder = new SpannableStringBuilder
                    (getText(R.string.preference_advanced_search_message));
            Object[] spans = builder.getSpans(0, builder.length(), Object.class);
            for (Object span : spans) {
                int start = builder.getSpanStart(span);
                int end = builder.getSpanEnd(span);
                int flags = builder.getSpanFlags(span);
                builder.removeSpan(span);
                builder.setSpan(new TypefaceSpan("sans-serif-medium"), start, end, flags);
            }
            MessageDialog.create(this, builder, false);
        } else if (preference == fastSearchPreference) {
            ToastUtils.show(getActivity(), R.string.message_uninstall_reminder);
        }
    }
}
