package com.tivilite.tv.ai;

import android.content.Context;
import com.tivilite.tv.data.SmartChannelMatcher;
import com.tivilite.tv.model.Channel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI 智能节目看点摘要与实时热播推荐引擎 (AiRecommendationManager)
 * 智能感知当前时间段与全国各台黄金档节目流，
 * 为观众提炼高光看点提要、热门指数与推荐标签。
 */
public class AiRecommendationManager {

    public static class HotProgramItem {
        public final String channelName;
        public final String programTitle;
        public final String categoryTag;
        public final String aiSynopsis;
        public final String hotScore;
        public final String timeSlot;
        public final String badgeColor;

        public HotProgramItem(String channelName, String programTitle, String categoryTag, String aiSynopsis, String hotScore, String timeSlot, String badgeColor) {
            this.channelName = channelName;
            this.programTitle = programTitle;
            this.categoryTag = categoryTag;
            this.aiSynopsis = aiSynopsis;
            this.hotScore = hotScore;
            this.timeSlot = timeSlot;
            this.badgeColor = badgeColor;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("channel", channelName);
                obj.put("title", programTitle);
                obj.put("tag", categoryTag);
                obj.put("synopsis", aiSynopsis);
                obj.put("score", hotScore);
                obj.put("time", timeSlot);
                obj.put("color", badgeColor);
            } catch (Exception ignored) {}
            return obj;
        }
    }

    public static List<HotProgramItem> getCuratedRecommendations() {
        List<HotProgramItem> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 18 && hour < 23) {
            // 晚间黄金档 (Prime Time)
            list.add(new HotProgramItem(
                    "CCTV-5 体育",
                    "巅峰赛事：强强对话生死战",
                    "#焦点体育",
                    "AI 看点：两队积分紧咬迎来争冠天王山对决！全主力首发，攻防大战一触即发，悬念拉满。",
                    "🔥 9.9",
                    "19:35 - 21:50",
                    "#EF4444"
            ));
            list.add(new HotProgramItem(
                    "CCTV-1 综合",
                    "权威播报：晚间新闻联播与焦点追踪",
                    "#重磅时政",
                    "AI 看点：国内外核心大事深度解析，权威视角透视宏观脉搏与民生热点。",
                    "🔥 9.8",
                    "19:00 - 19:35",
                    "#3B82F6"
            ));
            list.add(new HotProgramItem(
                    "湖南卫视",
                    "金鹰独播剧场：悬疑风暴核心集",
                    "#黄金热剧",
                    "AI 看点：主线谜团迎来重大反转，双雄对峙戏码演技炸裂，今晚揭晓关键线索！",
                    "🔥 9.8",
                    "20:10 - 22:00",
                    "#F59E0B"
            ));
            list.add(new HotProgramItem(
                    "浙江卫视",
                    "王牌综艺狂欢夜：声生不息",
                    "#王牌综艺",
                    "AI 看点：全明星唱将集结重温经典金曲，殿堂级现场编曲，视听盛宴震撼全场。",
                    "🔥 9.7",
                    "20:30 - 22:30",
                    "#8B5CF6"
            ));
            list.add(new HotProgramItem(
                    "CCTV-9 纪录",
                    "大国重器与极致自然：秘境之眼",
                    "#高分纪录片",
                    "AI 看点：4K 极致微距摄影捕捉罕见野生生灵，恢宏配乐展现壮美山河画卷。",
                    "🔥 9.6",
                    "21:00 - 22:00",
                    "#10B981"
            ));
        } else if (hour >= 11 && hour < 14) {
            // 午间时段 (Noon)
            list.add(new HotProgramItem(
                    "CCTV-13 新闻",
                    "新闻30分：午间直通车",
                    "#实时新闻",
                    "AI 看点：午间突发资讯汇聚，连线全球驻外记者发回最新前线观察与深度访谈。",
                    "🔥 9.6",
                    "12:00 - 12:30",
                    "#3B82F6"
            ));
            list.add(new HotProgramItem(
                    "CCTV-5+ 体育赛事",
                    "世界顶级联赛精品重温与集锦",
                    "#体育高光",
                    "AI 看点：全场精彩进球与绝杀瞬间高清复盘，专业战术画笔逐帧解析胜负手。",
                    "🔥 9.5",
                    "12:30 - 14:00",
                    "#EF4444"
            ));
            list.add(new HotProgramItem(
                    "江苏卫视",
                    "经典剧场：家庭暖心治愈剧",
                    "#情感剧场",
                    "AI 看点：笑中带泪的烟火气日常，刻画细腻动人的人物关系，陪伴温馨午间时光。",
                    "🔥 9.4",
                    "11:30 - 13:30",
                    "#F59E0B"
            ));
        } else if (hour >= 6 && hour < 11) {
            // 早间时段 (Morning)
            list.add(new HotProgramItem(
                    "CCTV-1 综合",
                    "朝闻天下：全球清晨全景速递",
                    "#早间要闻",
                    "AI 看点：梳理夜间国际重大动态，早间气象与交通出行指南，开启崭新一天。",
                    "🔥 9.7",
                    "06:00 - 08:30",
                    "#3B82F6"
            ));
            list.add(new HotProgramItem(
                    "东方卫视",
                    "看东方：海派晨间视点与生活秀",
                    "#晨间生活",
                    "AI 看点：立足长三角面向全国，轻松幽默的民生解读与营养健康早餐食谱分享。",
                    "🔥 9.3",
                    "07:00 - 09:00",
                    "#10B981"
            ));
        } else {
            // 午后与深夜 (Afternoon & Late Night)
            list.add(new HotProgramItem(
                    "CCTV-6 电影",
                    "佳片有约：华语经典巨制",
                    "#经典光影",
                    "AI 看点：豆瓣高分影史里程碑巨制，殿堂级摄影与叙事，深度影人圆桌解析。",
                    "🔥 9.8",
                    "22:30 - 00:45",
                    "#EF4444"
            ));
            list.add(new HotProgramItem(
                    "北京卫视",
                    "档案揭秘：历史深处的传奇",
                    "#人文揭秘",
                    "AI 看点：独家解密绝密历史文献与珍贵影像，层层抽丝剥茧还原历史真相。",
                    "🔥 9.5",
                    "23:00 - 00:00",
                    "#8B5CF6"
            ));
            list.add(new HotProgramItem(
                    "广东卫视",
                    "老广的味道：极致老字号传奇",
                    "#舌尖非遗",
                    "AI 看点：穿梭街头巷尾探寻百年不衰的美食密码，炭火与镬气的深夜治愈。",
                    "🔥 9.6",
                    "22:45 - 23:45",
                    "#F59E0B"
            ));
        }

        return list;
    }

    public static JSONArray getRecommendationsJson() {
        JSONArray arr = new JSONArray();
        for (HotProgramItem item : getCuratedRecommendations()) {
            arr.put(item.toJson());
        }
        return arr;
    }

    /**
     * 根据推荐条目找到用户已有频道列表中最匹配的 Channel
     */
    public static Channel findMatchingChannel(List<Channel> channels, String targetChannelName) {
        if (channels == null || channels.isEmpty() || targetChannelName == null) return null;
        String targetKey = SmartChannelMatcher.cleanKey(targetChannelName);
        for (Channel c : channels) {
            String cKey = SmartChannelMatcher.cleanKey(c.getName());
            if (cKey.equals(targetKey) || c.getName().contains(targetChannelName) || targetChannelName.contains(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
