# 短信发送流程与界面重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 SIM 识别和实际发送接线，重构短信页为带图标、可勾选、可显示整体发送进度的两卡片界面。

**Architecture:** 继续使用现有 Room 持久化发送队列与 `AndroidSmsGateway`，新增平台前台 `Service` 顺序消费冻结队列；`SendFlowViewModel` 负责草稿选择和从 Room Flow 派生整体进度。运行时权限留在 `MainActivity`，Compose 只表达权限状态和用户意图。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、Android `Service`、`SubscriptionManager`、`SmsManager`、JUnit 4、Compose UI Test。

**Spec:** `docs/superpowers/specs/2026-08-17-sms-sending-redesign-design.md`

## Global Constraints

- 只支持竖屏，页面整体不滚动，功能块内部允许滚动。
- 批量上限沿用现有约束，最多 100 条。
- 只显示整体发送进度与最终成功/失败数量，不在列表显示逐条发送状态。
- 完整显示电话号码，不打码。
- 不允许自动测试发送真实短信；所有发送测试使用假网关。
- 不修改 Gradle wrapper、Android Gradle Plugin、Kotlin、Compose BOM 或依赖列表。
- 图标使用项目内 Android Vector Drawable，不复制 LocalSend 品牌资源。
- Codex 只修改源码和测试源码，不运行 Gradle、Android Studio 或模拟器；计划中的命令留给用户在 Android Studio 或终端验证。
- 保留工作区已有未提交修改，不覆盖或提交截图、XML 抓取文件和 `gradle/gradle-daemon-jvm.properties`。

## 文件结构

- Create: `app/src/main/java/com/local/bulksms/ui/send/DraftSelection.kt` — 纯 Kotlin 的草稿选择合并规则。
- Create: `app/src/main/java/com/local/bulksms/sms/SmsSendingService.kt` — 前台顺序发送服务。
- Create: `app/src/main/java/com/local/bulksms/ui/icons/BulkSmsIcons.kt` — 本地图标资源的集中映射。
- Create: `app/src/main/res/drawable/ic_*.xml` — 数据、短信、设置、导入、SIM、模板、预览、发送和结果图标。
- Modify: `SendFlowViewModel.kt` — 选择状态、SIM 状态、队列创建和整体进度观察。
- Modify: `BulkSmsRepository.kt`/`Daos.kt` — 冻结选中草稿、读取任务和汇总状态。
- Modify: `MainActivity.kt`/`BulkSmsApplication.kt` — 权限桥接、SIM 重载和发送服务启动。
- Modify: `SmsScreen.kt`/`MessageReviewScreen.kt` — 两卡片、标准下拉框、只读预览和全选。
- Modify: `DataScreen.kt`/`SettingsScreen.kt`/`BulkSmsApp.kt` — 页面图标和明确的 SIM 权限状态。
- Modify: `AndroidManifest.xml` — 注册前台服务及 special-use 说明。

---

