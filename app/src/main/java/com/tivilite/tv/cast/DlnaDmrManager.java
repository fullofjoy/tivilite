package com.tivilite.tv.cast;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TiviLite DLNA Digital Media Renderer (DMR) Engine
 * Implements SSDP Discovery (UDP 239.255.255.250:1900) & UPnP AVTransport/RenderingControl.
 * Allows mobile phones (Bilibili, Tencent Video, iQiyi, YouTube, Gallery, etc.) to discover
 * "TiviLite TV" on local Wi-Fi and cast video streams directly with hardware acceleration.
 */
public class DlnaDmrManager {
    private static final String TAG = "DlnaDmrManager";
    private static final String MULTICAST_IP = "239.255.255.250";
    private static final int MULTICAST_PORT = 1900;

    public interface CastListener {
        void onCastStarted(String uri, String title, String artist, long positionMs);
        void onCastStopped();
        void onCastPlay();
        void onCastPause();
        void onCastSeek(long positionMs);
        void onCastVolume(int volumePercent);
    }

    private static volatile DlnaDmrManager instance;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String deviceUuid;
    private final String friendlyName;
    private int httpPort = 8888;
    private CastListener castListener;

    private MulticastSocket multicastSocket;
    private Thread ssdpThread;
    private Thread announceThread;
    private volatile boolean isRunning = false;

    // Current Cast State
    private volatile boolean isCasting = false;
    private volatile boolean isPaused = false;
    private volatile String currentMediaUri = "";
    private volatile String currentMediaTitle = "手机投屏视频";
    private volatile String currentMediaArtist = "DLNA 投屏设备";
    private volatile long currentDurationMs = 0;
    private volatile long currentPositionMs = 0;
    private volatile int currentVolume = 100;

