/*
 * Copyright (C) 2014 The Android Open Source Project
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
 *
 * ********************************************************************************
 *
 * Copyright 2014-2016 Fukurou Mishiranu
 */

package com.mishiranu.dashchan.util;

import java.lang.reflect.Field;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.customview.widget.ViewDragHelper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.mishiranu.dashchan.C;

public class DrawerToggle implements DrawerLayout.DrawerListener {
	private final Activity activity;
	private final DrawerLayout drawerLayout;

	private final ArrowDrawable arrowDrawable;
	private final SlideDrawable slideDrawable;
	private Drawable homeAsUpIndicator;

	public static final int MODE_DISABLED = 0;
	public static final int MODE_DRAWER = 1;
	public static final int MODE_UP = 2;

	private int mode = MODE_DISABLED;

	public DrawerToggle(Activity activity, DrawerLayout drawerLayout) {
		this.activity = activity;
		this.drawerLayout = drawerLayout;
		if (C.API_LOLLIPOP) {
			arrowDrawable = new ArrowDrawable(activity);
			slideDrawable = null;
		} else {
			arrowDrawable = null;
			homeAsUpIndicator = getThemeUpIndicatorObsolete();
			slideDrawable = new SlideDrawable(activity);
		}
	}

	private static final int DRAWER_CLOSE_DURATION;

