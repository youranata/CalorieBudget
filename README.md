# CalorieWidget

CalorieWidget 是一个以 Android 桌面小部件为主要入口的本地热量预算应用。它用固定精度的 milli-kcal 保存数据，支持 kcal/kJ 输入、来源备注、每日预算快照、记录增删改、历史查询和 Canvas 统计图表。应用不请求网络权限，不需要账号或食物数据库。

## 已实现功能

- 默认每日预算 2000 kcal，可在 App 中修改；每个日期单独保存预算快照。
- kcal 与 kJ 共用同一个余额：`1 kcal = 4.184 kJ`。
- 每次摄入都保存为独立 Room 记录：原始数值、原始单位、统一 milli-kcal、备注、时间戳和本地日期。
- 允许 0 kcal 记录以区分“有记录但总量为零”和“没有记录”；拒绝负数及超出固定精度范围的输入。
- App 内可新增、编辑（包括日期和时间）、删除任意历史记录；跨日期编辑会重新归属日期。
- 4×2 以上尺寸的小部件：查看剩余 kcal/kJ、已摄入/预算，并从 kcal、kJ 或“记录”入口打开统一新增页面。
- 每日零点采用“每次读取/写入时日期校验 + AlarmManager”双重机制；设备重启、日期/时区改变后重新安排任务。没有精确闹钟权限时自动使用非精确的 idle alarm。
- 历史页按有记录的日期展示预算、摄入、剩余和记录数；统计页支持 7/30/90 天和全部历史，缺失日期不会被伪造为 0 kcal。

## 打开项目

1. 用 Android Studio Hedgehog (2023.1.1) 或更新版本打开本目录 `CalorieBudget`。
2. Android Studio 使用 JDK 17（Gradle/AGP 也支持当前的 JDK 21）。
3. 配置 Android SDK Platform 35 和 Build Tools 35.0.0，等待 Gradle Sync 完成。
4. 最低支持 Android 8.0（API 26），目标 API 35。

命令行构建前，如果 SDK 不在默认位置，在项目根目录创建 `local.properties`，例如：

```properties
sdk.dir=<ANDROID_SDK>
```

`local.properties` 已在 `.gitignore` 中忽略，不应提交。

## 构建和测试

Linux/macOS：

```bash
./gradlew test
./gradlew assembleDebug
```

Debug APK 输出为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

通过 ADB 安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

本项目包含固定精度换算、负数/超大输入和本地日期归属的 JVM 单元测试。没有连接 Android 设备时，无法在本机验证具体厂商桌面小部件渲染；源码构建与单元测试应以命令输出为准。

## 在小米 HyperOS 添加小部件

安装并首次打开应用后，在桌面长按空白处，选择“小部件/Widgets”，搜索 **CalorieWidget**，拖动到桌面并调整到至少 4×2。HyperOS 可能要求允许应用自启动或关闭电池优化，才能让零点广播更稳定；即使后台任务被延迟，应用和小部件每次读取数据都会重新检查本地日期，不依赖周期性刷新来维持余额正确性。

Android 12 及以上若系统允许精确闹钟，应用会使用 `setExactAndAllowWhileIdle`；否则自动降级为 `setAndAllowWhileIdle`。应用声明 `SCHEDULE_EXACT_ALARM`，不会因缺少权限阻止使用。

## 目录结构

```text
CalorieBudget/
├── app/src/main/java/com/youranata/caloriewidget/
│   ├── data/                 Room 数据库、实体、DAO、事务仓库
│   ├── domain/               固定精度热量计算和本地日期工具
│   ├── receiver/             零点、重启、日期/时区变化广播与闹钟调度
│   ├── widget/               AppWidgetProvider 和 RemoteViews 刷新
│   ├── MainActivity.kt       今日摘要、记录管理和预算设置
│   ├── InputActivity.kt      新增/编辑记录
│   ├── HistoryActivity.kt    按日期历史列表
│   ├── DayDetailActivity.kt  某天记录详情
│   ├── StatsActivity.kt      趋势和统计
│   └── StatsChartView.kt     无第三方依赖的 Canvas 图表
├── app/src/main/res/         XML 页面、Widget、主题、图标
├── app/src/test/             JVM 单元测试
├── app/build.gradle.kts      Android/Kotlin/Room 依赖
├── build.gradle.kts          顶层插件版本
├── settings.gradle.kts       Gradle 模块和仓库
└── gradlew                   Gradle Wrapper 入口
```

## 数据和一致性说明

`IntakeRecordEntity` 是摄入事实数据来源，`DailyBudgetEntity` 只保存日期预算快照。剩余热量始终由“当日预算 - 当日记录总和”计算，不保存一个会与记录脱节的独立余额。新增、编辑、删除和跨日期编辑都在 Room 事务中完成；小部件刷新只读取汇总并更新 RemoteViews，不会写入或重复扣减记录。

当前数据库版本为 1。后续修改实体时应增加 Room migration 并保留用户数据，不要使用 destructive migration。
