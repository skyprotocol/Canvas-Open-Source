package com.tgc.sky;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressLint("UnsafeOptInUsageError")
class ExoplayerService {
    public String TAG = "Exoplayer";
    Context m_context;
    HlsMediaSource.Factory m_hlsMediaFactory;
    Image m_image0;
    Image m_image1;
    ImageReader m_imageReader;
    ExoPlayer m_player;
    // Decoders that have already failed on this device. VideoListener adds to
    // it on a decoder error; the MediaCodecSelector below filters against it so
    // the retry picks a different decoder. Matches TGC 0.34.5.
    List<String> codecBlackList = new ArrayList<>();
    // Written by VideoListener.onPlaybackStateChanged. TGC stores it but never
    // reads it; kept for parity.
    int m_state;

    public void Initialize(Context context) {
        if (this.m_player == null) {
            DefaultLoadControl defaultLoadControl = new DefaultLoadControl();
            this.m_context = context;
            DefaultBandwidthMeter build = new DefaultBandwidthMeter.Builder(context).build();
            DefaultTrackSelector defaultTrackSelector = new DefaultTrackSelector(context, new AdaptiveTrackSelection.Factory());
            this.m_hlsMediaFactory = new HlsMediaSource.Factory(new DefaultDataSource.Factory(context));
            DefaultRenderersFactory defaultRenderersFactory = new DefaultRenderersFactory(context);
            defaultRenderersFactory.setMediaCodecSelector(new MediaCodecSelector() {
                @Override
                public List<MediaCodecInfo> getDecoderInfos(String str, boolean z, boolean z2)
                        throws MediaCodecUtil.DecoderQueryException {
                    List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos(str, z, z2);
                    List<MediaCodecInfo> allowed = new ArrayList<>();
                    for (MediaCodecInfo mediaCodecInfo : decoderInfos) {
                        if (!ExoplayerService.this.codecBlackList.contains(mediaCodecInfo.name)) {
                            allowed.add(mediaCodecInfo);
                        }
                    }
                    // If every decoder is blacklisted, fall back to the last one
                    // rather than returning an empty list and failing outright.
                    if (allowed.isEmpty() && !decoderInfos.isEmpty()) {
                        allowed.add(decoderInfos.get(decoderInfos.size() - 1));
                    }
                    return allowed;
                }
            });
            ExoPlayer build2 = new ExoPlayer.Builder(context).setRenderersFactory(defaultRenderersFactory).setBandwidthMeter(build).setTrackSelector(defaultTrackSelector).setLoadControl(defaultLoadControl).build();
            this.m_player = build2;
            build2.addListener(new VideoListener(this));
        }
    }

    public void Terminate() {
        ExoPlayer exoPlayer = this.m_player;
        if (exoPlayer != null) {
            exoPlayer.release();
            this.m_player = null;
        }
        ImageReader imageReader = this.m_imageReader;
        if (imageReader != null) {
            imageReader.close();
            this.m_imageReader = null;
        }
    }

