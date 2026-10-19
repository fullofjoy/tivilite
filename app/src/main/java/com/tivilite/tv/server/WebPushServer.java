package com.tivilite.tv.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

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
            } catch (Exception e) {
                // Server closed or network changed
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = socket.getOutputStream()) {

                String firstLine = reader.readLine();
                if (firstLine == null) return;

                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                if (firstLine.startsWith("GET")) {
                    serveHtmlPage(out);
                } else if (firstLine.startsWith("POST")) {
                    char[] bodyChars = new char[contentLength];
                    int readTotal = 0;
                    while (readTotal < contentLength) {
                        int r = reader.read(bodyChars, readTotal, contentLength - readTotal);
                        if (r == -1) break;
                        readTotal += r;
                    }
                    String body = new String(bodyChars);
                    handlePostData(body);
                    serveSuccessResponse(out);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handlePostData(String body) {
        if (listener == null || body == null) return;
        try {
            String decoded = URLDecoder.decode(body, "UTF-8");
            for (String pair : decoded.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = pair.substring(0, eq).trim();
                    String val = pair.substring(eq + 1).trim();
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
                "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
                "<title>TiviLite 局域网配源助手</title><style>" +
                "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0F1016;color:#FFF;padding:24px;margin:0;}" +
                ".card{max-width:480px;margin:0 auto 20px;background:#1A1D27;border-radius:16px;padding:24px;border:1px solid #2D3346;box-shadow:0 8px 24px rgba(0,0,0,0.5);}" +
                "h2{color:#00E5FF;margin-top:0;font-size:22px;}p{color:#9CA3AF;font-size:14px;line-height:1.5;}" +
                "label{display:block;margin:16px 0 6px;color:#D1D5DB;font-size:14px;font-weight:600;}" +
                "input,textarea{width:100%;box-sizing:border-box;background:#0D0F14;border:1px solid #374151;border-radius:8px;padding:12px;color:#FFF;font-size:14px;}" +
                "input:focus,textarea:focus{border-color:#00E5FF;outline:none;}" +
                "button{width:100%;background:#3D7BFD;color:#FFF;border:none;border-radius:8px;padding:14px;font-size:16px;font-weight:700;cursor:pointer;margin-top:20px;transition:0.2s;}" +
                "button:hover{background:#2563EB;}" +
                ".tip{background:#111827;border-left:4px solid #10B981;padding:10px;margin-top:16px;border-radius:4px;font-size:12px;color:#9CA3AF;}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>📺 TiviLite 一键配源</h2>" +
                "<p>手机与电视在同一 Wi-Fi 下，直接在此粘贴 M3U 链接即可推送到电视大屏！</p>" +
                "<form method='POST' action='/api/push'>" +
                "<label>M3U 订阅源地址 (URL)：</label>" +
                "<input type='text' name='m3u_url' placeholder='http://example.com/live.m3u' />" +
                "<label>或直接粘贴 M3U 文本内容：</label>" +
                "<textarea name='m3u_content' rows='5' placeholder='#EXTM3U ...'></textarea>" +
                "<button type='submit'>🚀 立即推送到电视大屏</button>" +
                "</form>" +
                "<div class='tip'>💡 提示：推送成功后，电视端会立即刷新出最新频道分组。</div>" +
                "</div>" +
                "<div class='card' style='border-color:#3D7BFD;'>" +
                "<h2 style='color:#3D7BFD;'>👑 TiviLite Pro 激活</h2>" +
                "<p>免遥控器输入！手机微信复制激活码，直接在此粘贴即可激活大屏：</p>" +
                "<form method='POST' action='/api/activate'>" +
                "<label>Pro 激活码 (PRO-XXXX-XXXX-XXXX)：</label>" +
                "<input type='text' name='license_code' placeholder='PRO-9F96-E370-3F9B' style='text-transform:uppercase;' required />" +
                "<button type='submit' style='background:#10B981;'>✨ 立即激活电视端 Pro</button>" +
                "</form>" +
                "</div>" +
                "</body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void serveSuccessResponse(OutputStream out) throws Exception {
        String html = "<!DOCTYPE html><html><body style='background:#0F1016;color:#10B981;font-family:sans-serif;text-align:center;padding:50px;'>" +
                "<h1>✅ 推送成功！</h1>" +
                "<p style='color:#FFF;'>订阅源已成功发送到电视，电视端已自动载入最新频道列表。</p>" +
                "<a href='/' style='color:#00E5FF;text-decoration:none;'>返回继续配置</a>" +
                "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
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
