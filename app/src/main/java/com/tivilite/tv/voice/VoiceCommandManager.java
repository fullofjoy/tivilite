package com.tivilite.tv.voice;

import com.tivilite.tv.data.SmartChannelMatcher;
import com.tivilite.tv.model.Channel;
import java.util.List;
import java.util.Locale;

/**
 * 电视语音指令与自然语言换台引擎 (VoiceCommandManager)
 * 适配蓝牙遥控器麦克风按键 (KEYCODE_SEARCH / KEYCODE_VOICE_ASSIST)
 * 以及手机端 Web 虚拟语音助手，实现零学习门槛语音换台与大屏操作。
 */
public class VoiceCommandManager {

    public enum Action {
        SWITCH_CHANNEL,
        VOLUME_UP,
        VOLUME_DOWN,
        VOLUME_MUTE,
        ASPECT_RATIO,
        PICTURE_MODE,
        AUDIO_MODE,
        RECORD_TOGGLE,
        PIP_TOGGLE,
        PIP_SWAP,
        PIP_AUDIO,
        SUBTITLE_TOGGLE,
        FAILOVER,
        SHOW_REMINDERS,
        SHOW_RECOMMENDATIONS,
        QOS_RADAR,
        AUTO_FAILOVER,
        EXIT_CAST,
        THEME_TOGGLE,
        FONT_SCALE_TOGGLE,
        SCAN_NAS,
        BONDING_MODE_TOGGLE,
        AI_SUBTITLE_TOGGLE,
        WEBRTC_SYNC_TOGGLE,
        CLOUD_MESH_DIALOG,
        DOLBY_MODE_TOGGLE,
        P2P_MODE_TOGGLE,
        AI_ASSISTANT_PROMPT,
        ORIENTATION_TOGGLE,
        PIP_ENTER,
        CELLULAR_GUARD_TOGGLE,
        UNKNOWN
    }

    public static class VoiceResult {
        public final Action action;
        public final Channel targetChannel;
        public final int targetNumber;
        public final String feedbackText;

        public VoiceResult(Action action, Channel targetChannel, int targetNumber, String feedbackText) {
            this.action = action;
            this.targetChannel = targetChannel;
            this.targetNumber = targetNumber;
            this.feedbackText = feedbackText;
        }
    }

