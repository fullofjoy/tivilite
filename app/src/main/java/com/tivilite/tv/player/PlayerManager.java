package com.tivilite.tv.player;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;
import com.tivilite.tv.model.Channel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlayerManager {

    public interface PlaybackCallback {
        void onLoading(boolean isLoading);
        void onPlaybackReady();
        void onError(String message);
    }

    private final Context context;
    private ExoPlayer player;
    private PlaybackCallback callback;
    private Channel currentChannel;
    private boolean isCatchupMode = false;

    public PlayerManager(Context context) {
        this.context = context;
        initPlayer();
    }

    private void initPlayer() {
        // TiviMate-like low buffer configuration for 200-300ms channel switching
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        500,   // minBufferMs (0.5s starts playback immediately)
                        2500,  // maxBufferMs
                        250,   // bufferForPlaybackMs
                        500    // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
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
                if (callback != null) {
                    callback.onLoading(false);
                    callback.onError("播放异常: " + (error.getMessage() != null ? error.getMessage() : "源暂时无法访问"));
                }
            }
        });
    }

    public void attachPlayerView(PlayerView playerView) {
        playerView.setPlayer(player);
    }

    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    public void playChannel(Channel channel) {
        this.currentChannel = channel;
        this.isCatchupMode = false;
        if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
            if (callback != null) callback.onError("无效的频道播放地址");
            return;
        }

        playUrl(channel.getUrl());
    }

    public void playCatchup(Channel channel, long startUtc, long endUtc) {
        this.currentChannel = channel;
        this.isCatchupMode = true;

        if (channel == null) return;
        String rawUrl = channel.getUrl();
        if (rawUrl == null || rawUrl.isEmpty()) return;

        // Catch-up format: ?playseek=YYYYMMDDHHMMSS-YYYYMMDDHHMMSS
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        String startStr = sdf.format(new Date(startUtc));
        String endStr = sdf.format(new Date(endUtc));

        String catchupUrl;
        if (channel.getCatchupSource() != null && !channel.getCatchupSource().isEmpty()) {
            String template = channel.getCatchupSource();
            template = template.replace("{utc:YmdHMS}", startStr).replace("{utcend:YmdHMS}", endStr);
            catchupUrl = rawUrl.contains("?") ? (rawUrl + "&" + template.replace("?", "")) : (rawUrl + template);
        } else {
            String sep = rawUrl.contains("?") ? "&" : "?";
            catchupUrl = rawUrl + sep + "playseek=" + startStr + "-" + endStr;
        }

        playUrl(catchupUrl);
    }

    private void playUrl(String url) {
        if (callback != null) callback.onLoading(true);

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("TiviLite/1.0 (Android TV ExoPlayer)")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
                .setAllowCrossProtocolRedirects(true);

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        HlsMediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem);

        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    public boolean isCatchupMode() {
        return isCatchupMode;
    }

    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
