package com.tivilite.tv.data;

import com.tivilite.tv.model.Channel;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 智能台标与 EPG 模糊匹配补齐引擎 (SmartChannelMatcher)
 * 解决用户导入的自定义 M3U 中频道名称嘈杂 (包含分辨率标签、运营商后缀、乱七八糟标点)、
 * 缺失 tvg-logo 或台标失效导致的大屏体验缺失问题。
 */
public class SmartChannelMatcher {

    private static final String LOGO_CDN_BASE = "https://live.fanmingming.com/tv/";

    // Canonical Channel Meta
    public static class ChannelMeta {
        public final String canonicalName;
        public final String standardEpgName;
        public final String logoFileName;
        public final String defaultGroup;

        public ChannelMeta(String canonicalName, String standardEpgName, String logoFileName, String defaultGroup) {
            this.canonicalName = canonicalName;
            this.standardEpgName = standardEpgName;
            this.logoFileName = logoFileName;
            this.defaultGroup = defaultGroup;
        }

        public String getLogoUrl() {
            return LOGO_CDN_BASE + logoFileName;
        }
    }

    private static final Map<String, ChannelMeta> ALIAS_MAP = new HashMap<>();

    static {
        // 1. CCTV 央视频道聚合
        registerCctv("1", "CCTV-1 综合", "CCTV1.png", "央视频道", "cctv1", "cctv-1", "中央一套", "中央1台", "中央一台", "cctv1综合", "cctv-1综合");
        registerCctv("2", "CCTV-2 财经", "CCTV2.png", "央视频道", "cctv2", "cctv-2", "中央二套", "中央2台", "中央二台", "cctv2财经", "cctv-2财经");
        registerCctv("3", "CCTV-3 综艺", "CCTV3.png", "央视频道", "cctv3", "cctv-3", "中央三套", "中央3台", "中央三台", "cctv3综艺", "cctv-3综艺");
        registerCctv("4", "CCTV-4 中文国际", "CCTV4.png", "央视频道", "cctv4", "cctv-4", "中央四套", "中央4台", "中央四台", "cctv4中文国际", "cctv-4中文国际", "cctv4亚洲");
        registerCctv("5", "CCTV-5 体育", "CCTV5.png", "央视频道", "cctv5", "cctv-5", "中央五套", "中央5台", "中央五台", "cctv5体育", "cctv-5体育", "体育频道");
        registerCctv("5+", "CCTV-5+ 体育赛事", "CCTV5Plus.png", "央视频道", "cctv5+", "cctv-5+", "cctv5plus", "体育赛事", "中央五加", "赛事频道");
        registerCctv("6", "CCTV-6 电影", "CCTV6.png", "央视频道", "cctv6", "cctv-6", "中央六套", "中央6台", "中央六台", "cctv6电影", "cctv-6电影", "电影频道");
        registerCctv("7", "CCTV-7 国防军事", "CCTV7.png", "央视频道", "cctv7", "cctv-7", "中央七套", "中央7台", "中央七台", "cctv7国防军事", "国防军事", "军事频道");
        registerCctv("8", "CCTV-8 电视剧", "CCTV8.png", "央视频道", "cctv8", "cctv-8", "中央八套", "中央8台", "中央八台", "cctv8电视剧", "电视剧频道");
        registerCctv("9", "CCTV-9 纪录", "CCTV9.png", "央视频道", "cctv9", "cctv-9", "中央九套", "中央9台", "中央九台", "cctv9纪录", "纪录频道");
        registerCctv("10", "CCTV-10 科教", "CCTV10.png", "央视频道", "cctv10", "cctv-10", "中央十套", "中央10台", "中央十台", "cctv10科教", "科教频道");
        registerCctv("11", "CCTV-11 戏曲", "CCTV11.png", "央视频道", "cctv11", "cctv-11", "中央十一套", "中央11台", "中央十一台", "cctv11戏曲", "戏曲频道");
        registerCctv("12", "CCTV-12 社会与法", "CCTV12.png", "央视频道", "cctv12", "cctv-12", "中央十二套", "中央12台", "cctv12社会与法", "社会与法");
        registerCctv("13", "CCTV-13 新闻", "CCTV13.png", "央视频道", "cctv13", "cctv-13", "中央十三套", "中央13台", "cctv13新闻", "新闻频道", "央视新闻");
        registerCctv("14", "CCTV-14 少儿", "CCTV14.png", "央视频道", "cctv14", "cctv-14", "中央十四套", "中央14台", "cctv14少儿", "少儿频道");
        registerCctv("15", "CCTV-15 音乐", "CCTV15.png", "央视频道", "cctv15", "cctv-15", "中央十五套", "中央15台", "cctv15音乐", "音乐频道");
        registerCctv("16", "CCTV-16 奥林匹克", "CCTV16.png", "央视频道", "cctv16", "cctv-16", "中央十六套", "中央16台", "cctv16奥林匹克", "奥林匹克");
        registerCctv("17", "CCTV-17 农业农村", "CCTV17.png", "央视频道", "cctv17", "cctv-17", "中央十七套", "中央17台", "cctv17农业农村", "农业农村");
        registerCctv("4K", "CCTV-4K 超高清", "CCTV4K.png", "4K专区", "cctv4k", "cctv-4k", "央视4k", "cctv4k超高清");
        registerCctv("8K", "CCTV-8K 超高清", "CCTV8K.png", "4K专区", "cctv8k", "cctv-8k", "央视8k");

        // CGTN 系列
        registerChannel("CGTN", "CGTN 英语", "CGTN.png", "央视频道", "cgtn", "cgtnen", "cgtn英语", "中国国际电视台");
        registerChannel("CGTN 纪录", "CGTN 纪录", "CGTNDocumentary.png", "央视频道", "cgtndoc", "cgtn纪录", "cgtn纪录片");

        // 2. 全国主流卫视频道
        registerSatellite("湖南卫视", "Hunan.png", "hunan", "湖南卫视", "湖南台", "芒果台", "湖南卫视高清");
        registerSatellite("浙江卫视", "Zhejiang.png", "zhejiang", "浙江卫视", "浙江台", "中国蓝", "浙江卫视高清");
        registerSatellite("江苏卫视", "Jiangsu.png", "jiangsu", "江苏卫视", "江苏台", "荔枝台", "江苏卫视高清");
        registerSatellite("东方卫视", "Dongfang.png", "dongfang", "东方卫视", "东方台", "上海东方卫视", "上海卫视", "番茄台");
        registerSatellite("北京卫视", "Beijing.png", "beijing", "北京卫视", "北京台", "BTV北京卫视", "北京卫视高清");
        registerSatellite("广东卫视", "Guangdong.png", "guangdong", "广东卫视", "广东台", "广东卫视高清");
        registerSatellite("深圳卫视", "Shenzhen.png", "shenzhen", "深圳卫视", "深圳台", "深圳卫视高清");
        registerSatellite("安徽卫视", "Anhui.png", "anhui", "安徽卫视", "安徽台", "海豚台");
        registerSatellite("山东卫视", "Shandong.png", "shandong", "山东卫视", "山东台");
        registerSatellite("河南卫视", "Henan.png", "henan", "河南卫视", "河南台");
        registerSatellite("湖北卫视", "Hubei.png", "hubei", "湖北卫视", "湖北台");
        registerSatellite("四川卫视", "Sichuan.png", "sichuan", "四川卫视", "四川台");
        registerSatellite("重庆卫视", "Chongqing.png", "chongqing", "重庆卫视", "重庆台");
        registerSatellite("辽宁卫视", "Liaoning.png", "liaoning", "辽宁卫视", "辽宁台");
        registerSatellite("天津卫视", "Tianjin.png", "tianjin", "天津卫视", "天津台");
        registerSatellite("黑龙江卫视", "Heilongjiang.png", "heilongjiang", "黑龙江卫视", "黑龙江台");
        registerSatellite("吉林卫视", "Jilin.png", "jilin", "吉林卫视", "吉林台");
        registerSatellite("河北卫视", "Hebei.png", "hebei", "河北卫视", "河北台");
        registerSatellite("江西卫视", "Jiangxi.png", "jiangxi", "江西卫视", "江西台");
        registerSatellite("东南卫视", "Dongnan.png", "dongnan", "东南卫视", "福建东南卫视", "福建卫视");
        registerSatellite("广西卫视", "Guangxi.png", "guangxi", "广西卫视", "广西台");
        registerSatellite("海南卫视", "Hainan.png", "hainan", "海南卫视", "旅游卫视");
        registerSatellite("贵州卫视", "Guizhou.png", "guizhou", "贵州卫视", "贵州台");
        registerSatellite("云南卫视", "Yunnan.png", "yunnan", "云南卫视", "云南台");
        registerSatellite("陕西卫视", "Shaanxi.png", "shaanxi", "陕西卫视", "陕西台");
        registerSatellite("甘肃卫视", "Gansu.png", "gansu", "甘肃卫视", "甘肃台");
        registerSatellite("青海卫视", "Qinghai.png", "qinghai", "青海卫视", "青海台");
        registerSatellite("宁夏卫视", "Ningxia.png", "ningxia", "宁夏卫视", "宁夏台");
        registerSatellite("新疆卫视", "Xinjiang.png", "xinjiang", "新疆卫视", "新疆台");
        registerSatellite("西藏卫视", "Xizang.png", "xizang", "西藏卫视", "西藏台");
        registerSatellite("内蒙古卫视", "Neimenggu.png", "neimenggu", "内蒙古卫视", "内蒙卫视");
        registerSatellite("兵团卫视", "Bingtuan.png", "bingtuan", "兵团卫视");
        registerSatellite("三沙卫视", "Sansha.png", "sansha", "三沙卫视");
        registerSatellite("大湾区卫视", "GBA.png", "dawanqv", "大湾区卫视", "南方卫视");

        // 3. 港澳台与精选特色台
        registerChannel("凤凰卫视中文台", "凤凰中文", "PhoenixChinese.png", "特色频道", "凤凰中文", "凤凰卫视", "凤凰卫视中文台", "凤凰中文台");
        registerChannel("凤凰卫视资讯台", "凤凰资讯", "PhoenixInfo.png", "特色频道", "凤凰资讯", "凤凰卫视资讯台", "凤凰资讯台");
        registerChannel("凤凰卫视香港台", "凤凰香港", "PhoenixHongKong.png", "特色频道", "凤凰香港", "凤凰卫视香港台");
        registerChannel("TVB 翡翠台", "翡翠台", "Jade.png", "特色频道", "tvb", "翡翠台", "tvb翡翠台", "香港翡翠台");
        registerChannel("TVB 明珠台", "明珠台", "Pearl.png", "特色频道", "明珠台", "tvb明珠台");
        registerChannel("澳视澳门", "澳视澳门", "TDM.png", "特色频道", "澳视澳门", "澳门卫视", "tdm");
        registerChannel("CHC 高清电影", "CHC高清电影", "CHCMovie.png", "影视轮播", "chc", "chc电影", "chc高清电影");
        registerChannel("CHC 动作电影", "CHC动作电影", "CHCAction.png", "影视轮播", "chc动作电影", "chc动作");
        registerChannel("CHC 家庭影院", "CHC家庭影院", "CHCHome.png", "影视轮播", "chc家庭影院", "chc家庭");
    }

