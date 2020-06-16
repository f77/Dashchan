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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

import java.lang.ref.WeakReference;

public class PullableWrapper implements AbsListView.OnScrollListener {
    private final Wrapped listView;
    private final PullView topView, bottomView;

    private final float pullDeltaGain;

    public enum Side {NONE, BOTH, TOP, BOTTOM}

    public PullableWrapper(Wrapped listView) {
        this.listView = listView;
        Context context = listView.getContext();
        topView = C.API_LOLLIPOP ? new LollipopView(listView, true) : new JellyBeanView(listView, true);
        bottomView = C.API_LOLLIPOP ? new LollipopView(listView, false) : new JellyBeanView(listView, false);
        pullDeltaGain = ResourceUtils.isTablet(context.getResources().getConfiguration()) ? 6f : 4f;
    }

    public void handleAttrs(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray typedArray = listView.getContext().obtainStyledAttributes(attrs,
                new int[]{android.R.attr.color}, defStyleAttr, defStyleRes);
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        setColor(color);
    }

    public void setColor(int color) {
        topView.setColor(color);
        bottomView.setColor(color);
    }

    public interface PullCallback {
        public void onListPulled(PullableWrapper wrapper, Side side);
    }

    public interface PullStateListener {
        public void onPullStateChanged(PullableWrapper wrapper, boolean busy);
    }

    private PullCallback pullCallback;
    private PullStateListener pullStateListener;

    public void setOnPullListener(PullCallback callback) {
        this.pullCallback = callback;
    }

    public void setPullStateListener(PullStateListener listener) {
        this.pullStateListener = listener;
    }

    private Side pullSides = Side.NONE;
    private Side busySide = Side.NONE;

    public void setPullSides(Side sides) {
        if (sides == null) {
            sides = Side.NONE;
        }
        pullSides = sides;
    }

    public void startBusyState(Side side) {
        startBusyState(side, false);
    }

    private PullView getSidePullView(Side side) {
        return side == Side.TOP ? topView : side == Side.BOTTOM ? bottomView : null;
    }

