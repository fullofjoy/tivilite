package com.tivilite.tv.parser;

import com.tivilite.tv.data.SmartChannelMatcher;
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
            return new ArrayList<>(); // 纯净播放器定位：默认不硬编码内置电视频道
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
                    String parsedLogo = extractTag(TVG_LOGO_PATTERN, line, "");
                    currentChannel.setLogoUrl(SmartChannelMatcher.matchLogoUrl(name, parsedLogo));
                    currentChannel.setCatchupType(extractTag(CATCHUP_PATTERN, line, ""));
                    currentChannel.setCatchupSource(extractTag(CATCHUP_SOURCE_PATTERN, line, ""));
                    SmartChannelMatcher.enrichChannel(currentChannel);
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
                        ch.setLogoUrl(SmartChannelMatcher.matchLogoUrl(name, ""));
                        SmartChannelMatcher.enrichChannel(ch);
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

        return channels;
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
        return groupByCategories(null, channels);
    }

    public static Map<String, List<Channel>> groupByCategories(android.content.Context context, List<Channel> channels) {
        Map<String, List<Channel>> grouped = new LinkedHashMap<>();

        // 1. 如果有收藏频道，优先置顶展示「⭐ 我的收藏」
        if (context != null && channels != null && !channels.isEmpty()) {
            List<Channel> favs = com.tivilite.tv.favorite.FavoriteManager.getFavorites(context, channels);
            if (!favs.isEmpty()) {
                grouped.put("⭐ 我的收藏", favs);
            }
        }

        if (channels != null) {
            grouped.put("全部频道", new ArrayList<>(channels));

            for (Channel ch : channels) {
                String g = ch.getGroup();
                if (!grouped.containsKey(g)) {
                    grouped.put(g, new ArrayList<>());
                }
                grouped.get(g).add(ch);
            }
        }
        return grouped;
    }

    /**
     * 仅提供用于大屏硬解/软解/网络技术验证的合规开放测试流
     */
    public static List<Channel> getDemoChannels() {
        List<Channel> list = new ArrayList<>();
        Channel c1 = new Channel(1, "CGTN 官方开放流 (合规测试)", "开源演示", "https://livedoc.cgtn.com/500d/prog_index.m3u8");
        c1.setLogoUrl("https://live.fanmingming.com/tv/CGTN.png");
        list.add(c1);
        return list;
    }

    public static List<Channel> getDefaultChannels() {
        return getDemoChannels();
    }
}
