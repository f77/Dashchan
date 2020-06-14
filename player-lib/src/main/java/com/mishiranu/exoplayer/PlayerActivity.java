/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mishiranu.exoplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

/**
 * A fullscreen activity to play audio or video streams.
 */
public class PlayerActivity extends AppCompatActivity {
    public static final String STATE_PLAYLIST = "playlist";
    public static final String STATE_KEY_HIDE_SYSTEM_UI = "hide_system_ui";
    public static final String STATE_KEY_IS_REPEAT = "is_repeat";

    private PlaybackStateListener playbackStateListener;
    private static final String TAG = PlayerActivity.class.getName();

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private boolean playWhenReady = true;
    private int currentWindow;
    private long playbackPosition = 0;
    private boolean isControllerShowedNow = false;

    private Playlist playlist;
    private boolean isHideSystemUi;
    private boolean isRepeat;

    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());
        playerView = findViewById(R.id.video_view);
        initPlayerView(playerView);

        // https://github.com/google/ExoPlayer/issues/5725#issuecomment-493431401
        playerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });

        playbackStateListener = new PlaybackStateListener();

        // Get state data.
        initState(getIntent());

        // Set position.
        currentWindow = playlist.getCurrentPosition();
    }

    /**
     * Initialise activity state.
     *
     * @param intent Intent.
     */
    protected void initState(Intent intent) {
        playlist = intent.getParcelableExtra(STATE_PLAYLIST);
        isHideSystemUi = intent.getBooleanExtra(STATE_KEY_HIDE_SYSTEM_UI, false);
        isRepeat = intent.getBooleanExtra(STATE_KEY_IS_REPEAT, true);
    }

    /**
     * Initialize PlayerView.
     *
     * @param playerView PlayerView.
     */
    protected void initPlayerView(PlayerView playerView) {
        playerView.setControllerHideOnTouch(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isHideSystemUi) {
            hideSystemUi();
        }
        if ((Util.SDK_INT <= 23 || player == null)) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    private void initializePlayer() {
        if (player == null) {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector();
            trackSelector.setParameters(
                    trackSelector.buildUponParameters().setMaxVideoSizeSd());
            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        }

        playerView.setPlayer(player);
        MediaSource mediaSource = buildMediaSource(playlist);

        player.setPlayWhenReady(playWhenReady);
        player.seekTo(currentWindow, playbackPosition);
        player.addListener(playbackStateListener);
        player.setRepeatMode(isRepeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        player.prepare(mediaSource, false, false);
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.removeListener(playbackStateListener);
            player.release();
            player = null;
        }
    }

    private MediaSource buildMediaSource(Playlist playlist) {
        // These factories are used to construct two media sources below.
        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, "exoplayer-dashchan");
        ProgressiveMediaSource.Factory mediaSourceFactory =
                new ProgressiveMediaSource.Factory(dataSourceFactory);

        // Result media source.
        ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource();

        // Add single sources.
        for (Uri uri : playlist.getURIs()) {
            concatenatingMediaSource.addMediaSource(mediaSourceFactory.createMediaSource(uri));
        }

        return concatenatingMediaSource;
    }

    @SuppressLint("InlinedApi")
    private void hideSystemUi() {
        playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    /**
     * Reverse current status of the controller.
     */
    protected void changeControllerVisibility() {
        isControllerShowedNow = !isControllerShowedNow;
        if (isControllerShowedNow) {
            playerView.hideController();
        } else {
            playerView.showController();
        }
    }

    private class PlaybackStateListener implements Player.EventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady,
                                         int playbackState) {
            String stateString;
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    stateString = "ExoPlayer.STATE_IDLE      -";
                    break;
                case ExoPlayer.STATE_BUFFERING:
                    stateString = "ExoPlayer.STATE_BUFFERING -";
                    break;
                case ExoPlayer.STATE_READY:
                    stateString = "ExoPlayer.STATE_READY     -";
                    break;
                case ExoPlayer.STATE_ENDED:
                    stateString = "ExoPlayer.STATE_ENDED     -";
                    break;
                default:
                    stateString = "UNKNOWN_STATE             -";
                    break;
            }
            Log.d(TAG, "changed state to " + stateString
                    + " playWhenReady: " + playWhenReady);
        }
    }

    class MyGestureListener extends OnSwipeListener {
        private static final String DEBUG_TAG = "EXOPLAYER_GESTURES";

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d(DEBUG_TAG, "SINGLE TAP");
            changeControllerVisibility();
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onSwipe(Direction direction) {
            Log.d(DEBUG_TAG, "SWIPE TO: " + direction.name());
            switch (direction) {
                case up:
                    finish();
                    break;
                case down:
                    break;
                case left:
                    player.next();
                    break;
                case right:
                    player.previous();
                    break;
            }

            return super.onSwipe(direction);
        }
    }
}
