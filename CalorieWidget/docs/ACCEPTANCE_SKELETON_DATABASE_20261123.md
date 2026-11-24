# 独立验收：项目骨架与数据库

隐私处理说明：本报告中的机器路径、账号包名和环境指纹已脱敏；历史测试结论保留，显示的命令与模型字段为脱敏版本。原始日志仅保存在被 Git 忽略的本地证据目录。
验收日期：2026-11-23。被验收源码：当次项目骨架与数据库验收前的版本，具体源码引用仅保存在本地验收材料中。

## 结论

**项目骨架、干净编译、Android 导入模型和数据库测试通过。Android Studio 图形界面中的打开与 Gradle Sync 未实测。**

本轮未修改应用业务代码。环境未安装 Android Studio，且没有 `DISPLAY`，不能将命令行结果表述为已在 Android Studio 窗口中打开并同步成功。

## 独立方法

从指定 Git 提交通过 `git archive` 导出干净源码，在独立目录构建：

```text
.tools/acceptance/<run-id>/source/CalorieWidget/
```

导出时不存在 `app/build/`，未复制原工程的 `local.properties` 或生成代码。复用工作区已安装的 SDK 和已下载依赖，使用 `--offline --no-build-cache --no-configuration-cache`；这不是冷依赖下载测试。

JDK：JDK 21；Gradle 8.9；AGP 8.7.3；Kotlin 2.0.21；Room 2.6.1。SDK、Gradle 用户缓存、Android 用户配置与临时目录全部位于仓库 `.tools/` 下。

## 检查结果

| 验收项 | 结果与证据 |
|---|---|
| 标准工程骨架 | `settings.gradle.kts`、顶层/app Kotlin DSL、Wrapper 脚本/JAR、Manifest 齐全，`gradlew` 可执行 |
| Gradle 项目识别 | `projects` 识别根工程 CalorieWidget 和 `:app` |
| Android Studio 导入基础 | 使用 Gradle Tooling API 实际读取 AGP v2 `Versions`、`BasicAndroidProject`、`AndroidDsl`、`ProjectSyncIssues`，成功且 Sync Issue 为 0 |
| 模块与 SDK | 类型 APPLICATION，变体 debug/release，compileSdk 35、minSdk 26、targetSdk 35、Build Tools 35.0.0；Manifest 和 android.jar 路径有效 |
| 全新编译 | `assembleDebug` 成功；Kotlin、Kapt/Room、资源和 Manifest 编译完成 |
| XML | 24 个 XML 全部解析成功 |
| Room Schema | version 2；三张表 intake_records、daily_budgets、app_settings；独立构建生成的 schema 与 Git 中 schema 相同 |
| 数据库运行测试 | 7 个测试，0 失败、0 错误，覆盖建库、增删改/跨日期移动、负余额、并发幂等、预算快照、日期检查、事务回滚、文件持久化重开和 1→2 迁移 |
| Android Studio GUI | 未验证：本环境没有 Android Studio 和图形显示 |

实际构建命令，在独立导出的工程目录执行：

```bash
./gradlew --no-daemon --console=plain --offline \
  --no-build-cache --no-configuration-cache \
  projects clean :app:assembleDebug :app:testDebugUnitTest \
  --tests 'com.example.caloriewidget.RepositoryTest' \
  --tests 'com.example.caloriewidget.MigrationTest'
```

结果：`BUILD SUCCESSFUL in 43s`，51 个任务中 50 个执行、1 个 up-to-date。仍有 Kapt 语言模式回退与测试注解处理参数的非阻断提示。

## Android 导入模型

本轮探针源码：[acceptance/GradleModelProbe.java](acceptance/GradleModelProbe.java)。使用 Gradle 8.9 Tooling API 与 AGP 8.7.3 builder-model 调用，模型结果为：

```text
root=CalorieWidget
module=:app
projectType=APPLICATION
agp=8.7.3
compileTarget=android-35
buildTools=35.0.0
applicationId=com.example.caloriewidget
minSdk=26
targetSdk=35
variants=[debug, release]
manifest=<WORKSPACE>/.tools/acceptance/<run-id>/source/CalorieWidget/app/src/main/AndroidManifest.xml
bootClasspath=[<ANDROID_SDK>/platforms/android-35/android.jar]
syncErrors=0
syncIssues=0
```

首次探针调用缺少隔离 Action 的 Kotlin enum 运行时，补齐探针的序列化类路径后成功；应用源码和 Gradle 配置无需调整。这验证了 AGP 导入模型可读取，不等同于完整 Android Studio GUI Sync 验收。

## 产物与日志

独立构建 APK：`artifacts/CalorieWidget-acceptance-debug.apk`，6251422 字节。

SHA-256：`e4509686f513dba13b8ef51065944a5a17320831dddc212f036fce89e24f6c61`。

本地完整证据目录：`.tools/acceptance/<run-id>/`，包含原始 Git 导出、构建日志、JUnit XML、模型输出、探针源码及检查摘要。`.tools/` 和 APK 被 Git 忽略。

## Android Studio 人工补验

1. 用 Android Studio Ladybug 2024.2.1 或更新版本打开仓库中的 **CalorieWidget/**，不是外层 CalorieBudget/。
2. 配置 JDK 17/21、SDK Platform 35 和 Build Tools 35.0.0。
3. 执行 Gradle Sync；确认显示 `:app` 与 debug/release 变体，没有同步错误。
4. 执行 Build → Make Project 或运行 `assembleDebug`。

以上 GUI 操作在本轮均未声称已执行；本报告也不覆盖真机安装、HyperOS 桌面和后台闹钟验收。