    public void LoadUrl(String str) {
        EndVideo();
        MediaItem fromUri = MediaItem.fromUri(str);
        if (str.contains(".m3u8")) {
            this.m_player.setMediaSource(this.m_hlsMediaFactory.createMediaSource(fromUri));
        } else if (this.m_player.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM)) {
            this.m_player.setMediaItem(fromUri);
        }
        if (this.m_player.isCommandAvailable(Player.COMMAND_PREPARE)) {
            this.m_player.prepare();
        }
    }

    public void EndVideo() {
        ExoPlayer exoPlayer = this.m_player;
        if (exoPlayer != null && exoPlayer.isCommandAvailable(Player.COMMAND_STOP)) {
            this.m_player.stop();
        }
        if (this.m_imageReader != null) {
            if (this.m_image0 != null) {
                if (Build.VERSION.SDK_INT >= 28) {
                    this.m_image0.getHardwareBuffer().close();
                }
                this.m_image0.close();
                this.m_image0 = null;
            }
            if (this.m_image1 != null) {
                if (Build.VERSION.SDK_INT >= 28) {
                    this.m_image1.getHardwareBuffer().close();
                }
                this.m_image1 = null;
            }
            this.m_imageReader.close();
        }
        this.m_imageReader = null;
        if (this.m_player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
            this.m_player.clearVideoSurface();
        }
    }

    public void Update() {
        if (this.m_imageReader != null || this.m_player.getVideoFormat() == null) {
            return;
        }
        int i = this.m_player.getVideoFormat().width;
        int i2 = this.m_player.getVideoFormat().height;
        Log.d(this.TAG, "Video Metadata received: " + i + " " + i2);
        if (Build.VERSION.SDK_INT >= 29) {
            this.m_imageReader = ImageReader.newInstance(i, i2, ImageFormat.PRIVATE, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        } else {
            this.m_imageReader = ImageReader.newInstance(i, i2, ImageFormat.PRIVATE, 4);
        }
        if (this.m_player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
            this.m_player.setVideoSurface(this.m_imageReader.getSurface());
        }
    }

    public ExoplayerVideoMetadata GetMetadata() {
        if (this.m_player.getVideoFormat() == null) {
            return null;
        }
        ExoplayerVideoMetadata exoplayerVideoMetadata = new ExoplayerVideoMetadata();
        exoplayerVideoMetadata.width = this.m_player.getVideoFormat().width;
        exoplayerVideoMetadata.height = this.m_player.getVideoFormat().height;
        exoplayerVideoMetadata.framesPerSecond = this.m_player.getVideoFormat().frameRate;
        if (exoplayerVideoMetadata.framesPerSecond <= 30.0) {
            exoplayerVideoMetadata.framesPerSecond = 30.0d;
        }
        return exoplayerVideoMetadata;
    }

    public long GetPlaybackPositionMs() {
        if (this.m_player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return this.m_player.getCurrentPosition();
        }
        return 0L;
    }

    public HardwareBuffer GetNextHardwareBuffer() {
        Image acquireLatestImage;
        HardwareBuffer hardwareBuffer;
        if (this.m_imageReader == null || Build.VERSION.SDK_INT < 28 || (acquireLatestImage = this.m_imageReader.acquireLatestImage()) == null || (hardwareBuffer = acquireLatestImage.getHardwareBuffer()) == null) {
            return null;
        }
        Image image = this.m_image0;
        if (image != null) {
            Objects.requireNonNull(image.getHardwareBuffer()).close();
            this.m_image0.close();
        }
        this.m_image0 = this.m_image1;
        this.m_image1 = acquireLatestImage;
        return hardwareBuffer;
    }

    public void SetVolume(float f) {
        if (this.m_player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) {
            this.m_player.setVolume(f);
        }
    }

    public void Play() {
        if (this.m_player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            this.m_player.play();
        }
    }

    public void Pause() {
        if (this.m_player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            this.m_player.pause();
        }
    }

    // ---- Canvas addition, NOT part of TGC parity ----------------------
    // Sky exposes seek but never a duration, so a mod can jump to a timestamp
    // yet cannot draw a timeline. These feed the host value channel.
    // All guarded by COMMAND_GET_CURRENT_MEDIA_ITEM, which is what the Media3
    // javadoc requires for every one of them.
    public long GetDurationMs() {
        if (this.m_player == null
                || !this.m_player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return -1L;
        }
        long duration = this.m_player.getDuration();
        // Live streams and duration-less containers report TIME_UNSET.
        return duration == C.TIME_UNSET ? -1L : duration;
    }

    public long GetPositionMs() {
        if (this.m_player == null
                || !this.m_player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return -1L;
        }
        return this.m_player.getCurrentPosition();
    }

    // -1 unknown, 0 no, 1 yes. Deliberately separate from GetDurationMs:
    // "no duration" does not imply live, and having a duration does not imply
    // being seekable.
    public int GetLiveState() {
        if (this.m_player == null
                || !this.m_player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return -1;
        }
        return this.m_player.isCurrentMediaItemLive() ? 1 : 0;
    }

    public int GetSeekableState() {
        if (this.m_player == null
                || !this.m_player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return -1;
        }
        return this.m_player.isCurrentMediaItemSeekable() ? 1 : 0;
    }

    // Player.STATE_* (1 idle, 2 buffering, 3 ready, 4 ended), or -1 with no
    // player. m_state is written by VideoListener.onPlaybackStateChanged.
    public int GetPlaybackState() {
        return this.m_player == null ? -1 : this.m_state;
    }
    // ---- end Canvas addition -------------------------------------------

    public void Seek(long j) {
        if (this.m_player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            this.m_player.seekTo(j);
        }
    }
}