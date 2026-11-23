# CalorieWidget

CalorieWidget 是一个以桌面小部件为主要入口的本地热量预算应用。当天余额从 Room 明细计算，历史和图表使用每日目标与总摄入的只读归档，不单独保存余额。无账号、无食物数据库、无网络权限；自动云备份关闭。

## 使用

- 今日页展示剩余 kcal/kJ、已摄入热量、预算和按时间排序的记录。
- “新增记录”支持 kcal/kJ、最多三位小数、自由备注和当天时间；仅能操作当天记录。
- 允许 0 kcal 记录，拒绝负摄入、无效数字、超大数值和未来日期；余额可以为负数。每条记录/预算上限 1,000,000 kcal，备注最多 500 字。
- 点编辑可更改当天记录的数值、单位、备注及时间；删除均有确认。不允许移入历史日期、补写历史或修改历史目标。
- 设置预算立即应用于今天，并成为未来新日期的默认预算。已经创建的历史预算快照保留。
- 历史页按日期只读展示当日目标和总摄入，并由两者计算剩余量；不保留历史条数、备注、逐笔热量或记录时间。无记录的日期显示“尚无摄入记录”，未知日期显示暂无保存数据。
- 图表支持最近 7/30/90 天和全部历史；绿色为摄入，橙色为每日预算，横轴按真实日期间隔绘制，缺失日期断线。平均值排除没有记录的日期，包含有记录但总摄入为 0 的日期。
- 统计包含最近 7/30 天及历史平均、所选范围最高/最低、范围内和历史超预算天数。
- 支持系统浅色/深色主题；小部件提供常规和紧凑布局。

## Android Studio 与 SDK

使用 **Android Studio Ladybug 2024.2.1 或更新版本**，选择打开本目录（含 `settings.gradle.kts`）。在 SDK Manager 安装：

- Android SDK Platform 35。
- Android SDK Build Tools 35.0.0 和 Platform Tools。
- JDK 17 或 21；在 Gradle JDK 设置中选择对应 JDK。

请在文档、日志或截图中使用 `<WORKSPACE>`、`<ANDROID_SDK>` 等占位符，避免提交本机用户名、绝对路径、主机名和设备标识。

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
adb shell am start -n com.example.caloriewidget/.MainActivity
```

这是 Debug 签名 APK，发布到商店需要另行配置正式签名。应用 ID 使用通用的 `com.example.caloriewidget`；此前使用旧 ID 的开发版本不能直接覆盖安装本版本，旧版本的本地数据库也不会自动迁移到新应用 ID。当前验证结果与真机验收清单见 [docs/VALIDATION.md](docs/VALIDATION.md)。

独立验收报告：

- [项目骨架与 Room 数据库](docs/ACCEPTANCE_SKELETON_DATABASE_20261123.md)：干净源码构建、Android 导入模型和 IDE 验证边界。
- [热量计算与记录管理](docs/ACCEPTANCE_CALCULATION_RECORDS_20261123.md)：独立换算参考、记录增删改、跨日期计算与输入页交互。
- [仓库隐私](docs/ACCEPTANCE_PRIVACY_20261123.md)：个人与机器信息扫描、脱敏及历史处理边界。
- [历史只读归档及独立审计](docs/ACCEPTANCE_READONLY_HISTORY_20261123.md)：归档事务、旧版本升级、历史不可改动与其他功能回归。

## 小米 HyperOS 小部件

1. 安装后至少打开 App 一次。
2. 桌面长按空白区域（或双指捏合），进入“小部件/添加小部件”，在 Android 小部件列表找到 **CalorieWidget**；部分 HyperOS 版本需要进入“全部应用小部件”。
3. 拖入桌面，建议 4×2。支持横纵向缩放；桌面允许时可缩小为约 2×2，具体格数由桌面的网格和留白决定。
4. 点击余额进入今日记录；点 `+ kcal` 或 `+ kJ` 打开独立录入页；“备注来源”直接打开同一录入页并聚焦备注。提交后刷新全部实例。刷新不会新增记录。

## 零点、精确闹钟与后台限制

应用每次进入前台、读取汇总或写记录时校验本地日期。前台页面监测日期变化；在同一事务中先冻结历史的当日目标与总摄入，再删除历史明细；新的一天创建新预算并从零摄入开始。归档只执行一次，重复刷新不会重新扣减；已归档日期即使因回拨日期或时区变化再次成为今天，也保持只读。本地零点由 `LocalDate + ZoneId` 计算，不以固定 24 小时累加，因此支持夏令时。

- Android 8–11 使用 `setExactAndAllowWhileIdle`。
- Android 12+ 检查 `canScheduleExactAlarms()`；允许则精确安排，否则使用 `setAndAllowWhileIdle` 降级。今日页提供系统精确闹钟设置入口，授予权限为可选操作。
- 收到重启、日期/时间/时区更改、应用升级、精确闹钟授权广播时，刷新并重新安排下一次本地零点。
- HyperOS 的“应用信息 → 自启动”允许自启动，“省电策略”可选择不限制。不同版本菜单名称会有变化。
- 被系统限制、关机或强制停止时，不能保证桌面恰在 00:00 绘制新内容；重新打开应用或下一次获准刷新会按当前日期纠正。Android 强制停止期间不会执行后台闹钟或广播。

当天明细保存录入时的 `localDate` 和 `zoneId`，系统时区改变不会重新分配已有记录日期。夏令时跳过的无效时间会提示重新选择。历史汇总保留原日期，不再保存逐笔记录或时区。

## 数据与精度

- 内部 `Long` milli-kcal：1 kcal = 1000 milli-kcal。`1 kcal = 4.184 kJ` 使用 BigDecimal 换算，在保存时 HALF_UP 到 0.001 kcal。
- 示例：350 kcal + 1000 kJ（239.006 kcal），2000 kcal 预算剩余 1410.994 kcal，显示 1410.99 kcal。
- 原始输入以单位的千分之一整数单独保留；编辑不从两位小数显示值反推，避免反复编辑造成精度损失。
- `IntakeRecordEntity` 保存 ID、统一热量、原值/单位、备注、记录时间、创建时间、日期、时区和提交唯一键；同一提交键只插入一次。
- `DailyBudgetEntity` 按日期保存目标和归档总摄入；另保留一个有无数据标志，以区分明确零摄入与缺失数据，不保存历史记录条数。`AppSettingsEntity` 保存后续新日期的默认预算。
- 当天新增、修改、删除和预算变更采用 Room 事务，当天汇总从明细求和。历史归档在同一事务内完成汇总与明细删除，失败整体回滚。
- 查询未知历史日期不补造预算或零摄入日。升级时若旧明细缺少对应预算快照，只能采用当时保存的默认预算；已有预算快照原样保留。
- 数据库版本 3，支持 1→2→3 和 2→3 升级：当天明细、预算和设置保留，过往明细先汇总再删除。Room JSON schema 随源码提交，未知迁移明确失败，不使用破坏性重建。安装此版本后历史明细将不可恢复，只能查看归档目标与总摄入。

## 项目结构

```text
CalorieWidget/
├── app/build.gradle.kts
├── app/schemas/                  Room 版本化 schema
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/caloriewidget/
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