    private boolean startBusyState(Side side, boolean useCallback) {
        if (side == null || side == Side.NONE) {
            return false;
        }
        if (busySide != Side.NONE || side != pullSides && pullSides != Side.BOTH) {
            if (side == Side.BOTH && (busySide == Side.TOP || busySide == Side.BOTTOM)) {
                PullView pullView = getSidePullView(busySide);
                pullView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(side == Side.TOP));
                busySide = Side.BOTH;
            }
            return false;
        }
        busySide = side;
        PullView pullView = getSidePullView(side);
        if (pullView != null) {
            pullView.setState(PullView.State.LOADING, listView.getEdgeEffectShift(side == Side.TOP));
        }
        if (useCallback) {
            pullCallback.onListPulled(this, side);
        }
        notifyPullStateChanged(true);
        return true;
    }

    public void cancelBusyState() {
        if (busySide != Side.NONE) {
            busySide = Side.NONE;
            topView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(true));
            bottomView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(false));
            notifyPullStateChanged(false);
            updateStartY = true;
        }
    }

    private void notifyPullStateChanged(boolean busy) {
        if (pullStateListener != null) {
            pullStateListener.onPullStateChanged(this, busy);
        }
    }

    private boolean updateStartY = true;
    private float startY;

    private int deltaToPullStrain(float delta) {
        return (int) (pullDeltaGain * delta / listView.getHeight() * PullView.MAX_STRAIN);
    }

    private int pullStrainToDelta(int pullStrain) {
        return (int) (pullStrain * listView.getHeight() / (pullDeltaGain * PullView.MAX_STRAIN));
    }

    // Used to calculate list transition animation.
    private long topJumpStartTime;
    private long bottomJumpStartTime;

    private static final int BUSY_JUMP_TIME = 200;

    public void onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN || !scrolledToTop && !scrolledToBottom) {
            startY = ev.getY();
        } else if (updateStartY) {
            int hsize = ev.getHistorySize();
            startY = hsize > 0 ? ev.getHistoricalY(hsize - 1) : ev.getY();
        }
        updateStartY = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (busySide == Side.NONE) {
            if (action != MotionEvent.ACTION_DOWN) {
                float dy = ev.getY() - startY;
                boolean resetTop = true, resetBottom = true;
                EdgeEffectHandler edgeEffectHandler = listView.getEdgeEffectHandler();
                if (edgeEffectHandler != null) {
                    edgeEffectHandler.setPullable(true, true);
                    edgeEffectHandler.setPullable(false, true);
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    // Call getIdlePullStrain to get previous transient value
                    if (dy > 0 && scrolledToTop && (pullSides == Side.BOTH || pullSides == Side.TOP)) {
                        resetTop = false;
                        int pullStrain = topView.getAndResetIdlePullStrain();
                        if (pullStrain > 0) {
                            startY -= pullStrainToDelta(pullStrain);
                            dy = ev.getY() - startY;
                        }
                        int padding = listView.getEdgeEffectShift(true);
                        topView.setState(PullView.State.PULL, padding);
                        topView.setPullStrain(deltaToPullStrain(dy), padding);
                        if (edgeEffectHandler != null) {
                            edgeEffectHandler.finish(true);
                            edgeEffectHandler.setPullable(true, false);
                        }
                    } else if (dy < 0 && scrolledToBottom && (pullSides == Side.BOTH || pullSides == Side.BOTTOM)) {
                        resetBottom = false;
                        int pullStrain = bottomView.getAndResetIdlePullStrain();
                        if (pullStrain > 0) {
                            startY += pullStrainToDelta(pullStrain);
                            dy = ev.getY() - startY;
                        }
                        int padding = listView.getEdgeEffectShift(false);
                        bottomView.setState(PullView.State.PULL, padding);
                        bottomView.setPullStrain(-deltaToPullStrain(dy), padding);
                        if (edgeEffectHandler != null) {
                            edgeEffectHandler.finish(false);
                            edgeEffectHandler.setPullable(false, false);
                        }
                    }
                }
                int topPullStrain = topView.getPullStrain();
                int bottomPullStrain = bottomView.getPullStrain();
                if (resetTop && resetBottom && (topPullStrain > 0 || bottomPullStrain > 0)) {
                    if (topPullStrain > bottomPullStrain) {
                        topJumpStartTime = topView.calculateJumpStartTime();
                    } else {
                        bottomJumpStartTime = bottomView.calculateJumpStartTime();
                    }
                }
                if (action == MotionEvent.ACTION_UP) {
                    if (topPullStrain >= PullView.MAX_STRAIN) {
                        topJumpStartTime = System.currentTimeMillis();
                        boolean success = startBusyState(Side.TOP, true);
                        resetTop &= !success;
                    }
                    if (bottomPullStrain >= PullView.MAX_STRAIN) {
                        bottomJumpStartTime = System.currentTimeMillis();
                        boolean success = startBusyState(Side.BOTTOM, true);
                        resetBottom &= !success;
                    }
                }
                if (resetTop) {
                    topView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(true));
                }
                if (resetBottom) {
                    bottomView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(false));
                }
            }
        }
    }

    // States of list, can be defined only in scroll listener.
    private boolean scrolledToTop = true, scrolledToBottom = true;

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        try {
            scrolledToTop = totalItemCount == 0 || firstVisibleItem == 0 &&
                    view.getChildAt(0).getTop() >= listView.getEdgeEffectShift(true);
        } catch (Exception e) {
            scrolledToTop = false;
        }
        try {
            scrolledToBottom = totalItemCount == 0 || firstVisibleItem + visibleItemCount == totalItemCount &&
                    view.getChildAt(visibleItemCount - 1).getBottom() <= view.getHeight() -
                            listView.getEdgeEffectShift(false);
        } catch (Exception e) {
            scrolledToBottom = false;
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    private int lastShiftValue = 0;
    private int beforeShiftValue = 0;
    private boolean beforeRestoreCanvas = false;

    public void drawBefore(Canvas canvas) {
        int top = topView.calculateJumpValue(topJumpStartTime);
        int bottom = bottomView.calculateJumpValue(bottomJumpStartTime);
        int shift = top > bottom ? top : -bottom;
        if (shift != 0) {
            canvas.save();
            float height = listView.getHeight();
            float dy = (float) (Math.pow(Math.abs(pullStrainToDelta(shift)) / height, 2.5f))
                    * height * Math.signum(shift);
            canvas.translate(0, dy);
            beforeRestoreCanvas = true;
        }
        beforeShiftValue = shift;
    }

    public void drawAfter(Canvas canvas) {
        if (beforeRestoreCanvas) {
            beforeRestoreCanvas = false;
            canvas.restore();
        }
        int shift = beforeShiftValue;
        float density = ResourceUtils.obtainDensity(listView.getResources());
        topView.draw(canvas, listView.getEdgeEffectShift(true), density);
        bottomView.draw(canvas, listView.getEdgeEffectShift(false), density);
        if (lastShiftValue != shift) {
            lastShiftValue = shift;
            listView.invalidate();
        }
    }

    private interface PullView {
        public enum State {IDLE, PULL, LOADING}

        public static final int MAX_STRAIN = 1000;

        public void setColor(int color);

        public void setState(State state, int padding);

        public void setPullStrain(int pullStrain, int padding);

        public int getPullStrain();

        public int getAndResetIdlePullStrain();

        public void draw(Canvas canvas, int padding, float density);

        public long calculateJumpStartTime();

        public int calculateJumpValue(long jumpStartTime);
    }

    private static class JellyBeanView implements PullView {
        private static final int IDLE_FOLD_TIME = 500;
        private static final int LOADING_HALF_CYCLE_TIME = 600;

        private final WeakReference<Wrapped> wrapped;
        private final Paint paint = new Paint();
        private final int height;
        private final boolean top;

        private State previousState = State.IDLE;
        private State state = State.IDLE;

        private int startIdlePullStrain = 0;
        private long timeIdleStart = 0L;
        private long timeLoadingStart = 0L;
        private long timeLoadingToIdleStart = 0L;

        private int pullStrain = 0;

        private int color;

        public JellyBeanView(Wrapped wrapped, boolean top) {
            this.wrapped = new WeakReference<>(wrapped);
            this.height = (int) (3f * ResourceUtils.obtainDensity(wrapped.getContext()) + 0.5f);
            this.top = top;
        }

        @Override
        public void setColor(int color) {
            this.color = color;
        }

        private void invalidate(int padding) {
            Wrapped wrapped = this.wrapped.get();
            if (wrapped != null) {
                int offset = top ? padding : wrapped.getHeight() - height - padding;
                invalidate(0, offset, wrapped.getWidth(), offset + height);
            }
        }

        private void invalidate(int l, int t, int r, int b) {
            Wrapped wrapped = this.wrapped.get();
            if (wrapped != null) {
                wrapped.invalidate(l, t, r, b);
            }
        }

        @Override
        public void setState(State state, int padding) {
            if (this.state != state) {
                State prePreviousState = previousState;
                previousState = this.state;
                this.state = state;
                long time = System.currentTimeMillis();
                switch (this.state) {
                    case IDLE: {
                        timeIdleStart = time;
                        if (previousState == State.LOADING) {
                            timeLoadingToIdleStart = time;
                        }
                        startIdlePullStrain = previousState == State.LOADING ? 0 : pullStrain;
                        pullStrain = 0;
                        break;
                    }
                    case PULL: {
                        break;
                    }
                    case LOADING: {
                        // May continue use old animation until it over
                        boolean loadingToLoading = prePreviousState == State.LOADING &&
                                previousState == State.IDLE && time - timeIdleStart < LOADING_HALF_CYCLE_TIME;
                        if (!loadingToLoading) {
                            timeLoadingStart = previousState == State.IDLE ? time + LOADING_HALF_CYCLE_TIME : time;
                        }
                        timeLoadingToIdleStart = 0L;
                        break;
                    }
                }
                invalidate(padding);
            }
        }

        @Override
        public void setPullStrain(int pullStrain, int padding) {
            this.pullStrain = pullStrain;
            if (this.pullStrain > MAX_STRAIN) {
                this.pullStrain = MAX_STRAIN;
            } else if (this.pullStrain < 0) {
                this.pullStrain = 0;
            }
            if (state == State.PULL) {
                invalidate(padding);
            }
        }

        @Override
        public int getPullStrain() {
            return pullStrain;
        }

        @Override
        public int getAndResetIdlePullStrain() {
            if (startIdlePullStrain == 0) {
                return 0;
            }
            try {
                return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(System.currentTimeMillis()));
            } finally {
                startIdlePullStrain = 0;
            }
        }

        private float getIdleTransientPullStrainValue(long time) {
            int foldTime = IDLE_FOLD_TIME * startIdlePullStrain / MAX_STRAIN;
            if (foldTime <= 0) {
                return 0f;
            }
            float value = Math.min((float) (time - timeIdleStart) / foldTime, 1f);
            return (1f - value) * startIdlePullStrain / MAX_STRAIN;
        }

        @Override
        public void draw(Canvas canvas, int padding, float density) {
            Wrapped wrapped = this.wrapped.get();
            if (wrapped == null) {
                return;
            }
            Paint paint = this.paint;
            long time = System.currentTimeMillis();
            int width = wrapped.getWidth();
            int height = this.height;
            int offset = top ? padding : wrapped.getHeight() - height - padding;
            State state = this.state;
            State previousState = this.previousState;
            int primaryColor = color;
            int secondaryColor = 0x80 << 24 | 0x00ffffff & color;
            boolean needInvalidate = false;

            if (state == State.PULL) {
                int size = (int) (width / 2f * Math.pow((float) pullStrain / MAX_STRAIN, 2f));
                paint.setColor(primaryColor);
                canvas.drawRect(width / 2 - size, offset, width / 2 + size, offset + height, paint);
            }

            if (state == State.IDLE && previousState != State.LOADING) {
                float value = getIdleTransientPullStrainValue(time);
                int size = (int) (width / 2f * Math.pow(value, 4f));
                paint.setColor(primaryColor);
                canvas.drawRect(width / 2 - size, offset, width / 2 + size, offset + height, paint);
                if (value != 0) {
                    needInvalidate = true;
                }
            }

            if (state == State.LOADING || timeLoadingToIdleStart > 0L) {
                Interpolator interpolator = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR;
                final int cycle = 2 * LOADING_HALF_CYCLE_TIME;
                final int half = LOADING_HALF_CYCLE_TIME;
                long elapsed = time - timeLoadingStart;
                boolean startTransient = elapsed < 0;
                if (startTransient) {
                    elapsed += cycle;
                }
                int phase = (int) (elapsed % cycle);
                int partWidth;
                if (state != State.LOADING) {
                    long elapsedIdle = time - timeLoadingToIdleStart;
                    float value = Math.min((float) elapsedIdle / half, 1f);
                    partWidth = (int) (width / 2f * (1f - interpolator.getInterpolation(value)));
                    if (partWidth <= 0) {
                        partWidth = 0;
                        timeLoadingToIdleStart = 0L;
                    }
                } else {
                    partWidth = (int) (width / 2f);
                }
                if (!startTransient) {
                    paint.setColor(secondaryColor);
                    canvas.drawRect(0, offset, partWidth, offset + height, paint);
                    canvas.drawRect(width - partWidth, offset, width, offset + height, paint);
                }
                paint.setColor(primaryColor);
                if (phase <= half) {
                    float value = (float) phase / half;
                    int size = (int) (width / 2f * interpolator.getInterpolation(value));
                    int left = Math.min(width / 2 - size, partWidth);
                    int right = Math.max(width / 2 + size, width - partWidth);
                    canvas.drawRect(0, offset, left, offset + height, paint);
                    canvas.drawRect(right, offset, width, offset + height, paint);
                } else {
                    float value = (float) (phase - half) / half;
                    int size = (int) (width / 2f * interpolator.getInterpolation(value));
                    int left = width / 2 - size;
                    int right = width / 2 + size;
                    if (left < partWidth) {
                        canvas.drawRect(left, offset, partWidth, offset + height, paint);
                        canvas.drawRect(width - partWidth, offset, right, offset + height, paint);
                    }
                }
                needInvalidate = true;
            }

            if (needInvalidate) {
                invalidate(0, offset, width, offset + height);
            }
        }

        @Override
        public long calculateJumpStartTime() {
            return System.currentTimeMillis() - BUSY_JUMP_TIME * (MAX_STRAIN - pullStrain) / MAX_STRAIN;
        }

        @Override
        public int calculateJumpValue(long jumpStartTime) {
            int value = 0;
            switch (state) {
                case PULL: {
                    value = pullStrain;
                    break;
                }
                case IDLE:
                case LOADING: {
                    if (jumpStartTime > 0) {
                        value = (int) (MAX_STRAIN * (System.currentTimeMillis() - jumpStartTime) / BUSY_JUMP_TIME);
                        value = value < MAX_STRAIN ? MAX_STRAIN - value : 0;
                    }
                    break;
                }
            }
            return value;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static class LollipopView implements PullView {
        private static final int IDLE_FOLD_TIME = 100;
        private static final int LOADING_FOLD_TIME = 150;
        private static final int FULL_CYCLE_TIME = 6665;

        private static final int CIRCLE_RADIUS = 20;
        private static final int DEFAULT_CIRCLE_TARGET = 64;
        private static final float CENTER_RADIUS = 8.75f;
        private static final float STROKE_WIDTH = 2.5f;

        private final WeakReference<Wrapped> wrapped;
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rectF = new RectF();
        private final boolean top;

        private final Interpolator startInterpolator;
        private final Interpolator endInterpolator;

        private State previousState = State.IDLE;
        private State state = State.IDLE;

        private int startFoldingPullStrain = 0;
        private long timeStateStart = 0L;
        private long timeSpinStart = 0L;
        private float spinOffset = 0f;

        private int pullStrain = 0;

        public LollipopView(Wrapped wrapped, boolean top) {
            this.wrapped = new WeakReference<>(wrapped);
            this.top = top;
            Path startPath = new Path();
            startPath.lineTo(0.5f, 0f);
            startPath.cubicTo(0.7f, 0f, 0.6f, 1f, 1f, 1f);
            startInterpolator = new PathInterpolator(startPath);
            Path endPath = new Path();
            endPath.cubicTo(0.2f, 0f, 0.1f, 1f, 0.5f, 1f);
            endPath.lineTo(1f, 1f);
            endInterpolator = new PathInterpolator(endPath);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.SQUARE);
            ringPaint.setStrokeJoin(Paint.Join.MITER);
        }

        @Override
        public void setColor(int color) {
            circlePaint.setColor(color);
        }

        private void invalidate(int padding) {
            Wrapped wrapped = this.wrapped.get();
            if (wrapped != null) {
                float density = ResourceUtils.obtainDensity(wrapped.getContext());
                float commonShift = DEFAULT_CIRCLE_TARGET * density;
                float radius = CIRCLE_RADIUS * density;
                invalidate(wrapped, wrapped.getWidth(), radius, commonShift, padding);
            }
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        private void invalidate(Wrapped wrapped, int width, float radius, float commonShift, int padding) {
            int hw = width / 2;
            int l = hw - (int) radius - 1;
            int r = hw + (int) radius + 1;
            int t = padding - (int) (2f * radius) - 1;
            int b = padding + (int) (2f * commonShift) + 1;
            if (!top) {
                int height = wrapped.getHeight();
                int nt = height - t;
                int nb = height - b;
                t = nb;
                b = nt;
            }
            wrapped.invalidate(l, t, r, b);
        }

        @Override
        public void setState(State state, int padding) {
            if (this.state != state) {
                State prePreviousState = previousState;
                previousState = this.state;
                this.state = state;
                long time = System.currentTimeMillis();
                switch (this.state) {
                    case IDLE: {
                        timeStateStart = time;
                        startFoldingPullStrain = previousState == State.LOADING ? MAX_STRAIN : pullStrain;
                        pullStrain = 0;
                        break;
                    }
                    case PULL: {
                        break;
                    }
                    case LOADING: {
                        // May continue use old animation until it over
                        boolean loadingToLoading = prePreviousState == State.LOADING &&
                                previousState == State.IDLE && time - timeStateStart < 50;
                        if (!loadingToLoading) {
                            timeStateStart = time;
                            startFoldingPullStrain = pullStrain;
                            timeSpinStart = previousState == State.IDLE ? time : time - FULL_CYCLE_TIME / 10;
                            if (previousState == State.IDLE) {
                                spinOffset = 0f;
                            }
                        }
                        break;
                    }
                }
                invalidate(padding);
            }
        }

        @Override
        public void setPullStrain(int pullStrain, int padding) {
            this.pullStrain = pullStrain;
            if (this.pullStrain > 2 * MAX_STRAIN) {
                this.pullStrain = 2 * MAX_STRAIN;
            } else if (this.pullStrain < 0) {
                this.pullStrain = 0;
            }
            if (state == State.PULL) {
                invalidate(padding);
            }
        }

        @Override
        public int getPullStrain() {
            return pullStrain > MAX_STRAIN ? MAX_STRAIN : pullStrain;
        }

        @Override
        public int getAndResetIdlePullStrain() {
            if (startFoldingPullStrain == 0) {
                return 0;
            }
            try {
                return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(IDLE_FOLD_TIME, System.currentTimeMillis()));
            } finally {
                startFoldingPullStrain = 0;
            }
        }

        private float getIdleTransientPullStrainValue(int maxFoldTime, long time) {
            int foldTime = maxFoldTime * startFoldingPullStrain / MAX_STRAIN;
            if (foldTime <= 0) {
                return 0f;
            }
            float value = Math.min((float) (time - timeStateStart) / foldTime, 1f);
            return (1f - value) * startFoldingPullStrain / MAX_STRAIN;
        }

        @Override
        public void draw(Canvas canvas, int padding, float density) {
            Wrapped wrapped = this.wrapped.get();
            if (wrapped == null) {
                return;
            }
            Paint circlePaint = this.circlePaint;
            Paint ringPaint = this.ringPaint;
            long time = System.currentTimeMillis();
            int width = wrapped.getWidth();
            int height = wrapped.getHeight();
            State state = this.state;
            State previousState = this.previousState;
            boolean needInvalidate = false;
            float commonShift = DEFAULT_CIRCLE_TARGET * density;
            float radius = CIRCLE_RADIUS * density;

            float value = 0f;
            float scale = 1f;
            boolean spin = false;

            if (state == State.PULL) {
                value = (float) pullStrain / MAX_STRAIN;
            }

            if (state == State.IDLE) {
                if (previousState == State.LOADING) {
                    value = getIdleTransientPullStrainValue(LOADING_FOLD_TIME, time);
                    if (value > 0f) {
                        scale = value;
                        value = 1f;
                        spin = true;
                        needInvalidate = true;
                    }
                } else {
                    value = getIdleTransientPullStrainValue(IDLE_FOLD_TIME, time);
                    if (value != 0f) {
                        needInvalidate = true;
                    }
                }
            }

            if (state == State.LOADING) {
                value = getIdleTransientPullStrainValue(IDLE_FOLD_TIME, time);
                if (value <= 1f) {
                    value = 1f;
                }
                spin = true;
                if (previousState == State.IDLE) {
                    scale = Math.min((float) (time - timeStateStart) / LOADING_FOLD_TIME, 1f);
                }
                needInvalidate = true;
            }

            if (value != 0f) {
                int shift = (int) (commonShift * value) + padding;
                if (!top) {
                    shift = height - shift;
                }
                float centerX = width / 2f;
                float centerY = top ? shift - radius : shift + radius;
                boolean needRestore = false;
                if (scale != 1f && scale != 0f) {
                    canvas.save();
                    canvas.scale(scale, scale, centerX, centerY);
                    needRestore = true;
                }
                float ringRadius = CENTER_RADIUS * density;
                ringPaint.setStrokeWidth(STROKE_WIDTH * density);
                canvas.drawCircle(centerX, centerY, radius, circlePaint);

                float arcStart;
                float arcLength;
                int ringAlpha = 0xff;
                if (spin) {
                    float rotationValue = (float) ((time - timeSpinStart) % FULL_CYCLE_TIME) / FULL_CYCLE_TIME;
                    float animationValue = rotationValue * 5f % 1f;
                    float trimOffset = 0.25f * animationValue;
                    float trimStart = 0.75f * startInterpolator.getInterpolation(animationValue) + trimOffset;
                    float trimEnd = 0.75f * endInterpolator.getInterpolation(animationValue) + trimOffset;
                    float rotation = 2f * rotationValue;
                    arcStart = trimStart;
                    arcLength = trimEnd - arcStart;
                    arcStart += rotation + spinOffset;
                } else {
                    float alphaThreshold = 0.95f;
                    ringAlpha = (int) AnimationUtils.lerp(0x7f, 0xff, (Math.min(1f, Math.max(value, alphaThreshold))
                            - alphaThreshold) / (1f - alphaThreshold));
                    arcStart = value > 1f ? 0.25f + (value - 1f) * 0.5f : 0.25f * value;
                    arcLength = 0.75f * (float) Math.pow(Math.min(value, 1f), 0.75f);
                    if (value >= 1f) {
                        spinOffset = (value - 1f) * 0.5f;
                    }
                }
                ringPaint.setColor(0xffffff | (ringAlpha << 24));
                RectF size = rectF;
                size.set(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius);
                drawArc(canvas, ringPaint, size, arcStart, arcLength);
                if (needRestore) {
                    canvas.restore();
                }
            }

            if (needInvalidate) {
                invalidate(wrapped, width, radius, commonShift, padding);
            }
        }

        private void drawArc(Canvas canvas, Paint paint, RectF size, float start, float length) {
            if (length < 0.001f) {
                length = 0.001f;
            }
            Path path = this.path;
            path.reset();
            if (length >= 1f) {
                path.arcTo(size, 0f, 180f, false);
                path.arcTo(size, 180f, 180f, false);
            } else {
                path.arcTo(size, start * 360f - 90f, length * 360f, false);
            }
            canvas.drawPath(path, paint);
        }

        @Override
        public long calculateJumpStartTime() {
            return 0L;
        }

        @Override
        public int calculateJumpValue(long jumpStartTime) {
            return 0;
        }
    }

    public interface Wrapped extends EdgeEffectHandler.Shift {
        public Context getContext();

        public Resources getResources();

        public EdgeEffectHandler getEdgeEffectHandler();

        public void invalidate(int l, int t, int r, int b);

        public void invalidate();

        public int getWidth();

        public int getHeight();
    }
}