    public static DlnaDmrManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DlnaDmrManager.class) {
                if (instance == null) {
                    instance = new DlnaDmrManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DlnaDmrManager(Context context) {
        this.context = context;
        SharedPreferences prefs = context.getSharedPreferences("tivilite_cast_prefs", Context.MODE_PRIVATE);
        String uuid = prefs.getString("device_uuid", null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString("device_uuid", uuid).apply();
        }
        this.deviceUuid = uuid;
        this.friendlyName = "TiviLite TV (" + Build.MODEL + ")";
    }

    public void setCastListener(CastListener listener) {
        this.castListener = listener;
    }

    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        startSsdpListener();
        startPeriodicAnnouncer();
        Log.i(TAG, "DLNA DMR Engine started. FriendlyName: " + friendlyName + ", UUID: " + deviceUuid);
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        sendSsdpByebye();
        if (multicastSocket != null) {
            try {
                multicastSocket.close();
            } catch (Exception ignored) {}
            multicastSocket = null;
        }
        if (ssdpThread != null) {
            ssdpThread.interrupt();
            ssdpThread = null;
        }
        if (announceThread != null) {
            announceThread.interrupt();
            announceThread = null;
        }
        Log.i(TAG, "DLNA DMR Engine stopped.");
    }

    public boolean isCasting() {
        return isCasting;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public String getCurrentMediaTitle() {
        return currentMediaTitle;
    }

    public String getCurrentMediaUri() {
        return currentMediaUri;
    }

    public void updatePlaybackProgress(long positionMs, long durationMs) {
        this.currentPositionMs = positionMs;
        if (durationMs > 0) this.currentDurationMs = durationMs;
    }

    public void updateCastState(boolean casting, boolean paused) {
        this.isCasting = casting;
        this.isPaused = paused;
    }

    public void stopCast() {
        if (!isCasting) return;
        isCasting = false;
        isPaused = false;
        currentMediaUri = "";
        currentMediaTitle = "";
        if (castListener != null) {
            mainHandler.post(() -> castListener.onCastStopped());
        }
    }

    // ==========================================
    // SSDP Discovery & Advertiser
    // ==========================================

    private void startSsdpListener() {
        ssdpThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (isRunning) {
                try {
                    if (multicastSocket == null || multicastSocket.isClosed()) {
                        multicastSocket = new MulticastSocket(MULTICAST_PORT);
                        multicastSocket.setLoopbackMode(true);
                        multicastSocket.setReuseAddress(true);
                        InetAddress group = InetAddress.getByName(MULTICAST_IP);
                        multicastSocket.joinGroup(group);
                    }

                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    multicastSocket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleSsdpPacket(msg, packet.getAddress(), packet.getPort());
                } catch (Exception e) {
                    if (!isRunning) break;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }, "TiviLite-SSDP-Listener");
        ssdpThread.start();
    }

    private void handleSsdpPacket(String msg, InetAddress clientAddress, int clientPort) {
        if (msg == null || !msg.startsWith("M-SEARCH")) return;

        // Check search target (ST)
        String st = null;
        String[] lines = msg.split("\r?\n");
        for (String line : lines) {
            if (line.toUpperCase(Locale.ROOT).startsWith("ST:")) {
                st = line.substring(3).trim();
                break;
            }
        }
        if (st == null) return;

        boolean matches = "ssdp:all".equalsIgnoreCase(st)
                || "upnp:rootdevice".equalsIgnoreCase(st)
                || "urn:schemas-upnp-org:device:MediaRenderer:1".equalsIgnoreCase(st)
                || "urn:schemas-upnp-org:service:AVTransport:1".equalsIgnoreCase(st)
                || "urn:schemas-upnp-org:service:RenderingControl:1".equalsIgnoreCase(st)
                || ("uuid:" + deviceUuid).equalsIgnoreCase(st);

        if (matches) {
            sendSsdpResponse(clientAddress, clientPort, st);
        }
    }

    private void sendSsdpResponse(InetAddress clientAddress, int clientPort, String targetSt) {
        new Thread(() -> {
            try {
                String localIp = getLocalIpAddress();
                String location = "http://" + localIp + ":" + httpPort + "/dlna/description.xml";
                String st = "ssdp:all".equalsIgnoreCase(targetSt) ? "urn:schemas-upnp-org:device:MediaRenderer:1" : targetSt;

                String response = "HTTP/1.1 200 OK\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "DATE: " + getRfc1123Date() + "\r\n"
                        + "EXT:\r\n"
                        + "LOCATION: " + location + "\r\n"
                        + "SERVER: Android/10 UPnP/1.0 TiviLiteDMR/1.0\r\n"
                        + "ST: " + st + "\r\n"
                        + "USN: uuid:" + deviceUuid + "::" + st + "\r\n"
                        + "\r\n";

                byte[] data = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(data, data.length, clientAddress, clientPort);
                multicastSocket.send(reply);
            } catch (Exception ignored) {}
        }).start();
    }

    private void startPeriodicAnnouncer() {
        announceThread = new Thread(() -> {
            while (isRunning) {
                try {
                    sendSsdpAlive();
                    Thread.sleep(30000); // Send alive every 30 seconds
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "TiviLite-SSDP-Announcer");
        announceThread.start();
    }

    private void sendSsdpAlive() {
        try {
            String localIp = getLocalIpAddress();
            String location = "http://" + localIp + ":" + httpPort + "/dlna/description.xml";
            InetAddress group = InetAddress.getByName(MULTICAST_IP);

            String[] targets = new String[]{
                    "upnp:rootdevice",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:schemas-upnp-org:service:RenderingControl:1",
                    "urn:schemas-upnp-org:service:ConnectionManager:1"
            };

            for (String nt : targets) {
                String notify = "NOTIFY * HTTP/1.1\r\n"
                        + "HOST: 239.255.255.250:1900\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "LOCATION: " + location + "\r\n"
                        + "NT: " + nt + "\r\n"
                        + "NTS: ssdp:alive\r\n"
                        + "SERVER: Android/10 UPnP/1.0 TiviLiteDMR/1.0\r\n"
                        + "USN: uuid:" + deviceUuid + "::" + nt + "\r\n"
                        + "\r\n";

                byte[] bytes = notify.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, group, MULTICAST_PORT);
                if (multicastSocket != null && !multicastSocket.isClosed()) {
                    multicastSocket.send(packet);
                }
            }
        } catch (Exception ignored) {}
    }

    private void sendSsdpByebye() {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_IP);
            String notify = "NOTIFY * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "NT: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                    + "NTS: ssdp:byebye\r\n"
                    + "USN: uuid:" + deviceUuid + "::urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                    + "\r\n";

            byte[] bytes = notify.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, group, MULTICAST_PORT);
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.send(packet);
            }
        } catch (Exception ignored) {}
    }

    // ==========================================
    // UPnP XML Documents Generator
    // ==========================================

    public String getDeviceDescriptionXml(String hostAddress) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n"
                + "  <specVersion>\n"
                + "    <major>1</major>\n"
                + "    <minor>0</minor>\n"
                + "  </specVersion>\n"
                + "  <device>\n"
                + "    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>\n"
                + "    <friendlyName>" + escapeXml(friendlyName) + "</friendlyName>\n"
                + "    <manufacturer>TiviLite Dev</manufacturer>\n"
                + "    <manufacturerURL>https://github.com/fullofjoy/tivilite</manufacturerURL>\n"
                + "    <modelDescription>TiviLite Ultra-lightweight Android TV Player with DLNA DMR</modelDescription>\n"
                + "    <modelName>TiviLite TV</modelName>\n"
                + "    <modelNumber>v1.6.0</modelNumber>\n"
                + "    <modelURL>https://github.com/fullofjoy/tivilite</modelURL>\n"
                + "    <UDN>uuid:" + deviceUuid + "</UDN>\n"
                + "    <serviceList>\n"
                + "      <service>\n"
                + "        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>\n"
                + "        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\n"
                + "        <SCPDURL>/dlna/avtransport.xml</SCPDURL>\n"
                + "        <controlURL>/dlna/control/avtransport</controlURL>\n"
                + "        <eventSubURL>/dlna/event/avtransport</eventSubURL>\n"
                + "      </service>\n"
                + "      <service>\n"
                + "        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>\n"
                + "        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>\n"
                + "        <SCPDURL>/dlna/renderingcontrol.xml</SCPDURL>\n"
                + "        <controlURL>/dlna/control/renderingcontrol</controlURL>\n"
                + "        <eventSubURL>/dlna/event/renderingcontrol</eventSubURL>\n"
                + "      </service>\n"
                + "      <service>\n"
                + "        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\n"
                + "        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\n"
                + "        <SCPDURL>/dlna/connectionmanager.xml</SCPDURL>\n"
                + "        <controlURL>/dlna/control/connectionmanager</controlURL>\n"
                + "        <eventSubURL>/dlna/event/connectionmanager</eventSubURL>\n"
                + "      </service>\n"
                + "    </serviceList>\n"
                + "  </device>\n"
                + "</root>";
    }

    public String getAvTransportScpdXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <actionList>\n"
                + "    <action><name>SetAVTransportURI</name></action>\n"
                + "    <action><name>Play</name></action>\n"
                + "    <action><name>Pause</name></action>\n"
                + "    <action><name>Stop</name></action>\n"
                + "    <action><name>Seek</name></action>\n"
                + "    <action><name>GetPositionInfo</name></action>\n"
                + "    <action><name>GetTransportInfo</name></action>\n"
                + "    <action><name>GetMediaInfo</name></action>\n"
                + "  </actionList>\n"
                + "</scpd>";
    }

    public String getRenderingControlScpdXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <actionList>\n"
                + "    <action><name>SetVolume</name></action>\n"
                + "    <action><name>GetVolume</name></action>\n"
                + "    <action><name>SetMute</name></action>\n"
                + "    <action><name>GetMute</name></action>\n"
                + "  </actionList>\n"
                + "</scpd>";
    }

    public String getConnectionManagerScpdXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "  <actionList>\n"
                + "    <action><name>GetProtocolInfo</name></action>\n"
                + "  </actionList>\n"
                + "</scpd>";
    }

    // ==========================================
    // SOAP Action Processing
    // ==========================================

    public String handleSoapRequest(String path, String soapAction, String body) {
        if (soapAction == null) soapAction = "";
        soapAction = soapAction.replaceAll("\"", "");

        if (path.contains("avtransport")) {
            return handleAvTransportSoap(soapAction, body);
        } else if (path.contains("renderingcontrol")) {
            return handleRenderingControlSoap(soapAction, body);
        } else if (path.contains("connectionmanager")) {
            return handleConnectionManagerSoap(soapAction, body);
        }
        return wrapSoapResponse("ActionResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");
    }

    private String handleAvTransportSoap(String action, String body) {
        if (action.endsWith("SetAVTransportURI")) {
            String uri = extractTag(body, "CurrentURI");
            String metadata = extractTag(body, "CurrentURIMetaData");
            String title = "手机无线投屏";
            String artist = "DLNA 投屏设备";

            if (metadata != null && !metadata.isEmpty()) {
                String extractedTitle = extractTag(metadata, "dc:title");
                if (extractedTitle == null || extractedTitle.isEmpty()) {
                    extractedTitle = extractTag(metadata, "title");
                }
                if (extractedTitle != null && !extractedTitle.trim().isEmpty()) {
                    title = extractedTitle.trim();
                }
                String extractedArtist = extractTag(metadata, "dc:creator");
                if (extractedArtist != null && !extractedArtist.trim().isEmpty()) {
                    artist = extractedArtist.trim();
                }
            }

            this.currentMediaUri = uri != null ? uri.trim() : "";
            this.currentMediaTitle = title;
            this.currentMediaArtist = artist;
            this.isCasting = true;
            this.isPaused = false;

            final String finalUri = this.currentMediaUri;
            final String finalTitle = this.currentMediaTitle;
            final String finalArtist = this.currentMediaArtist;

            if (castListener != null) {
                mainHandler.post(() -> castListener.onCastStarted(finalUri, finalTitle, finalArtist, 0));
            }

            return wrapSoapResponse("SetAVTransportURIResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");

        } else if (action.endsWith("Play")) {
            this.isPaused = false;
            this.isCasting = true;
            if (castListener != null) {
                mainHandler.post(() -> castListener.onCastPlay());
            }
            return wrapSoapResponse("PlayResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");

        } else if (action.endsWith("Pause")) {
            this.isPaused = true;
            if (castListener != null) {
                mainHandler.post(() -> castListener.onCastPause());
            }
            return wrapSoapResponse("PauseResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");

        } else if (action.endsWith("Stop")) {
            stopCast();
            return wrapSoapResponse("StopResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");

        } else if (action.endsWith("Seek")) {
            String target = extractTag(body, "Target");
            long seekMs = parseTimeStringToMs(target);
            if (seekMs >= 0 && castListener != null) {
                mainHandler.post(() -> castListener.onCastSeek(seekMs));
            }
            return wrapSoapResponse("SeekResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");

        } else if (action.endsWith("GetPositionInfo")) {
            String relTime = formatMsToTimeString(currentPositionMs);
            String trackDuration = formatMsToTimeString(currentDurationMs > 0 ? currentDurationMs : 7200000L);
            String inner = "<Track>1</Track>\n"
                    + "<TrackDuration>" + trackDuration + "</TrackDuration>\n"
                    + "<TrackMetaData>&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot;&gt;&lt;item&gt;&lt;dc:title&gt;" + escapeXml(currentMediaTitle) + "&lt;/dc:title&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</TrackMetaData>\n"
                    + "<TrackURI>" + escapeXml(currentMediaUri) + "</TrackURI>\n"
                    + "<RelTime>" + relTime + "</RelTime>\n"
                    + "<AbsTime>" + relTime + "</AbsTime>\n"
                    + "<RelCount>2147483647</RelCount>\n"
                    + "<AbsCount>2147483647</AbsCount>\n";
            return wrapSoapResponse("GetPositionInfoResponse", "urn:schemas-upnp-org:service:AVTransport:1", inner);

        } else if (action.endsWith("GetTransportInfo")) {
            String state = !isCasting ? "STOPPED" : (isPaused ? "PAUSED_PLAYBACK" : "PLAYING");
            String inner = "<CurrentTransportState>" + state + "</CurrentTransportState>\n"
                    + "<CurrentTransportStatus>OK</CurrentTransportStatus>\n"
                    + "<CurrentSpeed>1</CurrentSpeed>\n";
            return wrapSoapResponse("GetTransportInfoResponse", "urn:schemas-upnp-org:service:AVTransport:1", inner);

        } else if (action.endsWith("GetMediaInfo")) {
            String trackDuration = formatMsToTimeString(currentDurationMs > 0 ? currentDurationMs : 7200000L);
            String inner = "<NrTracks>1</NrTracks>\n"
                    + "<MediaDuration>" + trackDuration + "</MediaDuration>\n"
                    + "<CurrentURI>" + escapeXml(currentMediaUri) + "</CurrentURI>\n"
                    + "<CurrentURIMetaData>&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot;&gt;&lt;item&gt;&lt;dc:title&gt;" + escapeXml(currentMediaTitle) + "&lt;/dc:title&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>\n"
                    + "<NextURI></NextURI>\n"
                    + "<NextURIMetaData></NextURIMetaData>\n"
                    + "<PlayMedium>NETWORK</PlayMedium>\n"
                    + "<RecordMedium>NOT_IMPLEMENTED</RecordMedium>\n"
                    + "<WriteStatus>NOT_WRITABLE</WriteStatus>\n";
            return wrapSoapResponse("GetMediaInfoResponse", "urn:schemas-upnp-org:service:AVTransport:1", inner);
        }

        return wrapSoapResponse("ActionResponse", "urn:schemas-upnp-org:service:AVTransport:1", "");
    }

    private String handleRenderingControlSoap(String action, String body) {
        if (action.endsWith("SetVolume")) {
            String desiredVol = extractTag(body, "DesiredVolume");
            if (desiredVol != null) {
                try {
                    int vol = Integer.parseInt(desiredVol.trim());
                    this.currentVolume = Math.max(0, Math.min(100, vol));
                    if (castListener != null) {
                        mainHandler.post(() -> castListener.onCastVolume(currentVolume));
                    }
                } catch (Exception ignored) {}
            }
            return wrapSoapResponse("SetVolumeResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "");
        } else if (action.endsWith("GetVolume")) {
            String inner = "<CurrentVolume>" + currentVolume + "</CurrentVolume>\n";
            return wrapSoapResponse("GetVolumeResponse", "urn:schemas-upnp-org:service:RenderingControl:1", inner);
        } else if (action.endsWith("SetMute")) {
            return wrapSoapResponse("SetMuteResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "");
        } else if (action.endsWith("GetMute")) {
            String inner = "<CurrentMute>0</CurrentMute>\n";
            return wrapSoapResponse("GetMuteResponse", "urn:schemas-upnp-org:service:RenderingControl:1", inner);
        }
        return wrapSoapResponse("ActionResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "");
    }

    private String handleConnectionManagerSoap(String action, String body) {
        if (action.endsWith("GetProtocolInfo")) {
            String inner = "<Source></Source>\n"
                    + "<Sink>http-get:*:video/*:DLNA.ORG_PN=MPEG_PS_PAL;DLNA.ORG_OP=01;DLNA.ORG_CI=0,http-get:*:audio/*:*,http-get:*:image/*:*</Sink>\n";
            return wrapSoapResponse("GetProtocolInfoResponse", "urn:schemas-upnp-org:service:ConnectionManager:1", inner);
        }
        return wrapSoapResponse("ActionResponse", "urn:schemas-upnp-org:service:ConnectionManager:1", "");
    }

    private String wrapSoapResponse(String actionResponse, String namespace, String innerXml) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
                + "  <s:Body>\n"
                + "    <u:" + actionResponse + " xmlns:u=\"" + namespace + "\">\n"
                + innerXml
                + "    </u:" + actionResponse + ">\n"
                + "  </s:Body>\n"
                + "</s:Envelope>";
    }

    // ==========================================
    // Utilities
    // ==========================================

    private static String extractTag(String xml, String tagName) {
        if (xml == null || tagName == null) return null;
        // Search tag with or without namespace (e.g. <CurrentURI> or <u:CurrentURI>)
        Pattern p = Pattern.compile("<(?:[a-zA-Z0-9_-]+:)?" + Pattern.quote(tagName) + "[^>]*>([\\s\\S]*?)</(?:[a-zA-Z0-9_-]+:)?" + Pattern.quote(tagName) + ">", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        if (m.find()) {
            String content = m.group(1);
            return unescapeXml(content.trim());
        }
        return null;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String unescapeXml(String text) {
        if (text == null) return "";
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    public static long parseTimeStringToMs(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        try {
            String[] parts = timeStr.trim().split(":");
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return (hours * 3600 + minutes * 60) * 1000 + (long) (seconds * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static String formatMsToTimeString(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String getRfc1123Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                int ip = wi.getIpAddress();
                if (ip != 0) {
                    return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                            (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                }
            }
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (nif.isLoopback() || !nif.isUp()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
