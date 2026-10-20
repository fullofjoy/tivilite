package com.tivilite.tv.server;

import android.content.Context;
import com.tivilite.tv.ai.AiRecommendationManager;
import com.tivilite.tv.cast.DlnaDmrManager;
import com.tivilite.tv.data.SubscriptionManager;
import com.tivilite.tv.epg.ReminderManager;
import com.tivilite.tv.player.LanMulticastManager;
import com.tivilite.tv.player.QosRadarManager;
import com.tivilite.tv.plugin.PluginManager;
import com.tivilite.tv.nas.SmbNasManager;
import com.tivilite.tv.network.MultiWanManager;
import com.tivilite.tv.ui.ThemeManager;
import com.tivilite.tv.subtitle.AiSubtitleManager;
import com.tivilite.tv.sync.WebRtcSyncManager;
import com.tivilite.tv.mesh.CloudMeshManager;
import com.tivilite.tv.player.BlurayDolbyManager;
import com.tivilite.tv.ai.LocalAiAssistantManager;
import com.tivilite.tv.p2p.P2pEdgeAccelerationManager;
import com.tivilite.tv.record.RecordingManager;
import com.tivilite.tv.sync.WebDavClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class WebPushServer {

    public interface OnM3UReceivedListener {
        void onM3UUrlReceived(String url, String name, boolean isAppend);
        void onM3UContentReceived(String content, String name, boolean isAppend);
        void onLicenseCodeReceived(String code);
        void onCheckUpdateRequested();
        void onEpgUrlReceived(String epgUrl);
        void onSubscriptionToggled(String id, boolean enabled);
        void onSubscriptionDeleted(String id);
        void onRemoteKeyReceived(String key, String value);
        void onRemoteZapReceived(int index, String name, int number);
        void onChannelFavoriteToggled(String channelName);
        void onAutoBootToggled();
        void onDecoderToggled();
        void onAspectRatioToggled();
        void onAudioTrackToggled();
        void onBestSourceRequested();
        void onLanSyncPullRequested(String targetIp);
        void onSubtitleToggled();
        void onBufferModeToggled();
        void onRecordingToggled();
        void onWebDavSyncRequested(String url, String user, String pass, boolean isUpload, boolean autoSync);

        // Phase 6
        void onPipToggled();
        void onPipSwapped();
        void onPipAudioToggled();
        void onVoiceCommandReceived(String command);
        void onPipChannelPlayRequested(int index, String name, int number);

        // Phase 7
        void onAudioModeToggled();
        void onPictureModeToggled();
        void onAudioModeChanged(int mode);
        void onPictureModeChanged(int mode);

        // Phase 8
        void onQosRadarToggled();
        void onAutoFailoverToggled();
        void onCastStopRequested();

        // Phase 9
        void onThemeToggled();
        void onFontScaleToggled();
        void onNasScanRequested();
        void onBondingModeToggled();
        void onNasPlayRequested(String path, String name);

        // Phase 10
        void onAiSubtitleToggled();
        void onWebRtcSyncToggled();
        void onMeshHandoffRequested(String targetDeviceId);

        // Phase 11
        void onDolbyModeToggled();
        void onP2pModeToggled();
        void onAiAssistantQuery(String prompt);

        // Stage 1 Mobile & Tablet
        void onToggleOrientation();
        void onEnterPipRequested();
        void onCellularGuardToggled();

        // Stage 12: LAN Subscription Mirror, Universal Handoff & Cloud NAS DVR
        void onCloneLanSubscriptionRequested();
        void onPullHandoffRequested();
        void onScheduleDvrRequested(String channelName, String channelUrl, String programTitle, long startTime, long endTime);
    }

    public interface StatusProvider {
        JSONObject getStatusJson();
        JSONArray getChannelsJson();
    }

    private final Context context;
    private final int port;
    private final OnM3UReceivedListener listener;
    private StatusProvider statusProvider;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public WebPushServer(int port, OnM3UReceivedListener listener) {
        this(null, port, listener);
    }

    public WebPushServer(Context context, int port, OnM3UReceivedListener listener) {
        this.context = context;
        this.port = port;
        this.listener = listener;
    }

    public void setStatusProvider(StatusProvider provider) {
        this.statusProvider = provider;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (isRunning && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    handleClient(socket);
                }
            } catch (Exception ignored) {
                // Server closed or network changed
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try {
                socket.setSoTimeout(10000);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // 1. Read HTTP request headers byte-by-byte until \r\n\r\n
                ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
                int b;
                while ((b = in.read()) != -1) {
                    headerBuf.write(b);
                    int sz = headerBuf.size();
                    byte[] data = headerBuf.toByteArray();
                    if (sz >= 4 && data[sz - 4] == '\r' && data[sz - 3] == '\n'
                            && data[sz - 2] == '\r' && data[sz - 1] == '\n') {
                        break;
                    }
                    if (sz >= 2 && data[sz - 2] == '\n' && data[sz - 1] == '\n') {
                        break;
                    }
                    if (sz > 65536) break; // header limit 64KB
                }

                String headerStr = headerBuf.toString(StandardCharsets.UTF_8.name());
                String[] headerLines = headerStr.split("\r?\n");
                if (headerLines.length == 0 || headerLines[0].trim().isEmpty()) return;

                String firstLine = headerLines[0];
                int contentLength = 0;
                for (String h : headerLines) {
                    if (h.toLowerCase().startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(h.substring(15).trim());
                        } catch (Exception ignored) {}
                    }
                }

                // 2. Read exact request body bytes based on contentLength
                String body = "";
                if (contentLength > 0) {
                    byte[] bodyBytes = new byte[contentLength];
                    int totalRead = 0;
                    while (totalRead < contentLength) {
                        int r = in.read(bodyBytes, totalRead, contentLength - totalRead);
                        if (r == -1) break;
                        totalRead += r;
                    }
                    body = new String(bodyBytes, 0, totalRead, StandardCharsets.UTF_8);
                }

                if (firstLine.startsWith("OPTIONS")) {
                    serveOptionsResponse(out);
                    return;
                }

                if (firstLine.startsWith("GET")) {
                    if (firstLine.contains("/live/multicast") || firstLine.contains("/stream/relay")) {
                        handleMulticastOrRelayStream(firstLine, out);
                        return;
                    } else if (firstLine.contains("/api/reminders") && context != null) {
                        List<ReminderManager.ReminderItem> list = ReminderManager.getInstance(context).getAllReminders();
                        JSONArray arr = new JSONArray();
                        for (ReminderManager.ReminderItem r : list) {
                            JSONObject j = r.toJson();
                            if (j != null) arr.put(j);
                        }
                        serveJsonResponse(out, arr.toString());
                    } else if (firstLine.contains("/api/recordings/download") && context != null) {
                        handleFileDownload(firstLine, out);
                        return;
                    } else if (firstLine.contains("/api/recordings") && context != null) {
                        serveJsonResponse(out, RecordingManager.getRecordingsJson(context).toString());
                    } else if (firstLine.contains("/api/subscriptions") && context != null) {
                        List<SubscriptionManager.Subscription> subs = SubscriptionManager.getSubscriptions(context);
                        JSONArray arr = new JSONArray();
                        for (SubscriptionManager.Subscription s : subs) arr.put(s.toJson());
                        serveJsonResponse(out, arr.toString());
                    } else if (firstLine.contains("/api/status")) {
                        JSONObject status = (statusProvider != null) ? statusProvider.getStatusJson() : new JSONObject();
                        serveJsonResponse(out, status.toString());
                    } else if (firstLine.contains("/api/channels")) {
                        JSONArray chs = (statusProvider != null) ? statusProvider.getChannelsJson() : new JSONArray();
                        serveJsonResponse(out, chs.toString());
                    } else if (firstLine.contains("/api/export") && context != null) {
                        JSONObject exportData = SubscriptionManager.exportAllData(context);
                        serveJsonResponse(out, exportData.toString());
                    } else if (firstLine.contains("/api/sync/webdav/status") && context != null) {
                        JSONObject dav = new JSONObject();
                        dav.put("url", WebDavClient.getWebDavUrl(context));
                        dav.put("user", WebDavClient.getWebDavUser(context));
                        dav.put("auto_sync", WebDavClient.isWebDavAutoSync(context));
                        dav.put("last_sync", WebDavClient.getFormattedLastSyncTime(context));
                        serveJsonResponse(out, dav.toString());
                    } else if (firstLine.contains("/api/recommendations")) {
                        serveJsonResponse(out, AiRecommendationManager.getRecommendationsJson().toString());
                    } else if (firstLine.contains("/dlna/description.xml") || firstLine.contains("/dlna/device.xml")) {
                        serveXmlResponse(out, DlnaDmrManager.getInstance(context).getDeviceDescriptionXml(""));
                        return;
                    } else if (firstLine.contains("/dlna/avtransport.xml")) {
                        serveXmlResponse(out, DlnaDmrManager.getInstance(context).getAvTransportScpdXml());
                        return;
                    } else if (firstLine.contains("/dlna/renderingcontrol.xml")) {
                        serveXmlResponse(out, DlnaDmrManager.getInstance(context).getRenderingControlScpdXml());
                        return;
                    } else if (firstLine.contains("/dlna/connectionmanager.xml")) {
                        serveXmlResponse(out, DlnaDmrManager.getInstance(context).getConnectionManagerScpdXml());
                        return;
                    } else if (firstLine.contains("/api/qos") && context != null) {
                        serveJsonResponse(out, QosRadarManager.getInstance(context).getLatestData().toJson().toString());
                    } else if (firstLine.contains("/api/plugins") && context != null) {
                        serveJsonResponse(out, PluginManager.getInstance(context).getPluginsJson().toString());
                    } else if (firstLine.contains("/api/cast/status") && context != null) {
                        DlnaDmrManager dmr = DlnaDmrManager.getInstance(context);
                        JSONObject c = new JSONObject();
                        c.put("isCasting", dmr.isCasting());
                        c.put("isPaused", dmr.isPaused());
                        c.put("title", dmr.getCurrentMediaTitle());
                        c.put("uri", dmr.getCurrentMediaUri());
                        serveJsonResponse(out, c.toString());
                    } else if (firstLine.contains("/api/nas") && context != null) {
                        serveJsonResponse(out, SmbNasManager.getInstance(context).getMediaListJson().toString());
                    } else if (firstLine.contains("/api/theme") && context != null) {
                        serveJsonResponse(out, ThemeManager.getInstance(context).getThemeStatusJson().toString());
                    } else if (firstLine.contains("/api/multiwan") && context != null) {
                        serveJsonResponse(out, MultiWanManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/subtitle") && context != null) {
                        serveJsonResponse(out, AiSubtitleManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/sync") && context != null) {
                        serveJsonResponse(out, WebRtcSyncManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/mesh") && context != null) {
                        serveJsonResponse(out, CloudMeshManager.getInstance(context).getMeshStatusJson().toString());
                    } else if (firstLine.contains("/api/cinema") && context != null) {
                        serveJsonResponse(out, BlurayDolbyManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/p2p") && context != null) {
                        serveJsonResponse(out, P2pEdgeAccelerationManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/mirror") && context != null) {
                        serveJsonResponse(out, com.tivilite.tv.sync.LanSubscriptionMirrorManager.getInstance(context).getMirrorStatusJson().toString());
                    } else if (firstLine.contains("/api/handoff") && context != null) {
                        serveJsonResponse(out, com.tivilite.tv.mesh.UniversalHandoffManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/dvr") && context != null) {
                        serveJsonResponse(out, com.tivilite.tv.record.NasDvrSchedulerManager.getInstance(context).getStatusJson().toString());
                    } else if (firstLine.contains("/api/device") && context != null) {
                        try {
                            org.json.JSONObject dev = new org.json.JSONObject();
                            dev.put("formFactor", com.tivilite.tv.device.DeviceFormFactorManager.getInstance(context).getFormFactor().name());
                            dev.put("formFactorName", com.tivilite.tv.device.DeviceFormFactorManager.getInstance(context).getFormFactorName());
                            dev.put("deviceModel", com.tivilite.tv.device.DeviceFormFactorManager.getInstance(context).getDeviceModelInfo());
                            dev.put("hasTouch", com.tivilite.tv.device.DeviceFormFactorManager.getInstance(context).hasTouchscreen());
                            dev.put("networkBadge", com.tivilite.tv.network.CellularDataGuardManager.getInstance(context).getNetworkTypeBadge());
                            dev.put("isCellular", com.tivilite.tv.network.CellularDataGuardManager.getInstance(context).isCellularNetwork());
                            dev.put("cellularGuardEnabled", com.tivilite.tv.network.CellularDataGuardManager.getInstance(context).isCellularGuardEnabled());
                            serveJsonResponse(out, dev.toString());
                        } catch (Exception e) {
                            serveJsonResponse(out, "{}");
                        }
                    } else {
                        serveHtmlPage(out);
                    }
                } else if (firstLine.startsWith("POST")) {
                    if (firstLine.contains("/dlna/control/")) {
                        String soapAction = "";
                        for (String h : headerLines) {
                            if (h.toLowerCase().startsWith("soapaction:")) {
                                soapAction = h.substring(11).trim();
                                break;
                            }
                        }
                        String soapResp = DlnaDmrManager.getInstance(context).handleSoapRequest(firstLine, soapAction, body);
                        serveXmlResponse(out, soapResp);
                        return;
                    }
                    String customResponse = handlePostData(firstLine, body);
                    if (customResponse != null && !customResponse.isEmpty()) {
                        serveJsonResponse(out, customResponse);
                    } else {
                        serveJsonResponse(out, "{\"success\":true,\"message\":\"大屏操作已实时响应！\"}");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleFileDownload(String requestLine, OutputStream out) {
        try {
            int qIdx = requestLine.indexOf("?");
            if (qIdx < 0) return;
            String query = requestLine.substring(qIdx + 1).split(" ")[0];
            String fileName = "";
            for (String part : query.split("&")) {
                int eq = part.indexOf("=");
                if (eq > 0 && "name".equals(part.substring(0, eq))) {
                    fileName = URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                    break;
                }
            }
            if (fileName.isEmpty()) return;
            File targetFile = new File(RecordingManager.getRecordingsDir(context), fileName);
            if (!targetFile.exists() || !targetFile.isFile()) return;

            long fileLen = targetFile.length();
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp2t\r\n" +
                    "Content-Length: " + fileLen + "\r\n" +
                    "Content-Disposition: attachment; filename=\"" + fileName + "\"\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));

            try (FileInputStream fis = new FileInputStream(targetFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMulticastOrRelayStream(String requestLine, OutputStream out) {
        try {
            int qIdx = requestLine.indexOf("?");
            if (qIdx < 0) return;
            String query = requestLine.substring(qIdx + 1).split(" ")[0];
            String targetUrl = "";
            for (String part : query.split("&")) {
                int eq = part.indexOf("=");
                if (eq > 0 && "url".equals(part.substring(0, eq))) {
                    targetUrl = URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                    break;
                }
            }
            if (targetUrl.isEmpty()) return;

            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp2t\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.flush();

            if (LanMulticastManager.isMulticastUrl(targetUrl)) {
                LanMulticastManager.getInstance(context).streamMulticastToClient(targetUrl, out);
            } else {
                LanMulticastManager.getInstance(context).streamHttpRelayToClient(targetUrl, out);
            }
        } catch (Exception ignored) {
        }
    }

    private String handlePostData(String requestLine, String body) {
        if (listener == null || body == null || body.trim().isEmpty()) return null;
        try {
            if (body.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(body);

                // Remote Key Event
                if (requestLine.contains("/api/remote/key") || obj.has("remote_key") || obj.has("key")) {
                    String key = obj.optString("remote_key", obj.optString("key", ""));
                    String val = obj.optString("value", "");
                    if (!key.isEmpty()) {
                        if ("aspect".equals(key)) {
                            listener.onAspectRatioToggled();
                            return null;
                        }
                        if ("audio".equals(key)) {
                            listener.onAudioTrackToggled();
                            return null;
                        }
                        if ("best_source".equals(key)) {
                            listener.onBestSourceRequested();
                            return null;
                        }
                        if ("subtitle".equals(key)) {
                            listener.onSubtitleToggled();
                            return null;
                        }
                        if ("buffer".equals(key)) {
                            listener.onBufferModeToggled();
                            return null;
                        }
                        if ("record".equals(key)) {
                            listener.onRecordingToggled();
                            return null;
                        }
                        if ("pip_toggle".equals(key)) {
                            listener.onPipToggled();
                            return null;
                        }
                        if ("pip_swap".equals(key)) {
                            listener.onPipSwapped();
                            return null;
                        }
                        if ("pip_audio".equals(key)) {
                            listener.onPipAudioToggled();
                            return null;
                        }
                        if ("qos_radar".equals(key)) {
                            listener.onQosRadarToggled();
                            return null;
                        }
                        if ("auto_failover".equals(key)) {
                            listener.onAutoFailoverToggled();
                            return null;
                        }
                        if ("cast_stop".equals(key)) {
                            DlnaDmrManager.getInstance(context).stopCast();
                            listener.onCastStopRequested();
                            return null;
                        }
                        if ("theme".equals(key)) {
                            listener.onThemeToggled();
                            return null;
                        }
                        if ("font_scale".equals(key)) {
                            listener.onFontScaleToggled();
                            return null;
                        }
                        if ("scan_nas".equals(key)) {
                            listener.onNasScanRequested();
                            return null;
                        }
                        if ("bonding".equals(key)) {
                            listener.onBondingModeToggled();
                            return null;
                        }
                        listener.onRemoteKeyReceived(key, val);
                        return null;
                    }
                }

                // Voice Command
                if (requestLine.contains("/api/voice") || obj.has("voice_command") || obj.has("command")) {
                    String cmd = obj.optString("voice_command", obj.optString("command", ""));
                    if (!cmd.isEmpty()) {
                        listener.onVoiceCommandReceived(cmd);
                        return "{\"success\":true,\"message\":\"电视已接收语音指令: " + cmd + "\"}";
                    }
                }

                // PiP Direct Play
                if (requestLine.contains("/api/pip/play") || obj.has("pip_channel_name") || obj.has("pip_index")) {
                    int idx = obj.optInt("pip_index", -1);
                    String name = obj.optString("pip_channel_name", obj.optString("pip_name", ""));
                    int num = obj.optInt("pip_number", -1);
                    listener.onPipChannelPlayRequested(idx, name, num);
                    return "{\"success\":true,\"message\":\"已在画中画副画面开启该频道\"}";
                }

                // Direct Channel Zap
                if (requestLine.contains("/api/remote/zap") || obj.has("zap_name") || obj.has("zap_number") || obj.has("zap_index")) {
                    int idx = obj.optInt("zap_index", obj.optInt("index", -1));
                    String name = obj.optString("zap_name", obj.optString("name", ""));
                    int num = obj.optInt("zap_number", obj.optInt("number", -1));
                    listener.onRemoteZapReceived(idx, name, num);
                    return null;
                }

                // Channel Favorite Toggle
                if (requestLine.contains("/api/channel/favorite") || obj.has("fav_channel")) {
                    String name = obj.optString("fav_channel", obj.optString("name", ""));
                    if (!name.isEmpty()) {
                        listener.onChannelFavoriteToggled(name);
                        return null;
                    }
                }

                // Auto Boot & Decoder Mode Toggles
                if (obj.has("toggle_auto_boot")) {
                    listener.onAutoBootToggled();
                    return null;
                }
                if (obj.has("toggle_decoder")) {
                    listener.onDecoderToggled();
                    return null;
                }
                if (obj.has("toggle_aspect")) {
                    listener.onAspectRatioToggled();
                    return null;
                }
                if (obj.has("toggle_audio")) {
                    listener.onAudioTrackToggled();
                    return null;
                }
                if (obj.has("best_source")) {
                    listener.onBestSourceRequested();
                    return null;
                }
                if (obj.has("toggle_subtitle")) {
                    listener.onSubtitleToggled();
                    return null;
                }
                if (obj.has("toggle_buffer")) {
                    listener.onBufferModeToggled();
                    return null;
                }
                if (obj.has("toggle_recording")) {
                    listener.onRecordingToggled();
                    return null;
                }
                if (obj.has("toggle_pip")) {
                    listener.onPipToggled();
                    return null;
                }
                if (obj.has("swap_pip")) {
                    listener.onPipSwapped();
                    return null;
                }
                if (obj.has("toggle_pip_audio")) {
                    listener.onPipAudioToggled();
                    return null;
                }

                // Phase 7: Audio Passthrough & Picture Quality Modes
                if (obj.has("toggle_audio_mode")) {
                    listener.onAudioModeToggled();
                    return null;
                }
                if (obj.has("toggle_picture_mode")) {
                    listener.onPictureModeToggled();
                    return null;
                }
                if (obj.has("set_audio_mode")) {
                    int mode = obj.optInt("set_audio_mode", 0);
                    listener.onAudioModeChanged(mode);
                    return null;
                }
                if (obj.has("set_picture_mode")) {
                    int mode = obj.optInt("set_picture_mode", 0);
                    listener.onPictureModeChanged(mode);
                    return null;
                }

                // Phase 8: QoS Radar, Auto Failover & Dynamic Plugins
                if (obj.has("toggle_qos_radar")) {
                    listener.onQosRadarToggled();
                    return null;
                }
                if (obj.has("toggle_auto_failover")) {
                    listener.onAutoFailoverToggled();
                    return null;
                }
                if (requestLine.contains("/api/cast/stop") || obj.has("stop_cast")) {
                    DlnaDmrManager.getInstance(context).stopCast();
                    listener.onCastStopRequested();
                    return "{\"success\":true,\"message\":\"已退出手机无线投屏，返回直播频道\"}";
                }
                if (requestLine.contains("/api/plugins/toggle") || obj.has("plugin_toggle_id")) {
                    String id = obj.optString("plugin_toggle_id", obj.optString("id", ""));
                    boolean en = obj.optBoolean("enabled", true);
                    if (context != null && !id.isEmpty()) {
                        boolean ok = PluginManager.getInstance(context).togglePlugin(id, en);
                        return "{\"success\":" + ok + ",\"message\":\"插件状态已切换\"}";
                    }
                }
                if (requestLine.contains("/api/plugins/delete") || obj.has("plugin_delete_id")) {
                    String id = obj.optString("plugin_delete_id", obj.optString("id", ""));
                    if (context != null && !id.isEmpty()) {
                        boolean ok = PluginManager.getInstance(context).deletePlugin(id);
                        return "{\"success\":" + ok + ",\"message\":\"插件已移除\"}";
                    }
                }
                if (requestLine.contains("/api/plugins/add") || obj.has("add_plugin")) {
                    if (context != null) {
                        PluginManager.PluginRule rule = PluginManager.PluginRule.fromJson(obj);
                        if (rule != null) {
                            boolean ok = PluginManager.getInstance(context).addOrUpdatePlugin(rule);
                            return "{\"success\":" + ok + ",\"message\":\"插件已成功导入/更新\"}";
                        }
                    }
                }

                // Phase 9: Theme, Font Scale, NAS Media & Multi-WAN
                if (obj.has("toggle_theme")) {
                    listener.onThemeToggled();
                    return null;
                }
                if (obj.has("toggle_font_scale")) {
                    listener.onFontScaleToggled();
                    return null;
                }
                if (requestLine.contains("/api/nas/scan") || obj.has("scan_nas")) {
                    listener.onNasScanRequested();
                    return "{\"success\":true,\"message\":\"已触发扫描本地存储与家庭私有云影视库\"}";
                }
                if (requestLine.contains("/api/nas/play") || obj.has("play_nas_path")) {
                    String path = obj.optString("play_nas_path", obj.optString("path", ""));
                    String name = obj.optString("name", "本地影片");
                    if (!path.isEmpty()) {
                        listener.onNasPlayRequested(path, name);
                        return "{\"success\":true,\"message\":\"电视正在播放: " + name + "\"}";
                    }
                }
                if (obj.has("toggle_bonding")) {
                    listener.onBondingModeToggled();
                    return null;
                }

                // Phase 10: AI Subtitles, WebRTC Low-Latency Sync & Cloud Mesh
                if (obj.has("toggle_ai_subtitle")) {
                    listener.onAiSubtitleToggled();
                    return null;
                }
                if (obj.has("toggle_webrtc_sync")) {
                    listener.onWebRtcSyncToggled();
                    return null;
                }
                if (requestLine.contains("/api/mesh/handoff") || obj.has("mesh_handoff")) {
                    String targetId = obj.optString("mesh_handoff", obj.optString("targetDeviceId", ""));
                    if (!targetId.isEmpty()) {
                        listener.onMeshHandoffRequested(targetId);
                        return "{\"success\":true,\"message\":\"已发起跨屏流转接力指令\"}";
                    }
                }

                // Phase 11: Blu-ray/Dolby Vision, P2P Acceleration & Local AI Assistant
                if (obj.has("toggle_dolby_mode")) {
                    listener.onDolbyModeToggled();
                    return null;
                }
                if (obj.has("toggle_p2p_mode")) {
                    listener.onP2pModeToggled();
                    return null;
                }
                if (requestLine.contains("/api/ai/ask") || obj.has("ai_prompt")) {
                    String p = obj.optString("ai_prompt", obj.optString("prompt", ""));
                    if (!p.isEmpty()) {
                        listener.onAiAssistantQuery(p);
                        return "{\"success\":true,\"message\":\"大屏 AI 助手已接收问题并正在解析执行\"}";
                    }
                }

                // Stage 1 Mobile & Tablet: Orientation, PiP & Cellular Guard
                if (obj.has("toggle_orientation")) {
                    listener.onToggleOrientation();
                    return null;
                }
                if (obj.has("enter_pip")) {
                    listener.onEnterPipRequested();
                    return null;
                }
                if (obj.has("toggle_cellular_guard")) {
                    listener.onCellularGuardToggled();
                    return null;
                }

                // Stage 12: LAN Subscription Mirror, Universal Handoff & Cloud NAS DVR
                if (requestLine.contains("/api/mirror/clone") || obj.has("mirror_clone")) {
                    listener.onCloneLanSubscriptionRequested();
                    return "{\"success\":true,\"message\":\"已发起局域网订阅探测与克隆同步\"}";
                }
                if (requestLine.contains("/api/handoff/pull") || obj.has("handoff_pull")) {
                    listener.onPullHandoffRequested();
                    return "{\"success\":true,\"message\":\"已发起近场播放接力提取\"}";
                }
                if (requestLine.contains("/api/dvr/schedule") || obj.has("dvr_schedule")) {
                    String ch = obj.optString("channel_name");
                    String prog = obj.optString("program_title");
                    long st = obj.optLong("start_time");
                    long et = obj.optLong("end_time");
                    String url = obj.optString("channel_url", "");
                    listener.onScheduleDvrRequested(ch, url, prog, st, et);
                    return "{\"success\":true,\"message\":\"已安排后台NAS/云端定时录制: " + prog + "\"}";
                }
                if (requestLine.contains("/api/dvr/cancel") || obj.has("dvr_cancel_id")) {
                    String id = obj.optString("dvr_cancel_id", obj.optString("id", ""));
                    if (context != null && !id.isEmpty()) {
                        com.tivilite.tv.record.NasDvrSchedulerManager.getInstance(context).cancelTask(id);
                        return "{\"success\":true,\"message\":\"已取消该定时录制任务\"}";
                    }
                }

                // Phase 7: EPG Program Reminders
                if (requestLine.contains("/api/reminders/add") || obj.has("add_reminder")) {
                    String ch = obj.optString("channel_name");
                    String prog = obj.optString("program_title");
                    long st = obj.optLong("start_time");
                    long et = obj.optLong("end_time");
                    String url = obj.optString("channel_url", "");
                    if (context != null && !ch.isEmpty() && !prog.isEmpty()) {
                        ReminderManager.getInstance(context).addReminder(ch, url, prog, st, et);
                        return "{\"success\":true,\"message\":\"已成功预约: " + prog + "\"}";
                    }
                }
                if (requestLine.contains("/api/reminders/delete") || obj.has("delete_reminder_id")) {
                    String id = obj.optString("delete_reminder_id", obj.optString("id", ""));
                    if (context != null && !id.isEmpty()) {
                        ReminderManager.getInstance(context).removeReminderById(id);
                        return "{\"success\":true,\"message\":\"已取消预约\"}";
                    }
                }

                // DVR Recordings Deletion
                if (requestLine.contains("/api/recordings/delete") || obj.has("recording_delete_name")) {
                    String delName = obj.optString("recording_delete_name", obj.optString("name", ""));
                    if (!delName.isEmpty() && context != null) {
                        boolean delOk = RecordingManager.deleteRecording(context, delName);
                        return "{\"success\":" + delOk + ",\"message\":\"已删除录制文件: " + delName + "\"}";
                    }
                    return null;
                }

                // WebDAV Cloud Sync
                if (requestLine.contains("/api/sync/webdav") || obj.has("webdav_action")) {
                    String action = obj.optString("webdav_action", obj.optString("action", "test"));
                    String davUrl = obj.optString("url", WebDavClient.getWebDavUrl(context));
                    String davUser = obj.optString("user", WebDavClient.getWebDavUser(context));
                    String davPass = obj.optString("pass", WebDavClient.getWebDavPass(context));
                    boolean autoSync = obj.optBoolean("auto_sync", WebDavClient.isWebDavAutoSync(context));

                    WebDavClient.saveWebDavConfig(context, davUrl, davUser, davPass, autoSync);

                    if ("test".equalsIgnoreCase(action)) {
                        WebDavClient.SyncResult res = WebDavClient.testConnection(davUrl, davUser, davPass);
                        return "{\"success\":" + res.success + ",\"message\":\"" + res.message.replace("\"", "\\\"") + "\"}";
                    } else if ("upload".equalsIgnoreCase(action)) {
                        WebDavClient.SyncResult res = WebDavClient.uploadBackup(context, davUrl, davUser, davPass);
                        return "{\"success\":" + res.success + ",\"message\":\"" + res.message.replace("\"", "\\\"") + "\"}";
                    } else if ("download".equalsIgnoreCase(action)) {
                        WebDavClient.SyncResult res = WebDavClient.downloadAndRestore(context, davUrl, davUser, davPass);
                        if (res.success) {
                            listener.onSubscriptionToggled("", true);
                        }
                        return "{\"success\":" + res.success + ",\"message\":\"" + res.message.replace("\"", "\\\"") + "\"}";
                    }
                    return "{\"success\":true,\"message\":\"WebDAV 配置已保存！\"}";
                }

                // LAN Sync Pull & Import
                if (requestLine.contains("/api/sync/pull") || obj.has("sync_pull_ip")) {
                    String ip = obj.optString("sync_pull_ip", obj.optString("ip", ""));
                    if (!ip.isEmpty()) listener.onLanSyncPullRequested(ip);
                    return null;
                }
                if (requestLine.contains("/api/sync/import") && context != null) {
                    SubscriptionManager.importAllData(context, obj);
                    listener.onSubscriptionToggled("", true);
                    return null;
                }

                // Subscription Management
                if (requestLine.contains("/api/subscription/toggle") || obj.has("sub_toggle_id")) {
                    String id = obj.optString("sub_toggle_id", obj.optString("id", ""));
                    boolean enabled = obj.optBoolean("enabled", true);
                    if (!id.isEmpty()) listener.onSubscriptionToggled(id, enabled);
                    return null;
                }

                if (requestLine.contains("/api/subscription/delete") || obj.has("sub_delete_id")) {
                    String id = obj.optString("sub_delete_id", obj.optString("id", ""));
                    if (!id.isEmpty()) listener.onSubscriptionDeleted(id);
                    return null;
                }

                boolean isAppend = !"overwrite".equalsIgnoreCase(obj.optString("mode", "append"));
                String subName = obj.optString("name", "自定义订阅");

                if (obj.has("m3u_url")) {
                    String url = obj.getString("m3u_url").trim();
                    if (!url.isEmpty()) listener.onM3UUrlReceived(url, subName, isAppend);
                }
                if (obj.has("m3u_content")) {
                    String content = obj.getString("m3u_content").trim();
                    if (!content.isEmpty()) listener.onM3UContentReceived(content, subName, isAppend);
                }
                if (obj.has("epg_url")) {
                    String epg = obj.getString("epg_url").trim();
                    if (!epg.isEmpty()) listener.onEpgUrlReceived(epg);
                }
                if (obj.has("license_code")) {
                    String code = obj.getString("license_code").trim();
                    if (!code.isEmpty()) listener.onLicenseCodeReceived(code);
                }
                if (obj.has("check_update")) {
                    listener.onCheckUpdateRequested();
                }
                return null;
            }

            // Support application/x-www-form-urlencoded
            String m3uUrl = "";
            String m3uContent = "";
            String licenseCode = "";
            String epgUrl = "";
            String name = "自定义订阅";
            boolean isAppend = true;

            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = URLDecoder.decode(pair.substring(0, eq).trim(), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1).trim(), "UTF-8");
                    if ("m3u_url".equals(key)) m3uUrl = val;
                    else if ("m3u_content".equals(key)) m3uContent = val;
                    else if ("license_code".equals(key)) licenseCode = val;
                    else if ("epg_url".equals(key)) epgUrl = val;
                    else if ("name".equals(key)) name = val;
                    else if ("mode".equals(key)) isAppend = !"overwrite".equalsIgnoreCase(val);
                    else if ("check_update".equals(key)) listener.onCheckUpdateRequested();
                    else if ("remote_key".equals(key)) listener.onRemoteKeyReceived(val, "");
                }
            }

            if (!m3uUrl.isEmpty()) listener.onM3UUrlReceived(m3uUrl, name, isAppend);
            if (!m3uContent.isEmpty()) listener.onM3UContentReceived(m3uContent, name, isAppend);
            if (!epgUrl.isEmpty()) listener.onEpgUrlReceived(epgUrl);
            if (!licenseCode.isEmpty()) listener.onLicenseCodeReceived(licenseCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void serveOptionsResponse(OutputStream out) throws Exception {
        String header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void serveHtmlPage(OutputStream out) throws Exception {
        StringBuilder sb = new StringBuilder();

        // 1. Head & Styles
        sb.append("<!DOCTYPE html><html lang='zh-CN'><head><meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no,viewport-fit=cover'>")
          .append("<title>TiviLite 灵动遥控与配源中心</title><style>")
          .append(":root{--bg:#0B0C10;--card:#151822;--accent:#00E5FF;--blue:#3D7BFD;--green:#10B981;--red:#EF4444;--gold:#F59E0B;--text:#F3F4F6;--muted:#9CA3AF;}")
          .append("* {box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent;}")
          .append("body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;background:var(--bg);color:var(--text);padding-bottom:30px;line-height:1.5;}")
          .append(".container{max-width:540px;margin:0 auto;padding:12px;}")
          
          // Header & Status Pill
          .append(".header{text-align:center;padding:10px 0 14px;}")
          .append(".header h1{color:var(--accent);font-size:20px;font-weight:800;display:flex;align-items:center;justify-content:center;gap:8px;}")
          .append(".tv-status-badge{display:inline-flex;align-items:center;gap:6px;background:rgba(16,185,129,0.12);border:1px solid rgba(16,185,129,0.3);color:var(--green);padding:4px 12px;border-radius:20px;font-size:12px;font-weight:bold;margin-top:6px;}")
          .append(".tv-dot{width:8px;height:8px;border-radius:50%;background:var(--green);animation:pulse 2s infinite;}")
          .append("@keyframes pulse{0%{opacity:1;}50%{opacity:0.3;}100%{opacity:1;}}")

          // Nav Tabs
          .append(".nav-tabs{display:flex;background:#10131B;border-radius:12px;padding:4px;gap:4px;margin-bottom:14px;border:1px solid rgba(255,255,255,0.06);}")
          .append(".nav-tab{flex:1;text-align:center;padding:10px 4px;font-size:13px;font-weight:bold;color:var(--muted);border-radius:8px;cursor:pointer;transition:all 0.15s;}")
          .append(".nav-tab.active{background:var(--blue);color:#FFF;box-shadow:0 4px 12px rgba(61,123,253,0.35);}")

          // Tab content panels
          .append(".tab-panel{display:none;}")
          .append(".tab-panel.active{display:block;}")

          // Cards & Common
          .append(".card{background:var(--card);border-radius:16px;padding:16px;margin-bottom:14px;border:1px solid rgba(255,255,255,0.08);box-shadow:0 8px 24px rgba(0,0,0,0.4);}")
          .append(".card-title{font-size:15px;font-weight:700;color:var(--accent);margin-bottom:12px;display:flex;align-items:center;justify-content:space-between;}")
          .append(".badge{background:rgba(0,229,255,0.15);color:var(--accent);padding:2px 8px;border-radius:4px;font-size:11px;font-weight:bold;}")
          .append("label{display:block;margin:10px 0 6px;color:#D1D5DB;font-size:13px;font-weight:600;}")
          .append("input,textarea,select{width:100%;box-sizing:border-box;background:#0C0E14;border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:12px;color:#FFF;font-size:14px;}")
          .append("input:focus,textarea:focus,select:focus{border-color:var(--accent);outline:none;background:#10141E;}")
          .append(".btn-primary{width:100%;background:var(--blue);color:#FFF;border:none;border-radius:10px;padding:12px;font-size:14px;font-weight:700;cursor:pointer;margin-top:12px;display:flex;align-items:center;justify-content:center;gap:6px;transition:opacity 0.15s;}")
          .append(".btn-primary:active{opacity:0.8;transform:scale(0.98);}")
          .append(".notice{background:rgba(16,185,129,0.12);border:1px solid rgba(16,185,129,0.3);color:var(--green);padding:10px 14px;border-radius:8px;font-size:13px;margin-bottom:12px;display:none;}")

          // Remote Control D-Pad
          .append(".dpad-wrap{display:flex;justify-content:center;margin:16px 0 20px;}")
          .append(".dpad{width:230px;height:230px;border-radius:50%;background:#1A1F2D;position:relative;border:2px solid rgba(255,255,255,0.12);box-shadow:0 10px 30px rgba(0,0,0,0.6);}")
          .append(".dpad-btn{position:absolute;background:transparent;border:none;color:var(--text);font-size:18px;font-weight:bold;cursor:pointer;display:flex;align-items:center;justify-content:center;}")
          .append(".dpad-btn:active{color:var(--accent);transform:scale(1.1);}")
          .append(".dpad-up{top:8px;left:50%;transform:translateX(-50%);width:70px;height:55px;}")
          .append(".dpad-down{bottom:8px;left:50%;transform:translateX(-50%);width:70px;height:55px;}")
          .append(".dpad-left{top:50%;left:8px;transform:translateY(-50%);width:55px;height:70px;}")
          .append(".dpad-right{top:50%;right:8px;transform:translateY(-50%);width:55px;height:70px;}")
          .append(".dpad-center{top:50%;left:50%;transform:translate(-50%,-50%);width:82px;height:82px;border-radius:50%;background:var(--blue);color:#FFF;font-size:17px;font-weight:800;border:2px solid var(--accent);box-shadow:0 0 16px rgba(0,229,255,0.3);}")
          .append(".dpad-center:active{background:var(--accent);color:#000;}")

          // Remote Keys Grid
          .append(".keys-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:12px;}")
          .append(".key-btn{background:#202534;border:1px solid rgba(255,255,255,0.1);color:var(--text);padding:12px 6px;border-radius:10px;font-size:12px;font-weight:bold;cursor:pointer;text-align:center;transition:all 0.1s;display:flex;flex-direction:column;align-items:center;gap:4px;}")
          .append(".key-btn:active{background:var(--accent);color:#000;transform:scale(0.96);}")
          .append(".key-accent{background:rgba(0,229,255,0.12);color:var(--accent);border-color:rgba(0,229,255,0.3);}")
          .append(".key-vol{background:#1B2438;border-color:rgba(61,123,253,0.3);color:#60A5FA;}")

          // Num Pad
          .append(".numpad{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:10px;}")
          .append(".num-btn{background:#181C28;border:1px solid rgba(255,255,255,0.08);color:#FFF;padding:12px;border-radius:8px;font-size:17px;font-weight:bold;cursor:pointer;font-family:monospace;text-align:center;}")
          .append(".num-btn:active{background:var(--blue);}")

          // Channel List in Phone
          .append(".ch-search{margin-bottom:10px;position:relative;}")
          .append(".ch-list-box{max-height:480px;overflow-y:auto;display:flex;flex-direction:column;gap:6px;}")
          .append(".ch-card{background:#0E1118;border:1px solid rgba(255,255,255,0.08);padding:10px 12px;border-radius:10px;display:flex;align-items:center;justify-content:space-between;cursor:pointer;}")
          .append(".ch-card:active{background:#1E2638;border-color:var(--accent);}")
          .append(".ch-left{display:flex;align-items:center;gap:10px;}")
          .append(".ch-num{font-family:monospace;font-size:13px;font-weight:bold;color:var(--accent);min-width:24px;}")
          .append(".ch-title{font-size:14px;font-weight:bold;color:#FFF;}")
          .append(".ch-group{font-size:11px;color:var(--muted);}")
          .append(".star-btn{background:none;border:none;font-size:18px;color:#9CA3AF;cursor:pointer;padding:4px;}")
          .append(".star-btn.fav{color:var(--gold);}")

          // Subscriptions
          .append(".sub-item{background:#0D1017;border:1px solid rgba(255,255,255,0.1);padding:10px 12px;border-radius:8px;margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;font-size:13px;}")
          .append(".sub-btn{padding:4px 8px;border-radius:4px;border:none;font-size:11px;font-weight:bold;cursor:pointer;margin-left:6px;}")
          .append(".btn-del{background:rgba(239,68,68,0.2);color:#EF4444;border:1px solid rgba(239,68,68,0.3);}")
          .append(".btn-toggle{background:rgba(16,185,129,0.2);color:#10B981;border:1px solid rgba(16,185,129,0.3);}")
          .append(".btn-file{background:#1E293B;border:1px dashed rgba(255,255,255,0.25);color:var(--accent);padding:12px;border-radius:10px;text-align:center;cursor:pointer;font-size:13px;font-weight:bold;margin-top:8px;}")
          .append(".btn-preset{background:#202534;border:1px solid rgba(255,255,255,0.12);color:var(--text);padding:12px;border-radius:10px;font-size:13px;font-weight:600;text-align:left;cursor:pointer;display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;}")
          .append(".voice-chip{background:#1B2030;color:var(--accent);border:1px solid rgba(0,229,255,0.25);padding:4px 10px;border-radius:14px;font-size:12px;font-weight:600;cursor:pointer;display:inline-block;}")
          .append(".voice-chip:active{background:var(--accent);color:#000;}")
          .append(".ai-card{background:#0E1118;border:1px solid rgba(255,255,255,0.08);border-radius:10px;padding:12px;margin-bottom:8px;}")
          .append("</style></head><body>");

        // 2. HTML Body & Container
        sb.append("<div class='container'>")
          .append("<div class='header'>")
          .append("<h1><span>📺</span> TiviLite 灵动遥控中心</h1>")
          .append("<div class='tv-status-badge'><div class='tv-dot'></div><span id='tvStatusText'>大屏已直连 · 实时双向互联</span></div>")
          .append("</div>")
          .append("<div id='alertBox' class='notice'></div>")

          // Top Tab Switcher
          .append("<div class='nav-tabs'>")
          .append("<div class='nav-tab active' onclick='showTab(\"remote\")'>📱 遥控</div>")
          .append("<div class='nav-tab' onclick='showTab(\"ai\")'>🤖 看点</div>")
          .append("<div class='nav-tab' onclick='showTab(\"reminders\")'>📅 预约</div>")
          .append("<div class='nav-tab' onclick='showTab(\"status\")'>📺 播放</div>")
          .append("<div class='nav-tab' onclick='showTab(\"channels\")'>📑 频道</div>")
          .append("<div class='nav-tab' onclick='showTab(\"recordings\")'>🔴 录制</div>")
          .append("<div class='nav-tab' onclick='showTab(\"subs\")'>📁 订阅</div>")
          .append("<div class='nav-tab' onclick='showTab(\"webdav\")'>☁️ 云盘</div>")
          .append("<div class='nav-tab' onclick='showTab(\"settings\")'>⚙️ 设置</div>")
          .append("</div>");

        // Panel 1: Virtual Remote Control
        sb.append("<div id='tab-remote' class='tab-panel active'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>🎮 触控遥控器 (大屏直控)</span><span class='badge' id='nowMiniName'>正在播放...</span></div>")
          
          // D-Pad
          .append("<div class='dpad-wrap'>")
          .append("<div class='dpad'>")
          .append("<button class='dpad-btn dpad-up' onclick='sendKey(\"up\")'>▲</button>")
          .append("<button class='dpad-btn dpad-down' onclick='sendKey(\"down\")'>▼</button>")
          .append("<button class='dpad-btn dpad-left' onclick='sendKey(\"left\")'>◀</button>")
          .append("<button class='dpad-btn dpad-right' onclick='sendKey(\"right\")'>▶</button>")
          .append("<button class='dpad-btn dpad-center' onclick='sendKey(\"ok\")'>OK</button>")
          .append("</div></div>")

          // Function Keys
          .append("<div class='keys-grid'>")
          .append("<button class='key-btn key-accent' onclick='sendKey(\"back\")'><span>🔙</span><span>返回</span></button>")
          .append("<button class='key-btn' onclick='sendKey(\"menu\")'><span>⚙️</span><span>菜单</span></button>")
          .append("<button class='key-btn' onclick='sendKey(\"favorite\")'><span>⭐</span><span>收藏</span></button>")
          .append("<button class='key-btn' onclick='sendKey(\"switch_source\")'><span>🔄</span><span>换线</span></button>")
          .append("</div>")

          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' onclick='sendKey(\"exit_catchup\")'><span>📺</span><span>退出回看</span></button>")
          .append("<button class='key-btn key-vol' onclick='sendKey(\"volume_down\")'><span>🔉</span><span>音量 -</span></button>")
          .append("<button class='key-btn key-vol' onclick='sendKey(\"volume_mute\")'><span>🔇</span><span>静音</span></button>")
          .append("<button class='key-btn key-vol' onclick='sendKey(\"volume_up\")'><span>🔊</span><span>音量 +</span></button>")
          .append("</div>")

          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' style='background:#B91C1C;color:#FFF;' onclick='postData({toggle_recording:true})'><span>🔴</span><span id='rmRecBtn'>录制</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_subtitle:true})'><span>💬</span><span>字幕</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_buffer:true})'><span>🛡️</span><span>防抖</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_aspect:true})'><span>📐</span><span>比例</span></button>")
          .append("</div>")

          // Phase 6: PiP Controls
          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' onclick='postData({toggle_pip:true})'><span>🖼️</span><span>画中画</span></button>")
          .append("<button class='key-btn' onclick='postData({swap_pip:true})'><span>🔄</span><span>主副对调</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_pip_audio:true})'><span>🔊</span><span>副音切换</span></button>")
          .append("<button class='key-btn' style='background:rgba(0,229,255,0.15);color:var(--accent);' onclick='showTab(\"ai\")'><span>🤖</span><span>AI看点</span></button>")
          .append("</div>")

          // Phase 7: Audio Passthrough & Picture Quality
          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' onclick='postData({toggle_audio_mode:true})'><span>🔊</span><span id='rmAudioModeBtn'>源码直通</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_picture_mode:true})'><span>🎛️</span><span id='rmPictureModeBtn'>画质引擎</span></button>")
          .append("<button class='key-btn' style='background:rgba(0,229,255,0.15);color:var(--accent);' onclick='showTab(\"reminders\")'><span>📅</span><span id='rmRemindersBadge'>0 个预约</span></button>")
          .append("<button class='key-btn' onclick='postData({best_source:true})'><span>⚡</span><span>智能测速</span></button>")
          .append("</div>")

          // Phase 8: QoS Radar, Failover & DLNA Cast
          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' onclick='postData({toggle_qos_radar:true})'><span>📊</span><span id='rmQosBtn'>极客雷达</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_auto_failover:true})'><span>🛡️</span><span id='rmFailoverBtn'>智能容灾</span></button>")
          .append("<button class='key-btn' style='background:rgba(239,68,68,0.15);color:#EF4444;' onclick='postData({stop_cast:true})'><span>📲</span><span>退出投屏</span></button>")
          .append("<button class='key-btn' style='background:rgba(61,123,253,0.15);color:var(--blue);' onclick='showTab(\"settings\")'><span>🔌</span><span>插件中心</span></button>")
          .append("</div>")

          // Phase 9: Theme, Font Scale, NAS & Multi-WAN
          .append("<div class='keys-grid'>")
          .append("<button class='key-btn' onclick='postData({toggle_theme:true})'><span>🎨</span><span id='rmThemeBtn'>大屏主题</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_font_scale:true})'><span>🔤</span><span id='rmFontBtn'>字号缩放</span></button>")
          .append("<button class='key-btn' onclick='postData({scan_nas:true})'><span>📁</span><span>扫描NAS</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_bonding:true})'><span>🌐</span><span id='rmBondingBtn'>双网聚合</span></button>")
          .append("</div>")

          // Phase 10: AI Subtitles, Low-Latency Sync & Cloud Mesh
          .append("<div class='keys-grid' style='margin-top:6px;'>")
          .append("<button class='key-btn' onclick='postData({toggle_ai_subtitle:true})'><span>🤖</span><span id='rmSubBtn'>AI字幕</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_webrtc_sync:true})'><span>⚡</span><span id='rmSyncBtn'>极速同步</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/mesh\").then(r=>r.json()).then(d=>alert(\"☁️ 自组网设备列表 (\"+d.peerCount+\" 台):\\n\"+JSON.stringify(d.peers,null,2)))'><span>☁️</span><span>Mesh网格</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/sync\").then(r=>r.json()).then(d=>alert(\"⚡ 同屏同步模式: \"+d.syncModeName))'><span>📡</span><span>时钟微调</span></button>")
          .append("</div>")

          // Phase 11: Blu-ray/Dolby, P2P Edge & AI Assistant
          .append("<div class='keys-grid' style='margin-top:6px;'>")
          .append("<button class='key-btn' onclick='postData({toggle_dolby_mode:true})'><span>🎬</span><span id='rmDvBtn'>杜比原盘</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_p2p_mode:true})'><span>⚡</span><span id='rmP2pBtn'>P2P加速</span></button>")
          .append("<button class='key-btn' onclick='var q=prompt(\"请输入您想咨询或搜索的内容（如：90年代周星驰经典喜剧）：\");if(q)postData({ai_prompt:q})'><span>🎙️</span><span>AI助理</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/p2p\").then(r=>r.json()).then(d=>alert(\"⚡ P2P边缘加速看板:\\n状态: \"+d.modeName+\"\\n节省宽带: \"+d.savedBandwidthMb+\"\\nP2P率: \"+d.p2pRatioPercent))'><span>📊</span><span>省流看板</span></button>")
          .append("</div>")

          // Stage 1: Mobile & Tablet Multi-Terminal Responsive Controls
          .append("<div class='keys-grid' style='margin-top:6px;'>")
          .append("<button class='key-btn' onclick='postData({toggle_orientation:true})'><span>🔄</span><span>屏幕旋转</span></button>")
          .append("<button class='key-btn' onclick='postData({enter_pip:true})'><span>🪟</span><span>系统画中画</span></button>")
          .append("<button class='key-btn' onclick='postData({toggle_cellular_guard:true})'><span>📶</span><span>流量防护</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/device\").then(r=>r.json()).then(d=>alert(\"📱 设备与多端信息:\\n型号: \"+d.deviceModel+\"\\n形态: \"+d.formFactorName+\"\\n当前网络: \"+d.networkBadge+\"\\n触摸屏: \"+(d.hasTouch?\"支持\":\"不支持\")))'><span>ℹ️</span><span>设备信息</span></button>")
          .append("</div>")

          // Stage 12: LAN Subscription Mirroring, Universal Playback Handoff & Cloud NAS DVR
          .append("<div class='keys-grid' style='margin-top:6px;'>")
          .append("<button class='key-btn' onclick='postData({mirror_clone:true})'><span>👯</span><span>局域网克隆</span></button>")
          .append("<button class='key-btn' onclick='postData({handoff_pull:true})'><span>📲</span><span>近场接力</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/dvr\").then(r=>r.json()).then(d=>alert(\"📼 NAS/云端录制队列 (\"+d.taskCount+\" 个):\\n录制中: \"+(d.isRecordingNow?d.activeProgram:\"无\")))'><span>📼</span><span>云录制状态</span></button>")
          .append("<button class='key-btn' onclick='fetch(\"/api/handoff\").then(r=>r.json()).then(d=>alert(\"📲 近场发现设备 (\"+d.sessionCount+\" 个):\\n\"+JSON.stringify(d.nearbySessions,null,2)))'><span>🔄</span><span>接力设备</span></button>")
          .append("</div>")

          // Voice Command Input
          .append("<div style='margin-top:14px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px;'>")
          .append("<div style='display:flex;justify-content:space-between;align-items:center;'>")
          .append("<label style='margin:0;'>🎙️ 语音/自然语言智能指令：</label>")
          .append("<button id='btnVoiceRecord' onclick='startVoiceInput()' style='background:none;border:none;color:var(--accent);font-size:12px;font-weight:bold;cursor:pointer;'>🎤 麦克风说话</button>")
          .append("</div>")
          .append("<div style='display:flex;gap:8px;margin-top:6px;'>")
          .append("<input type='text' id='voiceCmdInput' placeholder='如：换到CCTV5 / 调大音量 / 开启画中画' />")
          .append("<button class='btn-primary' style='width:90px;margin-top:0;background:#00E5FF;color:#000;' onclick='sendVoiceCmd()'>🎙️ 发送</button>")
          .append("</div>")
          .append("<div style='display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;'>")
          .append("<span class='voice-chip' onclick='quickVoice(\"换到CCTV1\")'>CCTV-1</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"换到CCTV5\")'>CCTV-5</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"换到湖南卫视\")'>湖南卫视</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"开启画中画\")'>开启画中画</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"主副对调\")'>主副对调</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"调大音量\")'>调大音量</span>")
          .append("<span class='voice-chip' onclick='quickVoice(\"开启录像\")'>开启录像</span>")
          .append("</div></div>")

          // Quick Zap Input
          .append("<div style='margin-top:14px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px;'>")
          .append("<label>快捷点播换台 (输入频道名或台号)：</label>")
          .append("<div style='display:flex;gap:8px;'>")
          .append("<input type='text' id='quickZapInput' placeholder='例如：CCTV1 / 湖南卫视 / 5' />")
          .append("<button class='btn-primary' style='width:90px;margin-top:0;' onclick='quickZap()'>🚀 换台</button>")
          .append("</div></div>")

          // Number Keypad
          .append("<div style='margin-top:12px;'>")
          .append("<div class='numpad'>")
          .append("<button class='num-btn' onclick='sendNum(1)'>1</button>")
          .append("<button class='num-btn' onclick='sendNum(2)'>2</button>")
          .append("<button class='num-btn' onclick='sendNum(3)'>3</button>")
          .append("<button class='num-btn' onclick='sendNum(4)'>4</button>")
          .append("<button class='num-btn' onclick='sendNum(5)'>5</button>")
          .append("<button class='num-btn' onclick='sendNum(6)'>6</button>")
          .append("<button class='num-btn' onclick='sendNum(7)'>7</button>")
          .append("<button class='num-btn' onclick='sendNum(8)'>8</button>")
          .append("<button class='num-btn' onclick='sendNum(9)'>9</button>")
          .append("<button class='num-btn' onclick='sendNum(0)'>0</button>")
          .append("<button class='num-btn' style='grid-column: span 2;background:#202534;' onclick='sendKey(\"ok\")'>确定/选台</button>")
          .append("</div></div>")

          .append("</div></div>");

        // Panel AI: Hot Recommendations
        sb.append("<div id='tab-ai' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>🤖 AI 实时热播看点榜</span><button class='sub-btn btn-del' onclick='fetchAiRecommendations()'>刷新</button></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:10px;'>实时提炼全国主要电视台黄金档看点摘要与热度指数，点击手机即刻转台：</p>")
          .append("<div id='aiListContainer'><p style='color:var(--muted);font-size:12px;'>正在加载看点推荐...</p></div>")
          .append("</div></div>");

        // Panel Reminders: Upcoming EPG alerts
        sb.append("<div id='tab-reminders' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>📅 节目预约管家</span><button class='sub-btn btn-del' onclick='fetchReminders()'>刷新</button></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:10px;'>开播前 90 秒大屏高亮弹窗提醒，遥控按 [OK] 立即收看：</p>")
          .append("<div id='remListContainer'><p style='color:var(--muted);font-size:12px;'>正在加载预约列表...</p></div>")
          .append("</div>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>➕ 快速新增预约</span></div>")
          .append("<label>电视台名称：</label>")
          .append("<input type='text' id='remChName' placeholder='如：CCTV-5 体育' />")
          .append("<label>节目看点名称：</label>")
          .append("<input type='text' id='remProgTitle' placeholder='如：2026欧冠半决赛' />")
          .append("<label>预约时间 (今天/明天 HH:mm)：</label>")
          .append("<input type='text' id='remTime' placeholder='例如：19:35 或 20:00' />")
          .append("<button class='btn-primary' style='background:#00E5FF;color:#000;' onclick='addCustomReminder()'>⏰ 立即预约该节目</button>")
          .append("</div></div>");

        // Panel 2: Now Playing & Live Status
        sb.append("<div id='tab-status' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>📺 大屏实时播放状态</span><span class='badge' id='stModeBadge'>实时直播</span></div>")
          .append("<div style='background:#0C0E14;border-radius:12px;padding:14px;margin-bottom:12px;'>")
          .append("<div style='display:flex;justify-content:space-between;align-items:center;'>")
          .append("<div><div style='color:var(--accent);font-size:22px;font-weight:800;' id='stChannelName'>正在获取...</div>")
          .append("<div style='color:var(--muted);font-size:12px;margin-top:2px;' id='stChannelGroup'>分组: --</div></div>")
          .append("<button id='stFavBtn' class='star-btn' style='font-size:28px;' onclick='sendKey(\"favorite\")'>⭐</button>")
          .append("</div>")
          .append("<div style='margin-top:12px;border-top:1px solid rgba(255,255,255,0.06);padding-top:10px;'>")
          .append("<div style='font-size:13px;color:#FFF;font-weight:bold;'>正在播出：<span id='stProgramTitle' style='color:var(--accent);'>加载中...</span></div>")
          .append("<div style='font-size:12px;color:var(--muted);margin-top:4px;'>播放线路：<span id='stSourceInfo' style='color:#FFF;font-weight:bold;'>--</span></div>")
          .append("<div style='font-size:12px;color:var(--muted);margin-top:2px;'>解码方式：<span id='stDecoderInfo' style='color:#FFF;'>--</span></div>")
          .append("</div></div>")

          // Actions
          .append("<button class='btn-primary' style='background:#3D7BFD;' onclick='sendKey(\"switch_source\")'>🔄 切换至下一个备用源线路</button>")
          .append("<button class='btn-primary' style='background:#10B981;margin-top:8px;' onclick='postData({toggle_decoder:true})'>🎬 切换硬件加速 / 软件兼容解码</button>")
          .append("<button class='btn-primary' style='background:#202534;margin-top:8px;' onclick='sendKey(\"exit_catchup\")'>📺 退出时移回看，直达最新直播</button>")

          .append("<div style='margin-top:16px;font-size:12px;color:var(--muted);'>")
          .append("<div>本机设备识别码: <strong id='stDeviceId' style='color:#FFF;'>--</strong></div>")
          .append("<div style='margin-top:2px;'>授权状态: <strong id='stProText' style='color:var(--gold);'>--</strong></div>")
          .append("</div></div></div>");

        // Panel 3: Channels List & Zap
        sb.append("<div id='tab-channels' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>📑 频道列表 (点击手机直达大屏)</span><span class='badge' id='chCountBadge'>0 个</span></div>")
          .append("<div class='ch-search'>")
          .append("<input type='text' id='chFilterInput' placeholder='🔍 输入频道名称或台号即搜即点...' oninput='filterChannels()' />")
          .append("</div>")
          .append("<div class='ch-list-box' id='chListBox'>")
          .append("<p style='color:var(--muted);font-size:12px;text-align:center;padding:20px;'>正在同步电视端频道列表...</p>")
          .append("</div>")
          .append("</div></div>");

        // Panel 4: DVR Recordings
        sb.append("<div id='tab-recordings' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>📹 电视实时节目录制 (DVR)</span><button class='sub-btn btn-del' onclick='fetchRecordings()'>刷新</button></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:10px;'>录制的节目保存在电视中，可一键下载到手机或电脑播放：</p>")
          .append("<button class='btn-primary' style='background:#B91C1C;' onclick='postData({toggle_recording:true})'>🔴 开始 / 停止录制当前频道节目</button>")
          .append("</div>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>📁 录制文件列表</span><span class='badge' id='recCountBadge'>0 个文件</span></div>")
          .append("<div id='recListContainer'><p style='color:var(--muted);font-size:12px;'>正在加载录制文件...</p></div>")
          .append("</div></div>");

        // Panel 5: Subscriptions Management
        sb.append("<div id='tab-subs' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>⚡ 官方精选合规源</span><span class='badge'>多线路聚合</span></div>")
          .append("<button class='btn-preset' style='background:rgba(16,185,129,0.15);border-color:rgba(16,185,129,0.4);' onclick='pushVerifiedDemo()'>")
          .append("<span style='color:#10B981;font-weight:bold;'>🚀 一键推送官方直连合规演示源 (CGTN/浙江卫视1080p/HLS全码率)</span><span class='badge' style='background:#10B981;'>推荐·秒开</span></button>")
          .append("<button class='btn-preset' onclick='pushUrl(\"https://live.fanmingming.com/tv/m3u/ipv6.m3u\", \"全国 IPv6 极速直连专线\")'>")
          .append("<span>⚡ 全国 IPv6 极速直连专线</span><span class='badge'>需IPv6网络</span></button>")
          .append("<button class='btn-preset' onclick='pushUrl(\"https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/v6.m3u\", \"CCTV 卫视聚合\")'>")
          .append("<span>🔥 CCTV & 各省卫视高清</span><span class='badge'>需IPv6网络</span></button>")
          .append("</div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>📡 自定义订阅与多源导入</span></div>")
          .append("<label>导入策略 (多源聚合)：</label>")
          .append("<select id='importMode'>")
          .append("<option value='append'>➕ 追加合并到现有订阅 (自动同名频道多线路聚合)</option>")
          .append("<option value='overwrite'>🔄 覆盖清空旧源 (仅保留本次新导入源)</option>")
          .append("</select>")
          .append("<label>订阅名称 (选填)：</label>")
          .append("<input type='text' id='subName' placeholder='例如：家庭专属宽带源 / 地方特色频道' />")
          .append("<label>订阅地址 (URL)：</label>")
          .append("<input type='text' id='inputUrl' placeholder='https://.../live.m3u 或 .txt' />")
          .append("<button class='btn-primary' onclick='submitUrl()'>🚀 推送订阅地址到电视</button>")

          .append("<div style='margin-top:14px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px;'>")
          .append("<label>上传本地 M3U / TXT 文件：</label>")
          .append("<div class='btn-file' onclick='document.getElementById(\"filePicker\").click()'>📂 点击选择本地 .m3u / .txt 文件</div>")
          .append("<input type='file' id='filePicker' accept='.m3u,.m3u8,.txt' style='display:none;' onchange='handleFileSelect(this)' />")
          .append("</div>")

          .append("<div style='margin-top:14px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px;'>")
          .append("<label>直接粘贴 M3U / TXT 文本：</label>")
          .append("<textarea id='textContent' rows='3' placeholder='#EXTM3U 或 频道名,URL'></textarea>")
          .append("<button class='btn-primary' style='background:#10B981;' onclick='submitContent()'>📝 推送文本内容到电视</button>")
          .append("</div></div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>📁 当前已加载的订阅源列表</span><button class='sub-btn btn-del' onclick='fetchSubs()'>刷新</button></div>")
          .append("<div id='subListContainer'><p style='color:var(--muted);font-size:12px;'>正在加载订阅列表...</p></div>")
          .append("</div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>📺 EPG 节目单网络接口</span></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:8px;'>支持标准 DIYP 接口，配置后自动展示大屏与手机端真实节目单：</p>")
          .append("<input type='text' id='epgInput' placeholder='http://epg.51zmt.top:8000/api/diyp/' value='http://epg.51zmt.top:8000/api/diyp/' />")
          .append("<button class='btn-primary' style='background:#3D7BFD;' onclick='submitEpg()'>🌐 保存并更新 EPG 节目单</button>")
          .append("</div></div>");

        // Panel 6: WebDAV Cloud Sync
        sb.append("<div id='tab-webdav' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>☁️ WebDAV 私有云盘同步</span><span class='badge'>多端漫游</span></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:8px;'>支持 Alist、群晖 NAS、坚果云、Nextcloud 等标准 WebDAV 私有服务：</p>")
          .append("<label>WebDAV 服务器地址：</label>")
          .append("<input type='text' id='davUrl' placeholder='http://192.168.1.100:5005/dav/ 或 https://...' />")
          .append("<label>用户名：</label>")
          .append("<input type='text' id='davUser' placeholder='admin' />")
          .append("<label>密码 / 访问令牌：</label>")
          .append("<input type='password' id='davPass' placeholder='••••••••' />")
          .append("<div style='margin-top:10px;'><label><input type='checkbox' id='davAuto' style='width:auto;margin-right:6px;' /> 开机自动从云端同步最新配置</label></div>")
          .append("<div style='color:var(--muted);font-size:12px;margin-top:8px;'>最近同步时间：<strong id='davLastSync' style='color:#FFF;'>--</strong></div>")
          .append("<div style='display:flex;gap:8px;margin-top:14px;'>")
          .append("<button class='btn-primary' style='background:#3D7BFD;flex:1;' onclick='submitDav(\"test\")'>⚡ 测试连接</button>")
          .append("<button class='btn-primary' style='background:#10B981;flex:1;' onclick='submitDav(\"upload\")'>☁️ 备份到云盘</button>")
          .append("<button class='btn-primary' style='background:#F59E0B;flex:1;' onclick='submitDav(\"download\")'>📥 从云端恢复</button>")
          .append("</div>")
          .append("</div></div>");

        // Panel 7: Settings & OTA
        sb.append("<div id='tab-settings' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>👑 Pro 会员一键激活</span></div>")
          .append("<label>设备识别码: <strong id='setDevId' style='color:#FFF;'>TV...</strong></label>")
          .append("<input type='text' id='licenseInput' placeholder='PRO-XXXX-XXXX-XXXX' style='text-transform:uppercase;' />")
          .append("<button class='btn-primary' style='background:#10B981;' onclick='submitLicense()'>✨ 立即激活电视端 Pro</button>")
          .append("</div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>🔌 极客插件扩展中心 (动态解密/穿透)</span><span class='badge'>v1.6.0</span></div>")
          .append("<p style='color:var(--muted);font-size:12px;margin-bottom:10px;'>已加载动态源解析脚本、B站/斗鱼防盗链 Referer 穿透与 CDN 加速规则：</p>")
          .append("<div id='pluginListContainer'>加载插件列表中...</div>")
          .append("</div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>⚙️ 偏好设置与系统更新</span></div>")
          .append("<button class='btn-primary' style='background:#202534;' onclick='postData({toggle_auto_boot:true})'>🚀 切换开机自动进入直播</button>")
          .append("<button class='btn-primary' style='background:#202534;margin-top:8px;' onclick='postData({check_update:true})'>🔍 电视端立即检查新版本 OTA</button>")
          .append("<p style='color:var(--muted);font-size:12px;margin-top:12px;'>TiviLite v1.6.0 · 纯净合规 · 无线投屏 · 极客雷达 · 插件扩展</p>")
          .append("</div></div>");

        // 3. Client JavaScript
        sb.append("</div><script>")
          .append("var allChannelsList = [];")
          .append("function showNotice(msg, isSuccess) {")
          .append("  var box = document.getElementById('alertBox');")
          .append("  box.style.display = 'block';")
          .append("  box.style.color = isSuccess ? '#10B981' : '#EF4444';")
          .append("  box.style.borderColor = isSuccess ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)';")
          .append("  box.innerText = msg;")
          .append("  window.scrollTo({top: 0, behavior: 'smooth'});")
          .append("}")
          .append("function showTab(name) {")
          .append("  var tabs = document.querySelectorAll('.nav-tab');")
          .append("  var panels = document.querySelectorAll('.tab-panel');")
          .append("  var names = ['remote', 'ai', 'reminders', 'status', 'channels', 'recordings', 'subs', 'webdav', 'settings'];")
          .append("  tabs.forEach(function(t, i) {")
          .append("    t.className = (names[i] === name) ? 'nav-tab active' : 'nav-tab';")
          .append("  });")
          .append("  panels.forEach(function(p) {")
          .append("    p.className = (p.id === 'tab-' + name) ? 'tab-panel active' : 'tab-panel';")
          .append("  });")
          .append("  if (name === 'ai') fetchAiRecommendations();")
          .append("  if (name === 'reminders') fetchReminders();")
          .append("  if (name === 'channels' && allChannelsList.length === 0) fetchChannels();")
          .append("  if (name === 'status') fetchStatus();")
          .append("  if (name === 'recordings') fetchRecordings();")
          .append("  if (name === 'webdav') fetchDavStatus();")
          .append("  if (name === 'settings') fetchPlugins();")
          .append("}")
          .append("function sendKey(key) {")
          .append("  if (navigator.vibrate) navigator.vibrate(20);")
          .append("  postData({remote_key: key});")
          .append("}")
          .append("function sendNum(n) {")
          .append("  if (navigator.vibrate) navigator.vibrate(20);")
          .append("  postData({remote_key: 'number', value: '' + n});")
          .append("}")
          .append("function quickZap() {")
          .append("  var q = document.getElementById('quickZapInput').value.trim();")
          .append("  if (!q) return;")
          .append("  postData({zap_name: q});")
          .append("  showNotice('已向电视发送换台指令: ' + q, true);")
          .append("  document.getElementById('quickZapInput').value = '';")
          .append("}")
          .append("function postData(payload) {")
          .append("  fetch('/api/push', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)})")
          .append("  .then(function(r){ return r.json(); })")
          .append("  .then(function(res){")
          .append("    if(res.message) showNotice('🎉 ' + res.message, true);")
          .append("    setTimeout(fetchStatus, 300);")
          .append("  })")
          .append("  .catch(function(){ showNotice('❌ 请求失败，请检查电视网络', false); });")
          .append("}")
          .append("function pushUrl(url, name) {")
          .append("  showNotice('正在推送到大屏: ' + name + '...', true);")
          .append("  postData({m3u_url: url, name: name, mode: 'append'});")
          .append("}")
          .append("function pushVerifiedDemo() {")
          .append("  var m3u = '#EXTM3U name=\"官方合规开源基准源\" x-tvg-url=\"http://epg.51zmt.top:8000/api/diyp/\"\\n'")
          .append("    + '#EXTINF:-1 tvg-name=\"BigBuckBunny\" group-title=\"开源基准 (CC-BY 3.0)\",Big Buck Bunny (开源电影基准 CC-BY 3.0)\\n'")
          .append("    + 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\\n'")
          .append("    + '#EXTINF:-1 tvg-name=\"AppleBipBop\" group-title=\"Apple 开发者测试\",Apple 官方音画同步基准 (BipBop 16:9)\\n'")
          .append("    + 'https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8\\n'")
          .append("    + '#EXTINF:-1 tvg-name=\"AppleHEVC\" group-title=\"Apple 开发者测试\",Apple HEVC/H.265 硬件解码基准\\n'")
          .append("    + 'https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8\\n'")
          .append("    + '#EXTINF:-1 tvg-name=\"Sintel\" group-title=\"开源基准 (CC-BY 3.0)\",Sintel 辛特尔 (开源电影 4K 基准)\\n'")
          .append("    + 'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8\\n'")
          .append("    + '#EXTINF:-1 tvg-id=\"cgtn\" tvg-name=\"CGTN\" tvg-logo=\"https://live.fanmingming.com/tv/CGTN.png\" group-title=\"官方开放直播\",CGTN 官方全球英语新闻 (公网开放流)\\n'")
          .append("    + 'https://english-livetx.cgtn.com/hls/yypdyyctzb_hd.m3u8\\n';")
          .append("  showNotice('正在推送官方开源合规基准源...', true);")
          .append("  postData({m3u_content: m3u, name: '官方合规开源基准源 (直连秒开)', mode: 'overwrite'});")
          .append("}")
          .append("function submitUrl() {")
          .append("  var u = document.getElementById('inputUrl').value.trim();")
          .append("  var n = document.getElementById('subName').value.trim() || '自定义订阅';")
          .append("  var m = document.getElementById('importMode').value;")
          .append("  if(!u) { alert('请输入有效订阅 URL'); return; }")
          .append("  showNotice('正在发送订阅到电视...', true);")
          .append("  postData({m3u_url: u, name: n, mode: m});")
          .append("}")
          .append("function submitContent() {")
          .append("  var c = document.getElementById('textContent').value.trim();")
          .append("  var n = document.getElementById('subName').value.trim() || '手动文本导入';")
          .append("  var m = document.getElementById('importMode').value;")
          .append("  if(!c) { alert('请粘贴文本内容'); return; }")
          .append("  showNotice('正在推送文本到电视...', true);")
          .append("  postData({m3u_content: c, name: n, mode: m});")
          .append("}")
          .append("function submitEpg() {")
          .append("  var e = document.getElementById('epgInput').value.trim();")
          .append("  if(!e) { alert('请输入 EPG 接口 URL'); return; }")
          .append("  showNotice('正在更新电视 EPG 接口...', true);")
          .append("  postData({epg_url: e});")
          .append("}")
          .append("function submitLicense() {")
          .append("  var l = document.getElementById('licenseInput').value.trim();")
          .append("  if(!l) { alert('请输入激活码'); return; }")
          .append("  showNotice('正在向电视发送卡密验证...', true);")
          .append("  postData({license_code: l});")
          .append("}")
          .append("function handleFileSelect(input) {")
          .append("  if(!input.files || input.files.length === 0) return;")
          .append("  var file = input.files[0];")
          .append("  var m = document.getElementById('importMode').value;")
          .append("  showNotice('正在读取并上传文件: ' + file.name + '...', true);")
          .append("  var reader = new FileReader();")
          .append("  reader.onload = function(e) { postData({m3u_content: e.target.result, name: file.name, mode: m}); };")
          .append("  reader.readAsText(file);")
          .append("}")
          .append("function fetchSubs() {")
          .append("  fetch('/api/subscriptions').then(function(r){return r.json();}).then(function(subs){")
          .append("    var box = document.getElementById('subListContainer');")
          .append("    if(!subs || subs.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;\">暂无保存的订阅源，请从上方推送。</p>'; return; }")
          .append("    var html = '';")
          .append("    subs.forEach(function(s){")
          .append("      html += '<div class=\"sub-item\"><div><strong>' + (s.name || '未命名订阅') + '</strong>'")
          .append("        + '<div style=\"color:var(--muted);font-size:11px;\">包含 ' + (s.channel_count || 0) + ' 个频道 · ' + (s.enabled ? '<span style=\"color:#10B981;\">已启用</span>' : '<span style=\"color:#EF4444;\">已禁用</span>') + '</div></div>'")
          .append("        + '<div><button class=\"sub-btn btn-toggle\" onclick=\"toggleSub(\\'' + s.id + '\\',' + (!s.enabled) + ')\">' + (s.enabled ? '停用' : '启用') + '</button>'")
          .append("        + '<button class=\"sub-btn btn-del\" onclick=\"delSub(\\'' + s.id + '\\')\">删除</button></div></div>';")
          .append("    });")
          .append("    box.innerHTML = html;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function toggleSub(id, enabled) { postData({sub_toggle_id: id, enabled: enabled}); }")
          .append("function delSub(id) { if(confirm('确定从电视中移除此订阅？')) postData({sub_delete_id: id}); }")
          .append("function fetchStatus() {")
          .append("  fetch('/api/status').then(function(r){return r.json();}).then(function(s){")
          .append("    if(s.name) {")
          .append("      document.getElementById('nowMiniName').innerText = s.name;")
          .append("      document.getElementById('stChannelName').innerText = (s.is_fav ? '⭐ ' : '') + s.name;")
          .append("      document.getElementById('stChannelGroup').innerText = '分组: ' + (s.group || '默认');")
          .append("      document.getElementById('stFavBtn').className = s.is_fav ? 'star-btn fav' : 'star-btn';")
          .append("      document.getElementById('stProgramTitle').innerText = s.current_program || '高清直播';")
          .append("      document.getElementById('stSourceInfo').innerText = s.source_info || '主线路';")
          .append("      document.getElementById('stDecoderInfo').innerText = s.decoder_mode || '硬件加速';")
          .append("      document.getElementById('stModeBadge').innerText = s.is_catchup_mode ? '7天时移回看' : '实时直播';")
          .append("    }")
          .append("    if(s.audio_mode_text && document.getElementById('rmAudioModeBtn')) document.getElementById('rmAudioModeBtn').innerText = s.audio_mode_text;")
          .append("    if(s.picture_mode_text && document.getElementById('rmPictureModeBtn')) document.getElementById('rmPictureModeBtn').innerText = s.picture_mode_text;")
          .append("    if(document.getElementById('rmRemindersBadge')) document.getElementById('rmRemindersBadge').innerText = (s.reminders_count || 0) + ' 个预约';")
          .append("    if(s.device_id) {")
          .append("      document.getElementById('stDeviceId').innerText = s.device_id;")
          .append("      document.getElementById('setDevId').innerText = s.device_id;")
          .append("    }")
          .append("    if(s.pro_status_text) document.getElementById('stProText').innerText = s.pro_status_text;")
          .append("    var rBtn = document.getElementById('rmRecBtn');")
          .append("    if(rBtn) {")
          .append("      rBtn.innerText = s.is_recording ? '录制中 ●' : '录制';")
          .append("      rBtn.parentElement.style.background = s.is_recording ? '#DC2626' : '#B91C1C';")
          .append("    }")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function fetchRecordings() {")
          .append("  fetch('/api/recordings').then(function(r){return r.json();}).then(function(list){")
          .append("    var box = document.getElementById('recListContainer');")
          .append("    document.getElementById('recCountBadge').innerText = (list ? list.length : 0) + ' 个文件';")
          .append("    if(!list || list.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;text-align:center;padding:16px;\">暂无录制文件</p>'; return; }")
          .append("    var html = '';")
          .append("    list.forEach(function(item){")
          .append("      html += '<div class=\"sub-item\"><div><strong>' + item.name + '</strong>'")
          .append("        + '<div style=\"color:var(--muted);font-size:11px;\">大小: ' + item.size_formatted + ' · 时间: ' + item.date_formatted + '</div></div>'")
          .append("        + '<div><a href=\"/api/recordings/download?name=' + encodeURIComponent(item.name) + '\" download class=\"sub-btn btn-toggle\" style=\"text-decoration:none;display:inline-block;\">⬇️ 下载</a>'")
          .append("        + '<button class=\"sub-btn btn-del\" onclick=\"delRec(\\'' + item.name + '\\')\">删除</button></div></div>';")
          .append("    });")
          .append("    box.innerHTML = html;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function delRec(name) {")
          .append("  if(confirm('确定删除录制文件: ' + name + '？')) {")
          .append("    postData({recording_delete_name: name});")
          .append("    setTimeout(fetchRecordings, 400);")
          .append("  }")
          .append("}")
          .append("function fetchDavStatus() {")
          .append("  fetch('/api/sync/webdav/status').then(function(r){return r.json();}).then(function(d){")
          .append("    if(d.url) document.getElementById('davUrl').value = d.url;")
          .append("    if(d.user) document.getElementById('davUser').value = d.user;")
          .append("    if(d.last_sync) document.getElementById('davLastSync').innerText = d.last_sync;")
          .append("    document.getElementById('davAuto').checked = !!d.auto_sync;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function submitDav(act) {")
          .append("  var u = document.getElementById('davUrl').value.trim();")
          .append("  var usr = document.getElementById('davUser').value.trim();")
          .append("  var p = document.getElementById('davPass').value.trim();")
          .append("  var a = document.getElementById('davAuto').checked;")
          .append("  showNotice('正在执行 WebDAV ' + act + ' 操作...', true);")
          .append("  postData({webdav_action: act, url: u, user: usr, pass: p, auto_sync: a});")
          .append("  setTimeout(fetchDavStatus, 1200);")
          .append("}")
          .append("function fetchChannels() {")
          .append("  fetch('/api/channels').then(function(r){return r.json();}).then(function(data){")
          .append("    allChannelsList = data || [];")
          .append("    document.getElementById('chCountBadge').innerText = allChannelsList.length + ' 个';")
          .append("    renderChannels(allChannelsList);")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function renderChannels(list) {")
          .append("  var box = document.getElementById('chListBox');")
          .append("  if(!list || list.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;text-align:center;padding:20px;\">无匹配频道</p>'; return; }")
          .append("  var html = '';")
          .append("  list.slice(0, 100).forEach(function(c){")
          .append("    html += '<div class=\"ch-card\" onclick=\"zapToIndex(' + c.index + ')\">'")
          .append("      + '<div class=\"ch-left\"><span class=\"ch-num\">' + (c.number < 10 ? '0' + c.number : c.number) + '</span>'")
          .append("      + '<div><div class=\"ch-title\">' + (c.is_fav ? '⭐ ' : '') + c.name + '</div>'")
          .append("      + '<div class=\"ch-group\">' + c.group + ' · ' + (c.sources || 1) + ' 条线路</div></div></div>'")
          .append("      + '<div><button class=\"sub-btn btn-toggle\" onclick=\"event.stopPropagation();pipPlayIndex(' + c.index + ',\\'' + c.name.replace(\"'\", \"\") + '\\')\">小窗</button>'")
          .append("      + '<button class=\"star-btn ' + (c.is_fav ? 'fav' : '') + '\" onclick=\"event.stopPropagation();toggleFav(\\'' + c.name.replace(\"'\", \"\") + '\\')\">⭐</button></div>'")
          .append("      + '</div>';")
          .append("  });")
          .append("  box.innerHTML = html;")
          .append("}")
          .append("function filterChannels() {")
          .append("  var kw = document.getElementById('chFilterInput').value.trim().toLowerCase();")
          .append("  if(!kw) { renderChannels(allChannelsList); return; }")
          .append("  var filtered = allChannelsList.filter(function(c){ return (c.name && c.name.toLowerCase().indexOf(kw) >= 0) || ('' + c.number).indexOf(kw) >= 0 || (c.group && c.group.toLowerCase().indexOf(kw) >= 0); });")
          .append("  renderChannels(filtered);")
          .append("}")
          .append("function zapToIndex(idx) {")
          .append("  if (navigator.vibrate) navigator.vibrate(20);")
          .append("  postData({zap_index: idx});")
          .append("  showNotice('已点播换台！', true);")
          .append("}")
          .append("function pipPlayIndex(idx, name) {")
          .append("  postData({pip_index: idx, pip_channel_name: name});")
          .append("  showNotice('已在画中画副画面开启: ' + name, true);")
          .append("}")
          .append("function toggleFav(name) {")
          .append("  postData({fav_channel: name});")
          .append("  setTimeout(function(){ fetchChannels(); fetchStatus(); }, 400);")
          .append("}")
          .append("function sendVoiceCmd(val) {")
          .append("  var cmd = val || document.getElementById('voiceCmdInput').value.trim();")
          .append("  if (!cmd) return;")
          .append("  postData({voice_command: cmd});")
          .append("  showNotice('已发送指令: ' + cmd, true);")
          .append("  if (document.getElementById('voiceCmdInput')) document.getElementById('voiceCmdInput').value = '';")
          .append("}")
          .append("function quickVoice(cmd) { sendVoiceCmd(cmd); }")
          .append("function startVoiceInput() {")
          .append("  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {")
          .append("    alert('当前手机浏览器不支持语音识别API，请直接在输入框中输入文字指令发送');")
          .append("    return;")
          .append("  }")
          .append("  var Speech = window.SpeechRecognition || window.webkitSpeechRecognition;")
          .append("  var rec = new Speech();")
          .append("  rec.lang = 'zh-CN';")
          .append("  rec.interimResults = false;")
          .append("  showNotice('🎙️ 正在聆听指令，请对着手机说话...', true);")
          .append("  rec.onresult = function(e) {")
          .append("    if(e.results && e.results[0]) {")
          .append("      var t = e.results[0][0].transcript;")
          .append("      document.getElementById('voiceCmdInput').value = t;")
          .append("      sendVoiceCmd(t);")
          .append("    }")
          .append("  };")
          .append("  rec.onerror = function() { showNotice('语音输入未完成，请重试', false); };")
          .append("  rec.start();")
          .append("}")
          .append("function fetchAiRecommendations() {")
          .append("  fetch('/api/recommendations').then(function(r){return r.json();}).then(function(list){")
          .append("    var box = document.getElementById('aiListContainer');")
          .append("    if (!list || list.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;\">暂无推荐看点</p>'; return; }")
          .append("    var html = '';")
          .append("    list.forEach(function(item){")
          .append("      html += '<div class=\"ai-card\"><div style=\"display:flex;justify-content:space-between;align-items:center;\">'")
          .append("        + '<div><span style=\"background:' + item.color + ';color:#FFF;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:bold;\">' + item.tag + '</span>'")
          .append("        + '<strong style=\"margin-left:6px;color:var(--accent);font-size:13px;\">' + item.channel + '</strong></div>'")
          .append("        + '<span style=\"color:#FF4D4F;font-weight:bold;font-size:12px;\">' + item.score + '</span></div>'")
          .append("        + '<div style=\"font-size:13px;font-weight:bold;color:#FFF;margin-top:6px;\">' + item.title + '</div>'")
          .append("        + '<div style=\"font-size:11px;color:var(--muted);margin-top:2px;\">' + item.synopsis + '</div>'")
          .append("        + '<div style=\"display:flex;justify-content:space-between;align-items:center;margin-top:8px;\">'")
          .append("        + '<span style=\"font-size:11px;color:#9CA3AF;\">时段: ' + item.time + '</span>'")
          .append("        + '<button class=\"btn-primary\" style=\"width:auto;padding:4px 12px;margin:0;font-size:11px;\" onclick=\"quickVoice(\\'换到' + item.channel + '\\')\">🚀 立即转台</button>'")
          .append("        + '</div></div>';")
          .append("    });")
          .append("    box.innerHTML = html;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function fetchReminders() {")
          .append("  fetch('/api/reminders').then(function(r){return r.json();}).then(function(list){")
          .append("    var box = document.getElementById('remListContainer');")
          .append("    if(!list || list.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;text-align:center;padding:16px;\">暂无预约节目</p>'; return; }")
          .append("    var html = '';")
          .append("    list.forEach(function(r){")
          .append("      var timeStr = new Date(r.startTime).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});")
          .append("      html += '<div class=\"sub-item\"><div><strong>' + r.channelName + '</strong>'")
          .append("        + '<div style=\"color:var(--accent);font-size:13px;font-weight:bold;\">《' + r.programTitle + '》</div>'")
          .append("        + '<div style=\"color:var(--muted);font-size:11px;\">开播时间: ' + timeStr + (r.reminded ? ' · <span style=\"color:#10B981;\">已提醒</span>' : ' · 待播') + '</div></div>'")
          .append("        + '<button class=\"sub-btn btn-del\" onclick=\"delReminder(\\'' + r.id + '\\')\">取消预约</button></div>';")
          .append("    });")
          .append("    box.innerHTML = html;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function delReminder(id) { postData({delete_reminder_id: id}); setTimeout(fetchReminders, 300); }")
          .append("function addCustomReminder() {")
          .append("  var ch = document.getElementById('remChName').value.trim();")
          .append("  var prog = document.getElementById('remProgTitle').value.trim();")
          .append("  var t = document.getElementById('remTime').value.trim();")
          .append("  if(!ch || !prog || !t) { alert('请填写频道、节目与时间'); return; }")
          .append("  var now = new Date();")
          .append("  var parts = t.split(':');")
          .append("  var target = new Date(now.getFullYear(), now.getMonth(), now.getDate(), parseInt(parts[0]), parseInt(parts[1]));")
          .append("  if (target.getTime() < now.getTime() - 60000) target.setDate(target.getDate() + 1);")
          .append("  postData({add_reminder: true, channel_name: ch, program_title: prog, start_time: target.getTime(), end_time: target.getTime() + 3600000});")
          .append("  showNotice('已成功预约《' + prog + '》，开播前电视将自动弹窗提醒！', true);")
          .append("  setTimeout(fetchReminders, 500);")
          .append("}")
          .append("function copyRelay(url) {")
          .append("  if(!url) return;")
          .append("  if(navigator.clipboard && navigator.clipboard.writeText) {")
          .append("    navigator.clipboard.writeText(url); showNotice('局域网中继流已复制！可在卧室电视/手机播放器播放', true);")
          .append("  } else { prompt('局域网中继直链 (复制到其他设备播放)：', url); }")
          .append("}")
          .append("function fetchPlugins() {")
          .append("  fetch('/api/plugins').then(function(r){return r.json();}).then(function(list){")
          .append("    var box = document.getElementById('pluginListContainer');")
          .append("    if(!list || list.length === 0) { box.innerHTML = '<p style=\"color:var(--muted);font-size:12px;\">暂无加载插件</p>'; return; }")
          .append("    var html = '';")
          .append("    list.forEach(function(p){")
          .append("      html += '<div class=\"sub-item\"><div><div style=\"font-weight:bold;color:#FFF;\">' + p.name + '</div>'")
          .append("        + '<div style=\"color:var(--muted);font-size:11px;\">' + p.description + ' (' + p.author + ')</div></div>'")
          .append("        + '<button class=\"sub-btn ' + (p.enabled ? 'btn-toggle' : 'btn-del') + '\" onclick=\"togglePlugin(\\'' + p.id + '\\',' + (!p.enabled) + ')\">'")
          .append("        + (p.enabled ? '已启用' : '已停用') + '</button></div>';")
          .append("    });")
          .append("    box.innerHTML = html;")
          .append("  }).catch(function(){});")
          .append("}")
          .append("function togglePlugin(id, en) {")
          .append("  postData({plugin_toggle_id: id, enabled: en});")
          .append("  setTimeout(fetchPlugins, 300);")
          .append("}")
          .append("window.onload = function() {")
          .append("  fetchSubs(); fetchStatus();")
          .append("  setInterval(fetchStatus, 3500);")
          .append("};")
          .append("</script></body></html>");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void serveXmlResponse(OutputStream out, String xml) throws Exception {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/xml; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void serveJsonResponse(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }
}
