package com.tivilite.tv.server;

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
import org.json.JSONObject;

public class WebPushServer {

    public interface OnM3UReceivedListener {
        void onM3UUrlReceived(String url);
        void onM3UContentReceived(String content);
        void onLicenseCodeReceived(String code);
    }

    private final int port;
    private final OnM3UReceivedListener listener;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public WebPushServer(int port, OnM3UReceivedListener listener) {
        this.port = port;
        this.listener = listener;
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

                if (firstLine.startsWith("GET")) {
                    serveHtmlPage(out);
                } else if (firstLine.startsWith("POST")) {
                    handlePostData(body);
                    serveJsonResponse(out, "{\"success\":true,\"message\":\"已成功推送到电视大屏并生效！\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handlePostData(String body) {
        if (listener == null || body == null || body.trim().isEmpty()) return;
        try {
            // Support JSON payloads
            if (body.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(body);
                if (obj.has("m3u_url")) {
                    String url = obj.getString("m3u_url").trim();
                    if (!url.isEmpty()) listener.onM3UUrlReceived(url);
                }
                if (obj.has("m3u_content")) {
                    String content = obj.getString("m3u_content").trim();
                    if (!content.isEmpty()) listener.onM3UContentReceived(content);
                }
                if (obj.has("license_code")) {
                    String code = obj.getString("license_code").trim();
                    if (!code.isEmpty()) listener.onLicenseCodeReceived(code);
                }
                return;
            }

            // Support application/x-www-form-urlencoded
            // CRITICAL: Split on '&' FIRST before URLDecoder to protect query strings inside URLs/M3U!
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = URLDecoder.decode(pair.substring(0, eq).trim(), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1).trim(), "UTF-8");
                    if ("m3u_url".equals(key) && !val.isEmpty()) {
                        listener.onM3UUrlReceived(val);
                    } else if ("m3u_content".equals(key) && !val.isEmpty()) {
                        listener.onM3UContentReceived(val);
                    } else if ("license_code".equals(key) && !val.isEmpty()) {
                        listener.onLicenseCodeReceived(val);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void serveHtmlPage(OutputStream out) throws Exception {
        String html = "<!DOCTYPE html><html lang='zh-CN'><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>" +
                "<title>TiviLite 局域网配源助手</title><style>" +
                ":root{--bg:#0B0C10;--card:#161922;--accent:#00E5FF;--blue:#3D7BFD;--green:#10B981;--text:#F3F4F6;--muted:#9CA3AF;}" +
                "* {box-sizing:border-box;margin:0;padding:0;}" +
                "body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;background:var(--bg);color:var(--text);padding:16px;line-height:1.5;}" +
                ".container{max-width:520px;margin:0 auto;}" +
                ".header{text-align:center;margin-bottom:20px;padding-top:10px;}" +
                ".header h1{color:var(--accent);font-size:22px;display:flex;align-items:center;justify-content:center;gap:8px;}" +
                ".header p{color:var(--muted);font-size:13px;margin-top:4px;}" +
                ".card{background:var(--card);border-radius:16px;padding:18px;margin-bottom:16px;border:1px solid rgba(255,255,255,0.08);box-shadow:0 8px 24px rgba(0,0,0,0.4);}" +
                ".card-title{font-size:16px;font-weight:700;color:var(--accent);margin-bottom:12px;display:flex;align-items:center;gap:6px;}" +
                ".preset-row{display:flex;flex-direction:column;gap:8px;margin-bottom:12px;}" +
                ".btn-preset{background:#202534;border:1px solid rgba(255,255,255,0.12);color:var(--text);padding:12px;border-radius:10px;font-size:13px;font-weight:600;text-align:left;cursor:pointer;display:flex;justify-content:space-between;align-items:center;transition:0.2s;}" +
                ".btn-preset:active{background:var(--blue);color:#FFF;}" +
                ".badge{background:rgba(0,229,255,0.15);color:var(--accent);padding:2px 8px;border-radius:4px;font-size:11px;font-weight:bold;}" +
                "label{display:block;margin:12px 0 6px;color:#D1D5DB;font-size:13px;font-weight:600;}" +
                "input,textarea{width:100%;box-sizing:border-box;background:#0C0E14;border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:12px;color:#FFF;font-size:14px;transition:0.2s;}" +
                "input:focus,textarea:focus{border-color:var(--accent);outline:none;background:#10141E;}" +
                ".btn-primary{width:100%;background:var(--blue);color:#FFF;border:none;border-radius:10px;padding:14px;font-size:15px;font-weight:700;cursor:pointer;margin-top:14px;transition:0.2s;display:flex;align-items:center;justify-content:center;gap:6px;}" +
                ".btn-primary:active{background:#2563EB;transform:scale(0.98);}" +
                ".btn-file{background:#1E293B;border:1px dashed rgba(255,255,255,0.25);color:var(--accent);padding:12px;border-radius:10px;text-align:center;cursor:pointer;font-size:13px;font-weight:bold;margin-top:8px;}" +
                ".notice{background:rgba(16,185,129,0.12);border:1px solid rgba(16,185,129,0.3);color:var(--green);padding:12px;border-radius:8px;font-size:13px;margin-bottom:14px;display:none;}" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>📺 TiviLite 大屏配源助手</h1>" +
                "<p>手机与电视在同一 Wi-Fi 下，一键极速推送到电视</p>" +
                "</div>" +
                "<div id='alertBox' class='notice'></div>" +

                "<!-- Card 1: 热门精选源一键推送 -->" +
                "<div class='card'>" +
                "<div class='card-title'>⚡ 热门精选源 (点击即推)</div>" +
                "<div class='preset-row'>" +
                "<button class='btn-preset' onclick='pushUrl(\"https://live.fanmingming.com/tv/m3u/ipv6.m3u\", \"全国 IPv6 直连专线\")'>" +
                "<span>⚡ 全国 IPv6 极速直连专线</span><span class='badge'>推荐</span>" +
                "</button>" +
                "<button class='btn-preset' onclick='pushUrl(\"https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/v6.m3u\", \"央视卫视 IPv6 高清\")'>" +
                "<span>🔥 CCTV & 各省卫视高清</span><span class='badge'>热门</span>" +
                "</button>" +
                "</div>" +
                "</div>" +

                "<!-- Card 2: 自定义 M3U / TXT 推送 -->" +
                "<div class='card'>" +
                "<div class='card-title'>📡 自定义订阅与文件导入</div>" +
                "<label>方式 1：输入订阅源地址 (URL)：</label>" +
                "<input type='text' id='inputUrl' placeholder='https://.../live.m3u 或 .txt' />" +
                "<button class='btn-primary' onclick='submitUrl()'>🚀 推送订阅地址到电视</button>" +

                "<div style='margin-top:16px; border-top:1px solid rgba(255,255,255,0.08); padding-top:14px;'>" +
                "<label>方式 2：直接选择手机里的本地文件：</label>" +
                "<div class='btn-file' onclick='document.getElementById(\"filePicker\").click()'>📂 点击选择本地 .m3u / .txt 播放列表文件</div>" +
                "<input type='file' id='filePicker' accept='.m3u,.m3u8,.txt' style='display:none;' onchange='handleFileSelect(this)' />" +
                "</div>" +

                "<div style='margin-top:16px; border-top:1px solid rgba(255,255,255,0.08); padding-top:14px;'>" +
                "<label>方式 3：直接粘贴 M3U 或 TXT 文本内容：</label>" +
                "<textarea id='textContent' rows='4' placeholder='#EXTM3U 或 频道名,URL'></textarea>" +
                "<button class='btn-primary' style='background:#10B981;' onclick='submitContent()'>📝 推送文本内容到电视</button>" +
                "</div>" +
                "</div>" +

                "<!-- Card 3: Pro 免遥控激活 -->" +
                "<div class='card' style='border-color:rgba(61,123,253,0.4);'>" +
                "<div class='card-title' style='color:#60A5FA;'>👑 Pro 会员一键激活</div>" +
                "<p style='color:var(--muted); font-size:12px; margin-bottom:10px;'>手机复制激活码，无需电视遥控器逐字输入：</p>" +
                "<input type='text' id='licenseInput' placeholder='PRO-XXXX-XXXX-XXXX' style='text-transform:uppercase;' />" +
                "<button class='btn-primary' style='background:#10B981;' onclick='submitLicense()'>✨ 立即激活电视端 Pro</button>" +
                "</div>" +

                "</div>" +
                "<script>" +
                "function showNotice(msg, isSuccess) {" +
                "  var box = document.getElementById('alertBox');" +
                "  box.style.display = 'block';" +
                "  box.style.color = isSuccess ? '#10B981' : '#EF4444';" +
                "  box.style.borderColor = isSuccess ? 'rgba(16,185,129,0.4)' : 'rgba(239,68,68,0.4)';" +
                "  box.innerText = msg;" +
                "  window.scrollTo({top: 0, behavior: 'smooth'});" +
                "}" +
                "function pushUrl(url, name) {" +
                "  showNotice('正在推送到电视大屏: ' + name + '...', true);" +
                "  postData({m3u_url: url});" +
                "}" +
                "function submitUrl() {" +
                "  var u = document.getElementById('inputUrl').value.trim();" +
                "  if(!u) { alert('请输入有效的订阅源 URL'); return; }" +
                "  showNotice('正在发送订阅地址到电视...', true);" +
                "  postData({m3u_url: u});" +
                "}" +
                "function submitContent() {" +
                "  var c = document.getElementById('textContent').value.trim();" +
                "  if(!c) { alert('请粘贴播放列表文本'); return; }" +
                "  showNotice('正在推送文本列表到电视...', true);" +
                "  postData({m3u_content: c});" +
                "}" +
                "function submitLicense() {" +
                "  var l = document.getElementById('licenseInput').value.trim();" +
                "  if(!l) { alert('请输入激活码'); return; }" +
                "  showNotice('正在向电视发送卡密验证...', true);" +
                "  postData({license_code: l});" +
                "}" +
                "function handleFileSelect(input) {" +
                "  if(!input.files || input.files.length === 0) return;" +
                "  var file = input.files[0];" +
                "  showNotice('正在读取并上传本地文件: ' + file.name + '...', true);" +
                "  var reader = new FileReader();" +
                "  reader.onload = function(e) {" +
                "    postData({m3u_content: e.target.result});" +
                "  };" +
                "  reader.readAsText(file);" +
                "}" +
                "function postData(payload) {" +
                "  fetch('/api/push', {" +
                "    method: 'POST'," +
                "    headers: {'Content-Type': 'application/json'}," +
                "    body: JSON.stringify(payload)" +
                "  })" +
                "  .then(function(r){ return r.json(); })" +
                "  .then(function(res){" +
                "    showNotice('🎉 ' + res.message, true);" +
                "  })" +
                "  .catch(function(err){" +
                "    showNotice('❌ 推送失败，请检查电视与手机是否在同一 Wi-Fi', false);" +
                "  });" +
                "}" +
                "</script>" +
                "</body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
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
