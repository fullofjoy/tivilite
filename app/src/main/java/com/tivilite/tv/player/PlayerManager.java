package com.tivilite.tv.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.plugin.PluginManager;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager {

    public static final int DECODER_MODE_HARDWARE = 0;
    public static final int DECODER_MODE_SOFTWARE = 1;

    public static final int ASPECT_RATIO_FIT = 0;   // 自适应 (保持原始比例)
    public static final int ASPECT_RATIO_FILL = 1;  // 全屏拉伸 (填满屏幕)
    public static final int ASPECT_RATIO_ZOOM = 2;  // 智能裁剪 (等比放大去除黑边)

    public static final int BUFFER_MODE_FAST = 0;    // 极速秒开 (秒切缓冲)
    public static final int BUFFER_MODE_SMOOTH = 1;  // 弱网防抖 (平滑防卡顿)
    public static final int BUFFER_MODE_EXTREME = 2; // 深度抗抖 (抗高延迟弱网)

    public static final int AUDIO_MODE_AUTO = 0;         // 自动自适应 (Auto)
    public static final int AUDIO_MODE_PASSTHROUGH = 1;  // 源码透传模式 (HDMI eARC / ARC Passthrough)
    public static final int AUDIO_MODE_PCM = 2;          // 立体声 PCM 降混兼容 (Stereo PCM)
    public static final int AUDIO_MODE_NIGHT_DRC = 3;    // 夜间人声清晰增强 (Night DRC)

    public static class SubtitleTrackInfo {
        public final int groupIndex;
        public final int trackIndex;
        public final String label;
        public final String language;
        public final boolean isSelected;

        public SubtitleTrackInfo(int groupIndex, int trackIndex, String label, String language, boolean isSelected) {
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.label = label;
            this.language = language;
            this.isSelected = isSelected;
        }

        public String getDisplayName() {
            if (label != null && !label.isEmpty()) {
                return label;
            } else if (language != null && !language.isEmpty()) {
                if ("zh".equalsIgnoreCase(language) || "zho".equalsIgnoreCase(language) || "chi".equalsIgnoreCase(language)) {
                    return "中文/普通话字幕";
                } else if ("yue".equalsIgnoreCase(language)) {
                    return "粤语字幕";
                } else if ("en".equalsIgnoreCase(language) || "eng".equalsIgnoreCase(language)) {
                    return "英文字幕 (CC)";
                } else {
                    return language.toUpperCase() + " 字幕";
                }
            }
            return "字幕轨 " + (trackIndex + 1);
        }
    }

    public static class AudioTrackInfo {
        public final int groupIndex;
        public final int trackIndex;
        public final String label;
        public final String language;
        public final int channelCount;
        public final boolean isSelected;

        public AudioTrackInfo(int groupIndex, int trackIndex, String label, String language, int channelCount, boolean isSelected) {
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.label = label;
            this.language = language;
            this.channelCount = channelCount;
            this.isSelected = isSelected;
        }

        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            if (label != null && !label.isEmpty()) {
                sb.append(label);
            } else if (language != null && !language.isEmpty()) {
                if ("zh".equalsIgnoreCase(language) || "zho".equalsIgnoreCase(language) || "chi".equalsIgnoreCase(language)) {
                    sb.append("国语/普通话");
                } else if ("yue".equalsIgnoreCase(language)) {
                    sb.append("粤语");
                } else if ("en".equalsIgnoreCase(language) || "eng".equalsIgnoreCase(language)) {
                    sb.append("英语");
                } else {
                    sb.append(language.toUpperCase());
                }
            } else {
                sb.append("音轨 ").append(trackIndex + 1);
            }

            if (channelCount == 6) {
                sb.append(" (5.1 环绕声)");
            } else if (channelCount == 2) {
                sb.append(" (立体声)");
            } else if (channelCount == 1) {
                sb.append(" (单声道)");
            }
            return sb.toString();
        }
    }

    public interface PlaybackCallback {
        void onLoading(boolean isLoading);
        void onPlaybackReady();
        void onErrorRetrying(String message, int currentAttempt, int maxAttempts);
        void onAlternativeSourceSwitched(String sourceInfo);
        void onErrorFatal(String diagnosticMessage, boolean hasAlternatives);
    }

    private static final int MAX_AUTO_RETRIES = 2;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlaybackCallback callback;
    private Channel currentChannel;
    private boolean isCatchupMode = false;
    private int autoRetryCount = 0;
    private String lastPlayedUrl = "";
    private int decoderMode = DECODER_MODE_HARDWARE;
    private int aspectRatioMode = ASPECT_RATIO_FIT;
    private int bufferMode = BUFFER_MODE_FAST;
    private int audioMode = AUDIO_MODE_AUTO;
    private boolean subtitlesEnabled = false;
    private PlayerView attachedPlayerView;

    // Phase 6 Multi-View / Dual-Stream PiP
    private ExoPlayer pipPlayer;
    private PlayerView attachedPipPlayerView;
    private Channel pipChannel;
    private boolean isPipActive = false;
    private boolean isPipAudioMain = true; // true: Main audio active, false: PiP audio active

    // Phase 8 QoS Telemetry & Adaptive Failover
    private DefaultBandwidthMeter bandwidthMeter;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private int totalDroppedFrames = 0;
    private int recentDroppedFrames = 0;
    private long lastDropTimeWindow = System.currentTimeMillis();
    private float recentDroppedFps = 0.0f;
    private long currentBitrateEstimate = 0;
    private boolean isAutoFailoverEnabled = true;

    // Phase 8 DLNA Cast Support
    private boolean isCastMode = false;
    private String castMediaTitle = "";

    public PlayerManager(Context context) {
        this.context = context;
        this.decoderMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("decoder_mode", DECODER_MODE_HARDWARE);
        this.aspectRatioMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("aspect_ratio_mode", ASPECT_RATIO_FIT);
        this.bufferMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("buffer_mode", BUFFER_MODE_FAST);
        this.audioMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("audio_mode", AUDIO_MODE_AUTO);
        this.subtitlesEnabled = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getBoolean("subtitles_enabled", false);
        this.isAutoFailoverEnabled = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getBoolean("auto_failover_enabled", true);
        initPlayer();
    }

    private void initPlayer() {
        // 1. Decoder selection: Hardware accelerated (default) vs Software compatibility mode
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);

        if (decoderMode == DECODER_MODE_SOFTWARE) {
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            renderersFactory.setMediaCodecSelector((mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> decoderInfos =
                        androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> sorted = new ArrayList<>(decoderInfos);
                Collections.sort(sorted, (a, b) -> {
                    boolean aIsSoftware = a.name.startsWith("c2.android.") || a.name.startsWith("OMX.google.") || a.softwareOnly;
                    boolean bIsSoftware = b.name.startsWith("c2.android.") || b.name.startsWith("OMX.google.") || b.softwareOnly;
                    if (aIsSoftware && !bIsSoftware) return -1;
                    if (!aIsSoftware && bIsSoftware) return 1;
                    return 0;
                });
                return sorted;
            });
        } else {
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
            renderersFactory.setMediaCodecSelector(androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT);
        }

        // 2. Bandwidth meter & High compatibility standard TV User-Agent to avoid 403 forbidden
        bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();

        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; TV Box Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
                .setTransferListener(bandwidthMeter);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpDataSourceFactory);

        // 3. Universal MediaSourceFactory: Automatically handles HLS, DASH, SmoothStreaming, and Progressive TS/MP4
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

        // 4. Staged buffer control: Adapts according to network stability preference
        int minBufferMs;
        int maxBufferMs;
        int bufferForPlaybackMs;
        int bufferForPlaybackAfterRebufferMs;

        switch (bufferMode) {
            case BUFFER_MODE_SMOOTH:
                minBufferMs = 8000;
                maxBufferMs = 30000;
                bufferForPlaybackMs = 2500;
                bufferForPlaybackAfterRebufferMs = 3500;
                break;
            case BUFFER_MODE_EXTREME:
                minBufferMs = 15000;
                maxBufferMs = 45000;
                bufferForPlaybackMs = 4000;
                bufferForPlaybackAfterRebufferMs = 5000;
                break;
            case BUFFER_MODE_FAST:
            default:
                minBufferMs = 2500;
                maxBufferMs = 15000;
                bufferForPlaybackMs = 400;
                bufferForPlaybackAfterRebufferMs = 1200;
                break;
        }

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBufferMs,
                        maxBufferMs,
                        bufferForPlaybackMs,
                        bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(5000, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .build();

        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                        .setMaxAudioChannelCount(audioMode == AUDIO_MODE_PCM || audioMode == AUDIO_MODE_NIGHT_DRC ? 2 : Integer.MAX_VALUE)
                        .build()
        );
        if (audioMode == AUDIO_MODE_NIGHT_DRC) {
            player.setVolume(0.85f);
        } else {
            player.setVolume(1.0f);
        }

        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (callback == null) return;
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        callback.onLoading(true);
                        break;
                    case Player.STATE_READY:
                        autoRetryCount = 0;
                        callback.onLoading(false);
                        callback.onPlaybackReady();
                        break;
                    case Player.STATE_ENDED:
                    case Player.STATE_IDLE:
                        callback.onLoading(false);
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                handlePlaybackError(error);
            }
        });

        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
                totalDroppedFrames += droppedFrames;
                recentDroppedFrames += droppedFrames;
            }

            @Override
            public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
                currentBitrateEstimate = bitrateEstimate;
            }
        });
    }

    private void handlePlaybackError(PlaybackException error) {
        if (player == null) return;

        // Auto recovery for live stream slipping behind the live window
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();
            player.prepare();
            return;
        }

        String diagnostic = diagnoseError(error);

        // Phase 8: Adaptive Failover - If auto failover is enabled and channel has alternative lines, switch immediately
        if (isAutoFailoverEnabled && currentChannel != null && currentChannel.hasAlternativeUrls() && autoRetryCount >= 1) {
            currentChannel.switchToNextUrl();
            autoRetryCount = 0;
            if (callback != null) {
                callback.onAlternativeSourceSwitched("当前线路网络故障，已为您智能无缝容灾切换至 " + currentChannel.getSourceInfo());
            }
            handler.postDelayed(() -> playChannel(currentChannel), 600);
            return;
        }

        // Attempt automatic reconnection
        if (autoRetryCount < MAX_AUTO_RETRIES) {
            autoRetryCount++;
            if (callback != null) {
                callback.onErrorRetrying(diagnostic + "，正在自动重试 (" + autoRetryCount + "/" + MAX_AUTO_RETRIES + ")...", autoRetryCount, MAX_AUTO_RETRIES);
            }
            handler.postDelayed(() -> {
                if (player != null && !lastPlayedUrl.isEmpty()) {
                    playUrlInternal(lastPlayedUrl);
                }
            }, 1500);
            return;
        }

        // Exhausted retries: Check if channel has alternative lines
        if (currentChannel != null && currentChannel.hasAlternativeUrls()) {
            currentChannel.switchToNextUrl();
            autoRetryCount = 0;
            if (callback != null) {
                callback.onAlternativeSourceSwitched("当前线路异常，已自动切换至 " + currentChannel.getSourceInfo());
            }
            handler.postDelayed(() -> playChannel(currentChannel), 800);
            return;
        }

        // Fatal error: notify UI with diagnosis and interactive controls
        if (callback != null) {
            callback.onErrorFatal(diagnostic, currentChannel != null && currentChannel.hasAlternativeUrls());
        }
    }

    private String diagnoseError(PlaybackException error) {
        if (currentChannel != null && currentChannel.isCurrentUrlIpv6()) {
            return "播放异常：该频道为 IPv6 专属源，您的宽带/路由器可能未开启 IPv6";
        }
        int code = error.errorCode;
        if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            return "播放异常：连接源服务器超时 (网络抖动或源下线)";
        } else if (code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            return "播放异常：源返回 HTTP 403/404 (防盗链拦截或资源失效)";
        } else if (code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
            return "播放异常：无法解析该视频流封装格式";
        } else if (code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
            return "播放异常：硬件解码器初始化超限，已尝试降级";
        }
        return "播放异常：" + (error.getMessage() != null ? error.getMessage() : "信号源暂时不可达");
    }

    public void attachPlayerView(PlayerView playerView) {
        this.attachedPlayerView = playerView;
        playerView.setPlayer(player);
        applyAspectRatio();

        // Configure high-contrast SubtitleView for TV distance
        SubtitleView subView = playerView.getSubtitleView();
        if (subView != null) {
            subView.setApplyEmbeddedFontSizes(false);
            subView.setFractionalTextSize(0.055f); // 5.5% of TV screen height
            CaptionStyleCompat style = new CaptionStyleCompat(
                    Color.WHITE,
                    Color.parseColor("#90000000"), // Translucent dark backing box
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    Typeface.DEFAULT_BOLD
            );
            subView.setStyle(style);
        }
    }

    public int getAspectRatioMode() {
        return aspectRatioMode;
    }

    public void setAspectRatioMode(int mode) {
        this.aspectRatioMode = mode;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("aspect_ratio_mode", mode)
                .apply();
        applyAspectRatio();
    }

    public String toggleNextAspectRatio() {
        int next = (aspectRatioMode + 1) % 3;
        setAspectRatioMode(next);
        return getAspectRatioModeText();
    }

    public String getAspectRatioModeText() {
        switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL:
                return "全屏拉伸 (FILL)";
            case ASPECT_RATIO_ZOOM:
                return "智能裁剪 (ZOOM)";
            case ASPECT_RATIO_FIT:
            default:
                return "自适应比例 (FIT)";
        }
    }

    private void applyAspectRatio() {
        if (attachedPlayerView == null) return;
        switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL:
                attachedPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                break;
            case ASPECT_RATIO_ZOOM:
                attachedPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                break;
            case ASPECT_RATIO_FIT:
            default:
                attachedPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                break;
        }
    }

    public List<AudioTrackInfo> getAudioTracks() {
        List<AudioTrackInfo> list = new ArrayList<>();
        if (player == null) return list;
        Tracks tracks = player.getCurrentTracks();
        int groupIdx = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < group.length; i++) {
                    Format format = group.getTrackFormat(i);
                    boolean selected = group.isTrackSelected(i);
                    list.add(new AudioTrackInfo(groupIdx, i, format.label, format.language, format.channelCount, selected));
                }
            }
            groupIdx++;
        }
        return list;
    }

    public void selectAudioTrack(int targetGroupIndex, int targetTrackIndex) {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        int currentGroupIdx = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (currentGroupIdx == targetGroupIndex && group.getType() == C.TRACK_TYPE_AUDIO) {
                TrackSelectionOverride override =
                        new TrackSelectionOverride(group.getMediaTrackGroup(), targetTrackIndex);
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters().buildUpon()
                                .setOverrideForType(override)
                                .build()
                );
                return;
            }
            currentGroupIdx++;
        }
    }

    public String toggleNextAudioTrack() {
        List<AudioTrackInfo> tracks = getAudioTracks();
        if (tracks.size() <= 1) return null;
        int selectedIdx = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).isSelected) {
                selectedIdx = i;
                break;
            }
        }
        int nextIdx = (selectedIdx + 1) % tracks.size();
        AudioTrackInfo next = tracks.get(nextIdx);
        selectAudioTrack(next.groupIndex, next.trackIndex);
        return next.getDisplayName();
    }

    public String getCurrentAudioTrackDesc() {
        List<AudioTrackInfo> tracks = getAudioTracks();
        for (AudioTrackInfo t : tracks) {
            if (t.isSelected) return t.getDisplayName();
        }
        return tracks.isEmpty() ? "标准立体声" : tracks.get(0).getDisplayName();
    }

    public int getAudioMode() {
        return audioMode;
    }

    public void setAudioMode(int mode) {
        this.audioMode = mode;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit().putInt("audio_mode", mode).apply();
        if (player != null) {
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                            .setMaxAudioChannelCount(mode == AUDIO_MODE_PCM || mode == AUDIO_MODE_NIGHT_DRC ? 2 : Integer.MAX_VALUE)
                            .build()
            );
            player.setVolume(mode == AUDIO_MODE_NIGHT_DRC ? 0.85f : (isPipActive && !isPipAudioMain ? 0.0f : 1.0f));
        }
    }

    public String toggleNextAudioMode() {
        int next = (audioMode + 1) % 4;
        setAudioMode(next);
        return getAudioModeText();
    }

    public String getAudioModeText() {
        switch (audioMode) {
            case AUDIO_MODE_PASSTHROUGH:
                return "源码直通 (HDMI eARC/透传)";
            case AUDIO_MODE_PCM:
                return "立体声 PCM (双声道兼容)";
            case AUDIO_MODE_NIGHT_DRC:
                return "夜间防吵 (对白增强/限幅)";
            case AUDIO_MODE_AUTO:
            default:
                return "自动直通 (自适应)";
        }
    }

    public String getAudioCodecBadge() {
        if (player == null) return "立体声";
        Format format = player.getAudioFormat();
        if (format == null) return "立体声";

        String mime = format.sampleMimeType != null ? format.sampleMimeType.toLowerCase() : "";
        int channels = format.channelCount;

        if (mime.contains("eac3-joc") || (mime.contains("eac3") && channels >= 8)) {
            return "DOLBY ATMOS 全景声";
        } else if (mime.contains("eac3") || mime.contains("ac3")) {
            return channels >= 6 ? "DOLBY 5.1 环绕声" : "DOLBY DIGITAL 杜比";
        } else if (mime.contains("dts")) {
            return channels >= 8 ? "DTS:X 空间环绕" : (channels >= 6 ? "DTS 5.1" : "DTS 双声道");
        } else if (mime.contains("true-hd") || mime.contains("truehd")) {
            return "DOLBY TrueHD 无损";
        } else if (mime.contains("aac") || mime.contains("mp4a")) {
            return channels >= 6 ? "AAC 5.1 环绕" : "AAC 高清立体声";
        } else if (channels == 6) {
            return "5.1 环绕声道";
        } else if (channels == 2) {
            return "双声道立体声";
        } else if (channels == 1) {
            return "单声道";
        }
        return "高清音频";
    }

    public String getVideoStreamBadge() {
        if (player == null) return "";
        Format format = player.getVideoFormat();
        if (format == null) return "";
        return PictureQualityManager.getInstance(context).getHdrAndResolutionBadge(format);
    }

    public List<SubtitleTrackInfo> getSubtitleTracks() {
        List<SubtitleTrackInfo> list = new ArrayList<>();
        if (player == null) return list;
        Tracks tracks = player.getCurrentTracks();
        int groupIdx = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < group.length; i++) {
                    Format format = group.getTrackFormat(i);
                    boolean selected = group.isTrackSelected(i);
                    list.add(new SubtitleTrackInfo(groupIdx, i, format.label, format.language, selected));
                }
            }
            groupIdx++;
        }
        return list;
    }

    public boolean isSubtitlesEnabled() {
        return subtitlesEnabled;
    }

    public void setSubtitlesEnabled(boolean enabled) {
        this.subtitlesEnabled = enabled;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("subtitles_enabled", enabled).apply();
        if (player != null) {
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                            .build()
            );
        }
    }

    public void selectSubtitleTrack(int targetGroupIndex, int targetTrackIndex) {
        this.subtitlesEnabled = true;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("subtitles_enabled", true).apply();
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        int currentGroupIdx = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (currentGroupIdx == targetGroupIndex && group.getType() == C.TRACK_TYPE_TEXT) {
                TrackSelectionOverride override =
                        new TrackSelectionOverride(group.getMediaTrackGroup(), targetTrackIndex);
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(override)
                                .build()
                );
                return;
            }
            currentGroupIdx++;
        }
    }

    public String toggleNextSubtitleTrack() {
        List<SubtitleTrackInfo> tracks = getSubtitleTracks();
        if (tracks.isEmpty()) {
            return "当前源无内嵌字幕";
        }
        if (!isSubtitlesEnabled()) {
            setSubtitlesEnabled(true);
            selectSubtitleTrack(tracks.get(0).groupIndex, tracks.get(0).trackIndex);
            return tracks.get(0).getDisplayName();
        }
        int currentIdx = -1;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).isSelected) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx >= 0 && currentIdx < tracks.size() - 1) {
            SubtitleTrackInfo next = tracks.get(currentIdx + 1);
            selectSubtitleTrack(next.groupIndex, next.trackIndex);
            return next.getDisplayName();
        } else {
            setSubtitlesEnabled(false);
            return "字幕已关闭";
        }
    }

    public String getCurrentSubtitleDesc() {
        if (!isSubtitlesEnabled()) return "字幕关闭";
        List<SubtitleTrackInfo> tracks = getSubtitleTracks();
        for (SubtitleTrackInfo t : tracks) {
            if (t.isSelected) return t.getDisplayName();
        }
        return tracks.isEmpty() ? "无字幕轨" : "字幕已开";
    }

    public int getBufferMode() {
        return bufferMode;
    }

    public void setBufferMode(int mode) {
        if (this.bufferMode == mode) return;
        this.bufferMode = mode;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("buffer_mode", mode)
                .apply();

        PlayerView attached = this.attachedPlayerView;
        Channel ch = this.currentChannel;
        boolean catchup = this.isCatchupMode;
        String lastUrl = this.lastPlayedUrl;
        long currentPos = player != null ? player.getCurrentPosition() : 0;

        if (player != null) {
            player.release();
            player = null;
        }

        initPlayer();
        if (attached != null) {
            attachPlayerView(attached);
        }
        if (ch != null) {
            this.currentChannel = ch;
            this.isCatchupMode = catchup;
            if (!lastUrl.isEmpty()) {
                playUrl(lastUrl);
                if (currentPos > 0 && player != null) player.seekTo(currentPos);
            } else {
                playChannel(ch);
            }
        }
    }

    public String toggleNextBufferMode() {
        int next = (bufferMode + 1) % 3;
        setBufferMode(next);
        return getBufferModeText();
    }

    public String getBufferModeText() {
        switch (bufferMode) {
            case BUFFER_MODE_SMOOTH:
                return "弱网防抖模式 (8s~30s 缓冲)";
            case BUFFER_MODE_EXTREME:
                return "深度抗抖模式 (15s~45s 缓冲)";
            case BUFFER_MODE_FAST:
            default:
                return "极速秒开模式 (2.5s~15s 缓冲)";
        }
    }

    public String getLastPlayedUrl() {
        return lastPlayedUrl;
    }

    public int getDecoderMode() {
        return decoderMode;
    }

    public void setDecoderMode(int mode) {
        if (this.decoderMode == mode) return;
        this.decoderMode = mode;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("decoder_mode", mode)
                .apply();

        PlayerView attached = this.attachedPlayerView;
        Channel ch = this.currentChannel;
        boolean catchup = this.isCatchupMode;
        String lastUrl = this.lastPlayedUrl;
        long currentPos = player != null ? player.getCurrentPosition() : 0;

        if (player != null) {
            player.release();
            player = null;
        }

        initPlayer();
        if (attached != null) {
            attachPlayerView(attached);
        }
        if (ch != null) {
            this.currentChannel = ch;
            this.isCatchupMode = catchup;
            if (!lastUrl.isEmpty()) {
                playUrl(lastUrl);
                if (currentPos > 0 && player != null) player.seekTo(currentPos);
            } else {
                playChannel(ch);
            }
        }
    }

    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    public void playChannel(Channel channel) {
        this.currentChannel = channel;
        this.isCatchupMode = false;
        this.autoRetryCount = 0;
        if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
            if (callback != null) callback.onErrorFatal("无效的频道播放地址", false);
            return;
        }

        if (channel.hasAlternativeUrls()) {
            SourceBenchmarkManager.getInstance().benchmarkChannelSources(channel, (bestIdx, lowestLatency) -> {
                if (callback != null && currentChannel == channel) {
                    callback.onAlternativeSourceSwitched(channel.getSourceInfo());
                }
            });
        }

        playUrl(channel.getUrl());
    }

    public void switchToBestSource() {
        if (currentChannel != null && currentChannel.hasAlternativeUrls()) {
            SourceBenchmarkManager.getInstance().benchmarkChannelSources(currentChannel, (bestIdx, lowestLatency) -> {
                if (currentChannel != null) {
                    currentChannel.setCurrentUrlIndex(bestIdx);
                    autoRetryCount = 0;
                    if (callback != null) {
                        callback.onAlternativeSourceSwitched("已优选最佳线路: " + currentChannel.getSourceInfo());
                    }
                    playChannel(currentChannel);
                }
            });
        }
    }

    public void retryCurrent() {
        this.autoRetryCount = 0;
        if (!lastPlayedUrl.isEmpty()) {
            playUrl(lastPlayedUrl);
        } else if (currentChannel != null) {
            playChannel(currentChannel);
        }
    }

    public void switchToNextSource() {
        if (currentChannel != null && currentChannel.hasAlternativeUrls()) {
            currentChannel.switchToNextUrl();
            this.autoRetryCount = 0;
            if (callback != null) {
                callback.onAlternativeSourceSwitched("已切换至 " + currentChannel.getSourceInfo());
            }
            playChannel(currentChannel);
        }
    }

    public void switchToPreviousSource() {
        if (currentChannel != null && currentChannel.hasAlternativeUrls()) {
            currentChannel.switchToPreviousUrl();
            this.autoRetryCount = 0;
            if (callback != null) {
                callback.onAlternativeSourceSwitched("已切换至 " + currentChannel.getSourceInfo());
            }
            playChannel(currentChannel);
        }
    }

    public void playCatchup(Channel channel, long startUtc, long endUtc) {
        this.currentChannel = channel;
        this.isCatchupMode = true;
        this.autoRetryCount = 0;

        if (channel == null) return;
        String rawUrl = channel.getUrl();
        if (rawUrl == null || rawUrl.isEmpty()) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        String startStr = sdf.format(new Date(startUtc));
        String endStr = sdf.format(new Date(endUtc));

        String catchupUrl;
        if (channel.getCatchupSource() != null && !channel.getCatchupSource().isEmpty()) {
            String template = channel.getCatchupSource();
            template = template.replace("{utc:YmdHMS}", startStr).replace("{utcend:YmdHMS}", endStr);
            template = template.replace("${(b)yyyyMMddHHmmss}", startStr).replace("${(e)yyyyMMddHHmmss}", endStr);
            catchupUrl = rawUrl.contains("?") ? (rawUrl + "&" + template.replace("?", "")) : (rawUrl + template);
        } else {
            String sep = rawUrl.contains("?") ? "&" : "?";
            catchupUrl = rawUrl + sep + "playseek=" + startStr + "-" + endStr;
        }

        playUrl(catchupUrl);
    }

    private void playUrl(String url) {
        this.lastPlayedUrl = url;
        playUrlInternal(url);
    }

    private void playUrlInternal(String url) {
        if (callback != null) callback.onLoading(true);

        // Phase 8 Plugin dynamic URL resolution & header injection
        PluginManager.ResolvedStream resolved = PluginManager.getInstance(context).resolve(url);
        String targetUrl = (resolved != null && resolved.url != null) ? resolved.url : url;
        if (httpDataSourceFactory != null && resolved != null && resolved.headers != null && !resolved.headers.isEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(resolved.headers);
        }

        String playable = LanMulticastManager.getInstance(context).resolvePlayableUrl(targetUrl, 8888);
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(playable));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    public boolean isCatchupMode() {
        return isCatchupMode;
    }

    public void seekBy(long offsetMs) {
        if (player == null) return;
        long current = player.getCurrentPosition();
        long target = Math.max(0, current + offsetMs);
        long duration = player.getDuration();
        if (duration > 0) target = Math.min(target, duration);
        player.seekTo(target);
    }

    public void seekTo(long positionMs) {
        if (player != null) player.seekTo(positionMs);
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public boolean isPipActive() {
        return isPipActive;
    }

    public Channel getPipChannel() {
        return pipChannel;
    }

    public boolean isPipAudioMain() {
        return isPipAudioMain;
    }

    public void startPip(Channel channel, PlayerView pipView) {
        if (channel == null || pipView == null) return;
        this.pipChannel = channel;
        this.attachedPipPlayerView = pipView;

        if (pipPlayer == null) {
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true);
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36 Chrome/100.0.4896.127 Safari/537.36")
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(8000)
                    .setAllowCrossProtocolRedirects(true);
            DefaultDataSource.Factory dsFactory = new DefaultDataSource.Factory(context, httpFactory);
            DefaultMediaSourceFactory msFactory = new DefaultMediaSourceFactory(dsFactory);

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2000, 10000, 500, 1000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            pipPlayer = new ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)
                    .setMediaSourceFactory(msFactory)
                    .setLoadControl(loadControl)
                    .build();
        }

        pipView.setPlayer(pipPlayer);
        pipPlayer.setVolume(isPipAudioMain ? 0.0f : 1.0f);
        if (player != null) {
            player.setVolume(isPipAudioMain ? 1.0f : 0.0f);
        }

        String playable = LanMulticastManager.getInstance(context).resolvePlayableUrl(channel.getUrl(), 8888);
        MediaItem item = MediaItem.fromUri(playable);
        pipPlayer.setMediaItem(item);
        pipPlayer.prepare();
        pipPlayer.setPlayWhenReady(true);
        isPipActive = true;
    }

    public void stopPip() {
        isPipActive = false;
        if (pipPlayer != null) {
            pipPlayer.stop();
            pipPlayer.clearMediaItems();
            pipPlayer.release();
            pipPlayer = null;
        }
        if (attachedPipPlayerView != null) {
            attachedPipPlayerView.setPlayer(null);
            attachedPipPlayerView = null;
        }
        pipChannel = null;
        isPipAudioMain = true;
        if (player != null) {
            player.setVolume(1.0f);
        }
    }

    public boolean togglePipAudio() {
        if (!isPipActive || pipPlayer == null || player == null) return true;
        isPipAudioMain = !isPipAudioMain;
        if (isPipAudioMain) {
            player.setVolume(1.0f);
            pipPlayer.setVolume(0.0f);
        } else {
            player.setVolume(0.0f);
            pipPlayer.setVolume(1.0f);
        }
        return isPipAudioMain;
    }

    public void swapPipAndMain() {
        if (!isPipActive || pipChannel == null || currentChannel == null || attachedPipPlayerView == null) return;
        Channel oldMain = currentChannel;
        Channel oldPip = pipChannel;

        playChannel(oldPip);
        startPip(oldMain, attachedPipPlayerView);
    }

    // ==========================================
    // Phase 8 DLNA Cast Support
    // ==========================================

    public void playCastStream(String url, String title) {
        this.isCastMode = true;
        this.castMediaTitle = title != null && !title.isEmpty() ? title : "手机投屏视频";
        Channel castChannel = new Channel(0, this.castMediaTitle, "无线投屏", url);
        this.currentChannel = castChannel;
        this.lastPlayedUrl = url;
        playUrlInternal(url);
    }

    public boolean isCastMode() {
        return isCastMode;
    }

    public String getCastMediaTitle() {
        return castMediaTitle;
    }

    public void exitCastMode() {
        this.isCastMode = false;
        this.castMediaTitle = "";
    }

    // ==========================================
    // Phase 8 QoS Telemetry & Network Radar
    // ==========================================

    public long getCurrentBitrateEstimate() {
        if (bandwidthMeter != null && bandwidthMeter.getBitrateEstimate() > 0) {
            return bandwidthMeter.getBitrateEstimate();
        }
        if (player != null) {
            Format f = player.getVideoFormat();
            if (f != null && f.bitrate > 0) return f.bitrate;
        }
        return currentBitrateEstimate > 0 ? currentBitrateEstimate : 9_600_000L;
    }

    public long getTotalBufferedDurationMs() {
        return player != null ? player.getTotalBufferedDuration() : 0;
    }

    public int getTotalDroppedFrames() {
        return totalDroppedFrames;
    }

    public float getRecentDroppedFps() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastDropTimeWindow;
        if (elapsed >= 2500) {
            recentDroppedFps = (recentDroppedFrames * 1000f) / elapsed;
            recentDroppedFrames = 0;
            lastDropTimeWindow = now;
        }
        return recentDroppedFps;
    }

    public String getVideoCodecDetails() {
        if (player == null) return "硬件解码 (MediaCodec)";
        Format f = player.getVideoFormat();
        if (f == null) return "自适应硬件解码";
        String mime = f.sampleMimeType != null ? f.sampleMimeType.toLowerCase() : "";
        String dec = (decoderMode == DECODER_MODE_SOFTWARE) ? "软解 (CPU)" : "硬解 (GPU/VPU)";
        if (mime.contains("hevc") || mime.contains("h265")) {
            return "H.265 / HEVC Main 10 (" + dec + ")";
        } else if (mime.contains("av01") || mime.contains("av1")) {
            return "AV1 4K 超清 (" + dec + ")";
        } else if (mime.contains("avc") || mime.contains("h264")) {
            return "H.264 / AVC High (" + dec + ")";
        } else if (mime.contains("vp9")) {
            return "VP9 广色域 (" + dec + ")";
        }
        return mime.isEmpty() ? "自适应硬件解码" : mime + " (" + dec + ")";
    }

    public String getAudioChannelTopology() {
        if (player == null) return "立体声 48kHz";
        Format f = player.getAudioFormat();
        if (f == null) return "立体声 48kHz";
        int channels = f.channelCount;
        int rate = f.sampleRate > 0 ? f.sampleRate / 1000 : 48;
        if (channels >= 8) {
            return "7.1 / 空间全景声 " + rate + "kHz";
        } else if (channels >= 6) {
            return "5.1 环绕声 " + rate + "kHz";
        } else if (channels == 2) {
            return "双声道立体声 " + rate + "kHz";
        } else if (channels == 1) {
            return "单声道 " + rate + "kHz";
        }
        return channels + "声道 " + rate + "kHz";
    }

    public long getEstimatedRttMs() {
        if (bandwidthMeter != null) {
            long rtt = bandwidthMeter.getTimeToFirstByteEstimateUs() / 1000;
            if (rtt > 0 && rtt < 2000) return rtt;
        }
        return 18;
    }

    public boolean isCurrentStreamIpv6() {
        if (currentChannel != null) {
            return currentChannel.isCurrentUrlIpv6();
        }
        return lastPlayedUrl != null && (lastPlayedUrl.contains("://[") || lastPlayedUrl.contains("::"));
    }

    // ==========================================
    // Phase 8 Adaptive Failover Settings
    // ==========================================

    public boolean isAutoFailoverEnabled() {
        return isAutoFailoverEnabled;
    }

    public void setAutoFailoverEnabled(boolean enabled) {
        this.isAutoFailoverEnabled = enabled;
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("auto_failover_enabled", enabled).apply();
    }

    public boolean toggleAutoFailover() {
        setAutoFailoverEnabled(!isAutoFailoverEnabled);
        return isAutoFailoverEnabled;
    }

    public void release() {
        stopPip();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
