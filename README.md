# CalorieWidget

以桌面小部件为主要入口的离线 Android 热量预算与摄入记录应用。Kotlin、原生 Views/XML、Room、Coroutines、AppWidget/RemoteViews、Canvas 图表，最低 Android 8.0（API 26）。无网络权限、无账号。

**完整 Android Studio 工程位于 [`CalorieWidget/`](CalorieWidget/README.md)**。请打开该目录，而非仓库外层。详细安装、SDK、构建、HyperOS 小部件和后台设置说明见工程 README。

```bash
cd CalorieWidget
./gradlew assembleDebug
./gradlew test
```

APK：`CalorieWidget/app/build/outputs/apk/debug/app-debug.apk`。

本工作区提供 `scripts/check.sh`，将 SDK、缓存、临时目录和 Android 用户配置限定在仓库的 `.tools/` 下。首次准备环境可运行 `scripts/bootstrap-sdk.sh`，然后执行 `scripts/check.sh test assembleDebug lintDebug`。这些工具及构建产物不会提交到 Git。

隐私检查：`python3 scripts/audit_privacy.py` 检查当前提交，`python3 scripts/audit_privacy.py --history` 检查可达历史；[隐私验收说明](CalorieWidget/docs/ACCEPTANCE_PRIVACY_20261123.md)区分当前文件清理与历史记录清理。

按项目维护者的验收要求，GitHub 用户名和仓库链接允许保留；个人邮箱、本机路径、主机名和设备标识仍须检查。