    private static void registerCctv(String num, String standardName, String logoFile, String group, String... aliases) {
        ChannelMeta meta = new ChannelMeta("CCTV-" + num, standardName, logoFile, group);
        for (String a : aliases) {
            ALIAS_MAP.put(cleanKey(a), meta);
        }
    }

    private static void registerSatellite(String name, String logoFile, String... aliases) {
        ChannelMeta meta = new ChannelMeta(name, name, logoFile, "卫视频道");
        ALIAS_MAP.put(cleanKey(name), meta);
        for (String a : aliases) {
            ALIAS_MAP.put(cleanKey(a), meta);
        }
    }

    private static void registerChannel(String canonicalName, String standardEpgName, String logoFile, String group, String... aliases) {
        ChannelMeta meta = new ChannelMeta(canonicalName, standardEpgName, logoFile, group);
        ALIAS_MAP.put(cleanKey(canonicalName), meta);
        for (String a : aliases) {
            ALIAS_MAP.put(cleanKey(a), meta);
        }
    }

    /**
     * 关键预处理算法：剔除分辨率、运营商、协议、标点等杂质
     */
    public static String cleanKey(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.getDefault());

        // 剔除中英文括号及括号内的修饰词
        s = s.replaceAll("[\\[\\(（【].*?[\\]\\)）】]", "");

