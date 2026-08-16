# Fixed SMS Workbench Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有分散的导入、模板和短信确认界面重构为固定竖屏单页工作台，并持久化用户最后一次表格、模板和草稿同步状态。

**Architecture:** Room 2 增加单例工作区实体保存动态二维表和当前选择，现有草稿表继续保存每条短信。`SendFlowViewModel` 成为主工作台唯一业务状态源，Compose 主页面只分配固定区域高度，表格和短信列表各自滚动；完整模板管理作为唯一独立页面。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room 2.8.4、Coroutines StateFlow、Navigation Compose、JUnit 4、Compose UI Test、API 37.1 模拟器。

**Spec:** `docs/superpowers/specs/2026-08-16-fixed-workbench-redesign.md`

## Global Constraints

- 应用名保持“批量短信助手”，包名保持 `com.local.bulksms`。
- `compileSdk = 37`、`targetSdk = 37`、`minSdk = 26`。
- Activity 只支持竖屏；主工作台自身不得上下滚动。
- 表格和短信列表必须分别提供内部滚动，表格列宽根据内容动态变化。
- 首次启动显示 3 列 × 5 行的张三、李四示例和“服务到期提醒”默认模板。
- 后续启动恢复用户最后保存的工作区，不得再次用示例覆盖。
- 导入最多 100 个数据行，覆盖现有表格前必须确认。
- 已取消同步的短信不得被表格、模板或导入刷新覆盖。
- 自动化测试不得通过真实 `SmsGateway` 发送短信。
- 保留未跟踪的 `gradle/gradle-daemon-jvm.properties`，不得把它纳入本计划提交。

---

### Task 1: Room 当前工作区与默认数据

**Files:**
- Create: `app/src/main/java/com/local/bulksms/model/WorkspaceSnapshot.kt`
- Modify: `app/src/main/java/com/local/bulksms/data/Entities.kt`
- Modify: `app/src/main/java/com/local/bulksms/data/Daos.kt`
- Modify: `app/src/main/java/com/local/bulksms/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/local/bulksms/data/BulkSmsRepository.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/data/AppDatabaseTest.kt`
- Modify: `app/build.gradle.kts`
- Generate: `app/schemas/com.local.bulksms.data.AppDatabase/2.json`

**Interfaces:**
- Consumes: `TemplateEntity`、`MessageDraftEntity`、Room v1 schema。
- Produces: `WorkspaceSnapshot`、`WorkspaceEntity`、`WorkspaceDao`、`BulkSmsRepository.loadOrCreateWorkspace()`、`saveWorkspace()` 和 `loadDraftsOnce()`。

- [ ] **Step 1: 写工作区默认值、往返和 v1→v2 迁移失败测试**

```kotlin
@Test fun firstWorkspaceContainsSampleRowsAndDefaultTemplate() = runTest {
    val workspace = repository.loadOrCreateWorkspace()
    assertEquals(listOf("名字", "电话", "服务到期日期"), workspace.rawRows.first())
    assertEquals("张三", workspace.rawRows[1][0])
    assertEquals("服务到期提醒", database.templateDao().byId(workspace.selectedTemplateId!!)?.name)
}

@Test fun savedWorkspaceRoundTripsNestedRows() = runTest {
    val saved = WorkspaceSnapshot.sample().copy(rawRows = listOf(listOf("姓名", "备注"), listOf("王五", "已续期")))
    repository.saveWorkspace(saved)
    assertEquals(saved, repository.loadOrCreateWorkspace())
}
```

迁移测试使用 `MigrationTestHelper` 先创建 v1 数据库，再以 `AppDatabase.MIGRATION_1_2` 打开并断言 `workspace` 表存在且旧模板仍可读取。

- [ ] **Step 2: 运行数据库测试并确认失败**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: FAIL，`WorkspaceSnapshot`、`workspace` 表和迁移尚不存在。

- [ ] **Step 3: 实现工作区实体、嵌套列表转换器和迁移**

```kotlin
data class WorkspaceSnapshot(
    val importId: String,
    val rawRows: List<List<String>>,
    val detectedHeader: Boolean,
    val firstRowIsHeader: Boolean,
    val phoneColumnIndex: Int?,
    val selectedTemplateId: String?,
    val selectedTemplateName: String,
    val selectedTemplateBody: String,
    val selectedSubscriptionId: Int?,
)
```

