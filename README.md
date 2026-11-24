# CalorieWidget

以桌面小部件为主要入口的离线 Android 热量预算与摄入记录应用。Kotlin、原生 Views/XML、Room、Coroutines、AppWidget/RemoteViews、Canvas 图表，最低 Android 8.0（API 26）。无网络权限、无账号。

本项目采用 **[Apache License 2.0](LICENSE)**，Copyright 2026 youranata。允许使用、修改、分发和商业使用，具体条件以协议原文为准；第三方组件保留各自许可，见 [NOTICE](NOTICE) 和 [第三方声明](THIRD_PARTY_NOTICES.md)。

当天保留完整明细并支持删改；过往仅保留每日目标和总摄入，归档后只能查看。升级会将已有历史明细汇总后删除，当天记录保留。[变更验收与独立审计](CalorieWidget/docs/ACCEPTANCE_READONLY_HISTORY_20261123.md)。

**[点击下载 APK（Debug）](https://github.com/youranata/CalorieBudget/raw/refs/heads/main/CalorieWidget/downloads/CalorieWidget-debug.apk)** · 支持 Android 8.0 及以上。下载后允许浏览器安装应用即可安装；若仓库为私有，需要先登录有访问权限的 GitHub 账号。

**完整 Android Studio 工程位于 [`CalorieWidget/`](CalorieWidget/README.md)**。请打开该目录，而非仓库外层。详细安装、SDK、构建、HyperOS 小部件和后台设置说明见工程 README。

```bash
cd CalorieWidget
./gradlew assembleDebug
./gradlew test
```

APK：`CalorieWidget/app/build/outputs/apk/debug/app-debug.apk`。

本工作区提供 `scripts/check.sh`，将 SDK、缓存、临时目录和 Android 用户配置限定在仓库的 `.tools/` 下。首次准备环境可运行 `scripts/bootstrap-sdk.sh`，然后执行 `scripts/check.sh test assembleDebug lintDebug`。SDK、缓存和构建目录不会提交到 Git；`CalorieWidget/downloads/` 仅保存供直接下载且经过隐私扫描的 Debug APK。

隐私检查：`python3 scripts/audit_privacy.py --allow-configured-commit-email` 检查当前提交，追加 `--history` 检查可达历史；需先在仓库本地 Git 配置中设置维护者允许公开的 `user.email`。[隐私验收说明](CalorieWidget/docs/ACCEPTANCE_PRIVACY_20261123.md)区分当前文件清理与历史记录清理。

按项目维护者的验收要求，GitHub 用户名、仓库链接和提交作者/提交者字段中的原真实邮箱允许公开；其余邮箱、本机路径、主机名和设备标识继续检查。

## 2.0 外观更新

App 与常规/紧凑桌面小部件采用统一的暖灰、深青绿配色、数字层级、圆角描边和浅深色样式。仅更新视觉资源与应用版本（2.0 / versionCode 3），既有文案、功能、当天明细及历史只读规则保持不变。

[2.0 视觉验收与独立审计](CalorieWidget/docs/ACCEPTANCE_VISUAL_2_0.md)。设计 skill 的下载文件、工具和原始截图仅保存在已忽略的 `.tools/` 或构建目录，不进入仓库。

## 外观细化

继续保持低饱和度，以雾蓝、鼠尾草绿、暖沙与灰紫建立层次。余额区使用柔和渐变及细线装饰，记录时间、操作按钮、历史提示和图表卡片共享同一套配色与间距；深色主题同步细化。既有文案、控件、功能和数据规则保持。

本轮版本为 **2.1（versionCode 4）**，沿用现有应用 ID、签名和数据库，可覆盖升级并保留当前数据。新版 APK 使用上方相同的下载链接。[2.1 视觉细化与内容保护验收](CalorieWidget/docs/ACCEPTANCE_VISUAL_2_1.md)。

## 2.1.1 小部件入口

小部件仅保留余额、`+ kcal` 和 `+ kJ` 三个入口。两个单位按钮从 28dp 增高到 48dp，录入页仍可填写来源备注。可覆盖升级并保留数据；上方下载链接提供新版 APK。[小部件入口验收](CalorieWidget/docs/ACCEPTANCE_WIDGET_2_1_1.md)。
