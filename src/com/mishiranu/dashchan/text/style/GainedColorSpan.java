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

package com.mishiranu.dashchan.text.style;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class GainedColorSpan extends CharacterStyle implements UpdateAppearance, ColorScheme.Span {
    private final int foregroundColor;
    private float colorGainFactor;

    public GainedColorSpan(int foregroundColor) {
        this.foregroundColor = foregroundColor;
    }

    @Override
    public void applyColorScheme(ColorScheme colorScheme) {
        if (colorScheme != null) {
            colorGainFactor = colorScheme.colorGainFactor;
        }
    }

    public int getForegroundColor() {
        return foregroundColor;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        if (paint.getColor() != Color.TRANSPARENT) {
            paint.setColor(GraphicsUtils.modifyColorGain(foregroundColor, colorGainFactor));
        }
    }
}
