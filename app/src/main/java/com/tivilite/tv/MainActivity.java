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

    private TextView osdChannelNum;
    private TextView osdChannelName;
    private TextView osdProgram;
    private TextView osdBadge;
    private TextView osdSource;
    private ImageView osdLogo;
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
    private CardView onboardingOverlay;
    private ImageView onboardingQrCode;
    private TextView onboardingServerUrl;
    private android.widget.Button btnOnboardingDemo;
    private LinearLayout remoteHintToast;
    private boolean invertUpDown = false;

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

        invertUpDown = getSharedPreferences("tivilite_prefs", MODE_PRIVATE).getBoolean("invert_up_down", false);
        com.tivilite.tv.license.LicenseManager.startTrial(this);

        initViews();
        initPlayer();
        initWebServer();
        loadChannels();

        // Silent background update check after app launch
        handler.postDelayed(() -> com.tivilite.tv.update.UpdateManager.checkUpdate(this, false), 4000);
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
    }

    private void initWebServer() {
        webServer = new WebPushServer(8888, new WebPushServer.OnM3UReceivedListener() {
            @Override
            public void onM3UUrlReceived(String url) {
                handler.post(() -> Toast.makeText(MainActivity.this, "📡 收到新订阅源，正在连接拉取...", Toast.LENGTH_SHORT).show());
                fetchM3UFromUrl(url);
            }

            @Override
            public void onM3UContentReceived(String content) {
                handler.post(() -> saveAndApplyM3U(content));
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
        File cacheFile = new File(getFilesDir(), "playlist.m3u");
        String content = "";
        if (cacheFile.exists()) {
            try (FileInputStream fis = new FileInputStream(cacheFile);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                content = sb.toString();
            } catch (Exception ignored) {}
        }

        allChannels = M3UParser.parse(content);
        groupedChannels = M3UParser.groupByCategories(this, allChannels);

        List<String> groups = new ArrayList<>(groupedChannels.keySet());
        groupAdapter = new GroupAdapter(groups, groupName -> {
            List<Channel> channels = groupedChannels.get(groupName);
            channelAdapter.setChannels(channels);
        });
        groupRecyclerView.setAdapter(groupAdapter);

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

    private void playSelectedChannel(Channel channel) {
        if (playbackErrorOverlay != null) {
            playbackErrorOverlay.setVisibility(View.GONE);
        }
        playerManager.playChannel(channel);
        channelAdapter.setCurrentPlayingChannel(channel);
        updateOsd(channel);
        showOsd();
        leftSidebar.setVisibility(View.GONE);

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

        if (channel.getLogoUrl() != null && !channel.getLogoUrl().isEmpty()) {
            Glide.with(this).load(channel.getLogoUrl()).placeholder(R.drawable.app_banner).into(osdLogo);
        } else {
            osdLogo.setImageResource(R.drawable.app_banner);
        }
    }

    private void showOsd() {
        bottomOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 5000);
    }

    private void populateEpgTimeline(Channel channel) {
        long now = System.currentTimeMillis();
        long oneHour = 3600 * 1000L;
        List<Program> list = new ArrayList<>();

        // Generate mock timeline for past 3 days and today (ready for Catch-up)
        list.add(new Program("当前直播节目", now - 1800000, now + 1800000, false));
        list.add(new Program("早间新闻精选 (回看)", now - (oneHour * 4), now - (oneHour * 3), true));
        list.add(new Program("晚间黄金剧场 (回看)", now - (oneHour * 12), now - (oneHour * 10), true));
        list.add(new Program("昨日综合焦点 (回看)", now - (oneHour * 24), now - (oneHour * 22), true));
        list.add(new Program("前日特别报道 (回看)", now - (oneHour * 48), now - (oneHour * 46), true));
        list.add(new Program("大屏精选纪录片 (回看)", now - (oneHour * 72), now - (oneHour * 70), true));

        epgAdapter.setPrograms(list);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // 0. Interactive Failover & Error Overlay Navigation
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
                    leftSidebar.setVisibility(View.VISIBLE);
                    channelRecyclerView.requestFocus();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (leftSidebar.getVisibility() == View.VISIBLE) {
                    leftSidebar.setVisibility(View.GONE);
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
                    leftSidebar.setVisibility(View.GONE);
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
            String ip = WebPushServer.getLocalIpAddress();
            String url = "http://" + ip + ":8888";
            webServerUrlText.setText(url);
            android.graphics.Bitmap qrBmp = generateQrCode(url);
            if (qrBmp != null && qrCodeImageView != null) {
                qrCodeImageView.setImageBitmap(qrBmp);
            }
            webConfigDialog.setVisibility(View.VISIBLE);
            if (btnToggleDirection != null) {
                btnToggleDirection.requestFocus();
            }
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
                            handler.post(() -> saveAndApplyM3U(content));
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

    private void saveAndApplyM3U(String content) {
        if (content == null || content.trim().isEmpty()) {
            Toast.makeText(this, "⚠️ 推送的内容为空，未更新频道", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File cacheFile = new File(getFilesDir(), "playlist.m3u");
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            loadChannels();
            if (!allChannels.isEmpty()) {
                currentChannelIndex = 0;
                playSelectedChannel(allChannels.get(0));
                Toast.makeText(this, "🎉 订阅源更新成功！共加载 " + allChannels.size() + " 个频道", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "⚠️ 未在订阅中解析出有效频道，请检查源格式 (支持 M3U / TXT)", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存订阅源异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerManager != null) playerManager.release();
        if (webServer != null) webServer.stop();
        handler.removeCallbacksAndMessages(null);
    }
}
