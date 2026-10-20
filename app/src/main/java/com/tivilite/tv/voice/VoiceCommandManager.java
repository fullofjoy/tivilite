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
        RECORD_TOGGLE,
        PIP_TOGGLE,
        PIP_SWAP,
        PIP_AUDIO,
        SUBTITLE_TOGGLE,
        FAILOVER,
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

        // 2. 录像控制
        if (input.contains("录制") || input.contains("录像") || input.contains("开始录制") || input.contains("录下来")) {
            return new VoiceResult(Action.RECORD_TOGGLE, null, 0, "🔴 已触发电视实时录制");
        }

        // 3. 画中画与多视口控制
        if (input.contains("主副对调") || input.contains("交换画面") || input.contains("切换主副") || input.contains("画面对调")) {
            return new VoiceResult(Action.PIP_SWAP, null, 0, "🔄 主副画面已即时对调");
        }
        if (input.contains("切换声音") || input.contains("副画面声音") || input.contains("副画声音") || input.contains("小窗声音")) {
            return new VoiceResult(Action.PIP_AUDIO, null, 0, "🔊 已切换主副音频焦点");
        }
        if (input.contains("画中画") || input.contains("小窗") || input.contains("同屏") || input.contains("双流") || input.contains("双视口") || input.contains("分屏")) {
            return new VoiceResult(Action.PIP_TOGGLE, null, 0, "🖼️ 已触发画中画同屏切换");
        }

        // 4. 字幕与画面比例
        if (input.contains("字幕") || input.contains("开字幕") || input.contains("关字幕") || input.contains("无障碍")) {
            return new VoiceResult(Action.SUBTITLE_TOGGLE, null, 0, "💬 已切换字幕显示状态");
        }
        if (input.contains("画面比例") || input.contains("比例") || input.contains("拉伸") || input.contains("全屏") || input.contains("自适应")) {
            return new VoiceResult(Action.ASPECT_RATIO, null, 0, "📐 已切换画面显示比例");
        }

        // 5. 换线路 / 测速
        if (input.contains("卡顿") || input.contains("换线路") || input.contains("换一条") || input.contains("备用源") || input.contains("测速")) {
            return new VoiceResult(Action.FAILOVER, null, 0, "⚡ 已为您优选切换最佳可用线路");
        }

        // 6. 数字切台 (如 "1台", "换到第12台")
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

        // 7. 频道名称模糊匹配与语义去噪
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
