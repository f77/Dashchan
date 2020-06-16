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

package com.mishiranu.dashchan.ui.gallery;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.ReadVideoTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.media.CachingInputStream;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.StringBlockBuilder;
import com.mishiranu.dashchan.util.ViewUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import chan.util.StringUtils;

public class VideoUnit implements AudioManager.OnAudioFocusChangeListener {
    private final PagerInstance instance;
    private final LinearLayout controlsView;
    private final AudioManager audioManager;

    private int layoutConfiguration = -1;
    private LinearLayout configurationView;
    private TextView timeTextView;
    private TextView totalTimeTextView;
    private SeekBar seekBar;
    private ImageButton playPauseButton;

    private VideoPlayer player;
    private BackgroundDrawable backgroundDrawable;
    private boolean initialized;
    private boolean wasPlaying;
    private boolean pausedByTransientLossOfFocus;
    private boolean finishedPlayback;
    private boolean trackingNow;
    private boolean hideSurfaceOnInit;

    private ReadVideoTask readVideoTask;

    public VideoUnit(PagerInstance instance) {
        this.instance = instance;
        controlsView = new LinearLayout(instance.galleryInstance.context);
        controlsView.setOrientation(LinearLayout.VERTICAL);
        controlsView.setVisibility(View.GONE);
        audioManager = (AudioManager) instance.galleryInstance.context.getSystemService(Activity.AUDIO_SERVICE);
    }