### Task 1: 草稿发送选择模型

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/send/DraftSelection.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Test: `app/src/test/java/com/local/bulksms/ui/send/DraftSelectionTest.kt`
- Test: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`

**Interfaces:**
- Produces: `reconcileDraftSelection(previousDraftIds: Set<Long>, previousSelectedIds: Set<Long>, newDraftIds: Set<Long>): Set<Long>`
- Produces: `SendFlowUiState.selectedDraftRowIds: Set<Long>`
- Produces: `toggleDraftSelection(rowId: Long, selected: Boolean)` 与 `selectAllDrafts(selected: Boolean)`

- [ ] **Step 1: 写失败测试，覆盖新增默认选中和保留取消状态**

```kotlin
@Test
fun reconcilePreservesExistingChoicesAndSelectsOnlyNewRows() {
    val actual = reconcileDraftSelection(
        previousDraftIds = setOf(1L, 2L),
        previousSelectedIds = setOf(1L),
        newDraftIds = setOf(1L, 2L, 3L),
    )
    assertEquals(setOf(1L, 3L), actual)
}
```

- [ ] **Step 2: 由用户验证测试因函数不存在而失败**

Run: `gradlew.bat testDebugUnitTest --tests "com.local.bulksms.ui.send.DraftSelectionTest"`

Expected: FAIL，`reconcileDraftSelection` 未定义。

- [ ] **Step 3: 实现纯函数和 ViewModel 选择入口**

```kotlin
internal fun reconcileDraftSelection(
    previousDraftIds: Set<Long>,
    previousSelectedIds: Set<Long>,
    newDraftIds: Set<Long>,
): Set<Long> = (previousSelectedIds intersect newDraftIds) + (newDraftIds - previousDraftIds)
```

所有生成或刷新草稿的入口先保存旧 `drafts` 与旧选择，再用该函数设置新选择。初始工作区旧集合为空，因此所有草稿默认选中。

- [ ] **Step 4: 添加单条和全选 ViewModel 测试**

```kotlin
viewModel.selectAllDrafts(false)
assertTrue(viewModel.state.value.selectedDraftRowIds.isEmpty())
viewModel.toggleDraftSelection(firstRowId, true)
assertEquals(setOf(firstRowId), viewModel.state.value.selectedDraftRowIds)
```

- [ ] **Step 5: 由用户验证选择模型测试通过**

Run: `gradlew.bat testDebugUnitTest --tests "com.local.bulksms.ui.send.*Selection*"`

Expected: PASS。

- [ ] **Step 6: 提交选择模型**

```powershell
git add app/src/main/java/com/local/bulksms/ui/send/DraftSelection.kt app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/test/java/com/local/bulksms/ui/send
git commit -m "feat: select drafts for SMS sending"
```

### Task 2: 冻结选中短信与整体进度模型

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/data/BulkSmsRepository.kt`
- Modify: `app/src/main/java/com/local/bulksms/data/Daos.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/data/AppDatabaseTest.kt`
- Test: `app/src/test/java/com/local/bulksms/ui/send/SendProgressTest.kt`

**Interfaces:**
- Consumes: `selectedDraftRowIds`
- Produces: `freezeQueue(importId: String, simSubscriptionId: Int, selectedRowIds: Set<Long>): String`
- Produces: `SendProgressUiState(total: Int, processed: Int, succeeded: Int, failed: Int, running: Boolean)`
- Produces: `SendDao.task(id: String): Flow<SendTaskEntity?>`

- [ ] **Step 1: 写失败数据库测试，确保未勾选草稿不入队**

```kotlin
val taskId = repository.freezeQueue("import-1", 7, setOf(2L))
val items = database.sendDao().itemsOnce(taskId)
assertEquals(listOf("13900139000"), items.map { it.phoneNumber })
```

- [ ] **Step 2: 由用户验证旧签名导致测试失败**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest`

Expected: FAIL，`freezeQueue` 不接受 `selectedRowIds`。

- [ ] **Step 3: 最小修改冻结队列查询**

在事务中读取现有草稿后执行：

```kotlin
val drafts = database.draftDao().byImportOnce(importId)
    .filter { it.rowId in selectedRowIds }
    .sortedWith(compareBy<MessageDraftEntity> { it.rowId }.thenBy { it.id })
