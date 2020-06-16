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

package com.mishiranu.dashchan.content;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.storage.DatabaseHelper;
import com.mishiranu.dashchan.util.Log;

import chan.content.ChanManager;
import chan.http.HttpClient;

public class MainApplication extends Application {
    private static MainApplication instance;

    public MainApplication() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.init(this);
        // Init
        ChanManager.getInstance();
        HttpClient.getInstance();
        DatabaseHelper.getInstance();
        CacheManager.getInstance();
        LocaleManager.getInstance().apply(this, false);
        ChanManager.getInstance().loadLibraries();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleManager.getInstance().apply(this, true);
    }

    public static MainApplication getInstance() {
        return instance;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public boolean isLowRam() {
        if (C.API_KITKAT) {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            return activityManager != null && activityManager.isLowRamDevice();
        } else {
            return Runtime.getRuntime().maxMemory() <= 64 * 1024 * 1024;
        }
    }
}
