package com.tivilite.tv.player;

import android.content.Context;
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
import androidx.media3.ui.PlayerView;
import com.tivilite.tv.model.Channel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlayerManager {

    public static final int DECODER_MODE_HARDWARE = 0;
    public static final int DECODER_MODE_SOFTWARE = 1;

    public static final int ASPECT_RATIO_FIT = 0;   // 自适应 (保持原始比例)
    public static final int ASPECT_RATIO_FILL = 1;  // 全屏拉伸 (填满屏幕)
    public static final int ASPECT_RATIO_ZOOM = 2;  // 智能裁剪 (等比放大去除黑边)

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
    private PlayerView attachedPlayerView;

    public PlayerManager(Context context) {
        this.context = context;
        this.decoderMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("decoder_mode", DECODER_MODE_HARDWARE);
        this.aspectRatioMode = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getInt("aspect_ratio_mode", ASPECT_RATIO_FIT);
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

        // 2. High compatibility standard TV User-Agent to avoid 403 forbidden
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; TV Box Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpDataSourceFactory);

        // 3. Universal MediaSourceFactory: Automatically handles HLS, DASH, SmoothStreaming, and Progressive TS/MP4
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

        // 4. Staged buffer control: 500ms fast start + 15s steady state buffer to absorb Wi-Fi jitter
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        2500,   // minBufferMs
                        15000,  // maxBufferMs
                        500,    // bufferForPlaybackMs (instant start)
                        1500    // bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(5000, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .build();

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

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
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

    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