require(drafts.isNotEmpty()) { "至少选择一条短信" }
```

- [ ] **Step 4: 写整体进度失败测试**

```kotlin
val progress = SendProgressUiState.from(
    listOf(SendStatus.SUBMITTED, SendStatus.FAILED, SendStatus.UNCERTAIN),
)
assertEquals(3, progress.total)
assertEquals(3, progress.processed)
assertEquals(1, progress.succeeded)
assertEquals(2, progress.failed)
assertFalse(progress.running)
```

- [ ] **Step 5: 实现整体统计与任务 Flow**

`processed` 统计 `SUBMITTED/FAILED/UNCERTAIN/CANCELLED`；`failed` 统计除 `SUBMITTED` 外的所有终态；存在 `PENDING/SUBMITTING` 时 `running = true`。

- [ ] **Step 6: 由用户验证数据库与进度测试通过**

Run: `gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest`

Expected: PASS。

- [ ] **Step 7: 提交队列筛选与进度模型**

```powershell
git add app/src/main/java/com/local/bulksms/data app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/test/java/com/local/bulksms/ui/send/SendProgressTest.kt app/src/androidTest/java/com/local/bulksms/data/AppDatabaseTest.kt
git commit -m "feat: freeze selected SMS queue and summarize progress"
```

### Task 3: 前台短信发送服务

**Files:**
- Create: `app/src/main/java/com/local/bulksms/sms/SmsSendingService.kt`
- Modify: `app/src/main/java/com/local/bulksms/BulkSmsApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/com/local/bulksms/sms/SmsSendingServiceTest.kt`

**Interfaces:**
- Consumes: `BulkSmsRepository.claimNext/completeAttempt/completeTaskIfTerminal`
- Consumes: `BulkSmsApplication.smsGateway`
- Produces: `SmsSendingService.start(context: Context, taskId: String)`
- Produces: Intent extra `EXTRA_TASK_ID`

- [ ] **Step 1: 写失败服务测试**

使用 `BulkSmsApplication.smsGatewayOverride` 注入依次返回成功、失败的假网关，创建两条队列后启动服务，轮询 Room 直到没有 `PENDING/SUBMITTING`，断言一条 `SUBMITTED`、一条 `FAILED`。

```kotlin
assertEquals(
    listOf(SendStatus.SUBMITTED, SendStatus.FAILED),
    database.sendDao().itemsOnce(taskId).map { it.status },
)
```

- [ ] **Step 2: 由用户验证服务尚不存在**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.sms.SmsSendingServiceTest`

Expected: FAIL，`SmsSendingService` 未定义。

- [ ] **Step 3: 实现平台 Service 的顺序消费循环**

```kotlin
while (true) {
    val item = repository.claimNext(taskId) ?: break
    val result = runCatching {
        gateway.submit(SmsSubmission(item.id, task.simSubscriptionId, item.phoneNumber, item.body))
    }.getOrElse { SmsSubmissionResult(false, AndroidSmsGateway.ERROR_INVALID_ARGUMENT) }
    repository.completeAttempt(
        item.id,
        if (result.success) SendStatus.SUBMITTED else SendStatus.FAILED,
        result.errorCode,
    )
    updateNotification(taskId)
}
repository.completeTaskIfTerminal(taskId)
```

服务使用 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`，`onDestroy` 取消作用域；同一任务的重复启动由内存中的任务 ID 集合拒绝。

- [ ] **Step 4: 创建通知渠道和整体进度通知**

通知内容只包含“正在发送 12/80”，不包含号码和正文。`onStartCommand` 首先 `startForeground`，结束后更新为“成功 X 条，失败 Y 条”并 `stopForeground(STOP_FOREGROUND_DETACH)`。

- [ ] **Step 5: 注册 special-use 前台服务**

```xml
<service
    android:name=".sms.SmsSendingService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-confirmed sequential SMS submission" />
</service>
```

- [ ] **Step 6: 由用户验证服务测试通过**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.sms.SmsSendingServiceTest`

Expected: PASS，且测试假网关记录到两次提交。

- [ ] **Step 7: 提交发送服务**

```powershell
git add app/src/main/java/com/local/bulksms/sms/SmsSendingService.kt app/src/main/java/com/local/bulksms/BulkSmsApplication.kt app/src/main/AndroidManifest.xml app/src/androidTest/java/com/local/bulksms/sms/SmsSendingServiceTest.kt
git commit -m "feat: send frozen SMS queue in foreground service"
```

### Task 4: SIM 权限与检测状态

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/sms/SimSubscriptionProvider.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/settings/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/sms/SimSubscriptionProviderTest.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/ui/settings/SettingsScreenTest.kt`

**Interfaces:**
- Produces: `enum class SimDetectionState { PERMISSION_REQUIRED, LOADING, AVAILABLE, EMPTY, ERROR }`
- Produces: `SendFlowUiState.simDetectionState`
- Produces: callbacks `onRequestSimPermission` 与 `onRefreshSimOptions`

- [ ] **Step 1: 写失败 Compose 测试，区分未授权与无 SIM**

```kotlin
composeRule.onNodeWithText("需要电话权限才能读取 SIM").assertExists()
composeRule.onNodeWithTag("grant-sim-permission").performClick()
assertTrue(permissionRequested)
```

另一个状态为 `EMPTY` 时只显示“没有检测到活动 SIM”，不能显示授权按钮。

- [ ] **Step 2: 由用户验证状态类型未定义**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.settings.SettingsScreenTest`