	static {
		int duration;
		try {
			Field baseSettleDurationField = ViewDragHelper.class.getDeclaredField("BASE_SETTLE_DURATION");
			baseSettleDurationField.setAccessible(true);
			duration = (int) baseSettleDurationField.get(null);
		} catch (Exception e) {
			duration = 256;
		}
		DRAWER_CLOSE_DURATION = duration;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void setDrawerIndicatorMode(int mode) {
		if (this.mode != mode) {
			this.mode = mode;
			ActionBar actionBar = activity.getActionBar();
			if (mode == MODE_DISABLED) {
				if (C.API_JELLY_BEAN_MR2) {
					actionBar.setHomeAsUpIndicator(null);
				}
				actionBar.setDisplayHomeAsUpEnabled(false);
			} else {
				actionBar.setDisplayHomeAsUpEnabled(true);
				if (C.API_LOLLIPOP) {
					activity.getActionBar().setHomeAsUpIndicator(arrowDrawable);
					boolean open = drawerLayout.isDrawerOpen(Gravity.START) && arrowDrawable.position == 1f;
					if (!open) {
						ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
						animator.setDuration(DRAWER_CLOSE_DURATION);
						animator.addUpdateListener(new StateArrowAnimatorListener(mode == MODE_DRAWER));
						animator.start();
					}
				} else {
					setActionBarUpIndicatorObsolete(mode == MODE_DRAWER ? slideDrawable : homeAsUpIndicator);
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void syncState() {
		if (mode != MODE_DISABLED) {
			if (C.API_LOLLIPOP) {
				arrowDrawable.setPosition(mode == MODE_UP || drawerLayout.isDrawerOpen(Gravity.START) ? 1f : 0f);
				activity.getActionBar().setHomeAsUpIndicator(arrowDrawable);
			} else {
				slideDrawable.setPosition(drawerLayout.isDrawerOpen(Gravity.START) ? 1f : 0f);
				setActionBarUpIndicatorObsolete(mode == MODE_DRAWER ? slideDrawable : homeAsUpIndicator);
			}
		}
	}

	public void onConfigurationChanged() {
		if (!C.API_LOLLIPOP) {
			homeAsUpIndicator = getThemeUpIndicatorObsolete();
		}
		syncState();
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if (item != null && item.getItemId() == android.R.id.home) {
			if (drawerLayout.getDrawerLockMode(Gravity.START) != DrawerLayout.LOCK_MODE_UNLOCKED) {
				return false;
			}
			if (mode == MODE_DRAWER) {
				if (drawerLayout.isDrawerVisible(Gravity.START)) {
					drawerLayout.closeDrawer(Gravity.START);
				} else {
					drawerLayout.openDrawer(Gravity.START);
				}
				return true;
			} else if (mode == MODE_UP) {
				if (drawerLayout.isDrawerVisible(Gravity.START)) {
					drawerLayout.closeDrawer(Gravity.START);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onDrawerSlide(View drawerView, float slideOffset) {
		if (C.API_LOLLIPOP) {
			if (mode == MODE_DRAWER) {
				arrowDrawable.setPosition(slideOffset);
			}
		} else {
			float glyphOffset = slideDrawable.getPosition();
			if (slideOffset > 0.5f) {
				glyphOffset = Math.max(glyphOffset, Math.max(0.f, slideOffset - 0.5f) * 2);
			} else {
				glyphOffset = Math.min(glyphOffset, slideOffset * 2);
			}
			slideDrawable.setPosition(glyphOffset);
		}
	}

	@Override
	public void onDrawerOpened(View drawerView) {
		if (C.API_LOLLIPOP) {
			if (mode == MODE_DRAWER) {
				arrowDrawable.setPosition(1f);
			}
		} else {
			slideDrawable.setPosition(1);
		}
	}

	@Override
	public void onDrawerClosed(View drawerView) {
		if (C.API_LOLLIPOP) {
			if (mode == MODE_DRAWER) {
				arrowDrawable.setPosition(0f);
			}
		} else {
			slideDrawable.setPosition(0);
		}
	}

	@Override
	public void onDrawerStateChanged(int newState) {}

	private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);

	private class ArrowDrawable extends Drawable {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		private final float barThickness;
		private final float topBottomArrowSize;
		private final float barSize;
		private final float middleArrowSize;
		private final float barGap;
		private final int size;

		private boolean verticalMirror = false;
		private float position;

		public ArrowDrawable(Context context) {
			paint.setAntiAlias(true);
			paint.setColor(0xffffffff);
			float density = ResourceUtils.obtainDensity(context);
			size = (int) (24f * density);
			barSize = 16f * density;
			topBottomArrowSize = 9.5f * density;
			barThickness = 2f * density;
			barGap = 3f * density;
			middleArrowSize = 13.6f * density;
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setStrokeCap(Paint.Cap.SQUARE);
			paint.setStrokeWidth(barThickness);
		}

		public void setPosition(float position) {
			position = Math.min(1f, Math.max(0f, position));
			if (position == 1f) {
				verticalMirror = true;
			} else if (position == 0f) {
				verticalMirror = false;
			}
			this.position = position;
			invalidateSelf();
		}

		@Override
		public int getIntrinsicWidth() {
			return size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}

		@Override
		public void draw(Canvas canvas) {
			Rect bounds = getBounds();
			boolean rtl = isLayoutRtl();
			float position = this.position;
			float arrowSize = AnimationUtils.lerp(barSize, topBottomArrowSize, position);
			float middleBarSize = AnimationUtils.lerp(barSize, middleArrowSize, position);
			float middleBarCut = AnimationUtils.lerp(0f, barThickness / 2f, position);
			float rotation = AnimationUtils.lerp(0f, ARROW_HEAD_ANGLE, position);
			float canvasRotate = AnimationUtils.lerp(rtl ? 0f : -180f, rtl ? 180f : 0f, position);
			float topBottomBarOffset = AnimationUtils.lerp(barGap + barThickness, 0f, position);
			path.rewind();
			float arrowEdge = -middleBarSize / 2f + 0.5f;
			path.moveTo(arrowEdge + middleBarCut, 0f);
			path.rLineTo(middleBarSize - middleBarCut, 0f);
			float arrowWidth = Math.round(arrowSize * Math.cos(rotation));
			float arrowHeight = Math.round(arrowSize * Math.sin(rotation));
			path.moveTo(arrowEdge, topBottomBarOffset);
			path.rLineTo(arrowWidth, arrowHeight);
			path.moveTo(arrowEdge, -topBottomBarOffset);
			path.rLineTo(arrowWidth, -arrowHeight);
			path.moveTo(0f, 0f);
			path.close();
			canvas.save();
			canvas.rotate(canvasRotate * ((verticalMirror ^ rtl) ? -1f : 1f), bounds.centerX(), bounds.centerY());
			canvas.translate(bounds.centerX(), bounds.centerY());
			canvas.drawPath(path, paint);
			canvas.restore();
		}

		@Override
		public void setAlpha(int alpha) {}

		@Override
		public void setColorFilter(ColorFilter colorFilter) {}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	private class StateArrowAnimatorListener implements ValueAnimator.AnimatorUpdateListener {
		private final boolean enable;

		public StateArrowAnimatorListener(boolean enable) {
			this.enable = enable;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			float value = (float) animation.getAnimatedValue();
			arrowDrawable.setPosition(enable ? 1f - value : value);
		}
	}

	private class SlideDrawable extends Drawable {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final int size;

		private float position;

		private SlideDrawable(Context context) {
			paint.setColor(0xff979797);
			float density = ResourceUtils.obtainDensity(context);
			size = (int) (16f * density);
		}

		public void setPosition(float position) {
			this.position = position;
			invalidateSelf();
		}

		public float getPosition() {
			return position;
		}

		@Override
		public int getIntrinsicWidth() {
			return size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}

		@Override
		public void draw(Canvas canvas) {
			Rect bounds = getBounds();
			canvas.save();
			canvas.translate(bounds.left, bounds.top);
			boolean rtl = isLayoutRtl();
			if (rtl) {
				canvas.translate(bounds.width(), 0);
				canvas.scale(-1, 1);
			}
			canvas.scale(bounds.width() / 48f, bounds.height() / 48f);
			canvas.translate(-16f * position, 0);
			canvas.drawRect(0, 4, 30, 12, paint);
			canvas.drawRect(0, 22, 30, 30, paint);
			canvas.drawRect(0, 40, 30, 48, paint);
			canvas.restore();
		}

		@Override
		public void setAlpha(int alpha) {}

		@Override
		public void setColorFilter(ColorFilter colorFilter) {}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	private static final int[] THEME_ATTRS = new int[] {android.R.attr.homeAsUpIndicator};

	private Drawable getThemeUpIndicatorObsolete() {
		if (C.API_JELLY_BEAN_MR2) {
			TypedArray a = activity.getActionBar().getThemedContext().obtainStyledAttributes(null,
					THEME_ATTRS, android.R.attr.actionBarStyle, 0);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		} else {
			TypedArray a = activity.obtainStyledAttributes(THEME_ATTRS);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		}
	}

	private ImageView upIndicatorView;

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private void setActionBarUpIndicatorObsolete(Drawable upDrawable) {
		if (C.API_JELLY_BEAN_MR2) {
			activity.getActionBar().setHomeAsUpIndicator(upDrawable);
		} else {
			if (upIndicatorView == null) {
				View home = activity.findViewById(android.R.id.home);
				if (home == null) {
					return;
				}
				ViewGroup parent = (ViewGroup) home.getParent();
				int childCount = parent.getChildCount();
				if (childCount != 2) {
					return;
				}
				View first = parent.getChildAt(0);
				View second = parent.getChildAt(1);
				View up = first.getId() == android.R.id.home ? second : first;
				if (up instanceof ImageView) {
					upIndicatorView = (ImageView) up;
				}
			}
			if (upIndicatorView != null) {
				upIndicatorView.setImageDrawable(upDrawable);
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private boolean isLayoutRtl() {
		return C.API_JELLY_BEAN_MR1 ? activity.getWindow().getDecorView().getLayoutDirection()
				== View.LAYOUT_DIRECTION_RTL : false;
	}
}