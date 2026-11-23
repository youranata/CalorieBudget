# CalorieBudget

一个简洁、轻量的 Android 热量预算管理工具，支持通过桌面小部件快速记录每日热量摄入，并实时查看剩余热量预算。

项目主要关注日常使用中的记录效率和数据管理，希望以尽可能少的操作完成热量记录，并通过历史数据了解长期摄入情况。

> **项目状态：开发中**

## Features

### Daily Calorie Budget

- 支持自定义每日热量预算。
- 默认每日预算为 2000 kcal。
- 实时计算并显示当日剩余热量。
- 超出预算后允许剩余热量显示为负数。
- 同时支持 kcal 和 kJ 两种热量单位。

单位换算：

- 1 kcal = 4.184 kJ
- 1 kJ ≈ 0.239 kcal

### Quick Intake Recording

支持通过两种单位记录热量：

- **kcal**：直接输入千卡数值。
- **kJ**：输入千焦数值，自动换算为 kcal。

每条摄入记录包含：

- 热量数值。
- 原始输入数值及单位。
- 食物或摄入来源备注。
- 记录时间。
- 所属日期。

所有记录统一换算后参与每日热量统计。

### Home Screen Widget

提供 Android 桌面小部件，方便用户快速查看和记录热量。

主要功能：

- 显示当日剩余热量。
- 显示每日预算和已摄入热量。
- 提供 kcal 和 kJ 两种快速录入入口。
- 支持填写摄入来源备注。
- 记录完成后自动刷新小部件。

界面以剩余热量为核心，尽量减少不必要的信息和操作。

### History & Statistics

完整应用支持查看和管理历史摄入数据。

计划提供：

- 按日期查看摄入记录。
- 新增、编辑和删除记录。
- 查看每日总摄入热量。
- 对比每日摄入量与预算。
- 查看一段时间内的热量变化趋势。
- 统计每日平均摄入量及预算达成情况。

历史记录独立保存，不会因为日期切换而丢失。

### Daily Reset

- 每天按照设备本地时间零点切换至新的一天。
- 新的一天重新计算剩余热量。
- 历史摄入记录永久保留。
- 使用日期校验和系统定时任务共同保障日期切换的正确性。
- 考虑设备重启及系统时区变化等情况。

## Technical Stack

| Technology | Purpose |
|---|---|
| Kotlin | Main programming language |
| Android SDK | Android application development |
| XML Layout | UI layout |
| AppWidget / RemoteViews | Home screen widget |
| Room | Local database |
| Kotlin Coroutines | Asynchronous operations |
| AlarmManager | Scheduled tasks and daily refresh |
| Canvas / Chart Library | Historical data visualization |
| Gradle Kotlin DSL | Build configuration |

## Data Design

项目使用 Room 实现本地数据持久化，主要包含每日预算和摄入记录。

### Daily Budget

每日预算与日期关联保存。

修改当前日期的预算不会影响历史日期已经保存的预算。

### Intake Records

每次摄入作为一条独立记录保存，包含：

- 原始输入数值。
- 原始输入单位。
- 统一换算后的热量数值。
- 来源备注。
- 时间戳。
- 所属日期。

### Calorie Calculation

每日剩余热量按照以下规则计算：

**剩余热量 = 每日预算 - 当日所有有效摄入记录的热量总和**

例如：

| Item | Value |
|---|---:|
| Daily Budget | 2000 kcal |
| Intake 1 | 500 kcal |
| Intake 2 | 650 kcal |
| Intake 3 | 350 kcal |
| Total Intake | 1500 kcal |
| Remaining | 500 kcal |

如果累计摄入超过预算，剩余热量可以为负数。

热量计算采用固定精度数值，尽量避免浮点数运算带来的累计误差。

## Privacy

- 数据默认保存在设备本地。
- 无需注册账号。
- 无需联网即可使用。
- 不依赖云端服务。
- 不主动上传个人摄入记录。

## Project Structure

项目采用模块化组织方式，主要职责划分如下：

```text
CalorieBudget/
├── app/
│   ├── src/main/
│   │   ├── java/.../
│   │   │   ├── MainActivity.kt
│   │   │   ├── InputActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── entity/
│   │   │   │   ├── dao/
│   │   │   │   └── AppDatabase.kt
│   │   │   ├── repository/
│   │   │   ├── widget/
│   │   │   │   └── WidgetProvider.kt
│   │   │   ├── receiver/
│   │   │   │   └── ResetReceiver.kt
│   │   │   └── utils/
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── drawable/
│   │   │   ├── values/
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
└── README.md
```

以上为预期的模块组织结构，实际目录可能随开发进度调整。

## Requirements

- Android Studio
- JDK 17（以项目 Gradle 配置为准）
- Android SDK
- Android 8.0（API 26）及以上设备

## Build & Run

### 1. Clone the Repository

```bash
git clone git@github.com:youranata/CalorieBudget.git
cd CalorieBudget
```

也可以使用 HTTPS：

```bash
git clone https://github.com/youranata/CalorieBudget.git
```

### 2. Open in Android Studio

使用 Android Studio 打开项目根目录，等待 Gradle Sync 完成。

### 3. Build Debug APK

Linux / macOS：

```bash
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

生成的 APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Run Tests

```bash
./gradlew test
```

### 5. Install on Android Device

连接开启 USB 调试的 Android 设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装完成后，在手机桌面长按空白区域，进入小部件选择界面，添加 CalorieBudget Widget。

## Development Roadmap

- [ ] Initialize Android project structure.
- [ ] Implement Room database.
- [ ] Implement kcal / kJ conversion.
- [ ] Implement daily calorie budget and remaining calorie calculation.
- [ ] Implement intake record creation, editing and deletion.
- [ ] Implement home screen widget.
- [ ] Implement quick intake recording.
- [ ] Implement daily reset and date validation.
- [ ] Implement history and statistics.
- [ ] Add dark mode and UI refinements.
- [ ] Complete unit tests and device compatibility testing.

## Design Principles

- **Simplicity**：优先展示剩余热量这一核心信息。
- **Efficiency**：尽可能减少记录一次摄入所需的操作。
- **Reliability**：历史数据持久保存，统计结果以实际记录为依据。
- **Privacy**：本地存储，不依赖账号和云端服务。
- **Lightweight**：避免不必要的后台任务和复杂功能。

## License

暂未指定开源许可证。项目目前主要用于个人开发和使用。