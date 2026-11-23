# 验证记录

当前 1.1 版本采用“当天明细可删改、历史只保留只读目标和总摄入”的规则。最新构建、数据库升级和独立范围审计见 [历史只读归档验收](ACCEPTANCE_READONLY_HISTORY_20261123.md)。下文保留早期版本的实际验证记录，其中“历史明细保留、跨日期编辑”不再是当前行为。

隐私处理说明：本报告中的机器路径、账号包名和环境指纹已脱敏；历史测试结论保留，显示的命令与模型字段为脱敏版本。原始日志仅保存在被 Git 忽略的本地证据目录。
验证日期：2026-11-23。工作区：`<WORKSPACE>`；Android Studio 工程：`CalorieWidget/`。工具、缓存、用户配置与临时文件统一保存在工作区 `.tools/` 内，无系统级安装。

## 实际执行结果

在仓库外层执行：

```bash
./scripts/check.sh test assembleDebug lintDebug
```

脚本在工程内调用 `./gradlew --no-daemon --console=plain test assembleDebug lintDebug`。

- **BUILD SUCCESSFUL**，最终全量检查耗时 52 秒。
- Debug：21 个测试用例，0 失败，0 错误。
- Release：21 个测试用例，0 失败，0 错误。
- `assembleDebug` 成功生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `lintDebug` 通过，没有 Error。仍有布局风格、动态文本国际化、紧凑布局字号、依赖版本建议等 Warning，未使用 baseline 或关闭检查来隐藏错误。
- `apksigner verify --verbose` 通过，APK v2 签名有效，覆盖最低 Android 8.0。
- `aapt2 dump badging` 确认应用 ID `com.example.caloriewidget`，minSdk 26、targetSdk 35，无 `INTERNET` 权限。
- 全部 XML 通过解析，Gradle 也完成资源与 Manifest 合并。Gradle Wrapper 固定 8.9 并配置官方分发包 SHA-256。

首次扩展测试时 Maven Central 曾出现一次 403，后续重试成功；最终检查没有依赖下载阻断。Kapt 会提示 Kotlin 2.0 语言模式回退到 1.9 进行 stub 生成，该提示不影响上述编译和测试结果。

## 自动验证覆盖

| 范围 | 实际覆盖 |
|---|---|
| 精度与输入 | kcal/kJ 混合余额、239.006 kcal 换算、原值精度、零值、负数、超大输入、三位小数、预算校验 |
| 日期 | 明确时区日期归属、春秋夏令时的 23/25 小时日、跨多天后的下一次零点 |
| Room | 内存库初始化、文件库关闭后重新打开、记录/预算持久化 |
| 记录一致性 | 新增、编辑、跨日期移动、删除、负余额、事务失败回滚 |
| 并发/幂等 | 24 个不同提交并发新增、12 次相同提交键仅新增一次 |
| 预算快照 | 设置预算立即作用于今天、历史预算保留、未来新日期沿用设置 |
| 缺失日期 | 无记录日期不计入均值，有记录的零摄入日计入均值 |
| 数据保护 | 多次日期检查保留旧记录、1→2 迁移保留记录和预算并生成不同提交键 |
| 页面 | 五个 Activity 在 API 26 和 35 的 Robolectric 环境中成功创建，取消未提交输入不新增记录 |
| 小部件 | 两种 RemoteViews 布局在 API 26 和 35 成功加载；负余额、备注入口、kJ 跳转、模拟桌面绑定、反复刷新不新增摄入 |
| 闹钟 | API 26/35 重复安排保持一个任务；API 35 无精确闹钟许可时降级，有许可时精确安排 |

JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html` 与 `testReleaseUnitTest/index.html`。Lint 报告：`app/build/reports/lint-results-debug.html`。这些文件属于本地构建产物，不提交 Git。

## 尚未在设备上验证

当前没有连接 Android 手机，也没有运行完整 Android 模拟器。Robolectric 的页面和 Widget host 模拟不能证明 HyperOS 桌面视觉或真实后台调度已验收。

以下为明确待真机执行的检查：

1. 安装 APK、首次打开，在 HyperOS 添加 4×2 小部件，缩放至桌面允许的紧凑尺寸；验证深浅主题、字体缩放、多个实例和重新添加。
2. 从 kcal/kJ/备注三个入口提交记录，连续点击确认；核对 App、全部小部件与历史图表只出现一条记录。
3. 编辑昨天记录的数值和日期、删除记录；核对两天的数据和当天 Widget。
4. 手机待机跨零点、关闭精确闹钟许可、限制后台，核对刷新延迟后的日期纠正；重启和时区变更后核对下次零点。
5. 强制停止再打开、后台进程回收、重启，核对记录持久化；关闭未提交输入不得生成记录。
6. 查阅 7/30/90 天和全部历史图表，观察缺失日期断线，验证历史预算不受今日设置变化影响。

强制停止期间系统不会分发应用闹钟/广播，因此不承诺在此状态下恰好于零点更新桌面；重开后按当前日期恢复。此限制不影响旧记录保存和余额计算。
