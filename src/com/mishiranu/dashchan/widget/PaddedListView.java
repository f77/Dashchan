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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PaddedListView extends ListView implements EdgeEffectHandler.Shift {
    private EdgeEffectHandler edgeEffectHandler;
    private EdgeEffectHandler.Shift shift;

    public PaddedListView(Context context) {
        super(context);
    }

    public PaddedListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PaddedListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("unused") // Overrides hidden Android API protected method
    protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b) {
        if (b - t == getHeight()) {
            t += getEdgeEffectShift(true);
            b -= getEdgeEffectShift(false);
        }
        scrollBar.setBounds(l, t, r, b);
        scrollBar.draw(canvas);
    }

    public void setEdgeEffectShift(EdgeEffectHandler.Shift shift) {
        this.shift = shift;
    }

    public EdgeEffectHandler getEdgeEffectHandler() {
        return edgeEffectHandler;
    }

    @Override
    public void setOverScrollMode(int mode) {
        super.setOverScrollMode(mode);
        if (mode == View.OVER_SCROLL_NEVER) {
            edgeEffectHandler = null;
        } else {
            EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, this);
            if (edgeEffectHandler != null) {
                this.edgeEffectHandler = edgeEffectHandler;
            }
        }
    }

    @Override
    public int getEdgeEffectShift(boolean top) {
        return shift != null ? shift.getEdgeEffectShift(top) : obtainEdgeEffectShift(top);
    }

    public final int obtainEdgeEffectShift(boolean top) {
        return top ? -getTopPaddingOffset() : getBottomPaddingOffset();
    }

    private Object getFastScroll() {
        try {
            Field field = AbsListView.class.getDeclaredField(C.API_LOLLIPOP ? "mFastScroll" : "mFastScroller");
            field.setAccessible(true);
            return field.get(this);
        } catch (Exception e) {
            // Reflective operation, ignore exception
        }
        return null;
    }

    private boolean fastScrollModified = false;

    @Override
    public void setFastScrollEnabled(boolean enabled) {
        super.setFastScrollEnabled(enabled);
        if (enabled && !fastScrollModified) {
            Object fastScroll = getFastScroll();
            if (fastScroll != null) {
                fastScrollModified = true;
                try {
                    Field field = fastScroll.getClass().getDeclaredField("mMinimumTouchTarget");
                    field.setAccessible(true);
                    field.setInt(fastScroll, field.getInt(fastScroll) / 3);
                } catch (Exception e) {
                    // Reflective operation, ignore exception
                }
            }
        }
    }

    private boolean fastScrollIntercept = false;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (super.onInterceptTouchEvent(ev)) {
            return true;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            boolean intercept = false;
            if (isFastScrollEnabled()) {
                // Fast scroll has higher priority, than click to child views
                boolean calculateManually = true;
                Object fastScroll = getFastScroll();
                if (fastScroll != null) {
                    try {
                        Method method = fastScroll.getClass().getDeclaredMethod("isPointInsideX", float.class);
                        method.setAccessible(true);
                        intercept = (boolean) method.invoke(fastScroll, ev.getX());
                        calculateManually = false;
                    } catch (Exception e) {
                        // Reflective operation, ignore exception
                    }
                }
                if (calculateManually) {
                    intercept = getWidth() - ev.getX() < (int) (30f * ResourceUtils.obtainDensity(this));
                }
            }
            fastScrollIntercept = intercept;
        }
        return fastScrollIntercept;
    }
}