Expected: FAIL，`simDetectionState` 未定义。

- [ ] **Step 3: 将异常处理移出 Provider 并增加 ViewModel 状态入口**

Provider 保留活动订阅映射，调用层在权限通过后捕获 `UnsupportedOperationException`/系统异常并调用：

```kotlin
fun setSimPermissionRequired()
fun setSimLoading()
fun setSimOptions(options: List<SimOption>)
fun setSimDetectionError(message: String)
```

- [ ] **Step 4: MainActivity 接入 READ_PHONE_STATE launcher**

使用 `ActivityResultContracts.RequestPermission()`。首次组合时检查 `ContextCompat.checkSelfPermission`；授权后立即调用同一个 `reloadSimOptions()`，拒绝后保持 `PERMISSION_REQUIRED`。

- [ ] **Step 5: 设置页显示状态与刷新入口**

`LOADING` 显示进度；`PERMISSION_REQUIRED` 显示解释与授权按钮；`EMPTY` 显示无 SIM；`ERROR` 显示错误与重试；`AVAILABLE` 显示 RadioButton 列表。

- [ ] **Step 6: 由用户验证 SIM 相关测试通过**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.sms.SimSubscriptionProviderTest,com.local.bulksms.ui.settings.SettingsScreenTest`

Expected: PASS。

- [ ] **Step 7: 提交 SIM 权限修复**

```powershell
git add app/src/main/java/com/local/bulksms/sms/SimSubscriptionProvider.kt app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/main/java/com/local/bulksms/MainActivity.kt app/src/main/java/com/local/bulksms/ui/settings/SettingsScreen.kt app/src/androidTest/java/com/local/bulksms/sms/SimSubscriptionProviderTest.kt app/src/androidTest/java/com/local/bulksms/ui/settings/SettingsScreenTest.kt
git commit -m "fix: request permission before loading SIM subscriptions"
```

### Task 5: 只读短信选择列表与两卡片布局

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/ui/send/MessageReviewScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/ui/send/SmsScreenTest.kt`

**Interfaces:**
- Consumes: `selectedDraftRowIds`
- Produces: callbacks `onDraftSelectionChanged: (Long, Boolean) -> Unit`、`onSelectAllDrafts: (Boolean) -> Unit`
- Removes from active UI: `onDraftChanged`、`onDraftSyncChanged`、`onSyncAll`、`onUnsyncAll`

- [ ] **Step 1: 写失败 Compose 测试**

测试断言完整号码 `13800138000` 存在，`138****8000` 不存在，`message-body-*` 不再是可编辑文本框，每条有 `send-draft-<rowId>` 复选框。

```kotlin
composeRule.onNodeWithText("13800138000").assertExists()
composeRule.onNodeWithText("138****8000").assertDoesNotExist()
composeRule.onNodeWithTag("send-draft-1").performClick()
assertEquals(1L to false, selectionChange)
```

- [ ] **Step 2: 写全选/半选失败测试**

两个草稿只选一个时，顶部 `select-all-drafts` 必须具有 `ToggleableState.Indeterminate`；点击后回调 `true`，全选后再次点击回调 `false`。

- [ ] **Step 3: 由用户验证旧编辑界面导致测试失败**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.MessageReviewScreenTest,com.local.bulksms.ui.send.SmsScreenTest`

Expected: FAIL，旧页面仍打码并包含编辑框。

- [ ] **Step 4: 将 MessageReviewList 改为只读选择列表**

`MessageReviewItem` 使用 `Text` 显示正文和完整号码，`Checkbox` 只表达 `selected`。移除 `maskPhone`、`OutlinedTextField` 和同步语义。

- [ ] **Step 5: 使用两个圆角 Card 重排 SmsScreen**

模板 Card 使用固定内容高度；预览 Card 使用 `Modifier.weight(1f)`，内部 `LazyColumn` 滚动。删除模板与预览之间的裸 `HorizontalDivider`。

- [ ] **Step 6: 模板选择改为 ExposedDropdownMenuBox**

```kotlin
ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        value = selectedName,
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = Modifier.menuAnchor().testTag("template-selector"),
    )
    ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) { /* 模板项 */ }
}
```

模板项仍保留未保存修改确认逻辑。

- [ ] **Step 7: 由用户验证短信页面测试通过**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.MessageReviewScreenTest,com.local.bulksms.ui.send.SmsScreenTest`

