package com.tivilite.tv.server;

import android.content.Context;
import com.tivilite.tv.data.SubscriptionManager;
import java.io.ByteArrayOutputStream;
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
                    if (firstLine.contains("/api/subscriptions") && context != null) {
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
                    } else {
                        serveHtmlPage(out);
                    }
                } else if (firstLine.startsWith("POST")) {
                    handlePostData(firstLine, body);
                    serveJsonResponse(out, "{\"success\":true,\"message\":\"大屏操作已实时响应！\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handlePostData(String requestLine, String body) {
        if (listener == null || body == null || body.trim().isEmpty()) return;
        try {
            if (body.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(body);

                // Remote Key Event
                if (requestLine.contains("/api/remote/key") || obj.has("remote_key") || obj.has("key")) {
                    String key = obj.optString("remote_key", obj.optString("key", ""));
                    String val = obj.optString("value", "");
                    if (!key.isEmpty()) {
                        listener.onRemoteKeyReceived(key, val);
                        return;
                    }
                }

                // Direct Channel Zap
                if (requestLine.contains("/api/remote/zap") || obj.has("zap_name") || obj.has("zap_number") || obj.has("zap_index")) {
                    int idx = obj.optInt("zap_index", obj.optInt("index", -1));
                    String name = obj.optString("zap_name", obj.optString("name", ""));
                    int num = obj.optInt("zap_number", obj.optInt("number", -1));
                    listener.onRemoteZapReceived(idx, name, num);
                    return;
                }

                // Channel Favorite Toggle
                if (requestLine.contains("/api/channel/favorite") || obj.has("fav_channel")) {
                    String name = obj.optString("fav_channel", obj.optString("name", ""));
                    if (!name.isEmpty()) {
                        listener.onChannelFavoriteToggled(name);
                        return;
                    }
                }

                // Auto Boot & Decoder Mode Toggles
                if (obj.has("toggle_auto_boot")) {
                    listener.onAutoBootToggled();
                    return;
                }
                if (obj.has("toggle_decoder")) {
                    listener.onDecoderToggled();
                    return;
                }

                // Subscription Management
                if (requestLine.contains("/api/subscription/toggle") || obj.has("sub_toggle_id")) {
                    String id = obj.optString("sub_toggle_id", obj.optString("id", ""));
                    boolean enabled = obj.optBoolean("enabled", true);
                    if (!id.isEmpty()) listener.onSubscriptionToggled(id, enabled);
                    return;
                }

                if (requestLine.contains("/api/subscription/delete") || obj.has("sub_delete_id")) {
                    String id = obj.optString("sub_delete_id", obj.optString("id", ""));
                    if (!id.isEmpty()) listener.onSubscriptionDeleted(id);
                    return;
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
                return;
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
          .append("<div class='nav-tab active' onclick='showTab(\"remote\")'>📱 遥控器</div>")
          .append("<div class='nav-tab' onclick='showTab(\"status\")'>📺 正在播放</div>")
          .append("<div class='nav-tab' onclick='showTab(\"channels\")'>📑 频道点播</div>")
          .append("<div class='nav-tab' onclick='showTab(\"subs\")'>📁 订阅与源</div>")
          .append("<div class='nav-tab' onclick='showTab(\"settings\")'>⚙️ 系统设置</div>")
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

        // Panel 4: Subscriptions Management
        sb.append("<div id='tab-subs' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>⚡ 官方精选合规源</span><span class='badge'>多线路聚合</span></div>")
          .append("<button class='btn-preset' onclick='pushUrl(\"https://live.fanmingming.com/tv/m3u/ipv6.m3u\", \"全国 IPv6 极速直连专线\")'>")
          .append("<span>⚡ 全国 IPv6 极速直连专线</span><span class='badge'>推荐</span></button>")
          .append("<button class='btn-preset' onclick='pushUrl(\"https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/v6.m3u\", \"CCTV 卫视聚合\")'>")
          .append("<span>🔥 CCTV & 各省卫视高清</span><span class='badge'>热门</span></button>")
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

        // Panel 5: Settings & OTA
        sb.append("<div id='tab-settings' class='tab-panel'>")
          .append("<div class='card'>")
          .append("<div class='card-title'><span>👑 Pro 会员一键激活</span></div>")
          .append("<label>设备识别码: <strong id='setDevId' style='color:#FFF;'>TV...</strong></label>")
          .append("<input type='text' id='licenseInput' placeholder='PRO-XXXX-XXXX-XXXX' style='text-transform:uppercase;' />")
          .append("<button class='btn-primary' style='background:#10B981;' onclick='submitLicense()'>✨ 立即激活电视端 Pro</button>")
          .append("</div>")

          .append("<div class='card'>")
          .append("<div class='card-title'><span>⚙️ 偏好设置与系统更新</span></div>")
          .append("<button class='btn-primary' style='background:#202534;' onclick='postData({toggle_auto_boot:true})'>🚀 切换开机自动进入直播</button>")
          .append("<button class='btn-primary' style='background:#202534;margin-top:8px;' onclick='postData({check_update:true})'>🔍 电视端立即检查新版本 OTA</button>")
          .append("<p style='color:var(--muted);font-size:12px;margin-top:12px;'>TiviLite v1.1.0 · 纯净合规 · 多源聚合 · 极客互联</p>")
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
          .append("  tabs.forEach(function(t, i) {")
          .append("    var match = t.innerText.indexOf(name === 'remote' ? '遥控' : (name === 'status' ? '正在播放' : (name === 'channels' ? '频道点播' : (name === 'subs' ? '订阅' : '设置')))) >= 0;")
          .append("    t.className = match ? 'nav-tab active' : 'nav-tab';")
          .append("  });")
          .append("  panels.forEach(function(p) {")
          .append("    p.className = (p.id === 'tab-' + name) ? 'tab-panel active' : 'tab-panel';")
          .append("  });")
          .append("  if (name === 'channels' && allChannelsList.length === 0) fetchChannels();")
          .append("  if (name === 'status') fetchStatus();")
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
          .append("    if(s.device_id) {")
          .append("      document.getElementById('stDeviceId').innerText = s.device_id;")
          .append("      document.getElementById('setDevId').innerText = s.device_id;")
          .append("    }")
          .append("    if(s.pro_status_text) document.getElementById('stProText').innerText = s.pro_status_text;")
          .append("  }).catch(function(){});")
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
          .append("      + '<button class=\"star-btn ' + (c.is_fav ? 'fav' : '') + '\" onclick=\"event.stopPropagation();toggleFav(\\'' + c.name + '\\')\">⭐</button>'")
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
          .append("function toggleFav(name) {")
          .append("  postData({fav_channel: name});")
          .append("  setTimeout(function(){ fetchChannels(); fetchStatus(); }, 400);")
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
