# CalorieWidget

以桌面小部件为主要入口的离线 Android 热量预算与摄入记录应用。Kotlin、原生 Views/XML、Room、Coroutines、AppWidget/RemoteViews、Canvas 图表，最低 Android 8.0（API 26）。无网络权限、无账号。

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