Expected: PASS。

- [ ] **Step 8: 提交短信页重构**

```powershell
git add app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt app/src/main/java/com/local/bulksms/MainActivity.kt app/src/androidTest/java/com/local/bulksms/ui/send
git commit -m "feat: select read-only SMS previews in card layout"
```

### Task 6: 本地矢量图标与三页视觉补强

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/icons/BulkSmsIcons.kt`
- Create: `app/src/main/res/drawable/ic_data.xml`
- Create: `app/src/main/res/drawable/ic_sms.xml`
- Create: `app/src/main/res/drawable/ic_settings.xml`
- Create: `app/src/main/res/drawable/ic_file.xml`
- Create: `app/src/main/res/drawable/ic_clipboard.xml`
- Create: `app/src/main/res/drawable/ic_sim.xml`
- Create: `app/src/main/res/drawable/ic_phone.xml`
- Create: `app/src/main/res/drawable/ic_template.xml`
- Create: `app/src/main/res/drawable/ic_preview.xml`
- Create: `app/src/main/res/drawable/ic_send.xml`
- Create: `app/src/main/res/drawable/ic_success.xml`
- Create: `app/src/main/res/drawable/ic_error.xml`
- Modify: `app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/data/DataScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/settings/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/ui/BulkSmsNavigationTest.kt`

**Interfaces:**
- Produces: `object BulkSmsIcons`，每个属性为 `@DrawableRes Int`

- [ ] **Step 1: 写失败导航测试**

给三个导航 `Icon` 增加内容描述“数据图标、短信图标、设置图标”，测试断言存在，并断言不再存在仅由“数、短、设”生成的 icon 节点。

- [ ] **Step 2: 由用户验证当前单字图标使测试失败**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.BulkSmsNavigationTest`

Expected: FAIL，内容描述节点不存在。

- [ ] **Step 3: 添加 24dp 单色 Vector Drawable**

所有 XML 使用同一结构：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="..." />
</vector>
```

路径采用 Apache 2.0 的 Material Symbols 轮廓，不包含 LocalSend 商标资源；每个文件只含对应语义图形。

- [ ] **Step 4: 集中映射并替换单字导航**

```kotlin
Icon(
    painter = painterResource(destination.iconRes),
    contentDescription = "${destination.label}图标",
)
```

`AppDestination` 增加 `@DrawableRes val iconRes: Int`。

- [ ] **Step 5: 给页面标题与关键操作增加图标**

数据页导入卡使用文件/剪贴板图标；短信页模板、预览和发送使用对应图标；设置页 SIM 与号码列使用 SIM/电话图标。图标颜色统一使用 `MaterialTheme.colorScheme.primary`，错误图标使用 `error`。

- [ ] **Step 6: 由用户验证导航测试通过**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.BulkSmsNavigationTest`

Expected: PASS。

- [ ] **Step 7: 提交本地图标**

```powershell
git add app/src/main/java/com/local/bulksms/ui app/src/main/res/drawable app/src/androidTest/java/com/local/bulksms/ui/BulkSmsNavigationTest.kt
git commit -m "feat: add Material-style icons across app"
```

### Task 7: 发送权限、任务启动和整体反馈接线

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/sms/SmsGateway.kt`
- Test: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/ui/send/SmsScreenTest.kt`

**Interfaces:**
- Produces: `suspend fun createSelectedSendTask(): String?`
- Produces: `fun observeSendTask(taskId: String)`
- Produces: `BulkSmsCallbacks.onRequestSend`
- Consumes: `SmsSendingService.start(context, taskId)`

- [ ] **Step 1: 写失败 ViewModel 测试，验证发送前置条件**

```kotlin
viewModel.selectAllDrafts(false)
assertNull(viewModel.createSelectedSendTask())
assertEquals("请至少选择一条短信", viewModel.state.value.blockingError)
```

另外覆盖未选 SIM、空号码、无手机号列；合法状态只冻结所选行并返回任务 ID。

- [ ] **Step 2: 写失败 Compose 进度测试**

