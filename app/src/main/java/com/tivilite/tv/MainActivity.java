package com.tivilite.tv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.model.Program;
import com.tivilite.tv.parser.M3UParser;
import com.tivilite.tv.player.PlayerManager;
import com.tivilite.tv.server.WebPushServer;
import com.tivilite.tv.ui.ChannelAdapter;
import com.tivilite.tv.ui.EpgAdapter;
import com.tivilite.tv.ui.GroupAdapter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.tivilite.tv.ai.AiRecommendationManager;
import com.tivilite.tv.data.SmartChannelMatcher;
import com.tivilite.tv.data.SubscriptionManager;
import com.tivilite.tv.epg.EpgManager;
import com.tivilite.tv.epg.ReminderManager;
import com.tivilite.tv.cast.DlnaDmrManager;
import com.tivilite.tv.player.QosRadarManager;
import com.tivilite.tv.plugin.PluginManager;
import com.tivilite.tv.player.LanMulticastManager;
import com.tivilite.tv.player.PictureQualityManager;
import com.tivilite.tv.ui.RemindersAdapter;
import com.tivilite.tv.record.RecordingManager;
import com.tivilite.tv.sync.WebDavClient;
import com.tivilite.tv.ui.AiRecommendationsAdapter;
import com.tivilite.tv.voice.VoiceCommandManager;
import com.tivilite.tv.nas.SmbNasManager;
import com.tivilite.tv.network.MultiWanManager;
import com.tivilite.tv.ui.ThemeManager;
import com.tivilite.tv.subtitle.AiSubtitleManager;
import com.tivilite.tv.sync.WebRtcSyncManager;
import com.tivilite.tv.mesh.CloudMeshManager;
import com.tivilite.tv.ui.MeshDeviceAdapter;
import com.tivilite.tv.player.BlurayDolbyManager;
import com.tivilite.tv.ai.LocalAiAssistantManager;
import com.tivilite.tv.p2p.P2pEdgeAccelerationManager;
import com.tivilite.tv.device.DeviceFormFactorManager;
import com.tivilite.tv.touch.TouchGestureOverlayManager;
import com.tivilite.tv.network.CellularDataGuardManager;
import android.content.pm.ActivityInfo;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.view.MotionEvent;
import android.content.res.Configuration;
import android.os.Build;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ProgressBar loadingIndicator;
    private LinearLayout leftSidebar;
    private LinearLayout rightEpgPanel;
    private ConstraintLayout bottomOsd;
    private CardView webConfigDialog;

    private RecyclerView groupRecyclerView;
    private RecyclerView channelRecyclerView;
    private RecyclerView epgRecyclerView;
    private TextView epgTitle;
    private TextView epgSubtitle;

    private TextView osdChannelNum;
    private TextView osdChannelName;
    private TextView osdProgram;
    private TextView osdBadge;
    private TextView osdSource;
    private ImageView osdLogo;
    private TextView osdSubtitle;
    private TextView osdRecordBadge;
    private TextView webServerUrlText;
    private ImageView qrCodeImageView;

    private LinearLayout catchupSeekBarContainer;
    private ProgressBar catchupProgressBar;
    private TextView seekTimeText;
    private TextView channelNumberInputBadge;

    private CardView playbackErrorOverlay;
    private TextView errorTitle;
    private TextView errorDiagnostic;
    private TextView errorRetryStatus;

    private TextView settingProStatusText;
    private android.widget.Button btnViewProDetails;
    private android.widget.Button btnToggleDirection;
    private android.widget.Button btnCheckUpdate;
    private android.widget.Button btnToggleAutoBoot;
    private android.widget.Button btnToggleDecoder;
    private android.widget.Button btnToggleAspectRatio;
    private android.widget.Button btnToggleAudioTrack;
    private android.widget.Button btnToggleSubtitle;
    private android.widget.Button btnToggleBufferMode;
    private android.widget.Button btnToggleMiniDrawer;
    private android.widget.Button btnToggleRecording;
    private android.widget.Button btnRecordingsList;
    private android.widget.Button btnSwitchBestSource;
    private android.widget.Button btnWebDavSync;
    private android.widget.Button btnLanSync;
    private TextView osdAspect;
    private TextView osdAudio;
    private TextView subscriptionSummaryText;
    private CardView onboardingOverlay;
    private ImageView onboardingQrCode;
    private TextView onboardingServerUrl;
    private android.widget.Button btnOnboardingDemo;
    private LinearLayout remoteHintToast;
    private boolean invertUpDown = false;
    private boolean miniDrawerPreview = true;

    // Phase 6: PiP Multi-View
    private CardView pipCard;
    private PlayerView pipPlayerView;
    private TextView pipChannelTitle;
    private TextView pipAudioStatus;
    private TextView pipHint;
    private android.widget.Button btnTogglePip;
    private android.widget.Button btnSwapPip;

    // Phase 6: Voice HUD
    private CardView voiceHudBanner;
    private TextView voiceHudText;
    private final Runnable hideVoiceHudRunnable = () -> {
        if (voiceHudBanner != null) voiceHudBanner.setVisibility(View.GONE);
    };

    // Phase 6: AI Hot Recommendations
    private CardView dialogAiRecommendations;
    private RecyclerView aiRecyclerView;
    private android.widget.Button btnCloseAiDialog;
    private android.widget.Button btnAiRecommendations;
    private AiRecommendationsAdapter aiRecommendationsAdapter;

    // Phase 7: Audio Passthrough & Picture Quality
    private android.widget.Button btnToggleAudioMode;
    private android.widget.Button btnTogglePictureMode;
    private android.widget.Button btnViewReminders;
    private TextView osdAudioBadge;
    private TextView osdPictureBadge;

    // Phase 7: Reminder HUD & Dialog
    private CardView reminderHudBanner;
    private TextView reminderHudText;
    private CardView dialogReminders;
    private RecyclerView remindersRecyclerView;
    private TextView remindersEmptyText;
    private android.widget.Button btnCloseRemindersDialog;
    private RemindersAdapter remindersAdapter;
    private ReminderManager.ReminderItem currentAlertReminder;
    private final Runnable hideReminderHudRunnable = () -> {
        if (reminderHudBanner != null) reminderHudBanner.setVisibility(View.GONE);
        currentAlertReminder = null;
    };
    private final Runnable reminderCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            checkUpcomingReminders();
            handler.postDelayed(this, 15000);
        }
    };

    // Phase 8: QoS Radar Overlay & DLNA Cast
    private CardView qosRadarOverlay;
    private TextView qosDownloadSpeed;
    private TextView qosVideoSpec;
    private TextView qosVideoCodec;
    private TextView qosAudioSpec;
    private TextView qosBufferHealth;
    private TextView qosDroppedFrames;
    private TextView qosNetworkRtt;
    private TextView qosIpProtocol;
    private android.widget.Button btnToggleQosRadar;
    private android.widget.Button btnToggleAutoFailover;

    // Phase 9: Theme, Font Scale, NAS & Multi-WAN
    private android.widget.Button btnToggleTheme;
    private android.widget.Button btnToggleFontScale;
    private android.widget.Button btnScanNasMedia;
    private android.widget.Button btnToggleBonding;

    // Phase 10: AI Subtitles, WebRTC Sync & Cloud Mesh
    private android.widget.Button btnToggleAiSubtitle;
    private android.widget.Button btnToggleWebRtcSync;
    private android.widget.Button btnViewCloudMesh;
    private CardView subtitleHudBanner;
    private TextView subtitlePrimaryText;
    private TextView subtitleSecondaryText;
    private TextView osdSyncBadge;
    private CardView dialogCloudMesh;
    private TextView meshMyDeviceNameText;
    private RecyclerView meshRecyclerView;
    private TextView meshEmptyText;
    private android.widget.Button btnCloseMeshDialog;
    private MeshDeviceAdapter meshDeviceAdapter;

    // Phase 11: Blu-ray/Dolby Vision, P2P Edge & Local AI Assistant
    private android.widget.Button btnToggleDolbyMode;
    private android.widget.Button btnToggleP2pMode;
    private android.widget.Button btnAskAiAssistant;
    private CardView aiAssistantOverlay;
    private TextView aiAssistantPromptText;
    private TextView aiAssistantAnswerText;
    private android.widget.Button btnAiAssistantAction;
    private android.widget.Button btnCloseAiAssistant;
    private LocalAiAssistantManager.AiResponse latestAiResponse;

    // Stage 1: Mobile & Tablet Multi-Terminal Responsive
    private TouchGestureOverlayManager touchGestureManager;
    private CardView touchGestureHud;
    private TextView touchGestureIcon;
    private TextView touchGestureTitle;
    private ProgressBar touchGestureProgress;
    private CardView cellularGuardBanner;
    private TextView cellularGuardText;
    private TextView btnDismissCellularBanner;
    private TextView osdDeviceBadge;
    private TextView btnToggleOrientation;
    private TextView btnEnterPip;
    private android.widget.Button btnToggleCellularGuard;
    private Runnable hideTouchHudRunnable;

    private CardView castHudBanner;
    private TextView castTitleText;
    private TextView castSubtitleText;
    private DlnaDmrManager dlnaDmrManager;
    private Channel channelBeforeCast;

    private PlayerManager playerManager;
    private WebPushServer webServer;
    private ChannelAdapter channelAdapter;
    private GroupAdapter groupAdapter;
    private EpgAdapter epgAdapter;

    private List<Channel> allChannels = new ArrayList<>();
    private Map<String, List<Channel>> groupedChannels;
    private int currentChannelIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideOsdRunnable = () -> bottomOsd.setVisibility(View.GONE);
    private final Runnable hideSeekBarRunnable = () -> {
        if (catchupSeekBarContainer != null) catchupSeekBarContainer.setVisibility(View.GONE);
    };
    private final Runnable hideRemoteHintRunnable = () -> {
        if (remoteHintToast != null) remoteHintToast.setVisibility(View.GONE);
    };
    private final StringBuilder numberInputBuffer = new StringBuilder();
    private final Runnable commitNumberInputRunnable = this::commitNumberInput;
    private long lastBackPressTime = 0;

    private boolean okKeyLongPressed = false;
    private final Runnable okLongPressRunnable = () -> {
        okKeyLongPressed = true;
        if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
            Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
            if (ch != null) {
                toggleChannelFavorite(ch);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep screen on continuously to prevent TV system screensaver or sleep timeout
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        DeviceFormFactorManager formFactorManager = DeviceFormFactorManager.getInstance(this);
        if (formFactorManager.isTv()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        }

        invertUpDown = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("invert_up_down", false);
        com.tivilite.tv.license.LicenseManager.startTrial(this);

        initViews();
        if (formFactorManager.shouldEnableTouchGestures()) {
            initTouchGestures();
        }
        initPlayer();
        initWebServer();
        loadChannels();

        // Silent background update check after app launch
        handler.postDelayed(() -> com.tivilite.tv.update.UpdateManager.checkUpdate(this, false), 4000);

        // Auto WebDAV Cloud Sync on startup if enabled
        if (WebDavClient.isWebDavAutoSync(this)) {
            String davUrl = WebDavClient.getWebDavUrl(this);
            if (!davUrl.isEmpty()) {
                new Thread(() -> {
                    WebDavClient.SyncResult res = WebDavClient.downloadAndRestore(
                            MainActivity.this,
                            davUrl,
                            WebDavClient.getWebDavUser(MainActivity.this),
                            WebDavClient.getWebDavPass(MainActivity.this)
                    );
                    if (res.success) {
                        handler.post(() -> {
                            applyAggregatedSubscriptions();
                            updateSubscriptionSummary();
                        });
                    }
                }).start();
            }
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        if (playerView != null) {
            playerView.setKeepScreenOn(true);
        }
        loadingIndicator = findViewById(R.id.loadingIndicator);
        leftSidebar = findViewById(R.id.leftSidebar);
        rightEpgPanel = findViewById(R.id.rightEpgPanel);
        bottomOsd = findViewById(R.id.bottomOsd);
        webConfigDialog = findViewById(R.id.webConfigDialog);
        catchupSeekBarContainer = findViewById(R.id.catchupSeekBarContainer);
        catchupProgressBar = findViewById(R.id.catchupProgressBar);
        seekTimeText = findViewById(R.id.seekTimeText);
        channelNumberInputBadge = findViewById(R.id.channelNumberInputBadge);

        groupRecyclerView = findViewById(R.id.groupRecyclerView);
        channelRecyclerView = findViewById(R.id.channelRecyclerView);
        epgRecyclerView = findViewById(R.id.epgRecyclerView);
        epgTitle = findViewById(R.id.epgTitle);
        epgSubtitle = findViewById(R.id.epgSubtitle);

        osdChannelNum = findViewById(R.id.osdChannelNum);
        osdChannelName = findViewById(R.id.osdChannelName);
        osdProgram = findViewById(R.id.osdProgram);
        osdBadge = findViewById(R.id.osdBadge);
        osdSource = findViewById(R.id.osdSource);
        osdLogo = findViewById(R.id.osdLogo);
        webServerUrlText = findViewById(R.id.webServerUrlText);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);

        playbackErrorOverlay = findViewById(R.id.playbackErrorOverlay);
        errorTitle = findViewById(R.id.errorTitle);
        errorDiagnostic = findViewById(R.id.errorDiagnostic);
        errorRetryStatus = findViewById(R.id.errorRetryStatus);

        settingProStatusText = findViewById(R.id.settingProStatusText);
        btnViewProDetails = findViewById(R.id.btnViewProDetails);
        btnToggleDirection = findViewById(R.id.btnToggleDirection);

        if (btnToggleDirection != null) {
            updateDirectionButtonText();
            btnToggleDirection.setOnClickListener(v -> {
                invertUpDown = !invertUpDown;
                getSharedPreferences("tivilite_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("invert_up_down", invertUpDown)
                        .apply();
                updateDirectionButtonText();
                Toast.makeText(this, invertUpDown ? "已开启切台反转模式 (▲下一台 / ▼上一台)" : "已恢复正常切台模式 (▲上一台 / ▼下一台)", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnViewProDetails != null) {
            btnViewProDetails.setOnClickListener(v -> showProDialog());
        }

        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setText("🔄 检查在线新版本更新 (当前: v" + BuildConfig.VERSION_NAME + ")");
            btnCheckUpdate.setOnClickListener(v -> {
                com.tivilite.tv.update.UpdateManager.checkUpdate(this, true);
            });
        }

        btnToggleAutoBoot = findViewById(R.id.btnToggleAutoBoot);
        if (btnToggleAutoBoot != null) {
            updateAutoBootButtonText();
            btnToggleAutoBoot.setOnClickListener(v -> {
                boolean cur = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("auto_boot_start", true);
                boolean next = !cur;
                getSharedPreferences("tivilite_prefs", MODE_PRIVATE).edit().putBoolean("auto_boot_start", next).apply();
                updateAutoBootButtonText();
                Toast.makeText(this, next ? "🚀 已开启开机自动进入直播" : "已关闭开机自启", Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleQosRadar = findViewById(R.id.btnToggleQosRadar);
        if (btnToggleQosRadar != null) {
            updateQosRadarButtonText();
            btnToggleQosRadar.setOnClickListener(v -> toggleQosRadar());
        }

        btnToggleAutoFailover = findViewById(R.id.btnToggleAutoFailover);
        if (btnToggleAutoFailover != null) {
            updateAutoFailoverButtonText();
            btnToggleAutoFailover.setOnClickListener(v -> toggleAutoFailover());
        }

        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        if (btnToggleTheme != null) {
            updateThemeButtonText();
            btnToggleTheme.setOnClickListener(v -> toggleTheme());
        }

        btnToggleFontScale = findViewById(R.id.btnToggleFontScale);
        if (btnToggleFontScale != null) {
            updateFontScaleButtonText();
            btnToggleFontScale.setOnClickListener(v -> toggleFontScale());
        }

        btnScanNasMedia = findViewById(R.id.btnScanNasMedia);
        if (btnScanNasMedia != null) {
            btnScanNasMedia.setOnClickListener(v -> scanNasMedia(true));
        }

        btnToggleBonding = findViewById(R.id.btnToggleBonding);
        if (btnToggleBonding != null) {
            updateBondingButtonText();
            btnToggleBonding.setOnClickListener(v -> toggleBonding());
        }

        btnToggleAiSubtitle = findViewById(R.id.btnToggleAiSubtitle);
        if (btnToggleAiSubtitle != null) {
            updateAiSubtitleButtonText();
            btnToggleAiSubtitle.setOnClickListener(v -> toggleAiSubtitle());
        }

        btnToggleWebRtcSync = findViewById(R.id.btnToggleWebRtcSync);
        if (btnToggleWebRtcSync != null) {
            updateWebRtcSyncButtonText();
            btnToggleWebRtcSync.setOnClickListener(v -> toggleWebRtcSync());
        }

        btnViewCloudMesh = findViewById(R.id.btnViewCloudMesh);
        if (btnViewCloudMesh != null) {
            btnViewCloudMesh.setOnClickListener(v -> showCloudMeshDialog());
        }

        btnToggleDolbyMode = findViewById(R.id.btnToggleDolbyMode);
        if (btnToggleDolbyMode != null) {
            updateDolbyButtonText();
            btnToggleDolbyMode.setOnClickListener(v -> toggleDolbyMode());
        }

        btnToggleP2pMode = findViewById(R.id.btnToggleP2pMode);
        if (btnToggleP2pMode != null) {
            updateP2pButtonText();
            btnToggleP2pMode.setOnClickListener(v -> toggleP2pMode());
        }

        btnAskAiAssistant = findViewById(R.id.btnAskAiAssistant);
        if (btnAskAiAssistant != null) {
            btnAskAiAssistant.setOnClickListener(v -> showAiAssistantDialog("90年代周星驰经典喜剧电影"));
        }

        aiAssistantOverlay = findViewById(R.id.aiAssistantOverlay);
        aiAssistantPromptText = findViewById(R.id.aiAssistantPromptText);
        aiAssistantAnswerText = findViewById(R.id.aiAssistantAnswerText);
        btnAiAssistantAction = findViewById(R.id.btnAiAssistantAction);
        btnCloseAiAssistant = findViewById(R.id.btnCloseAiAssistant);
        if (btnCloseAiAssistant != null) {
            btnCloseAiAssistant.setOnClickListener(v -> {
                if (aiAssistantOverlay != null) aiAssistantOverlay.setVisibility(View.GONE);
            });
        }
        if (btnAiAssistantAction != null) {
            btnAiAssistantAction.setOnClickListener(v -> {
                if (latestAiResponse != null) {
                    if ("PLAY_CHANNEL".equals(latestAiResponse.action) && latestAiResponse.targetChannel != null) {
                        playSelectedChannel(latestAiResponse.targetChannel);
                    } else if ("PLAY_NAS".equals(latestAiResponse.action) && latestAiResponse.targetNasPath != null) {
                        Channel ch = new Channel(9999, "🎬 " + latestAiResponse.prompt, "📁 家庭 NAS 影视库", latestAiResponse.targetNasPath);
                        playSelectedChannel(ch);
                    }
                    if (aiAssistantOverlay != null) aiAssistantOverlay.setVisibility(View.GONE);
                }
            });
        }

        subtitleHudBanner = findViewById(R.id.subtitleHudBanner);
        subtitlePrimaryText = findViewById(R.id.subtitlePrimaryText);
        subtitleSecondaryText = findViewById(R.id.subtitleSecondaryText);
        osdSyncBadge = findViewById(R.id.osdSyncBadge);

        dialogCloudMesh = findViewById(R.id.dialogCloudMesh);
        meshMyDeviceNameText = findViewById(R.id.meshMyDeviceNameText);
        meshRecyclerView = findViewById(R.id.meshRecyclerView);
        meshEmptyText = findViewById(R.id.meshEmptyText);
        btnCloseMeshDialog = findViewById(R.id.btnCloseMeshDialog);
        if (btnCloseMeshDialog != null) {
            btnCloseMeshDialog.setOnClickListener(v -> {
                if (dialogCloudMesh != null) dialogCloudMesh.setVisibility(View.GONE);
            });
        }

        // Stage 1: Mobile & Tablet Multi-Terminal Responsive
        touchGestureHud = findViewById(R.id.touchGestureHud);
        touchGestureIcon = findViewById(R.id.touchGestureIcon);
        touchGestureTitle = findViewById(R.id.touchGestureTitle);
        touchGestureProgress = findViewById(R.id.touchGestureProgress);

        cellularGuardBanner = findViewById(R.id.cellularGuardBanner);
        cellularGuardText = findViewById(R.id.cellularGuardText);
        btnDismissCellularBanner = findViewById(R.id.btnDismissCellularBanner);
        if (btnDismissCellularBanner != null) {
            btnDismissCellularBanner.setOnClickListener(v -> {
                if (cellularGuardBanner != null) cellularGuardBanner.setVisibility(View.GONE);
            });
        }

        osdDeviceBadge = findViewById(R.id.osdDeviceBadge);
        if (osdDeviceBadge != null) {
            osdDeviceBadge.setText(DeviceFormFactorManager.getInstance(this).getFormFactorName());
            osdDeviceBadge.setOnClickListener(v -> {
                Toast.makeText(this, DeviceFormFactorManager.getInstance(this).getDeviceModelInfo(), Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleOrientation = findViewById(R.id.btnToggleOrientation);
        if (btnToggleOrientation != null) {
            btnToggleOrientation.setOnClickListener(v -> toggleOrientation());
        }

        btnEnterPip = findViewById(R.id.btnEnterPip);
        if (btnEnterPip != null) {
            btnEnterPip.setOnClickListener(v -> enterSystemPip());
        }

        btnToggleCellularGuard = findViewById(R.id.btnToggleCellularGuard);
        if (btnToggleCellularGuard != null) {
            updateCellularGuardButtonText();
            btnToggleCellularGuard.setOnClickListener(v -> toggleCellularGuard());
        }

        checkCellularDataAlert();

        AiSubtitleManager.getInstance(this).setSubtitleListener(new AiSubtitleManager.SubtitleListener() {
            @Override
            public void onSubtitleUpdated(String primaryText, String secondaryText) {
                if (subtitleHudBanner != null) {
                    subtitleHudBanner.setVisibility(View.VISIBLE);
                }
                if (subtitlePrimaryText != null) {
                    subtitlePrimaryText.setText(primaryText);
                }
                if (subtitleSecondaryText != null) {
                    if (secondaryText != null && !secondaryText.isEmpty()) {
                        subtitleSecondaryText.setText(secondaryText);
                        subtitleSecondaryText.setVisibility(View.VISIBLE);
                    } else {
                        subtitleSecondaryText.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onSubtitleCleared() {
                if (subtitleHudBanner != null) {
                    subtitleHudBanner.setVisibility(View.GONE);
                }
            }
        });

        CloudMeshManager.getInstance(this).setHandoffListener((chName, chUrl, pts) -> {
            if (chUrl == null || chUrl.isEmpty()) return;
            Channel target = null;
            if (allChannels != null) {
                for (Channel c : allChannels) {
                    if (chUrl.equals(c.getUrl()) || (chName != null && chName.equals(c.getName()))) {
                        target = c;
                        break;
                    }
                }
            }
            if (target == null) {
                target = new Channel(8888, chName != null ? chName : "跨屏流转", "☁️ 局域网自组网接力", chUrl);
            }
            playSelectedChannel(target);
            if (pts > 0 && playerManager != null) {
                playerManager.seekTo(pts);
            }
            Toast.makeText(MainActivity.this, "📲 收到跨屏流转，接力播放: " + target.getName(), Toast.LENGTH_LONG).show();
        });

        qosRadarOverlay = findViewById(R.id.qosRadarOverlay);
        qosDownloadSpeed = findViewById(R.id.qosDownloadSpeed);
        qosVideoSpec = findViewById(R.id.qosVideoSpec);
        qosVideoCodec = findViewById(R.id.qosVideoCodec);
        qosAudioSpec = findViewById(R.id.qosAudioSpec);
        qosBufferHealth = findViewById(R.id.qosBufferHealth);
        qosDroppedFrames = findViewById(R.id.qosDroppedFrames);
        qosNetworkRtt = findViewById(R.id.qosNetworkRtt);
        qosIpProtocol = findViewById(R.id.qosIpProtocol);

        castHudBanner = findViewById(R.id.castHudBanner);
        castTitleText = findViewById(R.id.castTitleText);
        castSubtitleText = findViewById(R.id.castSubtitleText);

        btnToggleDecoder = findViewById(R.id.btnToggleDecoder);
        if (btnToggleDecoder != null) {
            updateDecoderButtonText();
            btnToggleDecoder.setOnClickListener(v -> {
                if (playerManager == null) return;
                int cur = playerManager.getDecoderMode();
                int next = (cur == PlayerManager.DECODER_MODE_HARDWARE) ? PlayerManager.DECODER_MODE_SOFTWARE : PlayerManager.DECODER_MODE_HARDWARE;
                playerManager.setDecoderMode(next);
                updateDecoderButtonText();
                Toast.makeText(this, next == PlayerManager.DECODER_MODE_SOFTWARE
                        ? "🎬 已切换为软件兼容解码模式 (防闪退花屏)"
                        : "🎬 已切换为硬件加速解码模式 (推荐流畅省电)", Toast.LENGTH_SHORT).show();
            });
        }

        osdAspect = findViewById(R.id.osdAspect);
        osdAudio = findViewById(R.id.osdAudio);
        osdSubtitle = findViewById(R.id.osdSubtitle);
        osdRecordBadge = findViewById(R.id.osdRecordBadge);
        osdAudioBadge = findViewById(R.id.osdAudioBadge);
        osdPictureBadge = findViewById(R.id.osdPictureBadge);

        miniDrawerPreview = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("mini_drawer_preview", true);

        btnToggleAspectRatio = findViewById(R.id.btnToggleAspectRatio);
        if (btnToggleAspectRatio != null) {
            updateAspectRatioButtonText();
            btnToggleAspectRatio.setOnClickListener(v -> {
                if (playerManager == null) return;
                String mode = playerManager.toggleNextAspectRatio();
                updateAspectRatioButtonText();
                if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                Toast.makeText(this, "📐 已切换画面比例: " + mode, Toast.LENGTH_SHORT).show();
            });
        }

        btnTogglePictureMode = findViewById(R.id.btnTogglePictureMode);
        if (btnTogglePictureMode != null) {
            updatePictureModeButtonText();
            btnTogglePictureMode.setOnClickListener(v -> {
                PictureQualityManager.getInstance(this).cycleNextMode(playerView);
                updatePictureModeButtonText();
                if (playerManager != null && playerManager.getCurrentChannel() != null) {
                    updateOsd(playerManager.getCurrentChannel());
                }
                Toast.makeText(this, "🎛️ 图像画质已切换至: " + PictureQualityManager.getInstance(this).getModeName(), Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleAudioTrack = findViewById(R.id.btnToggleAudioTrack);
        if (btnToggleAudioTrack != null) {
            updateAudioTrackButtonText();
            btnToggleAudioTrack.setOnClickListener(v -> {
                if (playerManager == null) return;
                String t = playerManager.toggleNextAudioTrack();
                if (t != null) {
                    updateAudioTrackButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    Toast.makeText(this, "🔊 已切换音轨: " + t, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "当前视频流仅有 1 组默认音轨", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnToggleAudioMode = findViewById(R.id.btnToggleAudioMode);
        if (btnToggleAudioMode != null) {
            updateAudioModeButtonText();
            btnToggleAudioMode.setOnClickListener(v -> {
                if (playerManager == null) return;
                String am = playerManager.toggleNextAudioMode();
                updateAudioModeButtonText();
                if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                Toast.makeText(this, "🔊 音频模式: " + am, Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleSubtitle = findViewById(R.id.btnToggleSubtitle);
        if (btnToggleSubtitle != null) {
            updateSubtitleButtonText();
            btnToggleSubtitle.setOnClickListener(v -> {
                if (playerManager == null) return;
                String sub = playerManager.toggleNextSubtitleTrack();
                updateSubtitleButtonText();
                if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                Toast.makeText(this, "💬 " + sub, Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleBufferMode = findViewById(R.id.btnToggleBufferMode);
        if (btnToggleBufferMode != null) {
            updateBufferModeButtonText();
            btnToggleBufferMode.setOnClickListener(v -> {
                if (playerManager == null) return;
                String buf = playerManager.toggleNextBufferMode();
                updateBufferModeButtonText();
                Toast.makeText(this, "🛡️ " + buf, Toast.LENGTH_SHORT).show();
            });
        }

        btnToggleRecording = findViewById(R.id.btnToggleRecording);
        if (btnToggleRecording != null) {
            updateRecordingButtonText();
            btnToggleRecording.setOnClickListener(v -> toggleLiveRecording());
        }

        btnRecordingsList = findViewById(R.id.btnRecordingsList);
        if (btnRecordingsList != null) {
            updateRecordingsListButtonText();
            btnRecordingsList.setOnClickListener(v -> showRecordingsDialog());
        }

        // Phase 6: PiP Multi-View
        pipCard = findViewById(R.id.pipCard);
        pipPlayerView = findViewById(R.id.pipPlayerView);
        pipChannelTitle = findViewById(R.id.pipChannelTitle);
        pipAudioStatus = findViewById(R.id.pipAudioStatus);
        pipHint = findViewById(R.id.pipHint);

        if (pipCard != null) {
            pipCard.setOnClickListener(v -> swapPipAndMain());
            pipCard.setOnLongClickListener(v -> {
                togglePipAudio();
                return true;
            });
        }

        btnTogglePip = findViewById(R.id.btnTogglePip);
        if (btnTogglePip != null) {
            updatePipButtonsText();
            btnTogglePip.setOnClickListener(v -> togglePip());
        }

        btnSwapPip = findViewById(R.id.btnSwapPip);
        if (btnSwapPip != null) {
            btnSwapPip.setOnClickListener(v -> {
                if (playerManager != null && playerManager.isPipActive()) {
                    swapPipAndMain();
                } else {
                    Toast.makeText(this, "当前未开启画中画，请先开启副画面", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Phase 6: AI Hot Recommendations
        dialogAiRecommendations = findViewById(R.id.dialogAiRecommendations);
        aiRecyclerView = findViewById(R.id.aiRecyclerView);
        btnCloseAiDialog = findViewById(R.id.btnCloseAiDialog);
        if (btnCloseAiDialog != null) {
            btnCloseAiDialog.setOnClickListener(v -> {
                if (dialogAiRecommendations != null) dialogAiRecommendations.setVisibility(View.GONE);
            });
        }

        btnAiRecommendations = findViewById(R.id.btnAiRecommendations);
        if (btnAiRecommendations != null) {
            btnAiRecommendations.setOnClickListener(v -> showAiRecommendationsDialog());
        }

        // Phase 7: Reminders List Button & Dialogs
        btnViewReminders = findViewById(R.id.btnViewReminders);
        if (btnViewReminders != null) {
            updateRemindersButtonText();
            btnViewReminders.setOnClickListener(v -> showRemindersDialog());
        }

        reminderHudBanner = findViewById(R.id.reminderHudBanner);
        reminderHudText = findViewById(R.id.reminderHudText);
        dialogReminders = findViewById(R.id.dialogReminders);
        remindersRecyclerView = findViewById(R.id.remindersRecyclerView);
        remindersEmptyText = findViewById(R.id.remindersEmptyText);
        btnCloseRemindersDialog = findViewById(R.id.btnCloseRemindersDialog);
        if (btnCloseRemindersDialog != null) {
            btnCloseRemindersDialog.setOnClickListener(v -> {
                if (dialogReminders != null) dialogReminders.setVisibility(View.GONE);
            });
        }

        if (playerView != null) {
            PictureQualityManager.getInstance(this).applyModeToView(playerView);
        }

        // Phase 6: Voice HUD
        voiceHudBanner = findViewById(R.id.voiceHudBanner);
        voiceHudText = findViewById(R.id.voiceHudText);

        btnWebDavSync = findViewById(R.id.btnWebDavSync);
        if (btnWebDavSync != null) {
            btnWebDavSync.setOnClickListener(v -> showWebDavDialog());
        }

        btnToggleMiniDrawer = findViewById(R.id.btnToggleMiniDrawer);
        if (btnToggleMiniDrawer != null) {
            updateMiniDrawerButtonText();
            btnToggleMiniDrawer.setOnClickListener(v -> {
                miniDrawerPreview = !miniDrawerPreview;
                getSharedPreferences("tivilite_prefs", MODE_PRIVATE).edit().putBoolean("mini_drawer_preview", miniDrawerPreview).apply();
                updateMiniDrawerButtonText();
                Toast.makeText(this, miniDrawerPreview ? "🎬 已开启抽屉边看边选小窗微缩" : "已关闭小窗微缩 (全屏背景)", Toast.LENGTH_SHORT).show();
            });
        }

        btnSwitchBestSource = findViewById(R.id.btnSwitchBestSource);
        if (btnSwitchBestSource != null) {
            btnSwitchBestSource.setOnClickListener(v -> {
                if (playerManager == null || playerManager.getCurrentChannel() == null) return;
                Toast.makeText(this, "⚡ 正在并发测速并优选最佳线路...", Toast.LENGTH_SHORT).show();
                playerManager.switchToBestSource();
            });
        }

        btnLanSync = findViewById(R.id.btnLanSync);
        if (btnLanSync != null) {
            btnLanSync.setOnClickListener(v -> showLanSyncDialog());
        }

        subscriptionSummaryText = findViewById(R.id.subscriptionSummaryText);
        updateSubscriptionSummary();

        View btnCloseDialog = findViewById(R.id.btnCloseDialog);
        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> {
                if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
            });
        }

        onboardingOverlay = findViewById(R.id.onboardingOverlay);
        onboardingQrCode = findViewById(R.id.onboardingQrCode);
        onboardingServerUrl = findViewById(R.id.onboardingServerUrl);
        btnOnboardingDemo = findViewById(R.id.btnOnboardingDemo);
        remoteHintToast = findViewById(R.id.remoteHintToast);

        groupRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        channelRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        epgRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        channelAdapter = new ChannelAdapter(this::playSelectedChannel);
        channelAdapter.setOnChannelLongClickListener(this::toggleChannelFavorite);
        channelRecyclerView.setAdapter(channelAdapter);

        epgAdapter = new EpgAdapter(program -> {
            Channel ch = playerManager.getCurrentChannel();
            if (ch != null) {
                long now = System.currentTimeMillis();
                if (program.getStartTime() > now) {
                    boolean added = ReminderManager.getInstance(this).toggleReminder(ch.getName(), ch.getUrl(), program);
                    Toast.makeText(this, added ? ("⏰ 已预约提醒：" + program.getTitle() + "\n开播前大屏将弹窗提醒您切换") : ("已取消预约：" + program.getTitle()), Toast.LENGTH_SHORT).show();
                    epgAdapter.notifyDataSetChanged();
                    updateRemindersButtonText();
                    return;
                }
                if (program.isCatchup() && !com.tivilite.tv.license.LicenseManager.isPro(this)) {
                    showProDialog();
                    return;
                }
                if (!program.isCatchup()) {
                    playerManager.playChannel(ch);
                    showOsd();
                    rightEpgPanel.setVisibility(View.GONE);
                    Toast.makeText(this, "📺 已退出回看，恢复实时直播", Toast.LENGTH_SHORT).show();
                    return;
                }
                playerManager.playCatchup(ch, program.getStartTime(), program.getEndTime());
                showOsd();
                rightEpgPanel.setVisibility(View.GONE);
                Toast.makeText(this, "正在加载回看: " + program.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });
        epgRecyclerView.setAdapter(epgAdapter);
        applyCurrentTheme();
    }

    private void updateDirectionButtonText() {
        if (btnToggleDirection != null) {
            btnToggleDirection.setText(invertUpDown
                    ? "🎮 遥控切台方向: 反转模式 (▲下一台 / ▼上一台) - 点击切换"
                    : "🎮 遥控切台方向: 正常模式 (▲上一台 / ▼下一台) - 点击切换");
        }
    }

    private void updateAutoBootButtonText() {
        if (btnToggleAutoBoot != null) {
            boolean autoBoot = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("auto_boot_start", true);
            btnToggleAutoBoot.setText(autoBoot
                    ? "🚀 开机自动直达直播: 已开启 - 点击切换"
                    : "🚀 开机自动直达直播: 已关闭 - 点击切换");
        }
    }

    private void updateDecoderButtonText() {
        if (btnToggleDecoder != null && playerManager != null) {
            boolean isSoftware = playerManager.getDecoderMode() == PlayerManager.DECODER_MODE_SOFTWARE;
            btnToggleDecoder.setText(isSoftware
                    ? "🎬 解码器模式: 软件兼容解码 (防闪退花屏) - 点击切换"
                    : "🎬 解码器模式: 硬件加速解码 (推荐流畅低耗) - 点击切换");
        }
    }

    private void updateAspectRatioButtonText() {
        if (btnToggleAspectRatio != null && playerManager != null) {
            btnToggleAspectRatio.setText("📐 画面比例: " + playerManager.getAspectRatioModeText() + " - 点击切换");
        }
    }

    private void updateAudioTrackButtonText() {
        if (btnToggleAudioTrack != null && playerManager != null) {
            btnToggleAudioTrack.setText("🔊 音轨/声道: " + playerManager.getCurrentAudioTrackDesc() + " - 点击切换");
        }
    }

    private void updateMiniDrawerButtonText() {
        if (btnToggleMiniDrawer != null) {
            btnToggleMiniDrawer.setText(miniDrawerPreview
                    ? "🎬 抽屉边看边选小窗微缩: 已开启 - 点击切换"
                    : "🎬 抽屉边看边选小窗微缩: 已关闭 - 点击切换");
        }
    }

    private void updateSubtitleButtonText() {
        if (btnToggleSubtitle != null && playerManager != null) {
            btnToggleSubtitle.setText("💬 字幕/无障碍: " + playerManager.getCurrentSubtitleDesc() + " - 点击切换");
        }
    }

    private void updateBufferModeButtonText() {
        if (btnToggleBufferMode != null && playerManager != null) {
            btnToggleBufferMode.setText("🛡️ 缓冲策略: " + playerManager.getBufferModeText() + " - 点击切换");
        }
    }

    private void updateRecordingButtonText() {
        if (btnToggleRecording != null) {
            boolean rec = RecordingManager.getInstance().isRecording();
            btnToggleRecording.setText(rec
                    ? "🔴 实时节目录制 (DVR): 正在录制中 - 点击停止"
                    : "🔴 实时节目录制 (DVR): 未录制 - 点击开始");
        }
    }

    private void updateRecordingsListButtonText() {
        if (btnRecordingsList != null) {
            List<RecordingManager.RecordingItem> list = RecordingManager.getRecordings(this);
            btnRecordingsList.setText("📁 已录制节目管理与查看 (共 " + list.size() + " 个)");
        }
    }

    private void updatePipButtonsText() {
        if (btnTogglePip != null) {
            boolean active = playerManager != null && playerManager.isPipActive();
            btnTogglePip.setText(active ? "🖼️ 画中画双流分屏: 正在同屏播放 - 点击关闭" : "🖼️ 画中画双流分屏: 已关闭 - 点击开启");
        }
    }

    private void togglePip() {
        if (playerManager == null) return;
        if (playerManager.isPipActive()) {
            playerManager.stopPip();
            if (pipCard != null) pipCard.setVisibility(View.GONE);
            updatePipButtonsText();
            Toast.makeText(this, "🖼️ 画中画同屏已关闭", Toast.LENGTH_SHORT).show();
        } else {
            if (allChannels == null || allChannels.isEmpty()) {
                Toast.makeText(this, "暂无可用频道，无法开启画中画", Toast.LENGTH_SHORT).show();
                return;
            }
            int pipIdx = (currentChannelIndex + 1) % allChannels.size();
            Channel pipCh = allChannels.get(pipIdx);
            startPipWithChannel(pipCh);
        }
    }

    private void startPipWithChannel(Channel pipCh) {
        if (playerManager == null || pipCh == null || pipPlayerView == null || pipCard == null) return;
        playerManager.startPip(pipCh, pipPlayerView);
        pipCard.setVisibility(View.VISIBLE);
        if (pipChannelTitle != null) pipChannelTitle.setText(pipCh.getName() + " (副画面)");
        if (pipAudioStatus != null) pipAudioStatus.setText(playerManager.isPipAudioMain() ? "🔇 静音" : "🔊 发声中");
        updatePipButtonsText();
        Toast.makeText(this, "🖼️ 画中画已开启同屏: " + pipCh.getName(), Toast.LENGTH_SHORT).show();
    }

    private void swapPipAndMain() {
        if (playerManager == null || !playerManager.isPipActive()) return;
        Channel oldPip = playerManager.getPipChannel();
        playerManager.swapPipAndMain();
        if (oldPip != null) {
            for (int i = 0; i < allChannels.size(); i++) {
                if (allChannels.get(i).getNumber() == oldPip.getNumber()) {
                    currentChannelIndex = i;
                    break;
                }
            }
            updateOsd(oldPip);
        }
        Channel newPip = playerManager.getPipChannel();
        if (pipChannelTitle != null && newPip != null) {
            pipChannelTitle.setText(newPip.getName() + " (副画面)");
        }
        Toast.makeText(this, "🔄 主副画面已即时对调！", Toast.LENGTH_SHORT).show();
    }

    private void togglePipAudio() {
        if (playerManager == null || !playerManager.isPipActive()) return;
        boolean mainActive = playerManager.togglePipAudio();
        if (pipAudioStatus != null) {
            pipAudioStatus.setText(mainActive ? "🔇 静音" : "🔊 发声中");
        }
        Toast.makeText(this, mainActive ? "🔊 已将声音切换至【主画面】" : "🔊 已将声音切换至【画中画副画面】", Toast.LENGTH_SHORT).show();
    }

    private void showVoiceHud(String message) {
        if (voiceHudBanner == null || voiceHudText == null) return;
        handler.removeCallbacks(hideVoiceHudRunnable);
        voiceHudText.setText(message);
        voiceHudBanner.setVisibility(View.VISIBLE);
        handler.postDelayed(hideVoiceHudRunnable, 3000);
    }

    private void handleVoiceCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) return;
        VoiceCommandManager.VoiceResult res = VoiceCommandManager.parse(rawCommand, allChannels);
        showVoiceHud(res.feedbackText);

        switch (res.action) {
            case SWITCH_CHANNEL:
                if (res.targetChannel != null) {
                    for (int i = 0; i < allChannels.size(); i++) {
                        if (allChannels.get(i).getNumber() == res.targetChannel.getNumber()) {
                            playChannelAt(i);
                            return;
                        }
                    }
                    playerManager.playChannel(res.targetChannel);
                    updateOsd(res.targetChannel);
                } else if (res.targetNumber > 0) {
                    handleNumberInput(res.targetNumber);
                }
                break;
            case VOLUME_UP:
                android.media.AudioManager amUp = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (amUp != null) amUp.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
                break;
            case VOLUME_DOWN:
                android.media.AudioManager amDown = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (amDown != null) amDown.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
                break;
            case VOLUME_MUTE:
                android.media.AudioManager amMute = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (amMute != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        amMute.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE, android.media.AudioManager.FLAG_SHOW_UI);
                    } else {
                        boolean isM = amMute.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0;
                        amMute.setStreamMute(android.media.AudioManager.STREAM_MUSIC, !isM);
                    }
                }
                break;
            case ASPECT_RATIO:
                if (playerManager != null) {
                    String m = playerManager.toggleNextAspectRatio();
                    updateAspectRatioButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                }
                break;
            case RECORD_TOGGLE:
                toggleLiveRecording();
                break;
            case PIP_TOGGLE:
                togglePip();
                break;
            case PIP_SWAP:
                swapPipAndMain();
                break;
            case PIP_AUDIO:
                togglePipAudio();
                break;
            case SUBTITLE_TOGGLE:
                if (playerManager != null) {
                    String subDesc = playerManager.toggleNextSubtitleTrack();
                    updateSubtitleButtonText();
                    if (osdSubtitle != null) osdSubtitle.setText(playerManager.isSubtitlesEnabled() ? "💬 字幕: 开" : "💬 字幕: 关");
                    Toast.makeText(this, "💬 " + subDesc, Toast.LENGTH_SHORT).show();
                }
                break;
            case PICTURE_MODE:
                PictureQualityManager.getInstance(this).cycleNextMode(playerView);
                updatePictureModeButtonText();
                if (playerManager != null && playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                break;
            case AUDIO_MODE:
                if (playerManager != null) {
                    playerManager.toggleNextAudioMode();
                    updateAudioModeButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                }
                break;
            case SHOW_REMINDERS:
                showRemindersDialog();
                break;
            case SHOW_RECOMMENDATIONS:
                showAiRecommendationsDialog();
                break;
            case FAILOVER:
                if (playerManager != null) playerManager.switchToBestSource();
                break;
            case QOS_RADAR:
                toggleQosRadar();
                break;
            case AUTO_FAILOVER:
                toggleAutoFailover();
                break;
            case EXIT_CAST:
                exitCast();
                break;
            case THEME_TOGGLE:
                toggleTheme();
                break;
            case FONT_SCALE_TOGGLE:
                toggleFontScale();
                break;
            case SCAN_NAS:
                scanNasMedia(true);
                break;
            case BONDING_MODE_TOGGLE:
                toggleBonding();
                break;
            case AI_SUBTITLE_TOGGLE:
                toggleAiSubtitle();
                break;
            case WEBRTC_SYNC_TOGGLE:
                toggleWebRtcSync();
                break;
            case CLOUD_MESH_DIALOG:
                showCloudMeshDialog();
                break;
            case DOLBY_MODE_TOGGLE:
                toggleDolbyMode();
                break;
            case P2P_MODE_TOGGLE:
                toggleP2pMode();
                break;
            case AI_ASSISTANT_PROMPT:
                showAiAssistantDialog(rawCommand);
                break;
            case ORIENTATION_TOGGLE:
                toggleOrientation();
                break;
            case PIP_ENTER:
                enterSystemPip();
                break;
            case CELLULAR_GUARD_TOGGLE:
                toggleCellularGuard();
                break;
            case UNKNOWN:
            default:
                break;
        }
    }

    private void showAiRecommendationsDialog() {
        if (dialogAiRecommendations == null) return;
        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
        List<AiRecommendationManager.HotProgramItem> list = AiRecommendationManager.getCuratedRecommendations();
        if (aiRecommendationsAdapter == null) {
            aiRecommendationsAdapter = new AiRecommendationsAdapter(list, item -> {
                Channel ch = AiRecommendationManager.findMatchingChannel(allChannels, item.channelName);
                if (ch != null) {
                    for (int i = 0; i < allChannels.size(); i++) {
                        if (allChannels.get(i).getNumber() == ch.getNumber()) {
                            playChannelAt(i);
                            break;
                        }
                    }
                } else {
                    Toast.makeText(MainActivity.this, "当前订阅中未找到该频道源: " + item.channelName, Toast.LENGTH_SHORT).show();
                }
                dialogAiRecommendations.setVisibility(View.GONE);
                showVoiceHud("🤖 AI 导播为您切至: " + item.programTitle);
            });
            if (aiRecyclerView != null) {
                aiRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                aiRecyclerView.setAdapter(aiRecommendationsAdapter);
            }
        } else {
            aiRecommendationsAdapter.updateData(list);
        }
        dialogAiRecommendations.setVisibility(View.VISIBLE);
        if (aiRecyclerView != null) aiRecyclerView.requestFocus();
    }

    private void showRemindersDialog() {
        if (dialogReminders == null) return;
        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
        List<ReminderManager.ReminderItem> reminders = ReminderManager.getInstance(this).getAllReminders();
        if (reminders.isEmpty()) {
            if (remindersEmptyText != null) remindersEmptyText.setVisibility(View.VISIBLE);
            if (remindersRecyclerView != null) remindersRecyclerView.setVisibility(View.GONE);
        } else {
            if (remindersEmptyText != null) remindersEmptyText.setVisibility(View.GONE);
            if (remindersRecyclerView != null) {
                remindersRecyclerView.setVisibility(View.VISIBLE);
                if (remindersAdapter == null) {
                    remindersAdapter = new RemindersAdapter(new RemindersAdapter.OnReminderActionListener() {
                        @Override
                        public void onReminderSelect(ReminderManager.ReminderItem item) {
                            dialogReminders.setVisibility(View.GONE);
                            switchToReminderChannel(item);
                        }

                        @Override
                        public void onReminderCancel(ReminderManager.ReminderItem item) {
                            ReminderManager.getInstance(MainActivity.this).removeReminderById(item.id);
                            showRemindersDialog();
                            updateRemindersButtonText();
                            if (epgAdapter != null) epgAdapter.notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "已取消预约: " + item.programTitle, Toast.LENGTH_SHORT).show();
                        }
                    });
                    remindersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                    remindersRecyclerView.setAdapter(remindersAdapter);
                }
                remindersAdapter.setItems(reminders);
            }
        }
        dialogReminders.setVisibility(View.VISIBLE);
        if (btnCloseRemindersDialog != null) btnCloseRemindersDialog.requestFocus();
    }

    private void checkUpcomingReminders() {
        List<ReminderManager.ReminderItem> alerts = ReminderManager.getInstance(this).pollPendingAlerts();
        if (alerts != null && !alerts.isEmpty()) {
            ReminderManager.ReminderItem alert = alerts.get(0);
            currentAlertReminder = alert;
            if (reminderHudBanner != null && reminderHudText != null) {
                reminderHudText.setText("【预约开播】[" + alert.channelName + "] 《" + alert.programTitle + "》 即将开播！ 按 [OK] 立即收看");
                reminderHudBanner.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideReminderHudRunnable);
                handler.postDelayed(hideReminderHudRunnable, 15000);
            }
        }
    }

    private void switchToReminderChannel(ReminderManager.ReminderItem item) {
        if (item == null) return;
        Channel target = null;
        if (allChannels != null) {
            String clean = SmartChannelMatcher.cleanKey(item.channelName);
            for (Channel c : allChannels) {
                if (SmartChannelMatcher.cleanKey(c.getName()).equals(clean) || c.getName().equalsIgnoreCase(item.channelName)) {
                    target = c;
                    break;
                }
            }
        }
        if (target != null) {
            playSelectedChannel(target);
            Toast.makeText(this, "📺 已为您切至预约频道: " + target.getName(), Toast.LENGTH_SHORT).show();
        } else if (item.channelUrl != null && !item.channelUrl.isEmpty()) {
            Channel ch = new Channel(999, item.channelName, "预约频道", item.channelUrl);
            playSelectedChannel(ch);
        } else {
            Toast.makeText(this, "未在当前播放列表中找到频道: " + item.channelName, Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePictureModeButtonText() {
        if (btnTogglePictureMode != null) {
            btnTogglePictureMode.setText("🎛️ 图像画质模式: " + PictureQualityManager.getInstance(this).getModeName() + " - 点击切换");
        }
    }

    private void updateAudioModeButtonText() {
        if (btnToggleAudioMode != null && playerManager != null) {
            btnToggleAudioMode.setText("🔊 音频源码直通: " + playerManager.getAudioModeText() + " - 点击切换");
        }
    }

    private void updateRemindersButtonText() {
        if (btnViewReminders != null) {
            int count = ReminderManager.getInstance(this).getUpcomingCount();
            btnViewReminders.setText("📅 节目预约管家: " + count + " 个待播节目 - 点击查看");
        }
    }

    private void toggleLiveRecording() {
        RecordingManager rm = RecordingManager.getInstance();
        if (rm.isRecording()) {
            rm.stopRecording();
            Toast.makeText(this, "⏹️ 正在停止录制并保存文件...", Toast.LENGTH_SHORT).show();
            return;
        }

        Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
        if (ch == null) {
            Toast.makeText(this, "当前无正在播放的频道，无法录制", Toast.LENGTH_SHORT).show();
            return;
        }

        String streamUrl = playerManager.getLastPlayedUrl();
        if (streamUrl == null || streamUrl.isEmpty()) {
            streamUrl = ch.getUrl();
        }

        boolean ok = rm.startRecording(this, ch, streamUrl, new RecordingManager.RecordingListener() {
            @Override
            public void onRecordingStarted(File outputFile, Channel channel) {
                updateRecordingButtonText();
                if (osdRecordBadge != null) {
                    osdRecordBadge.setText("● REC 00:00:00");
                    osdRecordBadge.setVisibility(View.VISIBLE);
                }
                Toast.makeText(MainActivity.this, "🔴 已开始实时录制: " + channel.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRecordingProgress(long elapsedMs, long bytesRecorded, String formattedRate) {
                if (osdRecordBadge != null) {
                    osdRecordBadge.setText("● REC " + formatDuration(elapsedMs) + " (" + formatBytes(bytesRecorded) + ")");
                    osdRecordBadge.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onRecordingFinished(File outputFile, long durationMs, long bytesRecorded) {
                updateRecordingButtonText();
                updateRecordingsListButtonText();
                if (osdRecordBadge != null) {
                    osdRecordBadge.setVisibility(View.GONE);
                }
                Toast.makeText(MainActivity.this, "✅ 录制完成已保存: " + outputFile.getName() + " (" + formatBytes(bytesRecorded) + ")", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onRecordingError(String errorMessage) {
                updateRecordingButtonText();
                if (osdRecordBadge != null) {
                    osdRecordBadge.setVisibility(View.GONE);
                }
                Toast.makeText(MainActivity.this, "❌ 录制失败: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        if (!ok) {
            Toast.makeText(this, "启动录制失败，请检查流地址有效性", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void showRecordingsDialog() {
        List<RecordingManager.RecordingItem> list = RecordingManager.getRecordings(this);
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(this);
        b.setTitle("📁 已录制节目管理 (DVR)");

        if (list.isEmpty()) {
            b.setMessage("暂无已录制节目文件。\n\n提示：在播放直播时按菜单键或遥控快捷键即可一键开启实时节目录制，视频支持在手机浏览器直接下载回放。");
            b.setPositiveButton("我知道了", null);
            b.show();
            return;
        }

        String[] itemNames = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            RecordingManager.RecordingItem item = list.get(i);
            itemNames[i] = item.fileName + "\n(" + item.getFormattedSize() + " · " + item.getFormattedDate() + ")";
        }

        b.setItems(itemNames, (dialog, which) -> {
            RecordingManager.RecordingItem selected = list.get(which);
            androidx.appcompat.app.AlertDialog.Builder op = new androidx.appcompat.app.AlertDialog.Builder(this);
            op.setTitle("选择操作: " + selected.fileName);
            String[] ops = new String[]{"▶ 立即在大屏播放该录制文件", "🗑️ 删除该录制文件"};
            op.setItems(ops, (d2, actIdx) -> {
                if (actIdx == 0) {
                    if (playerManager != null) {
                        playerManager.playChannel(new Channel(999, selected.fileName, "已录制节目", selected.filePath));
                        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
                        Toast.makeText(this, "正在播放本地录制: " + selected.fileName, Toast.LENGTH_SHORT).show();
                    }
                } else if (actIdx == 1) {
                    RecordingManager.deleteRecording(this, selected.fileName);
                    updateRecordingsListButtonText();
                    Toast.makeText(this, "已删除录制文件", Toast.LENGTH_SHORT).show();
                }
            });
            op.setNegativeButton("取消", null);
            op.show();
        });
        b.setNegativeButton("关闭", null);
        b.show();
    }

    private void showWebDavDialog() {
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(this);
        b.setTitle("☁️ WebDAV 私有云盘同步");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        TextView tip = new TextView(this);
        tip.setText("可与 Alist、群晖 NAS、坚果云等私有云盘无缝漫游配置：\n最近同步：" + WebDavClient.getFormattedLastSyncTime(this));
        tip.setTextColor(0xFFCCCCCC);
        tip.setTextSize(12);
        layout.addView(tip);

        final android.widget.EditText inputUrl = new android.widget.EditText(this);
        inputUrl.setHint("WebDAV URL (如 http://192.168.1.100:5005/dav/)");
        inputUrl.setText(WebDavClient.getWebDavUrl(this));
        layout.addView(inputUrl);

        final android.widget.EditText inputUser = new android.widget.EditText(this);
        inputUser.setHint("用户名 (选填)");
        inputUser.setText(WebDavClient.getWebDavUser(this));
        layout.addView(inputUser);

        final android.widget.EditText inputPass = new android.widget.EditText(this);
        inputPass.setHint("密码 / Token (选填)");
        inputPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPass.setText(WebDavClient.getWebDavPass(this));
        layout.addView(inputPass);

        final android.widget.CheckBox chkAuto = new android.widget.CheckBox(this);
        chkAuto.setText("开机启动时自动从云端同步配置");
        chkAuto.setChecked(WebDavClient.isWebDavAutoSync(this));
        chkAuto.setTextColor(0xFFFFFFFF);
        layout.addView(chkAuto);

        b.setView(layout);

        b.setPositiveButton("☁️ 备份到云盘", (dialog, which) -> {
            String url = inputUrl.getText().toString().trim();
            String user = inputUser.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();
            boolean auto = chkAuto.isChecked();
            WebDavClient.saveWebDavConfig(this, url, user, pass, auto);

            Toast.makeText(this, "正在上传配置到 WebDAV 云盘...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                WebDavClient.SyncResult res = WebDavClient.uploadBackup(MainActivity.this, url, user, pass);
                handler.post(() -> Toast.makeText(MainActivity.this, (res.success ? "🎉 " : "❌ ") + res.message, Toast.LENGTH_LONG).show());
            }).start();
        });

        b.setNeutralButton("📥 从云盘恢复", (dialog, which) -> {
            String url = inputUrl.getText().toString().trim();
            String user = inputUser.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();
            boolean auto = chkAuto.isChecked();
            WebDavClient.saveWebDavConfig(this, url, user, pass, auto);

            Toast.makeText(this, "正在从 WebDAV 云盘拉取配置...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                WebDavClient.SyncResult res = WebDavClient.downloadAndRestore(MainActivity.this, url, user, pass);
                handler.post(() -> {
                    if (res.success) {
                        applyAggregatedSubscriptions();
                        updateSubscriptionSummary();
                        Toast.makeText(MainActivity.this, "🎉 " + res.message, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "❌ " + res.message, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        b.setNegativeButton("取消", null);
        b.show();
    }

    private void showLeftSidebar() {
        leftSidebar.setVisibility(View.VISIBLE);
        channelRecyclerView.requestFocus();
        if (miniDrawerPreview && playerView != null) {
            playerView.animate()
                    .translationX(220f)
                    .scaleX(0.72f)
                    .scaleY(0.72f)
                    .setDuration(220)
                    .start();
        }
    }

    private void hideLeftSidebar() {
        leftSidebar.setVisibility(View.GONE);
        if (playerView != null) {
            playerView.animate()
                    .translationX(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(180)
                    .start();
        }
    }

    private void showLanSyncDialog() {
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(this);
        b.setTitle("📡 局域网多电视同源联动");
        b.setMessage("输入同局域网另一台电视 (如客厅或卧室) 的 IP 地址，一键同步克隆其全部 M3U 订阅、多线路聚合源与收藏列表：");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("例如：192.168.1.116");
        String myIp = com.tivilite.tv.server.WebPushServer.getLocalIpAddress();
        if (myIp != null && myIp.contains(".")) {
            input.setText(myIp.substring(0, myIp.lastIndexOf('.') + 1));
            input.setSelection(input.getText().length());
        }
        b.setView(input);

        b.setPositiveButton("🚀 开始同步", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (!ip.isEmpty()) {
                pullFromLanTv(ip);
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void pullFromLanTv(String targetIp) {
        Toast.makeText(this, "📡 正在连接目标电视 (" + targetIp + ")...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String targetUrl = "http://" + targetIp + ":8888/api/export";
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                    boolean ok = SubscriptionManager.importAllData(MainActivity.this, root);
                    handler.post(() -> {
                        if (ok) {
                            applyAggregatedSubscriptions();
                            updateSubscriptionSummary();
                            Toast.makeText(MainActivity.this, "🎉 局域网同源同步成功！已导入并加载所有配置", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "❌ 同步失败：配置格式无法识别", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    handler.post(() -> Toast.makeText(MainActivity.this, "❌ 无法连接目标电视，请确认目标电视正在运行 TiviLite 且处于同一Wi-Fi", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "❌ 局域网连接超时，请检查电视 IP (" + targetIp + ") 是否正确", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateSubscriptionSummary() {
        if (subscriptionSummaryText != null) {
            List<SubscriptionManager.Subscription> subs = SubscriptionManager.getSubscriptions(this);
            int enabledCount = 0;
            for (SubscriptionManager.Subscription s : subs) {
                if (s.isEnabled()) enabledCount++;
            }
            if (subs.isEmpty()) {
                subscriptionSummaryText.setText("📁 订阅源: 暂无生效订阅 (请扫码配源)");
            } else {
                subscriptionSummaryText.setText("📁 订阅源: " + enabledCount + "/" + subs.size() + " 套生效 (已聚合 " + allChannels.size() + " 个多线路频道)");
            }
        }
    }

    private void toggleChannelFavorite(Channel ch) {
        if (ch == null) return;
        boolean isFav = com.tivilite.tv.favorite.FavoriteManager.toggleFavorite(this, ch.getName());
        Toast.makeText(this, isFav ? "⭐ 已将 [" + ch.getName() + "] 加入「我的收藏」" : "已将 [" + ch.getName() + "] 从收藏移除", Toast.LENGTH_SHORT).show();
        updateOsd(ch);
        refreshCategoriesAndChannels();
    }

    private void refreshCategoriesAndChannels() {
        if (allChannels == null || allChannels.isEmpty()) return;
        groupedChannels = M3UParser.groupByCategories(this, allChannels);
        List<String> groups = new ArrayList<>(groupedChannels.keySet());
        groupAdapter = new GroupAdapter(groups, groupName -> {
            List<Channel> channels = groupedChannels.get(groupName);
            channelAdapter.setChannels(channels);
        });
        groupRecyclerView.setAdapter(groupAdapter);
        channelAdapter.notifyDataSetChanged();
    }

    private void showProDialog() {
        String devId = com.tivilite.tv.license.LicenseManager.getDeviceId(this);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pro_upgrade, null);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        TextView tvDevId = dialogView.findViewById(R.id.dialogDeviceId);
        TextView tvDesc = dialogView.findViewById(R.id.dialogProDesc);
        android.widget.Button btnStartTrialPro = dialogView.findViewById(R.id.btnStartTrialPro);
        android.widget.EditText inputCode = dialogView.findViewById(R.id.dialogInputCode);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancelPro);
        android.widget.Button btnActivate = dialogView.findViewById(R.id.btnActivatePro);

        tvDevId.setText(devId);

        if (com.tivilite.tv.license.LicenseManager.isPermanentPro(this)) {
            if (tvDesc != null) {
                tvDesc.setText("🎉 本机已成功永久激活 TiviLite Pro 尊享版！全部特权已解锁。");
                tvDesc.setTextColor(getResources().getColor(R.color.tv_badge_catchup));
            }
            if (inputCode != null) inputCode.setVisibility(View.GONE);
            if (btnActivate != null) btnActivate.setVisibility(View.GONE);
            if (btnStartTrialPro != null) btnStartTrialPro.setVisibility(View.GONE);
            btnCancel.setText("关闭窗口");
        } else if (com.tivilite.tv.license.LicenseManager.isTrialActive(this)) {
            long remaining = com.tivilite.tv.license.LicenseManager.getTrialRemainingHours(this);
            if (tvDesc != null) {
                tvDesc.setText("⭐ 当前正在 3 天全功能免费体验中 (剩余 " + remaining + " 小时)！\n体验期内可免费使用 7 天回看。试用到期后可随时在此输入卡密买断。");
            }
            if (btnStartTrialPro != null) btnStartTrialPro.setVisibility(View.GONE);
        } else {
            if (btnStartTrialPro != null) {
                btnStartTrialPro.setVisibility(View.VISIBLE);
                btnStartTrialPro.setOnClickListener(v -> {
                    com.tivilite.tv.license.LicenseManager.startTrial(this);
                    Toast.makeText(this, "🎉 3天免费 Pro 体验已开启！", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    if (settingProStatusText != null) {
                        settingProStatusText.setText(com.tivilite.tv.license.LicenseManager.getProStatusText(this));
                    }
                });
            }
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnActivate.setOnClickListener(v -> {
            String code = inputCode.getText().toString().trim();
            if (com.tivilite.tv.license.LicenseManager.activate(this, code)) {
                Toast.makeText(this, "🎉 激活成功！已解锁 TiviLite Pro 永久特权", Toast.LENGTH_LONG).show();
                if (settingProStatusText != null) {
                    settingProStatusText.setText(com.tivilite.tv.license.LicenseManager.getProStatusText(this));
                }
                dialog.dismiss();
            } else {
                Toast.makeText(this, "❌ 激活码无效，请检查设备码是否匹配", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void initPlayer() {
        playerManager = new PlayerManager(this);
        playerManager.attachPlayerView(playerView);
        playerManager.setCallback(new PlayerManager.PlaybackCallback() {
            @Override
            public void onLoading(boolean isLoading) {
                loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onPlaybackReady() {
                loadingIndicator.setVisibility(View.GONE);
                if (playbackErrorOverlay != null) {
                    playbackErrorOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onErrorRetrying(String message, int currentAttempt, int maxAttempts) {
                loadingIndicator.setVisibility(View.VISIBLE);
                if (playbackErrorOverlay != null) {
                    errorTitle.setText("信号波动重试中 (" + currentAttempt + "/" + maxAttempts + ")");
                    errorDiagnostic.setText(message);
                    errorRetryStatus.setText("正在尝试自动重连，请稍候...");
                    playbackErrorOverlay.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAlternativeSourceSwitched(String sourceInfo) {
                Toast.makeText(MainActivity.this, "🔄 " + sourceInfo, Toast.LENGTH_SHORT).show();
                if (osdSource != null) {
                    osdSource.setText(sourceInfo);
                }
            }

            @Override
            public void onErrorFatal(String diagnosticMessage, boolean hasAlternatives) {
                loadingIndicator.setVisibility(View.GONE);
                if (playbackErrorOverlay != null) {
                    errorTitle.setText("播放异常");
                    errorDiagnostic.setText(diagnosticMessage);
                    errorRetryStatus.setText(hasAlternatives ? "💡 可按遥控器 [◄ / ► 左右键] 切换备用线路" : "💡 建议检查网络或按 [▲ / ▼] 快速换台");
                    playbackErrorOverlay.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(MainActivity.this, diagnosticMessage, Toast.LENGTH_LONG).show();
                }
            }
        });

        // Phase 8: Hook QoS Radar Telemetry
        QosRadarManager.getInstance(this).setPlayerManager(playerManager);
        QosRadarManager.getInstance(this).setUpdateListener(data -> {
            if (qosRadarOverlay != null && qosRadarOverlay.getVisibility() == View.VISIBLE) {
                if (qosDownloadSpeed != null) qosDownloadSpeed.setText(data.downloadSpeed);
                if (qosVideoSpec != null) qosVideoSpec.setText(data.videoSpec);
                if (qosVideoCodec != null) qosVideoCodec.setText(data.videoCodec);
                if (qosAudioSpec != null) qosAudioSpec.setText(data.audioSpec);
                if (qosBufferHealth != null) {
                    qosBufferHealth.setText(data.bufferHealth);
                    if (data.bufferStatusLevel == 0) qosBufferHealth.setTextColor(0xFF10B981);
                    else if (data.bufferStatusLevel == 1) qosBufferHealth.setTextColor(0xFFF59E0B);
                    else qosBufferHealth.setTextColor(0xFFEF4444);
                }
                if (qosDroppedFrames != null) qosDroppedFrames.setText(data.droppedFrames);
                if (qosNetworkRtt != null) qosNetworkRtt.setText(data.networkRtt);
                if (qosIpProtocol != null) qosIpProtocol.setText(data.ipProtocol);
            }
        });
        if (QosRadarManager.getInstance(this).isRadarEnabled()) {
            if (qosRadarOverlay != null) qosRadarOverlay.setVisibility(View.VISIBLE);
            QosRadarManager.getInstance(this).setRadarEnabled(true);
        }

        // Phase 8: Initialize DLNA DMR Cast Engine
        dlnaDmrManager = DlnaDmrManager.getInstance(this);
        dlnaDmrManager.setHttpPort(8888);
        dlnaDmrManager.setCastListener(new DlnaDmrManager.CastListener() {
            @Override
            public void onCastStarted(String uri, String title, String artist, long positionMs) {
                if (playerManager == null) return;
                if (!playerManager.isCastMode()) {
                    channelBeforeCast = playerManager.getCurrentChannel();
                }
                hideAllPanels();
                playerManager.playCastStream(uri, title);
                if (castHudBanner != null) castHudBanner.setVisibility(View.VISIBLE);
                if (castTitleText != null) {
                    castTitleText.setText("正在无线投屏: 《" + (title != null && !title.isEmpty() ? title : "手机投屏视频") + "》");
                }
                if (castSubtitleText != null) {
                    castSubtitleText.setText("遥控器 [OK] 暂停/继续 · [◀/▶] 进度快进退 · [返回] 退出投屏");
                }
                Toast.makeText(MainActivity.this, "📲 收到手机投屏: 《" + title + "》", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCastStopped() {
                exitCast();
            }

            @Override
            public void onCastPlay() {
                if (playerManager != null) playerManager.play();
                if (castSubtitleText != null) castSubtitleText.setText("遥控器 [OK] 暂停/继续 · [◀/▶] 进度快进退 · [返回] 退出投屏");
            }

            @Override
            public void onCastPause() {
                if (playerManager != null) playerManager.pause();
                if (castSubtitleText != null) castSubtitleText.setText("⏸️ 已暂停 · 按 [OK] 键继续播放 · [返回] 退出投屏");
            }

            @Override
            public void onCastSeek(long positionMs) {
                if (playerManager != null) playerManager.seekTo(positionMs);
            }

            @Override
            public void onCastVolume(int volumePercent) {
                // Adjust volume
            }
        });
        dlnaDmrManager.start();
    }

    private void initWebServer() {
        webServer = new WebPushServer(this, 8888, new WebPushServer.OnM3UReceivedListener() {
            @Override
            public void onM3UUrlReceived(String url, String name, boolean isAppend) {
                handler.post(() -> Toast.makeText(MainActivity.this, "📡 收到订阅源: " + name + "，正在连接拉取...", Toast.LENGTH_SHORT).show());
                fetchM3UFromUrl(url, name, isAppend);
            }

            @Override
            public void onM3UContentReceived(String content, String name, boolean isAppend) {
                handler.post(() -> saveAndApplySubscription(name, "", content, isAppend));
            }

            @Override
            public void onEpgUrlReceived(String epgUrl) {
                handler.post(() -> {
                    getSharedPreferences("tivilite_prefs", MODE_PRIVATE).edit().putString("epg_api_url", epgUrl).apply();
                    Toast.makeText(MainActivity.this, "📺 已更新 EPG 节目单网络接口", Toast.LENGTH_SHORT).show();
                    Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
                    if (ch != null) {
                        updateOsd(ch);
                        populateEpgTimeline(ch);
                    }
                });
            }

            @Override
            public void onSubscriptionToggled(String id, boolean enabled) {
                handler.post(() -> {
                    SubscriptionManager.toggleSubscription(MainActivity.this, id, enabled);
                    applyAggregatedSubscriptions();
                    Toast.makeText(MainActivity.this, enabled ? "已启用该订阅" : "已停用该订阅", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onSubscriptionDeleted(String id) {
                handler.post(() -> {
                    SubscriptionManager.deleteSubscription(MainActivity.this, id);
                    applyAggregatedSubscriptions();
                    Toast.makeText(MainActivity.this, "已移除该订阅", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onLicenseCodeReceived(String code) {
                handler.post(() -> {
                    if (com.tivilite.tv.license.LicenseManager.activate(MainActivity.this, code)) {
                        Toast.makeText(MainActivity.this, "🎉 手机端已成功激活 TiviLite Pro！", Toast.LENGTH_LONG).show();
                        if (settingProStatusText != null) {
                            settingProStatusText.setText(com.tivilite.tv.license.LicenseManager.getProStatusText(MainActivity.this));
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "❌ 激活失败：激活码与当前设备不匹配", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCheckUpdateRequested() {
                handler.post(() -> com.tivilite.tv.update.UpdateManager.checkUpdate(MainActivity.this, true));
            }

            @Override
            public void onRemoteKeyReceived(String key, String value) {
                handler.post(() -> {
                    if ("up".equals(key)) {
                        if (leftSidebar.getVisibility() == View.VISIBLE || rightEpgPanel.getVisibility() == View.VISIBLE) {
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP));
                        } else {
                            switchChannelRelative(invertUpDown ? 1 : -1);
                        }
                    } else if ("down".equals(key)) {
                        if (leftSidebar.getVisibility() == View.VISIBLE || rightEpgPanel.getVisibility() == View.VISIBLE) {
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
                        } else {
                            switchChannelRelative(invertUpDown ? -1 : 1);
                        }
                    } else if ("left".equals(key)) {
                        if (rightEpgPanel.getVisibility() == View.VISIBLE) {
                            rightEpgPanel.setVisibility(View.GONE);
                        } else if (leftSidebar.getVisibility() != View.VISIBLE) {
                            showLeftSidebar();
                        } else {
                            groupRecyclerView.requestFocus();
                        }
                    } else if ("right".equals(key)) {
                        if (leftSidebar.getVisibility() == View.VISIBLE) {
                            hideLeftSidebar();
                        } else if (rightEpgPanel.getVisibility() != View.VISIBLE) {
                            Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
                            if (ch != null) populateEpgTimeline(ch);
                            rightEpgPanel.setVisibility(View.VISIBLE);
                            epgRecyclerView.requestFocus();
                        }
                    } else if ("ok".equals(key)) {
                        if (leftSidebar.getVisibility() == View.VISIBLE || rightEpgPanel.getVisibility() == View.VISIBLE || webConfigDialog.getVisibility() == View.VISIBLE) {
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
                        } else {
                            showOsd();
                        }
                    } else if ("back".equals(key)) {
                        if (catchupSeekBarContainer != null && catchupSeekBarContainer.getVisibility() == View.VISIBLE) {
                            catchupSeekBarContainer.setVisibility(View.GONE);
                            if (playerManager != null && playerManager.isCatchupMode()) {
                                Channel ch = playerManager.getCurrentChannel();
                                if (ch != null) playerManager.playChannel(ch);
                                showOsd();
                            }
                        } else if (channelNumberInputBadge != null && channelNumberInputBadge.getVisibility() == View.VISIBLE) {
                            numberInputBuffer.setLength(0);
                            channelNumberInputBadge.setVisibility(View.GONE);
                        } else if (webConfigDialog.getVisibility() == View.VISIBLE) {
                            webConfigDialog.setVisibility(View.GONE);
                        } else if (leftSidebar.getVisibility() == View.VISIBLE) {
                            hideLeftSidebar();
                        } else if (rightEpgPanel.getVisibility() == View.VISIBLE) {
                            rightEpgPanel.setVisibility(View.GONE);
                        } else if (bottomOsd.getVisibility() == View.VISIBLE) {
                            bottomOsd.setVisibility(View.GONE);
                        }
                    } else if ("menu".equals(key)) {
                        toggleWebConfigDialog();
                    } else if ("favorite".equals(key)) {
                        Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
                        if (ch != null) toggleChannelFavorite(ch);
                    } else if ("switch_source".equals(key)) {
                        if (playerManager != null) {
                            playerManager.switchToNextSource();
                            if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                        }
                    } else if ("exit_catchup".equals(key)) {
                        if (playerManager != null && playerManager.isCatchupMode()) {
                            Channel ch = playerManager.getCurrentChannel();
                            if (ch != null) {
                                playerManager.playChannel(ch);
                                showOsd();
                                Toast.makeText(MainActivity.this, "📺 已退出回看，恢复实时直播", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            showOsd();
                        }
                    } else if ("volume_up".equals(key)) {
                        android.media.AudioManager am = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
                        if (am != null) am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
                    } else if ("volume_down".equals(key)) {
                        android.media.AudioManager am = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
                        if (am != null) am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
                    } else if ("volume_mute".equals(key)) {
                        android.media.AudioManager am = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
                        if (am != null) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE, android.media.AudioManager.FLAG_SHOW_UI);
                            } else {
                                boolean isMute = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0;
                                am.setStreamMute(android.media.AudioManager.STREAM_MUSIC, !isMute);
                            }
                        }
                    } else if ("number".equals(key)) {
                        try {
                            int n = Integer.parseInt(value);
                            handleNumberInput(n);
                        } catch (Exception ignored) {}
                    }
                });
            }

            @Override
            public void onRemoteZapReceived(int index, String name, int number) {
                handler.post(() -> {
                    if (allChannels == null || allChannels.isEmpty()) return;
                    if (index >= 0 && index < allChannels.size()) {
                        playChannelAt(index);
                        return;
                    }
                    if (number > 0) {
                        for (int i = 0; i < allChannels.size(); i++) {
                            if (allChannels.get(i).getNumber() == number) {
                                playChannelAt(i);
                                return;
                            }
                        }
                    }
                    if (name != null && !name.trim().isEmpty()) {
                        String q = name.trim().toLowerCase();
                        for (int i = 0; i < allChannels.size(); i++) {
                            Channel c = allChannels.get(i);
                            if (c.getName().toLowerCase().contains(q) || (c.getNumber() + "").equals(q)) {
                                playChannelAt(i);
                                return;
                            }
                        }
                    }
                });
            }

            @Override
            public void onChannelFavoriteToggled(String channelName) {
                handler.post(() -> {
                    if (channelName == null || allChannels == null) return;
                    for (Channel c : allChannels) {
                        if (c.getName().equalsIgnoreCase(channelName)) {
                            toggleChannelFavorite(c);
                            return;
                        }
                    }
                });
            }

            @Override
            public void onAutoBootToggled() {
                handler.post(() -> {
                    boolean cur = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("auto_boot_start", true);
                    getSharedPreferences("tivilite_prefs", MODE_PRIVATE).edit().putBoolean("auto_boot_start", !cur).apply();
                    updateAutoBootButtonText();
                    Toast.makeText(MainActivity.this, !cur ? "🚀 已开启开机自动进入直播" : "已关闭开机自启", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDecoderToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    int cur = playerManager.getDecoderMode();
                    int next = (cur == PlayerManager.DECODER_MODE_HARDWARE) ? PlayerManager.DECODER_MODE_SOFTWARE : PlayerManager.DECODER_MODE_HARDWARE;
                    playerManager.setDecoderMode(next);
                    updateDecoderButtonText();
                    Toast.makeText(MainActivity.this, next == PlayerManager.DECODER_MODE_SOFTWARE
                            ? "🎬 已切换为软件兼容解码模式"
                            : "🎬 已切换为硬件加速解码模式", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAspectRatioToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    String mode = playerManager.toggleNextAspectRatio();
                    updateAspectRatioButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    Toast.makeText(MainActivity.this, "📐 已切换画面比例: " + mode, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAudioTrackToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    String t = playerManager.toggleNextAudioTrack();
                    if (t != null) {
                        updateAudioTrackButtonText();
                        if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                        Toast.makeText(MainActivity.this, "🔊 已切换音轨: " + t, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "当前视频流仅有 1 组默认音轨", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onBestSourceRequested() {
                handler.post(() -> {
                    if (playerManager == null || playerManager.getCurrentChannel() == null) return;
                    playerManager.switchToBestSource();
                    Toast.makeText(MainActivity.this, "⚡ 正在测速并优选最佳线路...", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onLanSyncPullRequested(String targetIp) {
                handler.post(() -> pullFromLanTv(targetIp));
            }

            @Override
            public void onSubtitleToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    String sub = playerManager.toggleNextSubtitleTrack();
                    updateSubtitleButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    Toast.makeText(MainActivity.this, "💬 " + sub, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBufferModeToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    String buf = playerManager.toggleNextBufferMode();
                    updateBufferModeButtonText();
                    Toast.makeText(MainActivity.this, "🛡️ " + buf, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onRecordingToggled() {
                handler.post(MainActivity.this::toggleLiveRecording);
            }

            @Override
            public void onWebDavSyncRequested(String url, String user, String pass, boolean isUpload, boolean autoSync) {
                new Thread(() -> {
                    if (isUpload) {
                        WebDavClient.uploadBackup(MainActivity.this, url, user, pass);
                    } else {
                        WebDavClient.SyncResult res = WebDavClient.downloadAndRestore(MainActivity.this, url, user, pass);
                        if (res.success) {
                            handler.post(() -> {
                                applyAggregatedSubscriptions();
                                updateSubscriptionSummary();
                            });
                        }
                    }
                }).start();
            }

            @Override
            public void onPipToggled() {
                handler.post(MainActivity.this::togglePip);
            }

            @Override
            public void onPipSwapped() {
                handler.post(MainActivity.this::swapPipAndMain);
            }

            @Override
            public void onPipAudioToggled() {
                handler.post(MainActivity.this::togglePipAudio);
            }

            @Override
            public void onVoiceCommandReceived(String command) {
                handler.post(() -> handleVoiceCommand(command));
            }

            @Override
            public void onPipChannelPlayRequested(int index, String name, int number) {
                handler.post(() -> {
                    if (allChannels == null || allChannels.isEmpty()) return;
                    Channel target = null;
                    if (index >= 0 && index < allChannels.size()) {
                        target = allChannels.get(index);
                    } else if (number > 0) {
                        for (Channel c : allChannels) {
                            if (c.getNumber() == number) {
                                target = c;
                                break;
                            }
                        }
                    } else if (name != null && !name.trim().isEmpty()) {
                        String clean = SmartChannelMatcher.cleanKey(name);
                        for (Channel c : allChannels) {
                            if (SmartChannelMatcher.cleanKey(c.getName()).equals(clean)) {
                                target = c;
                                break;
                            }
                        }
                    }
                    if (target != null) {
                        startPipWithChannel(target);
                    }
                });
            }

            @Override
            public void onAudioModeToggled() {
                handler.post(() -> {
                    if (playerManager == null) return;
                    String am = playerManager.toggleNextAudioMode();
                    updateAudioModeButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    Toast.makeText(MainActivity.this, "🔊 音频直通: " + am, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPictureModeToggled() {
                handler.post(() -> {
                    PictureQualityManager.getInstance(MainActivity.this).cycleNextMode(playerView);
                    updatePictureModeButtonText();
                    if (playerManager != null && playerManager.getCurrentChannel() != null) {
                        updateOsd(playerManager.getCurrentChannel());
                    }
                    Toast.makeText(MainActivity.this, "🎛️ 图像画质模式: " + PictureQualityManager.getInstance(MainActivity.this).getModeName(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAudioModeChanged(int mode) {
                handler.post(() -> {
                    if (playerManager == null) return;
                    playerManager.setAudioMode(mode);
                    updateAudioModeButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                });
            }

            @Override
            public void onPictureModeChanged(int mode) {
                handler.post(() -> {
                    PictureQualityManager.getInstance(MainActivity.this).setMode(mode, playerView);
                    updatePictureModeButtonText();
                    if (playerManager != null && playerManager.getCurrentChannel() != null) {
                        updateOsd(playerManager.getCurrentChannel());
                    }
                });
            }

            @Override
            public void onQosRadarToggled() {
                handler.post(MainActivity.this::toggleQosRadar);
            }

            @Override
            public void onAutoFailoverToggled() {
                handler.post(MainActivity.this::toggleAutoFailover);
            }

            @Override
            public void onCastStopRequested() {
                handler.post(MainActivity.this::exitCast);
            }

            @Override
            public void onThemeToggled() {
                handler.post(MainActivity.this::toggleTheme);
            }

            @Override
            public void onFontScaleToggled() {
                handler.post(MainActivity.this::toggleFontScale);
            }

            @Override
            public void onNasScanRequested() {
                handler.post(() -> scanNasMedia(true));
            }

            @Override
            public void onBondingModeToggled() {
                handler.post(MainActivity.this::toggleBonding);
            }

            @Override
            public void onNasPlayRequested(String path, String name) {
                handler.post(() -> {
                    if (path == null || path.isEmpty()) return;
                    Channel target = null;
                    if (allChannels != null) {
                        for (Channel c : allChannels) {
                            if (path.equals(c.getUrl())) {
                                target = c;
                                break;
                            }
                        }
                    }
                    if (target == null) {
                        target = new Channel(9999, "🎬 " + (name != null ? name : "NAS 影片"), "📁 家庭 NAS 影视库", path);
                    }
                    playSelectedChannel(target);
                    Toast.makeText(MainActivity.this, "🎬 正在播放 NAS 影视: " + target.getName(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAiSubtitleToggled() {
                handler.post(MainActivity.this::toggleAiSubtitle);
            }

            @Override
            public void onWebRtcSyncToggled() {
                handler.post(MainActivity.this::toggleWebRtcSync);
            }

            @Override
            public void onMeshHandoffRequested(String targetDeviceId) {
                handler.post(() -> {
                    CloudMeshManager.getInstance(MainActivity.this).sendHandoffToPeer(targetDeviceId);
                    Toast.makeText(MainActivity.this, "📲 手机遥控已发起跨屏接力", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDolbyModeToggled() {
                handler.post(MainActivity.this::toggleDolbyMode);
            }

            @Override
            public void onP2pModeToggled() {
                handler.post(MainActivity.this::toggleP2pMode);
            }

            @Override
            public void onAiAssistantQuery(String prompt) {
                handler.post(() -> showAiAssistantDialog(prompt));
            }

            @Override
            public void onToggleOrientation() {
                handler.post(MainActivity.this::toggleOrientation);
            }

            @Override
            public void onEnterPipRequested() {
                handler.post(MainActivity.this::enterSystemPip);
            }

            @Override
            public void onCellularGuardToggled() {
                handler.post(MainActivity.this::toggleCellularGuard);
            }
        });

        webServer.setStatusProvider(new WebPushServer.StatusProvider() {
            @Override
            public org.json.JSONObject getStatusJson() {
                org.json.JSONObject json = new org.json.JSONObject();
                try {
                    Channel ch = playerManager != null ? playerManager.getCurrentChannel() : null;
                    if (ch != null) {
                        json.put("name", ch.getName());
                        json.put("number", ch.getNumber());
                        json.put("group", ch.getGroup());
                        json.put("logo", ch.getLogoUrl() != null ? ch.getLogoUrl() : "");
                        json.put("source_info", ch.getSourceInfo());
                        json.put("has_catchup", ch.hasCatchup());
                        json.put("is_fav", com.tivilite.tv.favorite.FavoriteManager.isFavorite(MainActivity.this, ch.getName()));
                        
                        com.tivilite.tv.model.Program p = EpgManager.getInstance().getCachedCurrentProgram(ch.getName());
                        if (p != null) {
                            json.put("current_program", p.getTitle());
                        } else {
                            json.put("current_program", ch.hasCatchup() ? "高清直播 (支持7天时移回看)" : "高清直播");
                        }
                    }
                    json.put("is_catchup_mode", playerManager != null && playerManager.isCatchupMode());
                    json.put("decoder_mode", (playerManager != null && playerManager.getDecoderMode() == PlayerManager.DECODER_MODE_SOFTWARE)
                            ? "软件兼容解码" : "硬件加速 (MediaCodec)");
                    json.put("aspect_ratio", playerManager != null ? playerManager.getAspectRatioModeText() : "自适应 (FIT)");
                    json.put("audio_track", playerManager != null ? playerManager.getCurrentAudioTrackDesc() : "标准立体声");
                    json.put("is_recording", RecordingManager.getInstance().isRecording());
                    json.put("subtitle_desc", playerManager != null ? playerManager.getCurrentSubtitleDesc() : "关");
                    json.put("buffer_mode", playerManager != null ? playerManager.getBufferModeText() : "极速秒开");
                    json.put("audio_mode", playerManager != null ? playerManager.getAudioMode() : 0);
                    json.put("audio_mode_text", playerManager != null ? playerManager.getAudioModeText() : "自动直通");
                    json.put("audio_codec_badge", playerManager != null ? playerManager.getAudioCodecBadge() : "立体声");
                    json.put("picture_mode", PictureQualityManager.getInstance(MainActivity.this).getCurrentMode());
                    json.put("picture_mode_text", PictureQualityManager.getInstance(MainActivity.this).getModeName());
                    json.put("video_spec_badge", playerManager != null ? playerManager.getVideoStreamBadge() : "");
                    json.put("reminders_count", ReminderManager.getInstance(MainActivity.this).getUpcomingCount());
                    json.put("webdav_last_sync", WebDavClient.getFormattedLastSyncTime(MainActivity.this));
                    json.put("total_channels", allChannels != null ? allChannels.size() : 0);
                    json.put("device_id", com.tivilite.tv.license.LicenseManager.getDeviceId(MainActivity.this));
                    json.put("form_factor", DeviceFormFactorManager.getInstance(MainActivity.this).getFormFactor().name());
                    json.put("form_factor_name", DeviceFormFactorManager.getInstance(MainActivity.this).getFormFactorName());
                    json.put("is_cellular", CellularDataGuardManager.getInstance(MainActivity.this).isCellularNetwork());
                    json.put("pro_status_text", com.tivilite.tv.license.LicenseManager.getProStatusText(MainActivity.this));
                    json.put("version", BuildConfig.VERSION_NAME);
                } catch (Exception ignored) {}
                return json;
            }

            @Override
            public org.json.JSONArray getChannelsJson() {
                org.json.JSONArray arr = new org.json.JSONArray();
                if (allChannels != null) {
                    for (int i = 0; i < allChannels.size(); i++) {
                        Channel ch = allChannels.get(i);
                        org.json.JSONObject obj = new org.json.JSONObject();
                        try {
                            obj.put("index", i);
                            obj.put("number", ch.getNumber());
                            obj.put("name", ch.getName());
                            obj.put("group", ch.getGroup());
                            obj.put("has_catchup", ch.hasCatchup());
                            obj.put("is_fav", com.tivilite.tv.favorite.FavoriteManager.isFavorite(MainActivity.this, ch.getName()));
                            obj.put("sources", ch.getUrlCount());
                            obj.put("relay_url", LanMulticastManager.getInstance(MainActivity.this).generateLanRelayUrl(
                                    WebPushServer.getLocalIpAddress(), 8888, ch.getUrl()));
                            arr.put(obj);
                        } catch (Exception ignored) {}
                    }
                }
                return arr;
            }
        });
        webServer.start();
        String localIp = WebPushServer.getLocalIpAddress();
        String url = "http://" + localIp + ":8888";
        webServerUrlText.setText(url);
        android.graphics.Bitmap qr = generateQrCode(url);
        if (qr != null && qrCodeImageView != null) {
            qrCodeImageView.setImageBitmap(qr);
        }
    }

    private void loadChannels() {
        applyAggregatedSubscriptions();
    }

    private void applyAggregatedSubscriptions() {
        allChannels = SubscriptionManager.getAggregatedChannels(this);
        List<Channel> nasChannels = SmbNasManager.getInstance(this).getAsChannels(allChannels.size() + 1);
        if (nasChannels != null && !nasChannels.isEmpty()) {
            allChannels.addAll(nasChannels);
        }
        groupedChannels = M3UParser.groupByCategories(this, allChannels);

        List<String> groups = new ArrayList<>(groupedChannels.keySet());
        groupAdapter = new GroupAdapter(groups, groupName -> {
            List<Channel> channels = groupedChannels.get(groupName);
            channelAdapter.setChannels(channels);
        });
        groupRecyclerView.setAdapter(groupAdapter);

        updateSubscriptionSummary();

        if (!allChannels.isEmpty()) {
            if (onboardingOverlay != null) onboardingOverlay.setVisibility(View.GONE);
            channelAdapter.setChannels(allChannels);
            // Restore last played channel on boot / app launch
            int savedIndex = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getInt("last_channel_index", 0);
            if (savedIndex < 0 || savedIndex >= allChannels.size()) savedIndex = 0;
            currentChannelIndex = savedIndex;
            playSelectedChannel(allChannels.get(currentChannelIndex));

            showRemoteHintToast();
        } else {
            // 纯净播放器定位：本地未配源时，弹出高颜值首次使用扫码导流屏
            showOnboardingWizard();
        }
    }

    private void showOnboardingWizard() {
        if (onboardingOverlay != null) {
            String ip = WebPushServer.getLocalIpAddress();
            String url = "http://" + ip + ":8888";
            if (onboardingServerUrl != null) onboardingServerUrl.setText(url);
            android.graphics.Bitmap qr = generateQrCode(url);
            if (qr != null && onboardingQrCode != null) {
                onboardingQrCode.setImageBitmap(qr);
            }
            onboardingOverlay.setVisibility(View.VISIBLE);
            if (btnOnboardingDemo != null) {
                btnOnboardingDemo.requestFocus();
                btnOnboardingDemo.setOnClickListener(v -> {
                    allChannels = M3UParser.getDemoChannels();
                    groupedChannels = M3UParser.groupByCategories(this, allChannels);
                    channelAdapter.setChannels(allChannels);
                    if (onboardingOverlay != null) onboardingOverlay.setVisibility(View.GONE);
                    currentChannelIndex = 0;
                    playSelectedChannel(allChannels.get(0));
                    Toast.makeText(this, "📺 已载入开源合规演示流 (仅供解码测试)", Toast.LENGTH_SHORT).show();
                    showRemoteHintToast();
                });
            }
        }
    }

    private void showRemoteHintToast() {
        if (remoteHintToast != null) {
            remoteHintToast.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideRemoteHintRunnable);
            handler.postDelayed(hideRemoteHintRunnable, 4500);
        }
    }

    private void playChannelAt(int index) {
        if (allChannels != null && index >= 0 && index < allChannels.size()) {
            playSelectedChannel(allChannels.get(index));
        }
    }

    private void playSelectedChannel(Channel channel) {
        if (playbackErrorOverlay != null) {
            playbackErrorOverlay.setVisibility(View.GONE);
        }
        playerManager.playChannel(channel);
        channelAdapter.setCurrentPlayingChannel(channel);
        updateOsd(channel);
        showOsd();
        hideLeftSidebar();

        // Remember last played channel index
        int idx = allChannels.indexOf(channel);
        if (idx >= 0) {
            currentChannelIndex = idx;
            getSharedPreferences("tivilite_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("last_channel_index", currentChannelIndex)
                    .apply();
        }
    }

    private void updateOsd(Channel channel) {
        boolean isFav = com.tivilite.tv.favorite.FavoriteManager.isFavorite(this, channel.getName());
        osdChannelNum.setText(String.format("%02d", channel.getNumber()));
        osdChannelName.setText(isFav ? "⭐ " + channel.getName() : channel.getName());
        osdProgram.setText(channel.hasCatchup() ? "正在播出：高清直播 (支持7天时移回看)" : "正在播出：高清直播");
        osdBadge.setText(channel.hasCatchup() ? "时移回看" : "高清实时");
        if (osdSource != null) {
            osdSource.setText(channel.getSourceInfo());
        }

        if (osdAspect != null && playerManager != null) {
            osdAspect.setText("📐 " + playerManager.getAspectRatioModeText());
        }
        if (osdAudio != null && playerManager != null) {
            osdAudio.setText("🔊 " + playerManager.getCurrentAudioTrackDesc());
        }
        if (osdSubtitle != null && playerManager != null) {
            osdSubtitle.setText("💬 " + playerManager.getCurrentSubtitleDesc());
        }
        if (osdRecordBadge != null) {
            osdRecordBadge.setVisibility(RecordingManager.getInstance().isRecording() ? View.VISIBLE : View.GONE);
        }
        if (osdAudioBadge != null && playerManager != null) {
            osdAudioBadge.setText(playerManager.getAudioCodecBadge());
            osdAudioBadge.setVisibility(View.VISIBLE);
        }
        if (osdPictureBadge != null && playerManager != null) {
            String spec = playerManager.getVideoStreamBadge();
            String pMode = PictureQualityManager.getInstance(this).getModeName();
            osdPictureBadge.setText(!spec.isEmpty() ? (spec + " · " + pMode) : pMode);
            osdPictureBadge.setVisibility(View.VISIBLE);
        }

        if (osdSyncBadge != null) {
            String syncBadge = WebRtcSyncManager.getInstance(this).getSyncBadge();
            if (!syncBadge.isEmpty()) {
                osdSyncBadge.setText(syncBadge);
                osdSyncBadge.setVisibility(View.VISIBLE);
            } else {
                osdSyncBadge.setVisibility(View.GONE);
            }
        }

        long currentPts = playerManager != null ? playerManager.getCurrentPosition() : 0;
        CloudMeshManager.getInstance(this).updatePlaybackState(channel.getName(), channel.getUrl(), currentPts);
        AiSubtitleManager.getInstance(this).notifyStreamChanged(channel.getName());

        if (channel.getLogoUrl() != null && !channel.getLogoUrl().isEmpty()) {
            Glide.with(this).load(channel.getLogoUrl()).placeholder(R.drawable.app_banner).into(osdLogo);
        } else {
            osdLogo.setImageResource(R.drawable.app_banner);
        }

        // Asynchronously query live EPG title for OSD
        EpgManager.getInstance().loadEpgForChannel(this, channel, (name, currentProgram, fullSchedule) -> {
            if (channel != null && channel.getName().equals(name)) {
                if (currentProgram != null && playerManager != null && !playerManager.isCatchupMode()) {
                    osdProgram.setText("正在播出：" + currentProgram.getTitle());
                }
            }
        });
    }

    private void showOsd() {
        bottomOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 5000);
    }

    private void populateEpgTimeline(Channel channel) {
        if (channel == null) return;
        epgTitle.setText(channel.getName());
        epgSubtitle.setText(channel.hasCatchup() ? "7天时移回看 · 点击未来节目可预约" : "实时节目单 · 点击未来节目可预约");
        if (epgAdapter != null) epgAdapter.setChannelName(channel.getName());
        EpgManager.getInstance().loadEpgForChannel(this, channel, (name, currentProgram, fullSchedule) -> {
            if (fullSchedule != null && !fullSchedule.isEmpty()) {
                epgAdapter.setPrograms(fullSchedule);
            }
            if (currentProgram != null && playerManager != null && !playerManager.isCatchupMode()) {
                osdProgram.setText("正在播出：" + currentProgram.getTitle());
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (touchGestureManager != null) {
            boolean isDialogVisible = (webConfigDialog != null && webConfigDialog.getVisibility() == View.VISIBLE)
                    || (dialogAiRecommendations != null && dialogAiRecommendations.getVisibility() == View.VISIBLE)
                    || (dialogReminders != null && dialogReminders.getVisibility() == View.VISIBLE)
                    || (dialogCloudMesh != null && dialogCloudMesh.getVisibility() == View.VISIBLE)
                    || (aiAssistantOverlay != null && aiAssistantOverlay.getVisibility() == View.VISIBLE)
                    || (leftSidebar != null && leftSidebar.getVisibility() == View.VISIBLE)
                    || (rightEpgPanel != null && rightEpgPanel.getVisibility() == View.VISIBLE);

            if (!isDialogVisible) {
                touchGestureManager.onTouchEvent(ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 0. DLNA Cast Mode Remote Control
        if (playerManager != null && playerManager.isCastMode()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    exitCast();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (dlnaDmrManager != null && dlnaDmrManager.isPaused()) {
                        dlnaDmrManager.updateCastState(true, false);
                        playerManager.play();
                        if (castSubtitleText != null) castSubtitleText.setText("遥控器 [OK] 暂停/继续 · [◀/▶] 进度快进退 · [返回] 退出投屏");
                    } else if (dlnaDmrManager != null) {
                        dlnaDmrManager.updateCastState(true, true);
                        playerManager.pause();
                        if (castSubtitleText != null) castSubtitleText.setText("⏸️ 已暂停 · 按 [OK] 键继续播放 · [返回] 退出投屏");
                    }
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerManager.seekBy(-15000);
                    Toast.makeText(this, "⏪ 快退 15 秒", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerManager.seekBy(15000);
                    Toast.makeText(this, "⏩ 快进 15 秒", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }

        // 0. Reminders Dialog Remote Navigation
        if (dialogReminders != null && dialogReminders.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    dialogReminders.setVisibility(View.GONE);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // 0. Cloud Mesh Dialog Remote Navigation
        if (dialogCloudMesh != null && dialogCloudMesh.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    dialogCloudMesh.setVisibility(View.GONE);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // 0. AI Assistant Overlay Remote Navigation
        if (aiAssistantOverlay != null && aiAssistantOverlay.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    aiAssistantOverlay.setVisibility(View.GONE);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // 0. Reminder Alert HUD (Press OK to immediately switch to reserved channel)
        if (reminderHudBanner != null && reminderHudBanner.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    reminderHudBanner.setVisibility(View.GONE);
                    handler.removeCallbacks(hideReminderHudRunnable);
                    switchToReminderChannel(currentAlertReminder);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    reminderHudBanner.setVisibility(View.GONE);
                    handler.removeCallbacks(hideReminderHudRunnable);
                    currentAlertReminder = null;
                }
                return true;
            }
        }

        // 0. AI Recommendations Dialog Remote Navigation
        if (dialogAiRecommendations != null && dialogAiRecommendations.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    dialogAiRecommendations.setVisibility(View.GONE);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // 0. Settings / Web Config Dialog Remote Navigation (Handle BOTH ACTION_DOWN and ACTION_UP)
        if (webConfigDialog != null && webConfigDialog.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    webConfigDialog.setVisibility(View.GONE);
                }
                return true;
            }
            // Delegate all D-pad and Click (DOWN and UP) events directly to dialog buttons
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // Shortcut: PiP Multi-view Toggle Key 'P' or KEYCODE_WINDOW
            if (keyCode == KeyEvent.KEYCODE_WINDOW || keyCode == KeyEvent.KEYCODE_P) {
                togglePip();
                return true;
            }

            // Shortcut: Voice Search Key
            if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
                showVoiceHud("🎙️ 请通过手机遥控器使用语音/自然语言智能换台");
                return true;
            }

            // Shortcut: Dedicated TV REC or 'R' Key for live DVR recording
            if (keyCode == KeyEvent.KEYCODE_MEDIA_RECORD || keyCode == KeyEvent.KEYCODE_R) {
                toggleLiveRecording();
                return true;
            }

            // Shortcut: Subtitles / Captions Toggle Key 'C'
            if (keyCode == KeyEvent.KEYCODE_CAPTIONS || keyCode == KeyEvent.KEYCODE_C) {
                if (playerManager != null) {
                    String sub = playerManager.toggleNextSubtitleTrack();
                    updateSubtitleButtonText();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    Toast.makeText(this, "💬 " + sub, Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            // 1. Interactive Failover & Error Overlay Navigation
            if (playbackErrorOverlay != null && playbackErrorOverlay.getVisibility() == View.VISIBLE) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    playerManager.retryCurrent();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    playerManager.switchToPreviousSource();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    playerManager.switchToNextSource();
                    if (playerManager.getCurrentChannel() != null) updateOsd(playerManager.getCurrentChannel());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    playbackErrorOverlay.setVisibility(View.GONE);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    playbackErrorOverlay.setVisibility(View.GONE);
                    switchChannelRelative(-1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    playbackErrorOverlay.setVisibility(View.GONE);
                    switchChannelRelative(1);
                    return true;
                }
            }

            // 1. Numeric Keypad Direct Zapping (0-9)
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                handleNumberInput(keyCode - KeyEvent.KEYCODE_0);
                return true;
            }

            // 2. Catch-up Seek Bar (Left/Right fast rewind/forward by 15s)
            if (playerManager != null && playerManager.isCatchupMode() 
                    && leftSidebar.getVisibility() != View.VISIBLE 
                    && rightEpgPanel.getVisibility() != View.VISIBLE) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    performCatchupSeek(-15000);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    performCatchupSeek(15000);
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (rightEpgPanel.getVisibility() == View.VISIBLE) {
                    rightEpgPanel.setVisibility(View.GONE);
                    return true;
                }
                if (leftSidebar.getVisibility() != View.VISIBLE) {
                    showLeftSidebar();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (leftSidebar.getVisibility() == View.VISIBLE) {
                    hideLeftSidebar();
                    return true;
                }
                if (rightEpgPanel.getVisibility() != View.VISIBLE) {
                    Channel ch = playerManager.getCurrentChannel();
                    if (ch != null) populateEpgTimeline(ch);
                    rightEpgPanel.setVisibility(View.VISIBLE);
                    epgRecyclerView.requestFocus();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (numberInputBuffer.length() > 0) {
                    commitNumberInput();
                    return true;
                }
                if (catchupSeekBarContainer != null && catchupSeekBarContainer.getVisibility() == View.VISIBLE && playerManager != null && playerManager.isCatchupMode()) {
                    catchupSeekBarContainer.setVisibility(View.GONE);
                    Channel ch = playerManager.getCurrentChannel();
                    if (ch != null) {
                        playerManager.playChannel(ch);
                        showOsd();
                        Toast.makeText(this, "📺 已退出回看，恢复实时直播", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    if (event.getRepeatCount() == 0) {
                        okKeyLongPressed = false;
                        handler.removeCallbacks(okLongPressRunnable);
                        handler.postDelayed(okLongPressRunnable, 700);
                    }
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    switchChannelRelative(invertUpDown ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    switchChannelRelative(invertUpDown ? -1 : 1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_MENU) {
                toggleWebConfigDialog();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (catchupSeekBarContainer != null && catchupSeekBarContainer.getVisibility() == View.VISIBLE) {
                    catchupSeekBarContainer.setVisibility(View.GONE);
                    if (playerManager != null && playerManager.isCatchupMode()) {
                        Channel ch = playerManager.getCurrentChannel();
                        if (ch != null) {
                            playerManager.playChannel(ch);
                            showOsd();
                            Toast.makeText(this, "📺 已退出回看，恢复实时直播", Toast.LENGTH_SHORT).show();
                        }
                    }
                    return true;
                }
                if (channelNumberInputBadge != null && channelNumberInputBadge.getVisibility() == View.VISIBLE) {
                    numberInputBuffer.setLength(0);
                    channelNumberInputBadge.setVisibility(View.GONE);
                    handler.removeCallbacks(commitNumberInputRunnable);
                    return true;
                }
                if (webConfigDialog.getVisibility() == View.VISIBLE) {
                    webConfigDialog.setVisibility(View.GONE);
                    return true;
                }
                if (leftSidebar.getVisibility() == View.VISIBLE) {
                    hideLeftSidebar();
                    return true;
                }
                if (rightEpgPanel.getVisibility() == View.VISIBLE) {
                    rightEpgPanel.setVisibility(View.GONE);
                    return true;
                }
                if (bottomOsd.getVisibility() == View.VISIBLE) {
                    bottomOsd.setVisibility(View.GONE);
                    return true;
                }
                if (playerManager != null && playerManager.isCatchupMode()) {
                    Channel ch = playerManager.getCurrentChannel();
                    if (ch != null) {
                        playerManager.playChannel(ch);
                        showOsd();
                        Toast.makeText(this, "📺 已退出回看，恢复实时直播", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(this, "再按一次退出 TiviLite", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    handler.removeCallbacks(okLongPressRunnable);
                    if (!okKeyLongPressed) {
                        showOsd();
                    }
                    okKeyLongPressed = false;
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void handleNumberInput(int digit) {
        handler.removeCallbacks(commitNumberInputRunnable);
        if (numberInputBuffer.length() >= 3) numberInputBuffer.setLength(0);
        numberInputBuffer.append(digit);

        if (channelNumberInputBadge != null) {
            channelNumberInputBadge.setText(numberInputBuffer.toString());
            channelNumberInputBadge.setVisibility(View.VISIBLE);
        }
        handler.postDelayed(commitNumberInputRunnable, 1400);
    }

    private void commitNumberInput() {
        if (channelNumberInputBadge != null) {
            channelNumberInputBadge.setVisibility(View.GONE);
        }
        if (numberInputBuffer.length() == 0 || allChannels.isEmpty()) return;
        try {
            int targetNum = Integer.parseInt(numberInputBuffer.toString());
            numberInputBuffer.setLength(0);

            for (int i = 0; i < allChannels.size(); i++) {
                if (allChannels.get(i).getNumber() == targetNum) {
                    playSelectedChannel(allChannels.get(i));
                    return;
                }
            }
            Toast.makeText(this, "未找到该频道号", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            numberInputBuffer.setLength(0);
        }
    }

    private void performCatchupSeek(long offsetMs) {
        if (playerManager == null || catchupSeekBarContainer == null) return;
        playerManager.seekBy(offsetMs);

        long current = playerManager.getCurrentPosition();
        long duration = playerManager.getDuration();
        if (duration > 0) {
            int percent = (int) ((current * 100) / duration);
            catchupProgressBar.setProgress(percent);
            seekTimeText.setText(formatDuration(current) + " / " + formatDuration(duration));
        } else {
            seekTimeText.setText(formatDuration(current));
        }

        catchupSeekBarContainer.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSeekBarRunnable);
        handler.postDelayed(hideSeekBarRunnable, 4000);
    }

    private String formatDuration(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void switchChannelRelative(int delta) {
        if (allChannels.isEmpty()) return;
        currentChannelIndex = (currentChannelIndex + delta + allChannels.size()) % allChannels.size();
        playSelectedChannel(allChannels.get(currentChannelIndex));
    }

    private void toggleWebConfigDialog() {
        if (webConfigDialog.getVisibility() == View.VISIBLE) {
            webConfigDialog.setVisibility(View.GONE);
        } else {
            if (settingProStatusText != null) {
                settingProStatusText.setText(com.tivilite.tv.license.LicenseManager.getProStatusText(this));
            }
            updateDirectionButtonText();
            updateAspectRatioButtonText();
            updatePictureModeButtonText();
            updateAudioTrackButtonText();
            updateAudioModeButtonText();
            updateRemindersButtonText();
            updateMiniDrawerButtonText();
            updateAutoBootButtonText();
            updateDecoderButtonText();
            String ip = WebPushServer.getLocalIpAddress();
            String url = "http://" + ip + ":8888";
            webServerUrlText.setText(url);
            android.graphics.Bitmap qrBmp = generateQrCode(url);
            if (qrBmp != null && qrCodeImageView != null) {
                qrCodeImageView.setImageBitmap(qrBmp);
            }
            updateSubscriptionSummary();
            webConfigDialog.setVisibility(View.VISIBLE);
            webConfigDialog.post(() -> {
                if (btnToggleDirection != null) {
                    btnToggleDirection.requestFocus();
                }
            });
        }
    }

    private android.graphics.Bitmap generateQrCode(String content) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void fetchM3UFromUrl(String urlStr) {
        fetchM3UFromUrl(urlStr, "网络订阅", true);
    }

    private void fetchM3UFromUrl(String urlStr, String name, boolean isAppend) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String curUrl = urlStr.trim();
                for (int redirect = 0; redirect < 5; redirect++) {
                    URL u = new URL(curUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box Build/QP1A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0 Safari/537.36");
                    conn.setRequestProperty("Accept", "*/*");
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null && !loc.isEmpty()) {
                            curUrl = loc;
                            conn.disconnect();
                            continue;
                        }
                    }
                    if (code == 200) {
                        try (InputStream in = conn.getInputStream();
                             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                            String content = sb.toString();
                            handler.post(() -> saveAndApplySubscription(name, urlStr, content, isAppend));
                            return;
                        }
                    } else {
                        throw new Exception("HTTP 状态码: " + code);
                    }
                }
                throw new Exception("重定向跳转次数过多");
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "拉取 M3U 订阅失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void saveAndApplySubscription(String name, String url, String content, boolean isAppend) {
        if (content == null || content.trim().isEmpty()) {
            Toast.makeText(this, "⚠️ 推送的内容为空，未更新频道", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            List<Channel> parsed = M3UParser.parse(content);
            if (parsed.isEmpty()) {
                Toast.makeText(this, "⚠️ 未在订阅中解析出有效频道，请检查源格式 (支持 M3U / TXT)", Toast.LENGTH_LONG).show();
                return;
            }

            SubscriptionManager.Subscription sub = new SubscriptionManager.Subscription(
                    UUID.randomUUID().toString(),
                    name != null && !name.trim().isEmpty() ? name.trim() : "自定义订阅",
                    url != null ? url : "",
                    content,
                    parsed.size(),
                    true,
                    System.currentTimeMillis()
            );

            SubscriptionManager.addOrUpdateSubscription(this, sub, !isAppend);
            applyAggregatedSubscriptions();
            Toast.makeText(this, "🎉 订阅 [" + sub.getName() + "] 保存成功！共聚合 " + allChannels.size() + " 个多线路频道", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存订阅源异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndApplyM3U(String content) {
        saveAndApplySubscription("手动导入", "", content, false);
    }

    private void hideAllPanels() {
        if (leftSidebar != null) hideLeftSidebar();
        if (rightEpgPanel != null) rightEpgPanel.setVisibility(View.GONE);
        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
        if (dialogAiRecommendations != null) dialogAiRecommendations.setVisibility(View.GONE);
        if (dialogReminders != null) dialogReminders.setVisibility(View.GONE);
        if (dialogCloudMesh != null) dialogCloudMesh.setVisibility(View.GONE);
        if (aiAssistantOverlay != null) aiAssistantOverlay.setVisibility(View.GONE);
        if (touchGestureHud != null) touchGestureHud.setVisibility(View.GONE);
        if (bottomOsd != null) bottomOsd.setVisibility(View.GONE);
    }

    private void toggleQosRadar() {
        boolean enabled = QosRadarManager.getInstance(this).toggleRadar();
        if (qosRadarOverlay != null) {
            qosRadarOverlay.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        updateQosRadarButtonText();
        Toast.makeText(this, enabled ? "📊 极客网络雷达: 已开启" : "极客网络雷达: 已关闭", Toast.LENGTH_SHORT).show();
    }

    private void updateQosRadarButtonText() {
        if (btnToggleQosRadar != null) {
            boolean en = QosRadarManager.getInstance(this).isRadarEnabled();
            btnToggleQosRadar.setText("📊 极客网络与画质雷达: " + (en ? "已开启" : "已关闭") + " - 点击切换");
        }
    }

    private void toggleAutoFailover() {
        if (playerManager == null) return;
        boolean en = playerManager.toggleAutoFailover();
        updateAutoFailoverButtonText();
        Toast.makeText(this, en ? "🛡️ 多源智能自动容灾: 已开启 (卡顿自动切线)" : "多源自动容灾: 已关闭", Toast.LENGTH_SHORT).show();
    }

    private void updateAutoFailoverButtonText() {
        if (btnToggleAutoFailover != null && playerManager != null) {
            boolean en = playerManager.isAutoFailoverEnabled();
            btnToggleAutoFailover.setText("🛡️ 多源智能自动容灾: " + (en ? "已开启 (秒切备用源)" : "已关闭") + " - 点击切换");
        }
    }

    private void exitCast() {
        if (dlnaDmrManager != null) dlnaDmrManager.stopCast();
        if (playerManager != null) playerManager.exitCastMode();
        if (castHudBanner != null) castHudBanner.setVisibility(View.GONE);
        if (channelBeforeCast != null) {
            playSelectedChannel(channelBeforeCast);
            Toast.makeText(this, "📺 已退出投屏，返回直播: " + channelBeforeCast.getName(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "已退出无线投屏", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTheme() {
        ThemeManager tm = ThemeManager.getInstance(this);
        String name = tm.toggleNextTheme();
        updateThemeButtonText();
        applyCurrentTheme();
        Toast.makeText(this, "🎨 大屏主题已切换: " + name, Toast.LENGTH_SHORT).show();
    }

    private void updateThemeButtonText() {
        if (btnToggleTheme != null) {
            btnToggleTheme.setText("🎨 大屏视觉主题: " + ThemeManager.getInstance(this).getThemeName() + " - 点击切换");
        }
    }

    private void toggleFontScale() {
        ThemeManager tm = ThemeManager.getInstance(this);
        String name = tm.toggleNextFontScale();
        updateFontScaleButtonText();
        applyCurrentFontScale();
        Toast.makeText(this, "🔤 界面字体已切换: " + name, Toast.LENGTH_SHORT).show();
    }

    private void updateFontScaleButtonText() {
        if (btnToggleFontScale != null) {
            btnToggleFontScale.setText("🔤 界面字体尺寸: " + ThemeManager.getInstance(this).getFontScaleName() + " - 点击切换");
        }
    }

    private void toggleBonding() {
        MultiWanManager mwm = MultiWanManager.getInstance(this);
        String text = mwm.toggleNextBondingMode();
        updateBondingButtonText();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void updateBondingButtonText() {
        if (btnToggleBonding != null) {
            btnToggleBonding.setText(MultiWanManager.getInstance(this).getBondingStatusText() + " - 点击切换");
        }
    }

    private void applyCurrentTheme() {
        ThemeManager tm = ThemeManager.getInstance(this);
        tm.applyThemeToCard(webConfigDialog);
        tm.applyThemeToCard(dialogAiRecommendations);
        tm.applyThemeToCard(dialogReminders);
        tm.applyThemeToCard(dialogCloudMesh);
        tm.applyThemeToCard(aiAssistantOverlay);
        tm.applyThemeToCard(touchGestureHud);
        tm.applyThemeToCard(qosRadarOverlay);
        tm.applyThemeToCard(castHudBanner);
        tm.applyThemeToCard(playbackErrorOverlay);
        tm.applyThemeToCard(pipCard);
        tm.applyThemeToCard(voiceHudBanner);
        tm.applyThemeToCard(reminderHudBanner);
        tm.applyThemeToCard(subtitleHudBanner);
        applyCurrentFontScale();
    }

    private void applyCurrentFontScale() {
        float mult = ThemeManager.getInstance(this).getFontMultiplier();
        if (osdChannelNum != null) osdChannelNum.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28 * mult);
        if (osdChannelName != null) osdChannelName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22 * mult);
        if (osdProgram != null) osdProgram.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15 * mult);
        if (osdSource != null) osdSource.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13 * mult);
        if (epgTitle != null) epgTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20 * mult);
        if (epgSubtitle != null) epgSubtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13 * mult);
        if (subtitlePrimaryText != null) subtitlePrimaryText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20 * mult);
        if (subtitleSecondaryText != null) subtitleSecondaryText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13 * mult);
    }

    private void toggleAiSubtitle() {
        String modeName = AiSubtitleManager.getInstance(this).toggleNextMode();
        updateAiSubtitleButtonText();
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show();
    }

    private void updateAiSubtitleButtonText() {
        if (btnToggleAiSubtitle != null) {
            btnToggleAiSubtitle.setText("🤖 AI 实时双语字幕: " + AiSubtitleManager.getInstance(this).getModeName() + " - 点击切换");
        }
    }

    private void toggleWebRtcSync() {
        String modeName = WebRtcSyncManager.getInstance(this).toggleNextSyncMode();
        updateWebRtcSyncButtonText();
        if (osdSyncBadge != null) {
            String badge = WebRtcSyncManager.getInstance(this).getSyncBadge();
            if (!badge.isEmpty()) {
                osdSyncBadge.setText(badge);
                osdSyncBadge.setVisibility(View.VISIBLE);
            } else {
                osdSyncBadge.setVisibility(View.GONE);
            }
        }
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show();
    }

    private void updateWebRtcSyncButtonText() {
        if (btnToggleWebRtcSync != null) {
            btnToggleWebRtcSync.setText("⚡ 极速低延迟与同屏同步: " + WebRtcSyncManager.getInstance(this).getSyncModeName() + " - 点击切换");
        }
    }

    private void showCloudMeshDialog() {
        if (dialogCloudMesh == null) return;
        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);

        if (meshMyDeviceNameText != null) {
            meshMyDeviceNameText.setText("本机: " + CloudMeshManager.getInstance(this).getDeviceName());
        }

        List<CloudMeshManager.PeerDevice> peers = CloudMeshManager.getInstance(this).getActivePeers();
        if (meshDeviceAdapter == null) {
            meshDeviceAdapter = new MeshDeviceAdapter(peer -> {
                CloudMeshManager.getInstance(this).sendHandoffToPeer(peer.deviceId);
                Toast.makeText(this, "📲 已将当前节目推流接力至: " + peer.name, Toast.LENGTH_LONG).show();
                dialogCloudMesh.setVisibility(View.GONE);
            });
            if (meshRecyclerView != null) {
                meshRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                meshRecyclerView.setAdapter(meshDeviceAdapter);
            }
        }
        meshDeviceAdapter.setPeers(peers);

        if (meshEmptyText != null) {
            meshEmptyText.setVisibility(peers.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (meshRecyclerView != null) {
            meshRecyclerView.setVisibility(peers.isEmpty() ? View.GONE : View.VISIBLE);
        }

        dialogCloudMesh.setVisibility(View.VISIBLE);
        if (btnCloseMeshDialog != null) {
            btnCloseMeshDialog.requestFocus();
        }
    }

    private void scanNasMedia(boolean showToast) {
        if (showToast) {
            Toast.makeText(this, "📁 正在全盘检索本地与 NAS 影视库...", Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            List<SmbNasManager.MediaItem> items = SmbNasManager.getInstance(this).scanLocalAndUsbStorage();
            handler.post(() -> {
                applyAggregatedSubscriptions();
                if (showToast) {
                    Toast.makeText(this, "📁 扫描完成，共索引 " + items.size() + " 部 NAS/本地影片", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void toggleDolbyMode() {
        String name = BlurayDolbyManager.getInstance(this).toggleNextMode();
        updateDolbyButtonText();
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
    }

    private void updateDolbyButtonText() {
        if (btnToggleDolbyMode != null) {
            btnToggleDolbyMode.setText("🎬 蓝光原盘与杜比视界: " + BlurayDolbyManager.getInstance(this).getModeName() + " - 点击切换");
        }
    }

    private void toggleP2pMode() {
        String name = P2pEdgeAccelerationManager.getInstance(this).toggleNextMode();
        updateP2pButtonText();
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
    }

    private void updateP2pButtonText() {
        if (btnToggleP2pMode != null) {
            btnToggleP2pMode.setText("⚡ P2P 分布式边缘加速: " + P2pEdgeAccelerationManager.getInstance(this).getModeName() + " - 点击切换");
        }
    }

    private void showAiAssistantDialog(String prompt) {
        if (aiAssistantOverlay == null) return;
        if (webConfigDialog != null) webConfigDialog.setVisibility(View.GONE);
        if (aiAssistantPromptText != null) {
            aiAssistantPromptText.setText("问：" + (prompt != null ? prompt : "有什么好看的电影？"));
        }
        if (aiAssistantAnswerText != null) {
            aiAssistantAnswerText.setText("🤖 正在深度检索全网智库与本地影视库...");
        }
        if (btnAiAssistantAction != null) {
            btnAiAssistantAction.setVisibility(View.GONE);
        }
        aiAssistantOverlay.setVisibility(View.VISIBLE);
        LocalAiAssistantManager.getInstance(this).ask(prompt, allChannels, response -> {
            latestAiResponse = response;
            if (aiAssistantAnswerText != null) {
                aiAssistantAnswerText.setText(response.answer);
            }
            if (btnAiAssistantAction != null) {
                if (!"NONE".equals(response.action)) {
                    btnAiAssistantAction.setText("▶️ 立即播放匹配内容");
                    btnAiAssistantAction.setVisibility(View.VISIBLE);
                    btnAiAssistantAction.requestFocus();
                } else {
                    btnAiAssistantAction.setVisibility(View.GONE);
                }
            }
        });
    }

    private void initTouchGestures() {
        touchGestureManager = new TouchGestureOverlayManager(this);
        touchGestureManager.setGestureListener(new TouchGestureOverlayManager.GestureListener() {
            @Override
            public void onBrightnessChanged(int percent) {
                showTouchHud("☀️", "亮度 " + percent + "%", percent);
            }

            @Override
            public void onVolumeChanged(int current, int max) {
                int percent = (max > 0) ? (current * 100 / max) : 0;
                showTouchHud("🔊", "音量 " + current + "/" + max, percent);
            }

            @Override
            public void onSeekPreview(long offsetSeconds) {
                String prefix = offsetSeconds >= 0 ? "+" : "";
                showTouchHud(offsetSeconds >= 0 ? "⏩" : "⏪", "快进 " + prefix + offsetSeconds + "秒", 50);
            }

            @Override
            public void onSeekCommit(long offsetSeconds) {
                if (playerManager != null) {
                    playerManager.seekBy(offsetSeconds * 1000);
                    Toast.makeText(MainActivity.this, (offsetSeconds >= 0 ? "⏩ 快进 " : "⏪ 快退 ") + Math.abs(offsetSeconds) + "秒", Toast.LENGTH_SHORT).show();
                }
                hideTouchHud();
            }

            @Override
            public void onDoubleTap() {
                if (playerManager != null) {
                    if (playerManager.isPlaying()) {
                        playerManager.pause();
                        showTouchHud("⏸️", "已暂停播放", 0);
                    } else {
                        playerManager.play();
                        showTouchHud("▶️", "继续播放", 100);
                    }
                }
            }

            @Override
            public void onSingleTap() {
                if (bottomOsd != null) {
                    if (bottomOsd.getVisibility() == View.VISIBLE) {
                        bottomOsd.setVisibility(View.GONE);
                    } else {
                        showOsd();
                    }
                }
            }

            @Override
            public void onSpeedBoostStart(float speed) {
                if (playerManager != null) {
                    playerManager.setPlaybackSpeed(speed);
                    showTouchHud("⚡", speed + "x 快速预览中...", 100);
                }
            }

            @Override
            public void onSpeedBoostEnd() {
                if (playerManager != null) {
                    playerManager.setPlaybackSpeed(1.0f);
                    hideTouchHud();
                }
            }
        });
    }

    private void showTouchHud(String icon, String title, int progress) {
        if (touchGestureHud == null) return;
        if (touchGestureIcon != null) touchGestureIcon.setText(icon);
        if (touchGestureTitle != null) touchGestureTitle.setText(title);
        if (touchGestureProgress != null) {
            touchGestureProgress.setVisibility(progress >= 0 ? View.VISIBLE : View.GONE);
            touchGestureProgress.setProgress(progress);
        }
        touchGestureHud.setVisibility(View.VISIBLE);

        if (hideTouchHudRunnable != null) {
            handler.removeCallbacks(hideTouchHudRunnable);
        }
        hideTouchHudRunnable = () -> {
            if (touchGestureHud != null) touchGestureHud.setVisibility(View.GONE);
        };
        handler.postDelayed(hideTouchHudRunnable, 1200);
    }

    private void hideTouchHud() {
        if (hideTouchHudRunnable != null) {
            handler.removeCallbacks(hideTouchHudRunnable);
        }
        if (touchGestureHud != null) {
            touchGestureHud.setVisibility(View.GONE);
        }
    }

    private void enterSystemPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                builder.setAspectRatio(new Rational(16, 9));
                enterPictureInPictureMode(builder.build());
            } catch (Exception e) {
                try {
                    enterPictureInPictureMode();
                } catch (Exception ex) {
                    Toast.makeText(this, "进入系统画中画失败: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "当前系统不支持系统级画中画 (需要 Android 8.0+)", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!DeviceFormFactorManager.getInstance(this).isTv()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && playerManager != null && playerManager.isPlaying()) {
            enterSystemPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideAllPanels();
            if (bottomOsd != null) bottomOsd.setVisibility(View.GONE);
            if (castHudBanner != null) castHudBanner.setVisibility(View.GONE);
            if (voiceHudBanner != null) voiceHudBanner.setVisibility(View.GONE);
            if (reminderHudBanner != null) reminderHudBanner.setVisibility(View.GONE);
            if (subtitleHudBanner != null) subtitleHudBanner.setVisibility(View.GONE);
        }
    }

    private void toggleOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Toast.makeText(this, "📱 屏幕已切换至竖屏模式", Toast.LENGTH_SHORT).show();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            Toast.makeText(this, "📺 屏幕已切换至横屏大屏模式", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleCellularGuard() {
        boolean en = CellularDataGuardManager.getInstance(this).toggleCellularGuard();
        updateCellularGuardButtonText();
        checkCellularDataAlert();
        Toast.makeText(this, en ? "📶 移动蜂窝流量防护: 已开启 (防流量偷跑)" : "📶 移动蜂窝流量防护: 已关闭 (允许流量播放)", Toast.LENGTH_SHORT).show();
    }

    private void updateCellularGuardButtonText() {
        if (btnToggleCellularGuard != null) {
            btnToggleCellularGuard.setText(CellularDataGuardManager.getInstance(this).getCellularGuardStatusText() + " - 点击切换");
        }
    }

    private void checkCellularDataAlert() {
        CellularDataGuardManager guard = CellularDataGuardManager.getInstance(this);
        if (guard.isCellularNetwork() && guard.isCellularGuardEnabled()) {
            if (cellularGuardBanner != null) cellularGuardBanner.setVisibility(View.VISIBLE);
        } else {
            if (cellularGuardBanner != null) cellularGuardBanner.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(reminderCheckerRunnable);
        handler.post(reminderCheckerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(reminderCheckerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dlnaDmrManager != null) dlnaDmrManager.stop();
        if (playerManager != null) playerManager.release();
        if (webServer != null) webServer.stop();
        CloudMeshManager.getInstance(this).stop();
        handler.removeCallbacksAndMessages(null);
    }
}
