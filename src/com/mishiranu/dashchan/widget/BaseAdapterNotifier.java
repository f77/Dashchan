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

import android.os.Handler;
import android.widget.BaseAdapter;

public class BaseAdapterNotifier implements Runnable {
    private static final Handler HANDLER = new Handler();

    private final BaseAdapter adapter;

    public BaseAdapterNotifier(BaseAdapter adapter) {
        this.adapter = adapter;
    }

    public void postNotifyDataSetChanged() {
        HANDLER.removeCallbacks(this);
        HANDLER.post(this);
    }

    @Override
    public void run() {
        adapter.notifyDataSetChanged();
    }
}
