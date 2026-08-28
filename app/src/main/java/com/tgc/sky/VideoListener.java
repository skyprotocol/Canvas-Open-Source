package com.tgc.sky;

import android.annotation.SuppressLint;
import android.util.Log;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;

@SuppressLint("UnsafeOptInUsageError")
class VideoListener implements Player.Listener {
    public String TAG = "VideoListener";
    public ExoPlayer m_player;
    public int m_retryTime;
    public ExoplayerService m_service;

    public VideoListener(ExoplayerService exoplayerService) {
        this.m_retryTime = 0;
        this.m_service = exoplayerService;
        // Safe: Initialize() assigns m_player before constructing this.
        this.m_player = exoplayerService.m_player;
    }

    @Override
    public void onPlaybackStateChanged(int i) {
        this.m_service.m_state = i;
    }

    @Override
    public void onIsPlayingChanged(boolean z) {
        // A successful play means the previous failure is behind us, so the
        // three-attempt budget starts over.
        if (z) {
            this.m_retryTime = 0;
        }
    }

    @Override
    public void onPlayerError(PlaybackException playbackException) {
        Log.d(this.TAG, "Video Error: " + playbackException.getMessage());
        Throwable cause = playbackException.getCause();
        if (cause instanceof HttpDataSource.HttpDataSourceException) {
            HttpDataSource.HttpDataSourceException httpDataSourceException = (HttpDataSource.HttpDataSourceException) cause;
            Throwable cause2 = httpDataSourceException.getCause();
            if (cause2 != null) {
                Log.d(this.TAG, "Video HTTP Error: " + cause2.toString() + ":" + cause2.getMessage());
            }
            boolean z = httpDataSourceException instanceof HttpDataSource.InvalidResponseCodeException;
        }

        boolean wasPlaying = this.m_player.isPlaying() || this.m_player.getPlayWhenReady();

        // The decoder exceptions are the CAUSE of the PlaybackException, not the
        // exception itself.
        MediaCodecInfo mediaCodecInfo = null;
        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
            mediaCodecInfo = ((MediaCodecRenderer.DecoderInitializationException) cause).codecInfo;
        } else if (cause instanceof MediaCodecDecoderException) {
            mediaCodecInfo = ((MediaCodecDecoderException) cause).codecInfo;
        }
        if (mediaCodecInfo != null && !this.m_service.codecBlackList.contains(mediaCodecInfo.name)) {
            Log.d(this.TAG, "Add Decoder to blackList. " + mediaCodecInfo.name);
            this.m_service.codecBlackList.add(mediaCodecInfo.name);
            this.m_service.EndVideo();
        }

        if (wasPlaying && this.m_retryTime < 3) {
            Log.d(this.TAG, "Retry to play the Video. " + this.m_retryTime + "/3");
            this.m_retryTime++;
            if (this.m_player.isCommandAvailable(Player.COMMAND_STOP)) {
                this.m_player.stop();
            }
            if (this.m_player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                this.m_player.clearVideoSurface();
                if (this.m_service.m_imageReader != null) {
                    this.m_player.setVideoSurface(this.m_service.m_imageReader.getSurface());
                }
            }
            if (this.m_player.isCommandAvailable(Player.COMMAND_PREPARE)) {
                this.m_player.prepare();
            }
            if (this.m_player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                this.m_player.play();
            }
        }
    }
}
