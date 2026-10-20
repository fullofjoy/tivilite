package com.tivilite.tv.player;

import android.content.Context;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.net.URLEncoder;

public class LanMulticastManager {

    private static final String TAG = "LanMulticastManager";
    private static LanMulticastManager instance;
    private final Context context;

    private LanMulticastManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized LanMulticastManager getInstance(Context context) {
        if (instance == null) {
            instance = new LanMulticastManager(context);
        }
        return instance;
    }

    /**
     * 判断是否为运营商 IPTV 组播直连协议 (rtp:// 或 udp://)
     */
    public static boolean isMulticastUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("rtp://") || lower.startsWith("udp://");
    }

    /**
     * 将 rtp:// 或 udp:// 组播协议转换为本地零开销 HTTP 直流
     */
    public String resolvePlayableUrl(String rawUrl, int localPort) {
        if (!isMulticastUrl(rawUrl)) {
            return rawUrl;
        }
        try {
            String encoded = URLEncoder.encode(rawUrl.trim(), "UTF-8");
            return "http://127.0.0.1:" + localPort + "/live/multicast?url=" + encoded;
        } catch (Exception e) {
            return rawUrl;
        }
    }

    /**
     * 生成供局域网内其他设备 (如卧室电视、手机、iPad) 直连播放的中继流 URL
     */
    public String generateLanRelayUrl(String hostIp, int localPort, String targetUrl) {
        if (targetUrl == null || hostIp == null) return "";
        try {
            String encoded = URLEncoder.encode(targetUrl.trim(), "UTF-8");
            if (isMulticastUrl(targetUrl)) {
                return "http://" + hostIp + ":" + localPort + "/live/multicast?url=" + encoded;
            } else {
                return "http://" + hostIp + ":" + localPort + "/stream/relay?url=" + encoded;
            }
        } catch (Exception e) {
            return targetUrl;
        }
    }

    /**
     * 接收并解封装组播 UDP/RTP 数据包，剥离 12 字节 RTP 头，输出纯净 MPEG-TS
     */
    public void streamMulticastToClient(String multicastUrl, OutputStream out) throws Exception {
        if (multicastUrl == null || multicastUrl.isEmpty()) return;

        boolean isRtp = multicastUrl.toLowerCase().startsWith("rtp://");
        String clean = multicastUrl.replace("rtp://", "").replace("udp://", "").replace("@", "");

        String host = clean;
        int port = isRtp ? 8148 : 5140;
        int colonIdx = clean.indexOf(':');
        if (colonIdx > 0) {
            host = clean.substring(0, colonIdx);
            try {
                port = Integer.parseInt(clean.substring(colonIdx + 1));
            } catch (Exception ignored) {
            }
        }

        InetAddress group = InetAddress.getByName(host);
        MulticastSocket socket = new MulticastSocket(port);
        socket.setReceiveBufferSize(1024 * 1024); // 1MB 缓冲区
        socket.setSoTimeout(5000); // 5s 超时

        try {
            socket.joinGroup(group);
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (!Thread.currentThread().isInterrupted()) {
                socket.receive(packet);
                int length = packet.getLength();
                int offset = 0;
                byte[] data = packet.getData();

                // 剥离 RTP 12 字节传输头 (RFC 3550)
                if (isRtp && length > 12) {
                    if ((data[0] & 0xC0) == 0x80) {
                        offset = 12;
                        length -= 12;
                    }
                }

                if (length > 0) {
                    out.write(data, offset, length);
                }
            }
        } finally {
            try {
                socket.leaveGroup(group);
            } catch (Exception ignored) {
            }
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 局域网同屏中继代理：为卧室电视或移动端转发主流直播流
     */
    public void streamHttpRelayToClient(String targetUrl, OutputStream out) throws Exception {
        if (targetUrl == null || targetUrl.isEmpty()) return;

        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36 Chrome/100.0.4896.127 Safari/537.36");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);

        try (InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while (!Thread.currentThread().isInterrupted() && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }
    }
}