    public void addViews(FrameLayout frameLayout) {
        frameLayout.addView(controlsView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    public void onResume() {
        if (player != null && initialized) {
            setPlaying(wasPlaying, true);
            updatePlayState();
        } else {
            wasPlaying = true;
        }
    }

    public void onPause() {
        if (player != null && initialized) {
            wasPlaying = player.isPlaying();
            setPlaying(false, true);
        } else {
            wasPlaying = false;
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
            if (layoutConfiguration != -1) {
                recreateVideoControls();
            }
        }
    }

    public void onApplyWindowPaddings(Rect rect) {
        if (C.API_LOLLIPOP) {
            controlsView.setPadding(rect.left, 0, rect.right, rect.bottom);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isCreated() {
        return player != null;
    }

    public void interrupt() {
        if (readVideoTask != null) {
            readVideoTask.cancel();
            readVideoTask = null;
        }
        if (initialized) {
            audioManager.abandonAudioFocus(this);
            initialized = false;
        }
        invalidateControlsVisibility();
        if (player != null) {
            player.free();
            player = null;
            instance.currentHolder.progressBar.setVisible(false, false);
        }
        if (backgroundDrawable != null) {
            backgroundDrawable.recycle();
            backgroundDrawable = null;
        }
        interruptHolder(instance.leftHolder);
        interruptHolder(instance.currentHolder);
        interruptHolder(instance.rightHolder);
    }

    private void interruptHolder(PagerInstance.ViewHolder holder) {
        if (holder != null) {
            holder.surfaceParent.removeAllViews();
        }
    }

    public void forcePause() {
        if (initialized) {
            wasPlaying = false;
            setPlaying(false, true);
        }
    }

    public void applyVideo(Uri uri, File file, boolean reload) {
        wasPlaying = true;
        finishedPlayback = false;
        hideSurfaceOnInit = false;
        VideoPlayer player = new VideoPlayer(Preferences.isVideoSeekAnyFrame());
        player.setListener(playerListener);
        boolean loadedFromFile = false;
        if (!reload && file.exists()) {
            try {
                player.init(file);
                loadedFromFile = true;
            } catch (IOException e) {
                // Player was consumed, create a new one and try to download a new video file
                player = new VideoPlayer(Preferences.isVideoSeekAnyFrame());
            }
        }
        this.player = player;
        if (loadedFromFile) {
            initializePlayer();
            seekBar.setSecondaryProgress(seekBar.getMax());
            instance.currentHolder.fullLoaded = true;
            instance.galleryInstance.callback.invalidateOptionsMenu();
        } else {
            VideoPlayer finalPlayer = player;
            PagerInstance.ViewHolder holder = instance.currentHolder;
            holder.progressBar.setIndeterminate(true);
            holder.progressBar.setVisible(true, false);
            final CachingInputStream inputStream = new CachingInputStream();
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        finalPlayer.init(inputStream);
                        return true;
                    } catch (VideoPlayer.InitializationException e) {
                        Log.persistent().stack(e);
                        return false;
                    } catch (IOException e) {
                        return false;
                    }
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    if (VideoUnit.this.player != finalPlayer) {
                        return;
                    }
                    PagerInstance.ViewHolder holder = instance.currentHolder;
                    holder.progressBar.setVisible(false, false);
                    if (result) {
                        initializePlayer();
                        instance.galleryInstance.callback.invalidateOptionsMenu();
                        if (readVideoTask == null) {
                            seekBar.setSecondaryProgress(seekBar.getMax());
                        }
                    } else {
                        if (readVideoTask != null) {
                            if (!readVideoTask.isError()) {
                                readVideoTask.cancel();
                                readVideoTask = null;
                            } else {
                                return;
                            }
                        }
                        instance.callback.showError(holder, instance.galleryInstance.context
                                .getString(R.string.message_playback_error));
                    }
                }
            }.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
            readVideoTask = new ReadVideoTask(instance.galleryInstance.chanName, uri, inputStream,
                    new ReadVideoCallback(player, holder));
            readVideoTask.executeOnExecutor(ReadVideoTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (!initialized) {
            return;
        }
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS: {
                setPlaying(false, false);
                updatePlayState();
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                boolean playing = player.isPlaying();
                setPlaying(false, false);
                if (playing) {
                    pausedByTransientLossOfFocus = true;
                }
                updatePlayState();
                break;
            }
            case AudioManager.AUDIOFOCUS_GAIN: {
                if (pausedByTransientLossOfFocus) {
                    setPlaying(true, false);
                }
                updatePlayState();
                break;
            }
        }
    }

    private boolean setPlaying(boolean playing, boolean resetFocus) {
        if (player.isPlaying() != playing) {
            if (resetFocus && player.isAudioPresent()) {
                if (playing) {
                    if (audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                            != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        return false;
                    }
                } else {
                    audioManager.abandonAudioFocus(this);
                }
            }
            player.setPlaying(playing);
            pausedByTransientLossOfFocus = false;
        }
        return true;
    }

    private void initializePlayer() {
        PagerInstance.ViewHolder holder = instance.currentHolder;
        holder.progressBar.setVisible(false, false);
        Point dimensions = player.getDimensions();
        backgroundDrawable = new BackgroundDrawable();
        backgroundDrawable.width = dimensions.x;
        backgroundDrawable.height = dimensions.y;
        holder.recyclePhotoView();
        holder.photoView.setImage(backgroundDrawable, false, true, false);
        View videoView = player.getVideoView(instance.galleryInstance.context);
        holder.surfaceParent.addView(videoView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        recreateVideoControls();
        playPauseButton.setEnabled(true);
        seekBar.setEnabled(true);
        initialized = true;
        pausedByTransientLossOfFocus = false;
        if (hideSurfaceOnInit) {
            showHideVideoView(false);
        }
        invalidateControlsVisibility();
        setPlaying(wasPlaying, true);
        updatePlayState();
    }

    @SuppressLint("RtlHardcoded")
    private void recreateVideoControls() {
        Context context = instance.galleryInstance.context;
        float density = ResourceUtils.obtainDensity(context);
        int targetLayoutCounfiguration = ResourceUtils.isTabletOrLandscape(context.getResources()
                .getConfiguration()) ? 1 : 0;
        if (targetLayoutCounfiguration != layoutConfiguration) {
            boolean firstTimeLayout = layoutConfiguration < 0;
            layoutConfiguration = targetLayoutCounfiguration;
            boolean longLayout = targetLayoutCounfiguration == 1;

            controlsView.removeAllViews();
            if (seekBar != null) {
                seekBar.removeCallbacks(progressRunnable);
            }
            trackingNow = false;

            configurationView = new LinearLayout(context);
            configurationView.setOrientation(LinearLayout.HORIZONTAL);
            configurationView.setGravity(Gravity.RIGHT);
            configurationView.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
            controlsView.addView(configurationView, LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

            LinearLayout controls = new LinearLayout(context);
            controls.setOrientation(longLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            controls.setBackgroundColor(instance.galleryInstance.actionBarColor);
            controls.setPadding((int) (8f * density), longLayout ? 0 : (int) (8f * density), (int) (8f * density), 0);
            controls.setClickable(true);
            controlsView.addView(controls, LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

            CharSequence oldTimeText = timeTextView != null ? timeTextView.getText() : null;
            timeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
            timeTextView.setTextSize(14f);
            timeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            if (C.API_LOLLIPOP) {
                timeTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
            }
            if (oldTimeText != null) {
                timeTextView.setText(oldTimeText);
            }

            totalTimeTextView = new TextView(context, null, android.R.attr.textAppearanceListItem);
            totalTimeTextView.setTextSize(14f);
            totalTimeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            if (C.API_LOLLIPOP) {
                totalTimeTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
            }

            int oldSecondaryProgress = seekBar != null ? seekBar.getSecondaryProgress() : -1;
            seekBar = new SeekBar(context);
            seekBar.setOnSeekBarChangeListener(seekBarListener);
            if (oldSecondaryProgress >= 0) {
                seekBar.setSecondaryProgress(oldSecondaryProgress);
            }

            playPauseButton = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
            playPauseButton.setScaleType(ImageButton.ScaleType.CENTER);
            playPauseButton.setOnClickListener(playPauseClickListener);

            if (longLayout) {
                controls.setGravity(Gravity.CENTER_VERTICAL);
                controls.addView(timeTextView, (int) (48f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                controls.addView(seekBar, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                controls.addView(playPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                controls.addView(totalTimeTextView, (int) (48f * density),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
            } else {
                LinearLayout controls1 = new LinearLayout(context);
                controls1.setOrientation(LinearLayout.HORIZONTAL);
                controls1.setGravity(Gravity.CENTER_VERTICAL);
                controls1.setPadding(0, (int) (8f * density), 0, (int) (8f * density));
                LinearLayout controls2 = new LinearLayout(context);
                controls2.setOrientation(LinearLayout.HORIZONTAL);
                controls2.setGravity(Gravity.CENTER_VERTICAL);
                controls.addView(controls1, LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                controls.addView(controls2, LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                controls1.addView(seekBar, LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                controls2.addView(timeTextView, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                controls2.addView(playPauseButton, (int) (80f * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                controls2.addView(totalTimeTextView, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            if (firstTimeLayout) {
                AnimationUtils.measureDynamicHeight(controlsView);
                controlsView.setTranslationY(controlsView.getMeasuredHeight());
                controlsView.setAlpha(0f);
            }
        }
        if (player != null) {
            configurationView.removeAllViews();
            ActionIconSet set = null;
            if (!player.isAudioPresent()) {
                if (set == null) {
                    set = new ActionIconSet(context);
                }
                ImageView imageView = new ImageView(context);
                imageView.setImageResource(set.getId(R.attr.actionVolumeOff));
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                if (C.API_LOLLIPOP) {
                    imageView.setImageAlpha(0x99);
                }
                configurationView.addView(imageView, (int) (48f * density), (int) (48f * density));
            }
            totalTimeTextView.setText(formatVideoTime(player.getDuration()));
            seekBar.setMax((int) player.getDuration());
        }
        seekBar.removeCallbacks(progressRunnable);
        seekBar.post(progressRunnable);
        updatePlayState();
    }

    private static String formatVideoTime(long position) {
        position /= 1000;
        int m = (int) (position / 60 % 60);
        int s = (int) (position % 60);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private final View.OnClickListener playPauseClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (initialized) {
                if (finishedPlayback) {
                    finishedPlayback = false;
                    player.setPosition(0);
                    setPlaying(true, true);
                } else {
                    boolean playing = !player.isPlaying();
                    setPlaying(playing, true);
                }
                updatePlayState();
            }
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (initialized) {
                int position;
                if (trackingNow) {
                    position = seekBar.getProgress();
                } else {
                    position = (int) player.getPosition();
                    seekBar.setProgress(position);
                }
                timeTextView.setText(formatVideoTime(position));
            }
            seekBar.postDelayed(this, 200);
        }
    };

    private final SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
        private int nextSeekPosition;

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            trackingNow = false;
            seekBar.removeCallbacks(progressRunnable);
            if (nextSeekPosition != -1) {
                seekBar.setProgress(nextSeekPosition);
                player.setPosition(nextSeekPosition);
                seekBar.postDelayed(progressRunnable, 250);
                if (finishedPlayback) {
                    finishedPlayback = false;
                    updatePlayState();
                }
            } else {
                progressRunnable.run();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            trackingNow = true;
            seekBar.removeCallbacks(progressRunnable);
            nextSeekPosition = -1;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                nextSeekPosition = progress;
            }
        }
    };

    private void updatePlayState() {
        if (player != null) {
            boolean playing = player.isPlaying();
            playPauseButton.setImageResource(ResourceUtils.getResourceId(instance.galleryInstance.context,
                    finishedPlayback ? R.attr.buttonRefresh : playing ? R.attr.buttonPause : R.attr.buttonPlay, 0));
            instance.galleryInstance.callback.setScreenOnFixed(!finishedPlayback && playing);
        }
    }

    public void viewTechnicalInfo() {
        if (initialized) {
            HashMap<String, String> technicalInfo = player.getTechnicalInfo();
            StringBlockBuilder builder = new StringBlockBuilder();
            String videoFormat = technicalInfo.get("video_format");
            String width = technicalInfo.get("width");
            String height = technicalInfo.get("height");
            String frameRate = technicalInfo.get("frame_rate");
            String pixelFormat = technicalInfo.get("pixel_format");
            String surfaceFormat = technicalInfo.get("surface_format");
            String useLibyuv = technicalInfo.get("use_libyuv");
            String audioFormat = technicalInfo.get("audio_format");
            String channels = technicalInfo.get("channels");
            String sampleRate = technicalInfo.get("sample_rate");
            String encoder = technicalInfo.get("encoder");
            String title = technicalInfo.get("title");
            if (videoFormat != null) {
                builder.appendLine("Video: " + videoFormat);
            }
            if (width != null && height != null) {
                builder.appendLine("Resolution: " + width + '×' + height);
            }
            if (frameRate != null) {
                builder.appendLine("Frame rate: " + frameRate);
            }
            if (pixelFormat != null) {
                builder.appendLine("Pixels: " + pixelFormat);
            }
            if (surfaceFormat != null) {
                builder.appendLine("Surface: " + surfaceFormat);
            }
            if ("1".equals(useLibyuv)) {
                builder.appendLine("Use libyuv: true");
            } else if ("0".equals(useLibyuv)) {
                builder.appendLine("Use libyuv: false");
            }
            builder.appendEmptyLine();
            if (audioFormat != null) {
                builder.appendLine("Audio: " + audioFormat);
            }
            if (channels != null) {
                builder.appendLine("Channels: " + channels);
            }
            if (sampleRate != null) {
                builder.appendLine("Sample rate: " + sampleRate + " Hz");
            }
            builder.appendEmptyLine();
            if (encoder != null) {
                builder.appendLine("Encoder: " + encoder);
            }
            if (!StringUtils.isEmptyOrWhitespace(title)) {
                builder.appendLine("Title: " + title);
            }
            String message = builder.toString();
            if (message.length() > 0) {
                AlertDialog dialog = new AlertDialog.Builder(instance.galleryInstance.context)
                        .setTitle(R.string.action_technical_info).setMessage(message)
                        .setPositiveButton(android.R.string.ok, null).create();
                dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_MESSAGE_SELECTABLE);
                dialog.show();
            }
        }
    }

    private boolean controlsVisible = false;

    public void invalidateControlsVisibility() {
        boolean visible = initialized && instance.galleryInstance.callback.isSystemUiVisible();
        if (layoutConfiguration >= 0 && controlsVisible != visible) {
            controlsView.animate().cancel();
            if (visible) {
                controlsView.setVisibility(View.VISIBLE);
                controlsView.animate().alpha(1f).translationY(0f).setDuration(250).setListener(null)
                        .setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR).start();
            } else {
                controlsView.animate().alpha(0f).translationY(controlsView.getHeight() -
                        configurationView.getHeight()).setDuration(350)
                        .setListener(new AnimationUtils.VisibilityListener(controlsView, View.GONE))
                        .setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR).start();
            }
            controlsVisible = visible;
        }
    }

    private final VideoPlayer.Listener playerListener = new VideoPlayer.Listener() {
        @Override
        public void onComplete(VideoPlayer player) {
            switch (Preferences.getVideoCompletionMode()) {
                case Preferences.VIDEO_COMPLETION_MODE_NOTHING: {
                    finishedPlayback = true;
                    updatePlayState();
                    break;
                }
                case Preferences.VIDEO_COMPLETION_MODE_LOOP: {
                    player.setPosition(0L);
                    break;
                }
            }
        }

        @Override
        public void onBusyStateChange(VideoPlayer player, boolean busy) {
            if (initialized) {
                PagerInstance.ViewHolder holder = instance.currentHolder;
                if (busy) {
                    holder.progressBar.setIndeterminate(true);
                }
                holder.progressBar.setVisible(busy, false);
            }
        }

        @Override
        public void onDimensionChange(VideoPlayer player) {
            if (backgroundDrawable != null) {
                backgroundDrawable.recycle();
                Point dimensions = player.getDimensions();
                backgroundDrawable.width = dimensions.x;
                backgroundDrawable.height = dimensions.y;
                instance.currentHolder.photoView.resetScale();
            }
        }
    };

    public void showHideVideoView(boolean show) {
        if (initialized) {
            View videoView = player.getVideoView(instance.galleryInstance.context);
            if (show) {
                backgroundDrawable.recycle();
                videoView.setVisibility(View.VISIBLE);
            } else {
                backgroundDrawable.setFrame(player.getCurrentFrame());
                videoView.setVisibility(View.GONE);
            }
        }
    }

    public void handleSwipingContent(boolean swiping, boolean hideSurface) {
        if (initialized) {
            playPauseButton.setEnabled(!swiping);
            seekBar.setEnabled(!swiping);
            if (swiping) {
                wasPlaying = player.isPlaying();
                setPlaying(false, true);
                if (hideSurface) {
                    showHideVideoView(false);
                }
            } else {
                setPlaying(wasPlaying, true);
                if (hideSurface) {
                    showHideVideoView(true);
                }
                updatePlayState();
            }
        } else if (player != null) {
            wasPlaying = !swiping;
            hideSurfaceOnInit = hideSurface && swiping;
        }
    }

    private class ReadVideoCallback implements ReadVideoTask.Callback {
        private final VideoPlayer workPlayer;
        private final PagerInstance.ViewHolder holder;

        public ReadVideoCallback(VideoPlayer player, PagerInstance.ViewHolder holder) {
            this.workPlayer = player;
            this.holder = holder;
        }

        @Override
        public void onReadVideoProgressUpdate(long progress, long progressMax) {
            if (initialized && workPlayer == player) {
                int max = seekBar.getMax();
                if (max > 0 && progressMax > 0) {
                    int newProgress = (int) (max * progress / progressMax);
                    seekBar.setSecondaryProgress(newProgress);
                }
            }
        }

        @Override
        public void onReadVideoSuccess(final CachingInputStream inputStream) {
            if (workPlayer != player) {
                return;
            }
            readVideoTask = null;
            if (initialized) {
                seekBar.setSecondaryProgress(seekBar.getMax());
            }
            new AsyncTask<Void, Void, Boolean>() {
                private File file;

                @Override
                protected Boolean doInBackground(Void... params) {
                    file = CacheManager.getInstance().getMediaFile(holder.galleryItem
                            .getFileUri(instance.galleryInstance.locator), false);
                    if (file == null) {
                        return false;
                    }
                    boolean success;
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        inputStream.writeTo(output);
                        success = true;
                    } catch (IOException e) {
                        success = false;
                        e.printStackTrace();
                    } finally {
                        IOUtils.close(output);
                    }
                    CacheManager.getInstance().handleDownloadedFile(file, success);
                    return success;
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    if (result && workPlayer == player) {
                        holder.fullLoaded = true;
                        instance.galleryInstance.callback.invalidateOptionsMenu();
                        try {
                            player.replaceStream(file);
                        } catch (IOException e) {
                            // Ignore exception
                        }
                        if (holder.galleryItem.size <= 0) {
                            holder.galleryItem.size = (int) file.length();
                            instance.galleryInstance.callback.updateTitle();
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        @Override
        public void onReadVideoFail(ErrorItem errorItem) {
            readVideoTask = null;
            holder.progressBar.setVisible(false, false);
            instance.callback.showError(holder, errorItem.toString());
            instance.galleryInstance.callback.invalidateOptionsMenu();
        }
    }

    private static class BackgroundDrawable extends Drawable {
        public int width;
        public int height;

        private Bitmap frame;
        private boolean draw = false;

        public void setFrame(Bitmap frame) {
            recycleInternal();
            this.frame = frame;
            draw = true;
            invalidateSelf();
        }

        public void recycle() {
            recycleInternal();
            if (draw) {
                draw = false;
                invalidateSelf();
            }
        }

        private void recycleInternal() {
            if (frame != null) {
                frame.recycle();
                frame = null;
            }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        @Override
        public void draw(Canvas canvas) {
            if (draw) {
                Rect bounds = getBounds();
                paint.setColor(Color.BLACK);
                canvas.drawRect(bounds, paint);
                if (frame != null) {
                    canvas.drawBitmap(frame, null, bounds, paint);
                }
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
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
    }
}
