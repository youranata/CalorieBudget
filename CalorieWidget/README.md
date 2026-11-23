# CalorieWidget

CalorieWidget 是一个以桌面小部件为主要入口的本地热量预算应用。所有余额和图表从 Room 中的摄入记录及每日预算计算，不单独保存余额。无账号、无食物数据库、无网络权限；自动云备份关闭。

## 使用

- 今日页展示剩余 kcal/kJ、已摄入热量、预算和按时间排序的记录。
- “新增记录”支持 kcal/kJ、最多三位小数、自由备注和日期/时间。历史详情内新增默认选中该历史日期。
- 允许 0 kcal 记录，拒绝负摄入、无效数字、超大数值和未来日期；余额可以为负数。每条记录/预算上限 1,000,000 kcal，备注最多 500 字。
- 点编辑可更改数值、单位、备注及日期/时间；删除均有确认。更改日期后，两天的统计从记录重新计算。
- 设置预算立即应用于今天，并成为未来新日期的默认预算。已经创建的历史预算快照保留。
- 历史页展示日期、预算、摄入、剩余和条数，支持日期选择。无记录的日期显示“尚无摄入记录”。
- 图表支持最近 7/30/90 天和全部历史；绿色为摄入，橙色为每日预算，横轴按真实日期间隔绘制，缺失日期断线。平均值排除没有记录的日期，包含有记录但总摄入为 0 的日期。
- 统计包含最近 7/30 天及历史平均、所选范围最高/最低、范围内和历史超预算天数。
- 支持系统浅色/深色主题；小部件提供常规和紧凑布局。

## Android Studio 与 SDK

使用 **Android Studio Ladybug 2024.2.1 或更新版本**，选择打开本目录（含 `settings.gradle.kts`）。在 SDK Manager 安装：

- Android SDK Platform 35。
- Android SDK Build Tools 35.0.0 和 Platform Tools。
- JDK 17 或 21；在 Gradle JDK 设置中选择对应 JDK。

Android Studio 通常自动生成 `local.properties`。命令行也可按实际位置创建：

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

`local.properties` 不提交 Git。随后选择 **File → Sync Project with Gradle Files**。Gradle Wrapper 使用 8.9，AGP 8.7.3，Kotlin 2.0.21；构建机首次 Sync 需要下载依赖，但应用运行无需联网。

本仓库所在 Linux 工作区可运行：

```bash
# 在仓库外层 CalorieBudget/ 执行，工具均安装在仓库内 .tools/
./scripts/bootstrap-sdk.sh
./scripts/check.sh test assembleDebug lintDebug
```

需要预先可用的 JDK、curl、unzip；不使用 sudo 或系统级安装。SDK 脚本会接受 Android SDK 许可证。

## 构建、测试和安装

在本工程目录执行：

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lintDebug
```

Windows 使用 `gradlew.bat`。APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。连接已启用 USB 调试的手机后：

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.youranata.caloriewidget/.MainActivity
```

这是 Debug 签名 APK，发布到商店需要另行配置正式签名。当前验证结果与真机验收清单见 [docs/VALIDATION.md](docs/VALIDATION.md)。

项目骨架与 Room 数据库另有[独立验收报告](docs/ACCEPTANCE_SKELETON_DATABASE_20261123.md)，包含干净源码构建、Android 导入模型检查及 Android Studio GUI 的未验证边界。

## 小米 HyperOS 小部件

1. 安装后至少打开 App 一次。
2. 桌面长按空白区域（或双指捏合），进入“小部件/添加小部件”，在 Android 小部件列表找到 **CalorieWidget**；部分 HyperOS 版本需要进入“全部应用小部件”。
3. 拖入桌面，建议 4×2。支持横纵向缩放；桌面允许时可缩小为约 2×2，具体格数由桌面的网格和留白决定。
4. 点击余额进入今日记录；点 `+ kcal` 或 `+ kJ` 打开独立录入页；“备注来源”直接打开同一录入页并聚焦备注。提交后刷新全部实例。刷新不会新增记录。

## 零点、精确闹钟与后台限制

应用每次进入前台、读取汇总或写记录时校验本地日期。前台页面监测日期变化；新的一天选择新预算和新日期记录，**不删除或清零旧记录**。本地零点由 `LocalDate + ZoneId` 计算，不以固定 24 小时累加，因此支持夏令时。

- Android 8–11 使用 `setExactAndAllowWhileIdle`。
- Android 12+ 检查 `canScheduleExactAlarms()`；允许则精确安排，否则使用 `setAndAllowWhileIdle` 降级。今日页提供系统精确闹钟设置入口，授予权限为可选操作。
- 收到重启、日期/时间/时区更改、应用升级、精确闹钟授权广播时，刷新并重新安排下一次本地零点。
- HyperOS 的“应用信息 → 自启动”允许自启动，“省电策略”可选择不限制。不同版本菜单名称会有变化。
- 被系统限制、关机或强制停止时，不能保证桌面恰在 00:00 绘制新内容；重新打开应用或下一次获准刷新会按当前日期纠正。Android 强制停止期间不会执行后台闹钟或广播。

历史记录保存录入时的 `localDate` 和 `zoneId`，系统时区改变不会重新分配历史记录日期。编辑时显示该条记录原时区；若用户主动修改日期，则按选择的新日期归属。夏令时跳过的无效时间会提示重新选择。

## 数据与精度

- 内部 `Long` milli-kcal：1 kcal = 1000 milli-kcal。`1 kcal = 4.184 kJ` 使用 BigDecimal 换算，在保存时 HALF_UP 到 0.001 kcal。
- 示例：350 kcal + 1000 kJ（239.006 kcal），2000 kcal 预算剩余 1410.994 kcal，显示 1410.99 kcal。
- 原始输入以单位的千分之一整数单独保留；编辑不从两位小数显示值反推，避免反复编辑造成精度损失。
- `IntakeRecordEntity` 保存 ID、统一热量、原值/单位、备注、记录时间、创建时间、日期、时区和提交唯一键；同一提交键只插入一次。
- `DailyBudgetEntity` 按日期保存预算，`AppSettingsEntity` 保存后续新日期的默认预算。
- 新增、修改、移动日期、删除及预算变更采用 Room 事务；汇总从记录求和，无易失余额缓存。
- 未曾创建快照的旧日期无法推知当时预算：第一次查询或补录时采用当前默认预算。已有快照始终保留。
- 数据库版本 2，包含保留数据的 1→2 迁移；Room JSON schema 随源码提交。未知迁移会明确失败，不使用破坏性重建。

## 项目结构

```text
CalorieWidget/
├── app/build.gradle.kts
├── app/schemas/                  Room 版本化 schema
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/youranata/caloriewidget/
│   │   ├── data/                 Entity、DAO、AppDatabase、事务 Repository
│   │   ├── domain/               热量、日期与统计计算
│   │   ├── ui/                   共享页面、记录行和保存 ViewModel
│   │   ├── receiver/             零点闹钟及系统事件
│   │   ├── widget/               AppWidgetProvider / RemoteViews
│   │   ├── *Activity.kt          今日、输入、历史、日期详情和统计
│   │   └── StatsChartView.kt     Canvas 图表
│   └── res/                      XML 布局、亮暗主题、小部件元数据与图标
├── app/src/test/                  数值、日期、统计、Room、迁移和界面测试
├── docs/VALIDATION.md
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/、gradlew、gradlew.bat
```
