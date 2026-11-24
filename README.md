# CalorieWidget

以桌面小部件为主要入口的离线 Android 热量预算与摄入记录应用。采用 Kotlin、原生 Views/XML、Room、Coroutines、AppWidget/RemoteViews 和 Canvas 图表，最低支持 Android 8.0（API 26）。无网络权限、无账号。

当前版本：**2.1.1（versionCode 5）**。

**[点击下载 APK（Debug）](https://github.com/youranata/CalorieBudget/raw/refs/heads/main/CalorieWidget/downloads/CalorieWidget-debug.apk)**。下载后允许浏览器安装应用即可安装；若仓库为私有，需要先登录有访问权限的 GitHub 账号。

## 功能

- 桌面小部件显示剩余 kcal/kJ、已摄入热量与预算；点击余额查看今日记录，通过 `+ kcal` 或 `+ kJ` 快速打开录入页。
- 当天保留完整明细，可新增、编辑和删除，支持来源备注；过往仅保留每日目标与总摄入，归档后只能查看。
- 自定义每日预算，允许余额为负数；按本地日期开始新的一天，历史预算保留快照。
- 历史图表支持最近 7/30/90 天及全部历史，区分无记录日期与明确零摄入。
- App 与常规、紧凑小部件使用统一的低饱和度配色，支持浅色和深色主题。

沿用同一应用 ID 和签名的版本可覆盖升级并保留数据。历史归档仅保存每日目标与总摄入；从保留历史明细的旧版升级时，历史明细会先汇总再删除，当天记录保留。

## 构建与安装

**完整 Android Studio 工程位于 [`CalorieWidget/`](CalorieWidget/README.md)**，请打开该目录。SDK 配置、Gradle Sync、ADB 安装、HyperOS 小部件和后台设置说明见[工程 README](CalorieWidget/README.md)。

```bash
cd CalorieWidget
./gradlew assembleDebug
./gradlew test
```

APK 输出：`CalorieWidget/app/build/outputs/apk/debug/app-debug.apk`。

仓库外层的 `scripts/bootstrap-sdk.sh` 用于在 `.tools/` 中准备 Android SDK；`scripts/check.sh test assembleDebug lintDebug` 使用该环境执行测试和构建。SDK、缓存和构建目录不提交 Git；`CalorieWidget/downloads/` 保存供直接下载的 Debug APK。

## 许可证

本项目采用 **[Apache License 2.0](LICENSE)**，Copyright 2026 youranata。允许使用、修改、分发和商业使用，具体条件以协议原文为准。第三方组件保留各自许可，见 [NOTICE](NOTICE) 和[第三方声明](THIRD_PARTY_NOTICES.md)。
