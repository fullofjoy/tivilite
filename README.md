# TiviLite (灵动TV) - 专为大屏而生的 Android TV 纯净播放器

<p align="center">
  <img src="app/src/main/res/drawable/app_banner.xml" width="360" alt="TiviLite Banner"/>
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Box-blue.svg" alt="Platform"/></a>
  <a href="#"><img src="https://img.shields.io/badge/MinSDK-21%2B%20(Android%205.0%2B)-green.svg" alt="MinSDK"/></a>
  <a href="#"><img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-orange.svg" alt="Engine"/></a>
  <a href="#"><img src="https://img.shields.io/badge/License-GPLv3-red.svg" alt="License"/></a>
</p>

---

## 📺 项目简介

**TiviLite** 是一款对标行业标杆 TiviMate 的轻量、超高颜值的 Android TV / 电视盒子专用 IPTV 播放器。

针对国内电视市场“收费高、无微信支付、换台卡顿、无法遥控配源”等痛点，TiviLite 专注于**极致遥控交互体验**与**极致换台响应速度**。

---

## ⚡ 核心功能与版本对比 (Open-Core)

本项目遵循 **Open-Core（开源社区版 + 商业特权版）** 模式运作：

| 功能特性 | 社区开源版 (Community) | 商业尊享版 (Pro) |
| :--- | :---: | :---: |
| **超清硬解播放 (Media3 ExoPlayer)** | ✅ 4K/60fps、H.265 硬解、低延迟换台 | ✅ 同左 + 增强抗丢包预加载 |
| **经典 TiviMate 电视遥控手感** | ✅ 上下切台、左侧分类抽屉、毛玻璃 OSD | ✅ 同左 + 长按自定义按键映射 |
| **M3U / M3U8 订阅源解析** | ✅ 支持单个 M3U 文件或网络订阅 URL | ✅ **多订阅源聚合 + 自动去重测速优选** |
| **7 天时移无缝回看 (Catch-up)** | ❌ 提示升级 Pro | ✅ **全功能无缝 7 天时间轴回看与时移** |
| **局域网扫码一键推源** | ❌ 需遥控器或文件导入 | ✅ **手机浏览器扫码一键粘贴推送订阅** |
| **画中画 / 多画面同屏监控** | ❌ 不支持 | ✅ **四宫格 / 双画面同屏实时预览** |
| **无广告、无弹窗、开机即播** | ✅ 纯净中立空壳 | ✅ 纯净中立空壳 |

---

## 🎮 电视遥控器键位指南

```
                    [ ▲ 上键 ] 
                 快速向上切换频道
                        │
   [ ◄ 左键 ] ──────────┼────────── [ ► 右键 ]
滑出左侧分类与频道列表     │      滑出右侧 7 天时移回看
                        │
                    [ ▼ 下键 ]
                 快速向下切换频道

[ OK / 确认键 ] : 全屏时呼出半透明 OSD 信息条；菜单中确认选择
[ 菜单键 / Menu ] : 呼出局域网扫码配源 / 设置面板
[ 返回键 / Back ] : 关闭浮层；双击安全退出应用
```

---

## 🛠️ 技术架构

* **UI 框架**：Android 原生 Leanback + Material Components（专为 10 英尺客厅远距离交互调优，纯暗黑低蓝光配色）。
* **播放内核**：Google 官方 **Media3 (ExoPlayer 1.2+)**，专属调优参数：
  * `minBufferMs = 500ms`（秒级切台）；
  * `maxBufferMs = 2500ms`（防弱网内存膨胀）。
* **回看时移协议**：支持 `#EXTINF` 标签中的 `catchup="append"` 及 `catchup-source="?playseek={utc:YmdHMS}-{utcend:YmdHMS}"`。
* **局域网配源服务**：内置极简嵌入式 HTTP 服务（Port 8888），手机在同一 Wi-Fi 下免安装任何 App，扫码即推。

---

## 🚀 编译与运行

### 方式 1：Android Studio 本地构建
1. 克隆代码仓库：
   ```bash
   git clone https://github.com/your-username/tivilite.git
   ```
2. 使用 **Android Studio (Hedgehog 或更高版本)** 打开工程根目录；
3. 连接安卓电视、机顶盒或模拟器（开启 ADB 调试）；
4. 点击右上角 **Run ▶** 即可完成安装。

### 方式 2：GitHub Actions 自动构建 APK
仓库内已内置 `.github/workflows/build-apk.yml`。提交推送代码到 GitHub 仓库后，GitHub 云端 runner 会在 2 分钟内自动编译产出 `TiviLite-Debug.apk`，可在 **Actions -> Artifacts** 中直接下载。

---

## 👑 TiviLite Pro 尊享版支持

TiviLite 遵循开放核心模式，社区开源版已满足绝大多数家庭的高清流畅播放需求。

如需解锁以下高级特权：
* 🌟 **7 天全天候时移回看与切片点播**
* 🌟 **手机微信/浏览器扫码一键无线推源**
* 🌟 **多订阅源聚合与极速测速优选**

可在电视端点击 **Pro 尊享特权** 窗口获取专属设备码，联系作者进行永久授权买断（一次买断，终身使用，断网可用）。

---

## 📄 免责声明 (Disclaimer)

* TiviLite 是一款**纯粹的中立多媒体播放器**，软件内**不内置、不提供、不分发任何电视节目或音视频流地址**。
* 所有流媒体源均由最终使用者自行合法提供。开发者不对使用者配置的第三方流媒体内容的合法性承担任何连带责任。

---

## 📜 开源协议

本项目开源部分采用 [GNU General Public License v3.0 (GPLv3)](LICENSE) 协议发布。
