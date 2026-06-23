# SpeakOnScreen

[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-orange)](LICENSE)

## 📖 简介

**SpeakOnScreen** 是一款 Android 工具应用，**按下电源键亮屏时自动用语音播报当前时间、日期、农历和电量信息**。

无需解锁，无需点亮屏幕查看，**听**就知道了。专为以下场景设计：

- 视障人士辅助
- 老年人便捷报时
- 开车时快速获取时间
- 闲置 Android 设备改造为桌面语音时钟

---

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 亮屏自动播报 | 监听系统亮屏广播，瞬间语音反馈 |
| 完整时间信息 | 播报内容包括时分、星期、农历、电量 |
| 前台服务保活 | 通知栏常驻，降低被系统清理概率 |
| 开机自启动 | 设备重启后自动恢复服务 |
| 极简权限 | 仅需通知和开机自启权限，无隐私风险 |
| 本地运行 | 不联网，不上传任何数据 |

---

## 🛠️ 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin | 主要开发语言 |
| Android Service | 后台服务与前台通知 |
| BroadcastReceiver | 监听 `ACTION_SCREEN_ON` 亮屏广播 |
| TextToSpeech | Android 系统原生 TTS 语音合成 |
| tyme4kt | 农历日期转换 |
| WorkManager | 定期保活心跳 |

---

## 📦 安装使用

### 方式一：直接安装 APK

1. 下载 [Releases](https://github.com/你的用户名/SpeakOnScreen/releases) 中的 APK 文件
2. 在手机上安装（允许未知来源安装）
3. 打开 App，点击「启动服务」即可

### 方式二：从源码编译

```bash
git clone https://github.com/你的用户名/SpeakOnScreen.git
cd SpeakOnScreen
./gradlew assembleRelease
```

---

## ⚙️ 首次使用须知

为了获得最佳体验，建议你完成以下设置：

| 设置项 | 原因 |
|--------|------|
| 开启通知权限 | Android 13+ 必需，前台服务需要 |
| 开启自启动权限 | 确保手机重启后自动运行 |
| 关闭电池优化 | 防止后台服务被系统清理 |

> 不同手机设置路径可能不同，一般位于「设置 → 应用管理」或「设置 → 省电与电池」中。

---

## 📁 项目结构

```text
app/src/main/java/com/example/speakdateandtime/
├── PowerListenerService.kt    # 核心服务：监听亮屏、TTS播报
├── BootReceiver.kt            # 开机自启广播接收器
├── HeartbeatWorker.kt         # WorkManager 定期保活
├── MainActivity.kt            # 主界面（Compose UI）
└── ServiceHelper.kt           # 服务状态检查工具
```

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库
2. 创建你的功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📄 开源协议

本项目采用 **MIT License**，允许自由使用、修改和分发。

---

## 🙏 致谢

- [tyme4kt](https://github.com/6tail/tyme4kt) — 农历日期转换库
- [Android Developers](https://developer.android.com/) — 官方文档