`WorkspaceEntity` 固定主键 `current`；Room 升级为 version 2，迁移 SQL 明确创建上述字段。`loadOrCreateWorkspace()` 在事务中仅当工作区不存在时插入 `WorkspaceSnapshot.sample()` 和默认模板。

- [ ] **Step 4: 运行数据库测试并确认通过**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: PASS，且生成 schema 2。

- [ ] **Step 5: 提交持久化层**

```bash
git add app/build.gradle.kts app/src/main/java/com/local/bulksms/model/WorkspaceSnapshot.kt app/src/main/java/com/local/bulksms/data app/src/androidTest/java/com/local/bulksms/data/AppDatabaseTest.kt app/schemas
git commit -m "feat: persist current SMS workspace"
```

---

### Task 2: 主工作台状态、直接表格操作和同步规则

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `WorkspaceSnapshot` 和 repository 方法、`HeaderDetector`、`TemplateRenderer`、`DraftSynchronizer`。
- Produces: `PendingImport`、`hasExistingData`、`addRow()`、`addColumn()`、`deleteLastRow()`、`deleteLastColumn()`、`clearTable()`、`editHeader()`、`updateTemplateBody()`、`syncAllDrafts()`、`unsyncAllDrafts()`、`confirmPendingImport()` 和 `cancelPendingImport()`。

- [ ] **Step 1: 写直接编辑、覆盖保护、批量同步和恢复失败测试**

```kotlin
@Test fun defaultStateStartsWithThreeColumnsFiveRowsAndDrafts() {
    val state = SendFlowViewModel().state.value
    assertEquals(3, state.table!!.columns.size)
    assertEquals(5, state.table.rows.size)
    assertEquals(2, state.drafts.size)
}

@Test fun importedRowsWaitForConfirmationWhenTableHasData() {
    val viewModel = SendFlowViewModel()
    viewModel.requestClipboardImport("名字\t电话\n王五\t13700137000")
    assertNotNull(viewModel.state.value.pendingImport)
    assertEquals("张三", viewModel.state.value.table!!.rows.first().cells.first())
}

@Test fun unsyncedDraftSurvivesConfirmedImport() {
    val viewModel = SendFlowViewModel()
    viewModel.editDraft(0L, "保留这条")
    viewModel.requestClipboardImport("名字\t电话\n王五\t13700137000")
    viewModel.confirmPendingImport()
    assertEquals("保留这条", viewModel.state.value.drafts.first { it.rowId == 0L }.currentBody)
}

@Test fun liveTemplateEditAndBulkSyncRegenerateMappedDrafts() {
    val viewModel = SendFlowViewModel()
    viewModel.unsyncAllDrafts()
    viewModel.updateTemplateBody("{名字}的新提醒")
    viewModel.syncAllDrafts()
    assertEquals("张三的新提醒", viewModel.state.value.drafts.first().currentBody)
}
```

- [ ] **Step 2: 运行 ViewModel 测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SendFlowViewModelTest" --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: FAIL，新状态和操作尚不存在。

- [ ] **Step 3: 实现状态转换和串行持久化**

使用 `Channel<SendFlowUiState>(Channel.CONFLATED)` 让 repository 保存始终串行且最终写入最新状态。导入确认应用新 `RawTable` 时按 row ID 优先保留旧的不同步草稿，再为其余非空行渲染同步草稿；超出新表范围的不同步草稿追加为独立草稿。

```kotlin
fun unsyncAllDrafts() = updateState { current ->
    current.copy(drafts = current.drafts.map { it.copy(syncWithTable = false) })
}

fun syncAllDrafts() = updateState { current ->
    val rows = current.table?.rows.orEmpty().associateBy { it.id }
    current.copy(drafts = current.drafts.map { draft ->
        rows[draft.rowId]?.let { row -> DraftSynchronizer.setSynced(draft, true, row, current.selectedTemplateBody.orEmpty()) }
            ?: draft.copy(syncWithTable = false)
    })
}
```

- [ ] **Step 4: 运行 ViewModel 与模板渲染回归测试**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SendFlowViewModelTest" --tests "*.TemplateRendererTest" --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: PASS。

