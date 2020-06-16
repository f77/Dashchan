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

package com.mishiranu.dashchan.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class SimpleBitmapDrawable extends Drawable {
    private final Bitmap bitmap;
    private final int width;
    private final int height;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public SimpleBitmapDrawable(Bitmap bitmap, int width, int height) {
        this.bitmap = bitmap;
        this.width = width;
        this.height = height;
    }

    public SimpleBitmapDrawable(Bitmap bitmap) {
        this(bitmap, bitmap.getWidth(), bitmap.getHeight());
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(bitmap, null, getBounds(), paint);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    public void recycle() {
        bitmap.recycle();
    }
}
