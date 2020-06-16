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

package com.mishiranu.dashchan.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;

public class WindowControlFrameLayout extends FrameLayout {
    public interface OnApplyWindowPaddingsListener {
        public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect);
    }

    private OnApplyWindowPaddingsListener onApplyWindowPaddingsListener;

    public WindowControlFrameLayout(Context context) {
        super(context);
        super.setFitsSystemWindows(true);
        super.setClipToPadding(false);
    }

    public void setOnApplyWindowPaddingsListener(OnApplyWindowPaddingsListener listener) {
        onApplyWindowPaddingsListener = listener;
    }

    @Override
    public void setFitsSystemWindows(boolean fitSystemWindows) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        throw new UnsupportedOperationException();
    }

    private Rect previousRect;

    private void onSystemWindowInsetsChangedInternal(Rect rect) {
        if (previousRect != null && previousRect.equals(rect)) {
            return;
        }
        previousRect = rect;
        if (onApplyWindowPaddingsListener != null) {
            onApplyWindowPaddingsListener.onApplyWindowPaddings(this, rect);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        try {
            return super.onApplyWindowInsets(insets);
        } finally {
            if (C.API_LOLLIPOP) {
                setPadding(0, 0, 0, 0);
                onSystemWindowInsetsChangedInternal(new Rect(insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()));
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean fitSystemWindows(Rect insets) {
        Rect rect = !C.API_LOLLIPOP ? new Rect(insets) : null;
        try {
            return super.fitSystemWindows(insets);
        } finally {
            if (!C.API_LOLLIPOP) {
                setPadding(0, 0, 0, 0);
                onSystemWindowInsetsChangedInternal(rect);
            }
        }
    }
}