- [ ] **Step 5: 提交工作台状态层**

```bash
git add app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt
git commit -m "feat: manage live fixed-workbench state"
```

---

### Task 3: 绿色主题与内容自适应表格

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/theme/BulkSmsTheme.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/EditableTable.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/ui/send/EditableTableTest.kt`

**Interfaces:**
- Consumes: Task 2 的表头/单元格回调。
- Produces: `BulkSmsTheme`、`contentAwareColumnWidth()`、可编辑表头、动态列宽和竖屏锁定。

- [ ] **Step 1: 写列宽和可编辑表头失败测试**

```kotlin
@Test fun longerColumnContentGetsMoreWidthWithinBounds() {
    assertTrue(contentAwareColumnWidth(listOf("名字", "张三")) < contentAwareColumnWidth(listOf("备注", "这是一段明显更长的内容")))
    assertEquals(240.dp, contentAwareColumnWidth(listOf("超长".repeat(100))))
}
```

Compose 测试替换 `header-0-editor` 文本并断言 `HeaderEdit(0, "客户姓名")`；保留横纵滚动语义断言。

- [ ] **Step 2: 运行表格测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.EditableTableTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: FAIL，动态宽度和表头编辑尚不存在。

- [ ] **Step 3: 实现主题、动态宽度和表头编辑**

```kotlin
internal fun contentAwareColumnWidth(values: List<String>): Dp {
    val units = values.maxOfOrNull { value -> value.sumOf { if (it.code > 0xff) 2 else 1 } } ?: 1
    return (units * 7.2f + 24f).dp.coerceIn(76.dp, 240.dp)
}
```

`EditableTable` 为每列预计算宽度，并把同一宽度应用到表头和所有单元格；Manifest 的 `MainActivity` 设置 `android:screenOrientation="portrait"`。

- [ ] **Step 4: 运行表格单元和模拟器测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.EditableTableTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: PASS。

- [ ] **Step 5: 提交主题和表格**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml app/src/main/java/com/local/bulksms/ui/theme app/src/main/java/com/local/bulksms/ui/send/EditableTable.kt app/src/androidTest/java/com/local/bulksms/ui/send/EditableTableTest.kt
git commit -m "feat: add green adaptive editable table"
```

---