    public static VoiceResult parse(String rawInput, List<Channel> channels) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new VoiceResult(Action.UNKNOWN, null, 0, "未能识别语音内容");
        }

        String input = rawInput.trim().toLowerCase(Locale.getDefault())
                .replace("，", "").replace("。", "").replace("！", "").replace("？", "");

        // 1. 音量控制
        if (input.contains("大声") || input.contains("加大音量") || input.contains("调大音量") || input.contains("声音大") || input.contains("增加音量") || input.contains("大点声")) {
            return new VoiceResult(Action.VOLUME_UP, null, 0, "🔊 已为您调大音量");
        }
        if (input.contains("小声") || input.contains("减小音量") || input.contains("调小音量") || input.contains("声音小") || input.contains("小点声") || input.contains("降低音量")) {
            return new VoiceResult(Action.VOLUME_DOWN, null, 0, "🔉 已为您调小音量");
        }
        if (input.contains("静音") || input.contains("别出声") || input.contains("闭嘴") || input.contains("取消静音")) {
            return new VoiceResult(Action.VOLUME_MUTE, null, 0, "🔇 已切换静音状态");
        }

        // 2. Phase 7 图像画质模式
        if (input.contains("画质") || input.contains("图像模式") || input.contains("影院模式") || input.contains("鲜艳模式") || input.contains("体育模式") || input.contains("标准画质")) {
            return new VoiceResult(Action.PICTURE_MODE, null, 0, "🎛️ 已切换图像画质模式");
        }

        // 3. Phase 7 音频直通与影院音效
        if (input.contains("源码直通") || input.contains("音频直通") || input.contains("杜比") || input.contains("防吵") || input.contains("夜间模式") || input.contains("立体声模式")) {
            return new VoiceResult(Action.AUDIO_MODE, null, 0, "🔊 已切换音频源码直通模式");
        }

        // 4. Phase 7 预约提醒
        if (input.contains("预约") || input.contains("我的预约") || input.contains("查看预约") || input.contains("有哪些预约")) {
            return new VoiceResult(Action.SHOW_REMINDERS, null, 0, "📅 正在为您打开节目预约管家");
        }

        // 5. AI 热播看点榜
        if (input.contains("热播") || input.contains("看点") || input.contains("推荐") || input.contains("今天看什么") || input.contains("有什么好看的")) {
            return new VoiceResult(Action.SHOW_RECOMMENDATIONS, null, 0, "🤖 正在为您打开 AI 实时热播看点榜");
        }

        // 6. 录像控制
        if (input.contains("录制") || input.contains("录像") || input.contains("开始录制") || input.contains("录下来")) {
            return new VoiceResult(Action.RECORD_TOGGLE, null, 0, "🔴 已触发电视实时录制");
        }

        // 7. 画中画与多视口控制
        if (input.contains("主副对调") || input.contains("交换画面") || input.contains("切换主副") || input.contains("画面对调")) {
            return new VoiceResult(Action.PIP_SWAP, null, 0, "🔄 主副画面已即时对调");
        }
        if (input.contains("切换声音") || input.contains("副画面声音") || input.contains("副画声音") || input.contains("小窗声音")) {
            return new VoiceResult(Action.PIP_AUDIO, null, 0, "🔊 已切换主副音频焦点");
        }
        if (input.contains("画中画") || input.contains("小窗") || input.contains("同屏") || input.contains("双流") || input.contains("双视口") || input.contains("分屏")) {
            return new VoiceResult(Action.PIP_TOGGLE, null, 0, "🖼️ 已触发画中画同屏切换");
        }

        // 8. 字幕与画面比例
        if (input.contains("字幕") || input.contains("开字幕") || input.contains("关字幕") || input.contains("无障碍")) {
            return new VoiceResult(Action.SUBTITLE_TOGGLE, null, 0, "💬 已切换字幕显示状态");
        }
        if (input.contains("画面比例") || input.contains("比例") || input.contains("拉伸") || input.contains("全屏") || input.contains("自适应")) {
            return new VoiceResult(Action.ASPECT_RATIO, null, 0, "📐 已切换画面显示比例");
        }

        // 9. 换线路 / 测速
        if (input.contains("卡顿") || input.contains("换线路") || input.contains("换一条") || input.contains("备用源") || input.contains("测速")) {
            return new VoiceResult(Action.FAILOVER, null, 0, "⚡ 已为您优选切换最佳可用线路");
        }

        // 10. Phase 8 极客网络雷达
        if (input.contains("网络雷达") || input.contains("极客雷达") || input.contains("网速监控") || input.contains("监控面板") || input.contains("雷达")) {
            return new VoiceResult(Action.QOS_RADAR, null, 0, "📊 已切换极客网络与画质雷达");
        }

        // 11. Phase 8 多源智能容灾
        if (input.contains("自动容灾") || input.contains("智能容灾") || input.contains("自动换源") || input.contains("智能切线")) {
            return new VoiceResult(Action.AUTO_FAILOVER, null, 0, "🛡️ 已切换多源智能自动容灾模式");
        }

        // 12. Phase 8 DLNA 投屏退出
        if (input.contains("退出投屏") || input.contains("停止投屏") || input.contains("关掉投屏") || input.contains("返回电视")) {
            return new VoiceResult(Action.EXIT_CAST, null, 0, "📲 已退出无线投屏，返回直播频道");
        }

        // 13. Phase 9 主题与大字号模式
        if (input.contains("主题") || input.contains("皮肤") || input.contains("毛玻璃") || input.contains("暗黑") || input.contains("长辈模式") || input.contains("切换主题")) {
            return new VoiceResult(Action.THEME_TOGGLE, null, 0, "🎨 已切换大屏视觉主题");
        }
        if (input.contains("字号") || input.contains("字体大小") || input.contains("大字") || input.contains("字体大点") || input.contains("小字")) {
            return new VoiceResult(Action.FONT_SCALE_TOGGLE, null, 0, "🔤 已切换界面字体显示尺寸");
        }

        // 14. Phase 9 私有云影视 NAS 扫描
        if (input.contains("扫描电影") || input.contains("家庭影库") || input.contains("本地视频") || input.contains("打开nas") || input.contains("影视库")) {
            return new VoiceResult(Action.SCAN_NAS, null, 0, "📁 正在为您扫描家庭私有云与本地影视库");
        }

        // 15. Phase 9 双网聚合与竞速模式
        if (input.contains("双网聚合") || input.contains("双网") || input.contains("网络聚合") || input.contains("竞速拉流") || input.contains("网卡模式")) {
            return new VoiceResult(Action.BONDING_MODE_TOGGLE, null, 0, "🌐 已切换双网卡聚合与竞速模式");
        }

        // 16. Phase 10 AI 实时字幕与双语同传
        if (input.contains("ai字幕") || input.contains("双语字幕") || input.contains("同传") || input.contains("实时字幕") || input.contains("翻译字幕")) {
            return new VoiceResult(Action.AI_SUBTITLE_TOGGLE, null, 0, "🤖 已切换 AI 实时同传双语字幕");
        }

        // 17. Phase 10 极低延迟赛事同屏同步
        if (input.contains("极速同步") || input.contains("赛事同步") || input.contains("低延迟") || input.contains("同屏") || input.contains("声音对齐")) {
            return new VoiceResult(Action.WEBRTC_SYNC_TOGGLE, null, 0, "⚡ 已切换极低延迟与多屏同步模式");
        }

        // 18. Phase 10 局域网设备自组网与流转接力
        if (input.contains("流转接力") || input.contains("设备网格") || input.contains("自组网") || input.contains("多房电视") || input.contains("卧室接力")) {
            return new VoiceResult(Action.CLOUD_MESH_DIALOG, null, 0, "☁️ 正在为您打开局域网设备自组网与流转接力");
        }

        // 19. Phase 11 蓝光原盘与杜比视界色彩映射
        if (input.contains("杜比") || input.contains("dolby") || input.contains("原盘") || input.contains("蓝光") || input.contains("色彩映射") || input.contains("hdr10")) {
            return new VoiceResult(Action.DOLBY_MODE_TOGGLE, null, 0, "🎬 已切换蓝光原盘与杜比视界色彩映射模式");
        }

        // 20. Phase 11 P2P 分布式边缘加速网络
        if (input.contains("p2p") || input.contains("边缘加速") || input.contains("省流") || input.contains("对等加速") || input.contains("局域网加速")) {
            return new VoiceResult(Action.P2P_MODE_TOGGLE, null, 0, "⚡ 已切换 P2P 分布式边缘加速模式");
        }

        // 21. Phase 11 端侧本地大模型智能问答与检索
        if (input.contains("帮我找") || input.contains("推荐") || input.contains("我想看") || input.contains("搜电影") || input.contains("周星驰") || input.contains("科幻")) {
            return new VoiceResult(Action.AI_ASSISTANT_PROMPT, null, 0, "🎙️ 大屏 AI 智能助手正在深度检索分析...");
        }

        // 22. Stage 1 多端响应式: 屏幕旋转切换
        if (input.contains("旋转") || input.contains("横屏") || input.contains("竖屏") || input.contains("转屏")) {
            return new VoiceResult(Action.ORIENTATION_TOGGLE, null, 0, "🔄 正在为您切换屏幕方向");
        }

        // 23. Stage 1 多端响应式: 系统画中画
        if (input.contains("系统画中画") || input.contains("小窗") || input.contains("悬浮窗") || input.contains("后台小窗")) {
            return new VoiceResult(Action.PIP_ENTER, null, 0, "🪟 正在为您开启系统画中画播放");
        }

        // 24. Stage 1 多端响应式: 移动蜂窝流量防护
        if (input.contains("流量") || input.contains("蜂窝") || input.contains("省流保护")) {
            return new VoiceResult(Action.CELLULAR_GUARD_TOGGLE, null, 0, "📶 已切换移动蜂窝流量防护模式");
        }

        // 10. 数字切台 (如 "1台", "换到第12台")
        String numStr = input.replaceAll("[^0-9]", "");
        if (!numStr.isEmpty() && (input.contains("台") || input.contains("号") || input.length() <= 3)) {
            try {
                int targetNum = Integer.parseInt(numStr);
                if (channels != null) {
                    for (Channel c : channels) {
                        if (c.getNumber() == targetNum) {
                            return new VoiceResult(Action.SWITCH_CHANNEL, c, targetNum, "📺 正在为您切换到 " + c.getName());
                        }
                    }
                }
                return new VoiceResult(Action.SWITCH_CHANNEL, null, targetNum, "📺 正在为您切换到 " + targetNum + " 号频道");
            } catch (Exception ignored) {}
        }

        // 11. 频道名称模糊匹配与语义去噪
        String cleanQuery = input.replace("请", "")
                .replace("帮我", "")
                .replace("我要看", "")
                .replace("想看", "")
                .replace("打开", "")
                .replace("播放", "")
                .replace("切换到", "")
                .replace("换到", "")
                .replace("转到", "")
                .replace("调到", "")
                .replace("看", "")
                .trim();

        if (channels != null && !channels.isEmpty()) {
            String cleanKey = SmartChannelMatcher.cleanKey(cleanQuery);

            // 第一轮：精准匹配 cleanKey
            for (Channel c : channels) {
                String cKey = SmartChannelMatcher.cleanKey(c.getName());
                if (cKey.equals(cleanKey)) {
                    return new VoiceResult(Action.SWITCH_CHANNEL, c, c.getNumber(), "📺 正在为您播放 " + c.getName());
                }
            }

            // 第二轮：包含匹配
            for (Channel c : channels) {
                String cKey = SmartChannelMatcher.cleanKey(c.getName());
                if (cKey.contains(cleanKey) || cleanKey.contains(cKey)) {
                    return new VoiceResult(Action.SWITCH_CHANNEL, c, c.getNumber(), "📺 正在为您播放 " + c.getName());
                }
            }

            // 第三轮：原始名称包含匹配
            for (Channel c : channels) {
                if (c.getName().toLowerCase().contains(cleanQuery) || cleanQuery.contains(c.getName().toLowerCase())) {
                    return new VoiceResult(Action.SWITCH_CHANNEL, c, c.getNumber(), "📺 正在为您播放 " + c.getName());
                }
            }
        }

        return new VoiceResult(Action.UNKNOWN, null, 0, "未找到名为 \"" + rawInput + "\" 的频道");
    }
}