        // 剔除杂散符号
        s = s.replaceAll("[\\s\\-_·/|#*+]+", "");

        // 剔除常见清晰度与格式后缀
        s = s.replace("1080p", "")
                .replace("720p", "")
                .replace("2160p", "")
                .replace("50fps", "")
                .replace("60fps", "")
                .replace("4k", "")
                .replace("8k", "")
                .replace("hd", "")
                .replace("fhd", "")
                .replace("uhd", "")
                .replace("hevc", "")
                .replace("h265", "")
                .replace("h264", "")
                .replace("高清", "")
                .replace("超清", "")
                .replace("标清", "")
                .replace("全高清", "")
                .replace("蓝光", "")
                .replace("杜比", "");

        // 剔除常见运营商与网络标签
        s = s.replace("ipv6", "")
                .replace("电信", "")
                .replace("联通", "")
                .replace("移动", "")
                .replace("广电", "")
                .replace("专线", "")
                .replace("备用", "")
                .replace("线路1", "")
                .replace("线路2", "")
                .replace("线路3", "")
                .replace("源1", "")
                .replace("源2", "")
                .replace("频道", "")
                .replace("电视台", "")
                .replace("台", "");

        return s.trim();
    }

    /**
     * 智能台标匹配：若当前无有效台标或台标损坏，自动补齐官方矢量/超清台标
     */
    public static String matchLogoUrl(String rawChannelName, String existingLogo) {
        if (existingLogo != null && !existingLogo.trim().isEmpty() && existingLogo.startsWith("http") && !existingLogo.contains("null")) {
            return existingLogo; // 保留源自带且合法的台标
        }

        String key = cleanKey(rawChannelName);
        if (ALIAS_MAP.containsKey(key)) {
            return ALIAS_MAP.get(key).getLogoUrl();
        }

        // 针对部分带 "cctv" 前缀的数字进行智能通配
        if (key.startsWith("cctv")) {
            String num = key.replace("cctv", "");
            if (num.matches("\\d+")) {
                return LOGO_CDN_BASE + "CCTV" + num + ".png";
            }
        }

        // 兜底：采用 fanmingming 标准命名规则
        return LOGO_CDN_BASE + rawChannelName.replaceAll("[\\[\\]\\s\\-_]", "").replace("高清", "").replace("超清", "") + ".png";
    }

    /**
     * 智能 EPG 查询标准名称匹配
     */
    public static String matchStandardEpgName(String rawChannelName) {
        String key = cleanKey(rawChannelName);
        if (ALIAS_MAP.containsKey(key)) {
            return ALIAS_MAP.get(key).standardEpgName;
        }
        return rawChannelName.replaceAll("[\\[\\]\\s\\-_（）()]", "")
                .replace("高清", "")
                .replace("超清", "")
                .replace("1080P", "")
                .replace("4K", "")
                .trim();
    }

    /**
     * 对 Channel 实体进行智能属性增强（补齐台标、标准化分组、优化展示名）
     */
    public static void enrichChannel(Channel channel) {
        if (channel == null) return;
        String rawName = channel.getName();
        String key = cleanKey(rawName);

        if (ALIAS_MAP.containsKey(key)) {
            ChannelMeta meta = ALIAS_MAP.get(key);
            if (channel.getLogoUrl() == null || channel.getLogoUrl().trim().isEmpty() || channel.getLogoUrl().contains("null")) {
                channel.setLogoUrl(meta.getLogoUrl());
            }
            if (channel.getGroup() == null || channel.getGroup().trim().isEmpty() || "默认频道".equals(channel.getGroup()) || "综合频道".equals(channel.getGroup())) {
                channel.setGroup(meta.defaultGroup);
            }
        } else {
            if (channel.getLogoUrl() == null || channel.getLogoUrl().trim().isEmpty()) {
                channel.setLogoUrl(matchLogoUrl(rawName, ""));
            }
        }
    }
}