### Task 4: 固定单页工作台与导入确认

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/send/SendWorkbenchScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/ImportScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/send/SendWorkbenchScreenTest.kt`

**Interfaces:**
- Consumes: Task 2 的全部工作台回调、Task 3 的表格与主题。
- Produces: 固定区域 `SendWorkbenchScreen`、覆盖确认对话框、现场模板编辑器、批量同步按钮、SIM Radio Button 和固定发送栏。

- [ ] **Step 1: 写固定布局和交互失败测试**

```kotlin
@Test fun workbenchShowsAllSectionsWithoutOuterScroll() {
    composeRule.setContent { SendWorkbenchScreen(state = sampleState, callbacks = fakeCallbacks) }
    composeRule.onNodeWithTag("send-workbench").assertExists().assert(hasNoScrollAction())
    composeRule.onNodeWithText("数据表格").assertExists()
    composeRule.onNodeWithText("现场模板").assertExists()
    composeRule.onNodeWithText("待发送短信").assertExists()
    composeRule.onNodeWithText("确认并发送").assertExists()
}
```

另写测试点击“剪切板”后断言“覆盖现有数据？”对话框出现，点击取消后调用 `cancelPendingImport`。

- [ ] **Step 2: 运行工作台 Compose 测试并确认失败**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.SendWorkbenchScreenTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: FAIL，工作台尚不存在。

- [ ] **Step 3: 实现固定工作台**

顶层使用无 `verticalScroll` 的 `Column`；数据区 `weight(1.05f)`、模板区固定紧凑高度、短信区 `weight(0.95f)`，底部发送栏固定。`EditableTable` 和 `LazyColumn` 只消费各自被分配的高度。短信项继续不使用用户名 Card。

- [ ] **Step 4: 运行工作台、表格和短信确认测试**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.SendWorkbenchScreenTest,com.local.bulksms.ui.send.EditableTableTest,com.local.bulksms.ui.send.MessageReviewScreenTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: PASS，且主工作台没有外层滚动语义。

- [ ] **Step 5: 提交主页面**

```bash
git add app/src/main/java/com/local/bulksms/ui/send app/src/androidTest/java/com/local/bulksms/ui/send
git commit -m "feat: compose fixed portrait SMS workbench"
```

---

### Task 5: 模板覆盖、另存为和独立模板页面

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/template/TemplateViewModel.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/template/TemplateScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Modify: `app/src/test/java/com/local/bulksms/ui/template/TemplateViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/ui/template/TemplateScreenTest.kt`

**Interfaces:**
- Consumes: repository `TemplateDao`、`SendFlowViewModel` 当前模板选择和 `BulkSmsTheme`。
- Produces: `TemplateViewModel.overwrite()`、`saveAs(name)`、完整模板管理页导航，以及主页面现场模板的覆盖/另存为对话框。

- [ ] **Step 1: 写覆盖保持 ID、另存为产生新 ID 的失败测试**

```kotlin
@Test fun overwriteKeepsIdWhileSaveAsCreatesNewId() = runTest {
    viewModel.selectTemplate("existing")
    viewModel.setEditorBody("修改正文")
    viewModel.overwrite()
    viewModel.saveAs("新模板")
    runCurrent()
    assertEquals(listOf("existing", "generated-new-id"), saved.map { it.id })
}
```

- [ ] **Step 2: 运行模板测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TemplateViewModelTest" --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: FAIL，显式覆盖与另存为接口尚不存在。

- [ ] **Step 3: 实现模板动作和页面导航**

`overwrite()` 仅在已选模板存在时更新原 ID；`saveAs(name)` 总是调用 `idFactory()`。`MainActivity` 使用 `NavHost` 的 `workbench` 与 `templates` 两个 route，不增加底部导航；返回工作台时复用 Activity 级 ViewModel。

- [ ] **Step 4: 运行模板单元与 Compose 测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.template.TemplateScreenTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: PASS。

- [ ] **Step 5: 提交模板和入口连接**

```bash
git add app/src/main/java/com/local/bulksms/MainActivity.kt app/src/main/java/com/local/bulksms/ui/template app/src/test/java/com/local/bulksms/ui/template app/src/androidTest/java/com/local/bulksms/ui/template
git commit -m "feat: edit and save templates from workbench"
```

---

### Task 6: 模拟器视觉验收与全量回归

**Files:**
- Modify as failures require: only files already listed in Tasks 1–5
- Do not modify: `gradle/gradle-daemon-jvm.properties`

**Interfaces:**
- Consumes: 完成后的固定工作台。
- Produces: 可安装 Debug APK、API 37.1 竖屏截图和完整验证结果。

- [ ] **Step 1: 运行 JVM、Lint 和 Debug 构建**

Run: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: BUILD SUCCESSFUL，Lint 无 error，APK 存在。

- [ ] **Step 2: 运行 API 37.1 模拟器测试**

Run: `$env:ADB_VENDOR_KEYS='C:\Users\zhenx\.android'; ./gradlew.bat connectedDebugAndroidTest --project-prop=kotlin.compiler.execution.strategy=in-process`

Expected: 所有 instrumentation tests PASS；测试使用假数据，不发送真实短信。

- [ ] **Step 3: 安装并截取主工作台竖屏效果**

Run: `$env:ADB_VENDOR_KEYS='C:\Users\zhenx\.android'; ./gradlew.bat installDebug --project-prop=kotlin.compiler.execution.strategy=in-process`

启动 `com.local.bulksms/.MainActivity`，截屏并检查顶部栏、表格、现场模板、短信标题、SIM 区和发送按钮同时可见；外层不滚动，表格与短信区域可独立滑动。

- [ ] **Step 4: 检查工作树和最终差异**

Run: `git diff --check && git status --short`

Expected: 无空白错误；只有 `gradle/gradle-daemon-jvm.properties` 保持为既有未跟踪文件。

- [ ] **Step 5: 提交仅由验收发现的修正**

```bash
git add app/src/main app/src/test app/src/androidTest app/schemas
git commit -m "fix: polish fixed workbench layout"
```