当 `sendProgress = SendProgressUiState(80, 12, 10, 2, true)` 时，断言“正在发送 12/80”和线性进度条存在，模板选择与复选框不可用。终态 `SendProgressUiState(80, 80, 76, 4, false)` 显示“成功 76 条，失败 4 条”。

- [ ] **Step 3: 由用户验证发送入口尚未接线**

Run: `gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.SmsScreenTest`

Expected: FAIL，整体进度字段/发送入口不存在。

- [ ] **Step 4: MainActivity 接入发送权限 launcher**

使用 `ActivityResultContracts.RequestMultiplePermissions()` 请求 `SEND_SMS`，Android 13 及以上同时请求 `POST_NOTIFICATIONS`。只有 `SEND_SMS` 获准才执行：

```kotlin
scope.launch {
    sendFlowViewModel.createSelectedSendTask()?.let { taskId ->
        sendFlowViewModel.observeSendTask(taskId)
        SmsSendingService.start(this@MainActivity, taskId)
    }
}
```

最终确认弹窗仍由 `SmsScreen` 展示；确认后调用 `onRequestSend`。

- [ ] **Step 5: ViewModel 观察 Room 并派生整体进度**

保存当前观察 Job；开始新任务前取消旧 Job。每次 `sendDao.items(taskId)` 发射时更新 `SendProgressUiState.from(statuses)`，任务完成后保留结果，直到数据、模板或新任务改变。

- [ ] **Step 6: SmsScreen 显示整体发送反馈**

运行中显示 `LinearProgressIndicator(progress = { processed / total.toFloat() })` 和计数；完成后使用成功/失败图标显示汇总。运行中禁用模板、模板正文、电话号码列、添加/删除/保存、选择框和发送按钮。

- [ ] **Step 7: 由用户验证发送入口与反馈测试通过**

Run: `gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.SmsScreenTest`

Expected: PASS。

- [ ] **Step 8: 提交端到端接线**

```powershell
git add app/src/main/java/com/local/bulksms/MainActivity.kt app/src/main/java/com/local/bulksms/ui app/src/main/java/com/local/bulksms/sms/SmsGateway.kt app/src/test/java/com/local/bulksms/ui/send app/src/androidTest/java/com/local/bulksms/ui/send/SmsScreenTest.kt
git commit -m "feat: request SMS permission and show send progress"
```

### Task 8: 回归清理与静态交付

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendWorkbenchScreen.kt`（仅移除已废弃回调造成的编译引用；该旧页面不重新设计）
- Modify: 受回调签名影响的现有测试
- Verify: 所有本轮改动文件

**Interfaces:**
- Removes: 生产界面对 `editDraft/setDraftSynced/syncAllDrafts/unsyncAllDrafts` 的调用；领域方法若仍被持久化兼容测试使用则暂不删除。

- [ ] **Step 1: 搜索旧 UI 与占位图标残留**

Run: `rg -n "maskPhone|实时同步|全部同步|全部取消|onDraftChanged|onDraftSyncChanged|label.take\(1\)" app/src/main/java`

Expected: 活跃三页流程无匹配；允许旧领域兼容代码保留同步字段。

- [ ] **Step 2: 检查规格覆盖与源码差异**

Run: `git diff --check`

Expected: 无空白错误。逐项确认 SIM、完整号码、下拉框、两卡片、选择、整体进度、图标和无 Gradle 变更。

- [ ] **Step 3: 给用户提供 Android Studio 验证命令**

建议用户在 Android Studio 先执行 `Sync Project with Gradle Files`，再运行：

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
gradlew.bat connectedDebugAndroidTest
```

人工验证只在测试 SIM 或明确不会产生费用的环境中进行；不点击真实发送确认。

- [ ] **Step 4: 用户返回编译或测试错误后按原始输出修复**

收集完整 Gradle task、首个 Kotlin/Manifest 错误和堆栈；每个错误单独修复，不猜测、不同时改多个无关点。

- [ ] **Step 5: 最终提交只包含源码、测试和设计文档**

```powershell
git status --short
git diff --cached --check
git commit -m "feat: complete selectable SMS sending workflow"
```

不得暂存截图、UI XML 抓取文件或 `gradle/gradle-daemon-jvm.properties`。
