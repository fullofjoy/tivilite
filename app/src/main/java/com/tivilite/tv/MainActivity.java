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
    private ImageView osdLogo;
    private TextView webServerUrlText;

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
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initPlayer();
        initWebServer();
        loadChannels();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        leftSidebar = findViewById(R.id.leftSidebar);
        rightEpgPanel = findViewById(R.id.rightEpgPanel);
        bottomOsd = findViewById(R.id.bottomOsd);
        webConfigDialog = findViewById(R.id.webConfigDialog);

        groupRecyclerView = findViewById(R.id.groupRecyclerView);
        channelRecyclerView = findViewById(R.id.channelRecyclerView);
        epgRecyclerView = findViewById(R.id.epgRecyclerView);

        osdChannelNum = findViewById(R.id.osdChannelNum);
        osdChannelName = findViewById(R.id.osdChannelName);
        osdProgram = findViewById(R.id.osdProgram);
        osdBadge = findViewById(R.id.osdBadge);
        osdLogo = findViewById(R.id.osdLogo);
        webServerUrlText = findViewById(R.id.webServerUrlText);

        groupRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        channelRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        epgRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        channelAdapter = new ChannelAdapter(this::playSelectedChannel);
        channelRecyclerView.setAdapter(channelAdapter);

        epgAdapter = new EpgAdapter(program -> {
            Channel ch = playerManager.getCurrentChannel();
            if (ch != null) {
                if (program.isCatchup() && !com.tivilite.tv.license.LicenseManager.isPro(this)) {
                    showProDialog();
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

    private void showProDialog() {
        String devId = com.tivilite.tv.license.LicenseManager.getDeviceId(this);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pro_upgrade, null);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        TextView tvDevId = dialogView.findViewById(R.id.dialogDeviceId);
        android.widget.EditText inputCode = dialogView.findViewById(R.id.dialogInputCode);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancelPro);
        android.widget.Button btnActivate = dialogView.findViewById(R.id.btnActivatePro);

        tvDevId.setText(devId);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnActivate.setOnClickListener(v -> {
            String code = inputCode.getText().toString().trim();
            if (com.tivilite.tv.license.LicenseManager.activate(this, code)) {
                Toast.makeText(this, "🎉 激活成功！已解锁 TiviLite Pro 永久特权", Toast.LENGTH_LONG).show();
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
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initWebServer() {
        webServer = new WebPushServer(8888, new WebPushServer.OnM3UReceivedListener() {
            @Override
            public void onM3UUrlReceived(String url) {
                fetchM3UFromUrl(url);
            }

            @Override
            public void onM3UContentReceived(String content) {
                saveAndApplyM3U(content);
            }
        });
        webServer.start();
        String localIp = WebPushServer.getLocalIpAddress();
        webServerUrlText.setText("http://" + localIp + ":8888");
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
        groupedChannels = M3UParser.groupByCategories(allChannels);

        List<String> groups = new ArrayList<>(groupedChannels.keySet());
        groupAdapter = new GroupAdapter(groups, groupName -> {
            List<Channel> channels = groupedChannels.get(groupName);
            channelAdapter.setChannels(channels);
        });
        groupRecyclerView.setAdapter(groupAdapter);

        if (!allChannels.isEmpty()) {
            channelAdapter.setChannels(allChannels);
            playSelectedChannel(allChannels.get(0));
        }
    }

    private void playSelectedChannel(Channel channel) {
        playerManager.playChannel(channel);
        channelAdapter.setCurrentPlayingChannel(channel);
        updateOsd(channel);
        showOsd();
        leftSidebar.setVisibility(View.GONE);
    }

    private void updateOsd(Channel channel) {
        osdChannelNum.setText(String.format("%02d", channel.getNumber()));
        osdChannelName.setText(channel.getName());
        osdProgram.setText(channel.hasCatchup() ? "正在播出：高清直播 (支持7天时移回看)" : "正在播出：高清直播");
        osdBadge.setText(channel.hasCatchup() ? "时移回看" : "高清实时");

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
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    showOsd();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    switchChannelRelative(-1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (leftSidebar.getVisibility() != View.VISIBLE && rightEpgPanel.getVisibility() != View.VISIBLE) {
                    switchChannelRelative(1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_MENU) {
                toggleWebConfigDialog();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
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
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(this, "再按一次退出 TiviLite", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
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
            String ip = WebPushServer.getLocalIpAddress();
            webServerUrlText.setText("http://" + ip + ":8888");
            webConfigDialog.setVisibility(View.VISIBLE);
        }
    }

    private void fetchM3UFromUrl(String urlStr) {
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (InputStream in = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    handler.post(() -> saveAndApplyM3U(sb.toString()));
                }
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "拉取 M3U 订阅失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveAndApplyM3U(String content) {
        try {
            File cacheFile = new File(getFilesDir(), "playlist.m3u");
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            loadChannels();
            Toast.makeText(this, "🎉 订阅源更新成功！共加载 " + allChannels.size() + " 个频道", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
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
