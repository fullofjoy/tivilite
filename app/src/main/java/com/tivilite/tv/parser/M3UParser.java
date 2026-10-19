package com.tivilite.tv.parser;

import com.tivilite.tv.model.Channel;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    private static final Pattern TVG_NAME_PATTERN = Pattern.compile("tvg-name=\"([^\"]*)\"");
    private static final Pattern TVG_ID_PATTERN = Pattern.compile("tvg-id=\"([^\"]*)\"");
    private static final Pattern TVG_LOGO_PATTERN = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern GROUP_TITLE_PATTERN = Pattern.compile("group-title=\"([^\"]*)\"");
    private static final Pattern CATCHUP_PATTERN = Pattern.compile("catchup=\"([^\"]*)\"");
    private static final Pattern CATCHUP_SOURCE_PATTERN = Pattern.compile("catchup-source=\"([^\"]*)\"");

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return getDefaultChannels();
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            Channel currentChannel = null;
            int channelNumber = 1;
            String currentTxtGroup = "默认频道";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF:")) {
                    String name = "";
                    int commaIdx = line.lastIndexOf(',');
                    if (commaIdx != -1 && commaIdx < line.length() - 1) {
                        name = line.substring(commaIdx + 1).trim();
                    }

                    String group = extractTag(GROUP_TITLE_PATTERN, line, "综合频道");
                    String tvgName = extractTag(TVG_NAME_PATTERN, line, name);
                    if (name.isEmpty()) name = tvgName;

                    currentChannel = new Channel(channelNumber, name, group, "");
                    currentChannel.setId(extractTag(TVG_ID_PATTERN, line, name));
                    currentChannel.setLogoUrl(extractTag(TVG_LOGO_PATTERN, line, ""));
                    currentChannel.setCatchupType(extractTag(CATCHUP_PATTERN, line, ""));
                    currentChannel.setCatchupSource(extractTag(CATCHUP_SOURCE_PATTERN, line, ""));
                } else if (line.contains(",#genre#")) {
                    // TVBox TXT format group header (e.g. "央视频道,#genre#")
                    currentTxtGroup = line.substring(0, line.indexOf(",#genre#")).trim();
                    currentChannel = null;
                } else if (!line.startsWith("#") && line.contains(",")) {
                    // TVBox TXT format channel (e.g. "CCTV-1,http://...#http://...")
                    int comma = line.indexOf(',');
                    String name = line.substring(0, comma).trim();
                    String urlsPart = line.substring(comma + 1).trim();
                    if (!name.isEmpty() && (urlsPart.startsWith("http://") || urlsPart.startsWith("https://") || urlsPart.startsWith("rtmp://") || urlsPart.startsWith("rtsp://") || urlsPart.startsWith("["))) {
                        String[] splitUrls = urlsPart.split("#");
                        Channel ch = new Channel(channelNumber++, name, currentTxtGroup, splitUrls[0].trim());
                        for (int i = 1; i < splitUrls.length; i++) {
                            if (!splitUrls[i].trim().isEmpty()) {
                                ch.addUrl(splitUrls[i].trim());
                            }
                        }
                        ch.setLogoUrl("https://live.fanmingming.cn/tv/" + name.replace("高清", "").replace("超清", "").trim() + ".png");
                        Channel existing = findExistingChannel(channels, name, currentTxtGroup);
                        if (existing != null) {
                            for (String u : splitUrls) {
                                if (!u.trim().isEmpty()) existing.addUrl(u.trim());
                            }
                        } else {
                            channels.add(ch);
                        }
                        currentChannel = null;
                        continue;
                    }
                } else if (!line.startsWith("#") && currentChannel != null) {
                    currentChannel.setUrl(line);

                    // Multi-source aggregation: Check if same channel already exists
                    Channel existing = findExistingChannel(channels, currentChannel.getName(), currentChannel.getGroup());
                    if (existing != null) {
                        existing.addUrl(line);
                        if (!currentChannel.getCatchupSource().isEmpty() && !existing.hasCatchup()) {
                            existing.setCatchupSource(currentChannel.getCatchupSource());
                        }
                    } else {
                        currentChannel.setNumber(channelNumber++);
                        channels.add(currentChannel);
                    }
                    currentChannel = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return channels.isEmpty() ? getDefaultChannels() : channels;
    }

    private static Channel findExistingChannel(List<Channel> list, String name, String group) {
        if (name == null || name.isEmpty()) return null;
        String cleanName = name.trim().toLowerCase();
        for (Channel ch : list) {
            if (ch.getName() != null && ch.getName().trim().toLowerCase().equals(cleanName)) {
                if (group == null || ch.getGroup().equals(group)) {
                    return ch;
                }
            }
        }
        return null;
    }

    private static String extractTag(Pattern pattern, String text, String defaultVal) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultVal;
    }

    public static Map<String, List<Channel>> groupByCategories(List<Channel> channels) {
        Map<String, List<Channel>> grouped = new LinkedHashMap<>();
        grouped.put("全部频道", new ArrayList<>(channels));

        for (Channel ch : channels) {
            String g = ch.getGroup();
            if (!grouped.containsKey(g)) {
                grouped.put(g, new ArrayList<>());
            }
            grouped.get(g).add(ch);
        }
        return grouped;
    }

    public static List<Channel> getDefaultChannels() {
        List<Channel> list = new ArrayList<>();

        // Multi-line resilient built-in channels with IPv6 and IPv4 fallbacks
        Channel c1 = new Channel(1, "CCTV-1 综合", "央视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226895/1.m3u8");
        c1.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225829/index.m3u8");
        c1.addUrl("http://live.yangshipin.cn/cctv1.m3u8");
        c1.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c1.setLogoUrl("https://live.fanmingming.cn/tv/CCTV1.png");
        list.add(c1);

        Channel c2 = new Channel(2, "CCTV-13 新闻", "央视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226316/1.m3u8");
        c2.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225859/index.m3u8");
        c2.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c2.setLogoUrl("https://live.fanmingming.cn/tv/CCTV13.png");
        list.add(c2);

        Channel c3 = new Channel(3, "CCTV-4 中文国际", "央视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226335/1.m3u8");
        c3.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225835/index.m3u8");
        c3.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c3.setLogoUrl("https://live.fanmingming.cn/tv/CCTV4.png");
        list.add(c3);

        Channel c4 = new Channel(4, "CCTV-5 体育", "央视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226469/1.m3u8");
        c4.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225838/index.m3u8");
        c4.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c4.setLogoUrl("https://live.fanmingming.cn/tv/CCTV5.png");
        list.add(c4);

        Channel c5 = new Channel(5, "浙江卫视", "卫视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226162/1.m3u8");
        c5.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225797/index.m3u8");
        c5.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c5.setLogoUrl("https://live.fanmingming.cn/tv/浙江卫视.png");
        list.add(c5);

        Channel c6 = new Channel(6, "湖南卫视", "卫视频道", "http://[2409:8087:8:21::18]:6610/otttv.bj.chinamobile.com/PLTV/88888888/224/3221226161/1.m3u8");
        c6.addUrl("http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225796/index.m3u8");
        c6.setCatchupSource("?playseek={utc:YmdHMS}-{utcend:YmdHMS}");
        c6.setLogoUrl("https://live.fanmingming.cn/tv/湖南卫视.png");
        list.add(c6);

        return list;
    }
}
