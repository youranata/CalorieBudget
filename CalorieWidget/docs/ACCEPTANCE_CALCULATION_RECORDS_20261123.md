# 独立验收：热量计算与记录管理

隐私处理说明：本报告中的机器路径、账号包名和环境指纹已脱敏；历史测试结论保留，显示的命令与模型字段为脱敏版本。原始日志仅保存在被 Git 忽略的本地证据目录。
验收日期：2026-11-23。业务源码基线：`83ad0f92818cad00bda6c22abbcf8aebb555d190`。

## 结论

**通过：在本轮自动验收范围内，新增、编辑、删除后的热量计算、余额和历史汇总正确。**

未发现需要修改业务代码的问题。本轮只新增 3 个验收测试文件和文档；原工程与独立导出副本的 `app/src/main/` 全部文件 SHA-256 与基线一致。

## 验收方法

从 Git 基线通过 `git archive` 导出干净源码，仅加入本轮验收测试，未复用原工程的生成代码、`app/build/` 或 `local.properties`。SDK、依赖缓存、Android 用户配置及临时文件均位于仓库 `.tools/` 中。离线复用已下载依赖，不属于冷网络下载测试。

- 换算参考：由数学恒等式 `1 / 4.184 = 125 / 523`，使用 BigInteger 整数商余数计算 HALF_UP 期望值，独立于业务代码的 BigDecimal 实现。
- 509 个 kJ 换算向量验证统一热量与原始输入；另核对正负显示舍入、零值、最小精度、数值上限和非法输入。
- 每次记录操作同时核对明细、SQL SUM、Repository 汇总、余额、记录数和历史投影。
- 按固定种子执行跨 5 个日期的 120 步新增/编辑/删除，在每一步与独立记录账本比较；API 26/35 分别执行。
- 界面使用真实 Activity、输入框、按钮、保存 ViewModel、Room 和 RemoteViews，在 Robolectric API 26/35 中测试，没有 mock Repository。

## 明确数值的逐步操作

以下是 `specifiedAddEditUnitChangeAndDeleteSequence` 实际通过的断言，预算始终为 2000 kcal。内部值保留三位小数，显示值四舍五入至两位小数。

| 操作 | 总摄入 kcal | 剩余 kcal（内部精度） | 记录数 |
|---|---:|---:|---:|
| 初始 | 0 | 2000 | 0 |
| 新增 A：350 kcal | 350 | 1650 | 1 |
| 新增 B：1000 kJ = 239.006 kcal | 589.006 | 1410.994 | 2 |
| 编辑 A 为 500 kcal | 739.006 | 1260.994 | 2 |
| 编辑 B 为 2092 kJ = 500 kcal | 1000 | 1000 | 2 |
| 删除 A | 500 | 1500 | 1 |
| 删除 B | 0 | 2000 | 0 |

第二次新增后的余额显示为 **1410.99 kcal / 5903.6 kJ**。2200 kcal 摄入配合 2000 kcal 预算得到 **-200 kcal**，未被限制为零。

## 记录管理覆盖

| 情景 | 结果 |
|---|---|
| 混合 kcal/kJ 新增 | 同一总摄入与余额；每次新增一条记录 |
| 编辑数值和单位 | 替换原记录；不会再次追加扣减或增加记录数 |
| 备注/时间修改 | 保留记录 ID、提交键和创建时间；时间顺序正确 |
| 原始三位小数 | `1234.567 kJ → 295.069 kcal`，反复编辑备注不损失输入精度 |
| 跨日期移动 | 两个日期同步重算；各自预算快照保持；历史操作不误计入今天 |
| 删除任意及最后一条 | 从求和中移除；最后一条删除后恢复预算，历史预算仍存在 |
| 重复保存/重复删除 | 同一提交不重复新增；重复编辑替换同一记录；重复删除不再次改变余额 |
| 删除后再编辑 | 明确失败，无记录复活或额外预算快照 |
| 无效修改 | 负数/超长备注修改失败，旧记录与预算事务回滚 |
| 并发 | 32 条混合单位新增、16 次同键提交、并行编辑和重复删除后，金额与条数正确 |
| 持久化 | 混合新增/跨日编辑/删除后，关闭并重开文件数据库，金额、日期、备注及删除状态保持 |
| 零摄入与缺失日 | 零摄入保存为一条明确记录；最后一条删除后不再计入平均值 |
| 输入页交互 | 实际点击保存并重复点击不会新增多条；无效输入、取消均不写记录 |
| 删除确认 | 今日页及历史详情，取消不改变数据；确认后才删除 |
| 页面与 Widget 刷新 | 输入页提交、编辑及确认删除后，今日页余额与模拟桌面 Widget 更新为正确值 |

## 实际执行结果

本轮专项检查：**40 个用例全部通过**，包含新增 30 次验收用例执行（4 个纯数值、9 个数据库场景 × 2 个 API、4 个交互场景 × 2 个 API）及原有 10 个计算/仓库测试。

```bash
# 独立导出工程目录内；通过环境变量使用工作区 SDK/缓存
./gradlew --no-daemon --console=plain --offline \
  --no-build-cache --no-configuration-cache \
  clean :app:assembleDebug :app:testDebugUnitTest \
  --tests 'com.example.caloriewidget.acceptance.*' \
  --tests 'com.example.caloriewidget.CalorieMathTest' \
  --tests 'com.example.caloriewidget.RepositoryTest'
```

结果：`BUILD SUCCESSFUL in 1m 15s`。

随后在同一独立源码目录执行完整回归：

```bash
./gradlew --no-daemon --console=plain --offline \
  --no-build-cache --no-configuration-cache test :app:assembleDebug
```

结果：`BUILD SUCCESSFUL in 1m 46s`。**Debug 51 个、Release 51 个用例全部通过，0 失败、0 错误、0 跳过。** APK 构建成功且 `apksigner verify --verbose` 验证通过。

首次构建时 Kotlin daemon 一次启动退出后自动恢复，Kapt 仍有语言模式回退及注解处理参数提示；未修改编译配置、未降低测试断言或跳过失败用例。

验收测试随源码提交，可在仓库外层重跑：

```bash
./scripts/check.sh :app:testDebugUnitTest \
  --tests 'com.example.caloriewidget.acceptance.*'
```

## 证据位置与验证边界

- 测试：[CalorieCalculationAcceptanceTest.kt](../app/src/test/java/com/example/caloriewidget/acceptance/CalorieCalculationAcceptanceTest.kt)、[RecordManagementAcceptanceTest.kt](../app/src/test/java/com/example/caloriewidget/acceptance/RecordManagementAcceptanceTest.kt)、[RecordManagementUiAcceptanceTest.kt](../app/src/test/java/com/example/caloriewidget/acceptance/RecordManagementUiAcceptanceTest.kt)。
- 原始导出、构建日志、测试 XML、参考结果及源码哈希：`.tools/acceptance/<run-id>/`。
- APK：`artifacts/CalorieWidget-records-acceptance-debug.apk`（6251422 字节）。SHA-256：`e4509686f513dba13b8ef51065944a5a17320831dddc212f036fce89e24f6c61`。

本轮确认计算、数据库和 Robolectric 交互行为；**没有进行真机人工操作，也没有新增 HyperOS 桌面、真实后台调度或 Android Studio GUI 的验证结论**。本轮没有修改应用业务代码，因此没有用修复后的行为替代对原基线的验收。
