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

package com.mishiranu.dashchan.content.async;

import chan.http.HttpHolder;

@SuppressWarnings("unchecked")
public abstract class HttpHolderTask<Params, Progress, Result> extends CancellableTask<Params, Progress, Result> {
    private final HttpHolder holder = new HttpHolder();

    @Override
    protected final Result doInBackground(Params... params) {
        try {
            return doInBackground(holder, params);
        } finally {
            holder.cleanup();
        }
    }

    protected abstract Result doInBackground(HttpHolder holder, Params... params);

    @Override
    public void cancel() {
        cancel(true);
        holder.interrupt();
    }
}
