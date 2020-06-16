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

package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.util.WebViewUtils;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

public class WebBrowserActivity extends StateActivity implements DownloadListener {
    private WebView webView;
    private ProgressView progressView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ResourceUtils.applyPreferredTheme(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.action_browser);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        ViewUtils.applyToolbarStyle(this, null);
        WebView webView = new WebView(this);
        this.webView = webView;
        WebSettings settings = webView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());
        progressView = new ProgressView(this);
        float density = ResourceUtils.obtainDensity(this);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        frameLayout.addView(progressView, FrameLayout.LayoutParams.MATCH_PARENT, (int) (3f * density + 0.5f));
        setContentView(frameLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setDownloadListener(this);
        WebViewUtils.clearAll(webView);
        webView.loadUrl(getIntent().getData().toString());
        registerForContextMenu(webView);
    }

    @Override
    protected void onFinish() {
        super.onFinish();
        webView.stopLoading();
        ViewUtils.removeFromParent(webView);
        WebViewUtils.clearAll(webView);
        webView.destroy();
    }

    private static final int OPTIONS_MENU_RELOAD = 0;
    private static final int OPTIONS_MENU_COPY_LINK = 1;
    private static final int OPTIONS_MENU_SHARE_LINK = 2;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        ActionIconSet set = new ActionIconSet(this);
        menu.add(0, OPTIONS_MENU_RELOAD, 0, R.string.action_reload).setIcon(set.getId(R.attr.actionRefresh))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, OPTIONS_MENU_COPY_LINK, 0, R.string.action_copy_link);
        menu.add(0, OPTIONS_MENU_SHARE_LINK, 0, R.string.action_share_link);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                finish();
                break;
            }
            case OPTIONS_MENU_RELOAD: {
                webView.reload();
                break;
            }
            case OPTIONS_MENU_COPY_LINK: {
                StringUtils.copyToClipboard(this, webView.getUrl());
                break;
            }
            case OPTIONS_MENU_SHARE_LINK: {
                String uriString = webView.getUrl();
                if (!StringUtils.isEmpty(uriString)) {
                    NavigationUtils.shareLink(this, null, Uri.parse(uriString));
                }
                break;
            }
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        WebView.HitTestResult hitTestResult = webView.getHitTestResult();
        switch (hitTestResult.getType()) {
            case WebView.HitTestResult.IMAGE_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE: {
                final Uri uri = Uri.parse(hitTestResult.getExtra());
                ChanLocator locator = ChanLocator.getDefault();
                if (locator.isWebScheme(uri) && locator.isImageExtension(uri.getPath())) {
                    menu.add(R.string.action_view).setOnMenuItemClickListener(item -> {
                        NavigationUtils.openImageVideo(WebBrowserActivity.this, uri, false);
                        return true;
                    });
                }
                break;
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewUtils.applyToolbarStyle(this, null);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
                                long contentLength) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            ToastUtils.show(this, R.string.message_unknown_address);
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String chanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
            if (chanName != null) {
                ChanLocator locator = ChanLocator.get(chanName);
                ChanLocator.NavigationData navigationData;
                if (locator.safe(true).isBoardUri(uri)) {
                    navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_THREADS,
                            locator.safe(true).getBoardName(uri), null, null, null);
                } else if (locator.safe(true).isThreadUri(uri)) {
                    navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.TARGET_POSTS,
                            locator.safe(true).getBoardName(uri), locator.safe(true).getThreadNumber(uri),
                            locator.safe(true).getPostNumber(uri), null);
                } else {
                    navigationData = locator.safe(true).handleUriClickSpecial(uri);
                }
                if (navigationData != null) {
                    new AlertDialog.Builder(WebBrowserActivity.this).setMessage(R.string.message_open_link_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                finish();
                                startActivity(NavigationUtils.obtainTargetIntent(WebBrowserActivity.this,
                                        chanName, navigationData, NavigationUtils.FLAG_RETURNABLE));
                            }).show();
                    return true;
                }
            }
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            String title = view.getTitle();
            if (StringUtils.isEmptyOrWhitespace(title)) {
                setTitle(R.string.action_browser);
            } else {
                setTitle(view.getTitle());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (Preferences.isVerifyCertificate()) {
                ToastUtils.show(WebBrowserActivity.this, R.string.message_invalid_certificate);
                super.onReceivedSslError(view, handler, error);
            } else {
                handler.proceed();
            }
        }
    }

    private class CustomWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressView.setProgress(newProgress);
        }
    }

    private static class ProgressView extends View {
        private static final int TRANSIENT_TIME = 200;

        private final Paint paint = new Paint();

        private long progressSetTime;
        private float transientProgress = 0f;
        private int progress = 0;

        public ProgressView(Context context) {
            super(context);
            int color = ResourceUtils.getColor(context, R.attr.colorAccentSupport);
            paint.setColor(Color.BLACK | color);
        }

        public void setProgress(int progress) {
            transientProgress = calculateTransient();
            progressSetTime = System.currentTimeMillis();
            this.progress = progress;
            invalidate();
        }

        private float getTime() {
            return Math.min((float) (System.currentTimeMillis() - progressSetTime) / TRANSIENT_TIME, 1f);
        }

        private float calculateTransient() {
            return AnimationUtils.lerp(transientProgress, progress, getTime());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Paint paint = this.paint;
            int progress = this.progress;
            float transientProgress = calculateTransient();
            int alpha = 0xff;
            boolean needInvalidate = transientProgress != progress;
            if (progress == 100) {
                float t = getTime();
                alpha = (int) (0xff * (1f - t));
                needInvalidate |= t < 1f;
            }
            paint.setAlpha(alpha);
            if (transientProgress > 0 && alpha > 0x00) {
                float width = getWidth() * transientProgress / 100;
                canvas.drawRect(0, 0, width, getHeight(), paint);
            }
            if (needInvalidate) {
                invalidate();
            }
        }
    }
}
