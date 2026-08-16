# Three-Page SMS Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed single-page workbench with persistent Data, SMS, and Settings pages using letter-addressed columns, compact table editing, inline template management, and bottom navigation.

**Architecture:** Keep one `MainActivity`, one repository-backed `SendFlowViewModel`, and one navigation host so all three pages share state. Split page UI into focused composables; keep parsing, template rendering, draft synchronization, and persistence in existing domain/ViewModel layers.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, StateFlow, Room, JUnit 4, AndroidX Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-08-16-three-page-sms-workflow-redesign.md`

## Global Constraints

- Portrait orientation only.
- Green Material 3 palette.
- At most 100 imported data rows.
- Column labels and template variables use Excel-style addresses (`A`…`Z`, `AA`…) and template matching is case-insensitive.
- `忽略首行` defaults to enabled; enabled means the first raw row is excluded from data.
- Unsynced SMS drafts must survive table and template refreshes unchanged.
- Never send a real SMS during tests or visual verification.
- Do not stage or modify `gradle/gradle-daemon-jvm.properties`.

---

### Task 1: Letter-addressed columns and case-insensitive templates

**Files:**
- Create: `app/src/main/java/com/local/bulksms/model/ColumnAddress.kt`
- Create: `app/src/test/java/com/local/bulksms/model/ColumnAddressTest.kt`
- Modify: `app/src/main/java/com/local/bulksms/importdata/HeaderDetector.kt`
- Modify: `app/src/main/java/com/local/bulksms/template/TemplateRenderer.kt`
- Modify: `app/src/main/java/com/local/bulksms/model/WorkspaceSnapshot.kt`
- Modify: `app/src/test/java/com/local/bulksms/importdata/TabularImportTest.kt`
- Modify: `app/src/test/java/com/local/bulksms/template/TemplateRendererTest.kt`
- Modify: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`

**Interfaces:**
- Produces: `fun columnAddress(index: Int): String`
- Produces: `HeaderDetector.materialize(raw, firstRowIsHeader)` whose `DynamicColumn.name` values are always letter addresses.
- Produces: `TemplateRenderer` validation and rendering that normalize variable keys with `uppercase()`.

- [ ] **Step 1: Write failing column-address and template tests**

```kotlin
@Test fun addressesContinueAfterZ() {
    assertEquals("A", columnAddress(0))
    assertEquals("Z", columnAddress(25))
    assertEquals("AA", columnAddress(26))
    assertEquals("AB", columnAddress(27))
}

@Test fun materializeUsesLettersAndOnlyHeaderModeDropsFirstRow() {
    val raw = RawTable(listOf(listOf("名字", "电话"), listOf("张三", "13800138000")))
    val ignored = HeaderDetector.materialize(raw, firstRowIsHeader = true)
    val retained = HeaderDetector.materialize(raw, firstRowIsHeader = false)
    assertEquals(listOf("A", "B"), ignored.columns.map { it.name })
    assertEquals(listOf("A", "B"), retained.columns.map { it.name })
    assertEquals(1, ignored.rows.size)
    assertEquals(2, retained.rows.size)
}

@Test fun variablesAreCaseInsensitive() {
    val renderer = TemplateRenderer(listOf("A", "B"), phoneColumnIndex = 1)
    val draft = renderer.renderDraft(DynamicRow(0, listOf("张三", "13800138000")), "{a}-{B}")
    assertEquals("张三-13800138000", draft.currentBody)
}
```

- [ ] **Step 2: Run the focused tests and verify the old name-based behavior fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest --tests com.local.bulksms.model.ColumnAddressTest --tests com.local.bulksms.importdata.TabularImportTest --tests com.local.bulksms.template.TemplateRendererTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: FAIL because `columnAddress` is missing, materialized names use imported headers, and `{a}` is rejected.

- [ ] **Step 3: Implement letter addressing and normalized template lookup**

```kotlin
fun columnAddress(index: Int): String {
    require(index >= 0)
    var value = index + 1
    return buildString {
        while (value > 0) {
            value--
            append(('A'.code + value % 26).toChar())
            value /= 26
        }
    }.reversed()
}
```

In `HeaderDetector.materialize`, replace header-derived names with:

```kotlin
val names = (0 until width).map(::columnAddress)
```

In `TemplateRenderer`, normalize both declared columns and token keys:

```kotlin
private fun String.variableKey(): String = trim().uppercase()

fun validate(template: String, columns: List<String>): Set<String> {
    val known = columns.mapTo(mutableSetOf()) { it.variableKey() }
    return token.findAll(template)
        .map { it.groupValues[1].variableKey() }
        .filterNot(known::contains)
        .toSet()
}
```

Build the render-value map with uppercase keys and look up `match.groupValues[1].variableKey()`. Change the default template to use `{A}` and `{C}`.

- [ ] **Step 4: Run focused tests and all JVM tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/model/ColumnAddress.kt app/src/main/java/com/local/bulksms/importdata/HeaderDetector.kt app/src/main/java/com/local/bulksms/template/TemplateRenderer.kt app/src/main/java/com/local/bulksms/model/WorkspaceSnapshot.kt app/src/test
git commit -m "feat: address SMS fields by column letters"
```

### Task 2: Arbitrary row and column deletion

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`

**Interfaces:**
- Consumes: `columnAddress(index)` and letter-addressed `ImportedTable` from Task 1.
- Produces: `fun deleteRow(rowId: Long)`.
- Produces: `fun deleteColumn(columnIndex: Int)`.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test fun deletingSpecificRowKeepsOtherRowsAndUnsyncedDraft() {
    val viewModel = SendFlowViewModel()
    viewModel.editDraft(1L, "保留李四")
    viewModel.deleteRow(0L)
    assertEquals(listOf("李四", "", "", ""), viewModel.state.value.table!!.rows.map { it.cells[0] })
    assertEquals("保留李四", viewModel.state.value.drafts.first { !it.syncWithTable }.currentBody)
}

@Test fun deletingPhoneColumnClearsPhoneSelection() {
    val viewModel = SendFlowViewModel()
    viewModel.deleteColumn(1)
    assertEquals(listOf("A", "B"), viewModel.state.value.table!!.columns.map { it.name })
    assertNull(viewModel.state.value.selectedPhoneColumn)
    assertNull(viewModel.state.value.table!!.phoneColumnIndex)
}
```

- [ ] **Step 2: Run the focused test and verify the methods are missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.local.bulksms.ui.send.SendFlowViewModelTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: compilation failure for `deleteRow` and `deleteColumn`.

- [ ] **Step 3: Implement indexed deletion with header offset and phone-column adjustment**

```kotlin
fun deleteRow(rowId: Long) {
    val current = mutableState.value
    val raw = current.rawTable ?: return
    val table = current.table ?: return
    if (table.rows.size <= 1) return
    val rawIndex = rowId.toInt() + if (table.firstRowIsHeader) 1 else 0
    if (rawIndex !in raw.rows.indices) return
    rematerializeCurrent(raw.copy(rows = raw.rows.filterIndexed { index, _ -> index != rawIndex }))
}

fun deleteColumn(columnIndex: Int) {
    val current = mutableState.value
    val raw = current.rawTable ?: return
    val table = current.table ?: return
    if (table.columns.size <= 1 || columnIndex !in table.columns.indices) return
    val selected = current.selectedPhoneColumn
    val adjusted = when {
        selected == null -> null
        selected == columnIndex -> null
        selected > columnIndex -> selected - 1
        else -> selected
    }
    val updatedRaw = raw.copy(rows = raw.rows.map { row -> row.filterIndexed { index, _ -> index != columnIndex } })
    rematerializeCurrent(updatedRaw, selectedPhoneColumn = adjusted)
}
```

Extend `rematerializeCurrent` to accept an explicit selected phone column and preserve unsynced drafts through `refreshDrafts`.

- [ ] **Step 4: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.local.bulksms.ui.send.SendFlowViewModelTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt
git commit -m "feat: delete selected table rows and columns"
```

### Task 3: Three-page application shell

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/AppDestination.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/data/DataScreen.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/MainActivityTest.kt`

**Interfaces:**
- Produces: `enum class AppDestination(val route: String, val label: String)` with DATA, SMS, SETTINGS.
- Produces: `@Composable fun BulkSmsApp(state, callbacks)` using a fixed `NavigationBar` and three routes.
- Produces page composable entry points with the exact callback shapes completed by Tasks 4–6.

- [ ] **Step 1: Replace the old entrance assertions with failing bottom-navigation tests**

```kotlin
@Test fun launchStartsOnDataAndBottomNavigationOpensEveryPage() {
    composeRule.onNodeWithText("导入数据").assertExists()
    composeRule.onNodeWithText("短信").performClick()
    composeRule.onNodeWithText("短信预览").assertExists()
    composeRule.onNodeWithText("设置").performClick()
    composeRule.onNodeWithText("忽略首行").assertExists()
}
```

- [ ] **Step 2: Run the activity test and verify the old workbench fails it**

Run:

```powershell
$env:ADB_VENDOR_KEYS='C:\Users\zhenx\.android'
.\gradlew.bat connectedDebugAndroidTest --project-prop=android.testInstrumentationRunnerArguments.class=com.local.bulksms.MainActivityTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: FAIL because the bottom navigation and new page headings do not exist.

- [ ] **Step 3: Implement the shared navigation shell**

```kotlin
enum class AppDestination(val route: String, val label: String) {
    DATA("data", "数据"), SMS("sms", "短信"), SETTINGS("settings", "设置")
}
```

`BulkSmsApp` owns `rememberNavController()`, renders `NavHost(startDestination = DATA.route)`, and renders a fixed `NavigationBar`. Use `popUpTo(DATA.route) { saveState = true }`, `launchSingleTop = true`, and `restoreState = true` when switching destinations. `MainActivity` only creates ViewModels/callbacks and calls `BulkSmsApp`.

- [ ] **Step 4: Run the activity test**

Run the command from Step 2.

Expected: BUILD SUCCESSFUL with the three destinations reachable.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/MainActivity.kt app/src/main/java/com/local/bulksms/ui app/src/androidTest/java/com/local/bulksms/MainActivityTest.kt
git commit -m "feat: navigate between data SMS and settings"
```

### Task 4: Compact data page and spreadsheet controls

**Files:**
- Replace: `app/src/main/java/com/local/bulksms/ui/send/EditableTable.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/data/DataScreen.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/data/DataScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/ui/send/EditableTableTest.kt`

**Interfaces:**
- Produces: `TableCallbacks(onCellChanged, onAddRow, onAddColumn, onDeleteRow, onDeleteColumn)`.
- Produces test tags: `import-file`, `import-clipboard`, `add-row`, `add-column`, `row-label-N`, `column-label-X`.

- [ ] **Step 1: Write failing Compose tests for the new data page**

```kotlin
@Test fun dataPageUsesImportCardsAndEdgeAddButtons() {
    composeRule.onNodeWithTag("import-file").assertExists()
    composeRule.onNodeWithTag("import-clipboard").assertExists()
    composeRule.onNodeWithTag("add-column").performClick()
    composeRule.onNodeWithTag("add-row").performClick()
    assertEquals(1, addedColumns)
    assertEquals(1, addedRows)
    composeRule.onNodeWithText("+ 行").assertDoesNotExist()
}

@Test fun longPressingLabelsRequestsSpecificDeletion() {
    composeRule.onNodeWithTag("row-label-1").performTouchInput { longClick() }
    composeRule.onNodeWithText("删除第 2 行？").assertExists()
    composeRule.onNodeWithText("取消").performClick()
    composeRule.onNodeWithTag("column-label-B").performTouchInput { longClick() }
    composeRule.onNodeWithText("删除第 B 列？").assertExists()
}
```

- [ ] **Step 2: Run the data/table tests and verify they fail**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --project-prop=android.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.data.DataScreenTest,com.local.bulksms.ui.send.EditableTableTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: FAIL because import cards, axis labels, long-press dialogs, and edge add buttons do not exist.

- [ ] **Step 3: Implement compact table axes and import cards**

Use a 36dp row-axis width, content-aware data columns, 38dp data row height, and `combinedClickable(onLongClick = ...)` for row/column labels. The header row renders a green add control after the last column; a final table row renders the green add-row control. Both add controls use a 40dp circular hit target without a resting border.

`DataScreen` owns the file picker and clipboard read, renders two equal `ElevatedCard`s labelled `文件` and `剪贴板`, and owns deletion confirmation dialogs. Only dialog confirmation invokes the corresponding callback.

- [ ] **Step 4: Run data/table tests**

Run the command from Step 2.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/ui/data app/src/main/java/com/local/bulksms/ui/send/EditableTable.kt app/src/androidTest/java/com/local/bulksms/ui/data app/src/androidTest/java/com/local/bulksms/ui/send/EditableTableTest.kt
git commit -m "feat: edit compact letter-addressed data table"
```

### Task 5: Inline template lifecycle and SMS page

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/template/TemplateViewModel.kt`
- Modify: `app/src/test/java/com/local/bulksms/ui/template/TemplateViewModelTest.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SmsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/components/RoundedActionIcon.kt`
- Create: `app/src/test/java/com/local/bulksms/ui/send/TemplateLifecycleTest.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/send/SmsScreenTest.kt`

**Interfaces:**
- Adds `TemplateUiState.savedBody: String` and computed `val isDirty: Boolean get() = editorBody != savedBody`.
- Produces: `fun create(name: String): TemplateEntity?`, `fun saveSelected(): TemplateEntity?`, `fun deleteSelected(): String?` on `TemplateViewModel`; return values let `MainActivity` update `SendFlowViewModel` immediately.
- Produces: `@Composable fun RoundedActionIcon(kind: ADD | REMOVE, onClick)` with a 40dp circular hit area, 14dp rounded-line glyph, 3dp stroke, and 850ms release fade.

- [ ] **Step 1: Write failing template lifecycle tests**

```kotlin
@Test fun saveOnlyEnablesAfterBodyChangesAndResetsAfterSave() {
    val templates = MutableStateFlow(listOf(defaultTemplate))
    val viewModel = TemplateViewModel(templates, { saved += it }, {}, backgroundScope)
    runCurrent()
    viewModel.selectTemplate(defaultTemplate.id)
    assertFalse(viewModel.state.value.isDirty)
    viewModel.setEditorBody("{A}新正文")
    assertTrue(viewModel.state.value.isDirty)
    assertEquals(defaultTemplate.id, viewModel.saveSelected()!!.id)
    assertFalse(viewModel.state.value.isDirty)
}

@Test fun createSelectAndDeleteNeverLeaveZeroTemplates() {
    val templates = MutableStateFlow(listOf(defaultTemplate))
    val viewModel = TemplateViewModel(
        templates = templates,
        saveTemplate = { saved -> templates.value = templates.value.filterNot { it.id == saved.id } + saved },
        deleteTemplate = { id -> templates.value = templates.value.filterNot { it.id == id } },
        scope = backgroundScope,
    )
    runCurrent()
    viewModel.selectTemplate(defaultTemplate.id)
    val created = viewModel.create("新模板")!!
    runCurrent()
    assertEquals("新模板", viewModel.state.value.editorName)
    viewModel.deleteSelected()
    runCurrent()
    assertEquals(1, viewModel.state.value.templates.size)
    assertNull(viewModel.deleteSelected())
    assertEquals(1, viewModel.state.value.templates.size)
}
```

Continue using the existing `TemplateViewModel` constructor injection for template flow/save/delete in tests rather than a Room database; production uses `TemplateViewModel.fromDao(repository.templateDao)`.

- [ ] **Step 2: Run focused lifecycle tests and verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.local.bulksms.ui.send.TemplateLifecycleTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: compilation failure for `isDirty`, `create`, `saveSelected`, and `deleteSelected`.

- [ ] **Step 3: Implement dirty tracking and safe template persistence**

When selecting a template, set both `editorBody` and `savedBody`. `setEditorBody` changes only the editable body. `saveSelected` persists the selected entity and then updates `savedBody`. `create` constructs and persists a new empty-body entity, selects it, and returns it. `deleteSelected` returns null without mutation when only one template remains; otherwise it deletes the selected entity, selects the first remaining entity, and returns that next ID. `MainActivity` mirrors template selection/body changes into `SendFlowViewModel` so synchronized drafts refresh live.

- [ ] **Step 4: Implement SMS page and rounded action controls**

Draw the glyph with Canvas rather than font characters:

```kotlin
Canvas(Modifier.size(14.dp)) {
    val stroke = 3.dp.toPx()
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
    if (kind == ADD) drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke, StrokeCap.Round)
}
```

Wrap it in a 40dp circular `Modifier` using `collectIsPressedAsState()` and `animateColorAsState` with immediate pressed color and `tween(850)` when released. `SmsScreen` renders selector, add/remove controls, body editor, variable chips, dirty-gated save button, message list, bulk sync controls, and fixed send footer. Remove visible sync text from `MessageReviewItem`; retain `contentDescription = "与表同步"`.

- [ ] **Step 5: Run lifecycle and SMS Compose tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.local.bulksms.ui.send.TemplateLifecycleTest --project-prop=kotlin.compiler.execution.strategy=in-process
.\gradlew.bat connectedDebugAndroidTest --project-prop=android.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.SmsScreenTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: both commands report BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/ui/send app/src/main/java/com/local/bulksms/ui/components app/src/test/java/com/local/bulksms/ui/send app/src/androidTest/java/com/local/bulksms/ui/send
git commit -m "feat: manage templates from SMS preview page"
```

### Task 6: Settings page and complete callback wiring

**Files:**
- Modify: `app/src/main/java/com/local/bulksms/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/bulksms/MainActivityTest.kt`

**Interfaces:**
- Consumes: `setFirstRowIsHeader`, `selectPhoneColumn`, `selectSubscription`, and current table/SIM state.
- Produces: settings controls with tags `ignore-first-row`, `phone-column-X`, `sim-ID`.

- [ ] **Step 1: Write failing settings tests**

```kotlin
@Test fun settingsEditsSharedParsingAndRadioSelections() {
    composeRule.onNodeWithTag("ignore-first-row").performClick()
    assertFalse(ignoreFirstRow)
    composeRule.onNodeWithTag("phone-column-C").performClick()
    assertEquals(2, selectedPhoneColumn)
    composeRule.onNodeWithTag("sim-7").performClick()
    assertEquals(7, selectedSubscription)
}
```

- [ ] **Step 2: Run settings/activity tests and verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --project-prop=android.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.settings.SettingsScreenTest,com.local.bulksms.MainActivityTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: FAIL because the settings controls or their callbacks do not exist yet.

- [ ] **Step 3: Implement settings controls and MainActivity callback graph**

Render `Switch(checked = state.table?.firstRowIsHeader == true)` with user-facing label “忽略首行”. Render one `RadioButton` per table column and one per SIM option. Wire all Data, SMS, and Settings callbacks through `BulkSmsApp` to the repository-backed `SendFlowViewModel`. Keep final SMS submission callback inert until the foreground send service task is implemented.

- [ ] **Step 4: Run settings/activity tests**

Run the command from Step 2.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/local/bulksms/MainActivity.kt app/src/main/java/com/local/bulksms/ui/BulkSmsApp.kt app/src/main/java/com/local/bulksms/ui/settings app/src/androidTest/java/com/local/bulksms
git commit -m "feat: configure import and SIM settings"
```

### Task 7: Full regression and emulator visual verification

**Files:**
- Modify only if a failing test or visual defect identifies a concrete issue.
- Do not commit: `bulk-sms-*.png`, `bulk-sms-*.xml`, `gradle/gradle-daemon-jvm.properties`.

**Interfaces:**
- Consumes the completed three-page app.
- Produces a verified debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Run all JVM tests, lint, and assemble**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: BUILD SUCCESSFUL with no lint errors.

- [ ] **Step 2: Run all emulator tests**

```powershell
$env:ADB_VENDOR_KEYS='C:\Users\zhenx\.android'
.\gradlew.bat connectedDebugAndroidTest --project-prop=kotlin.compiler.execution.strategy=in-process
```

Expected: all tests finish with zero failures. No test invokes the real SMS gateway.

- [ ] **Step 3: Install and inspect all three pages**

```powershell
.\gradlew.bat installDebug --project-prop=kotlin.compiler.execution.strategy=in-process
& 'C:\Users\zhenx\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell am start -n com.local.bulksms/.MainActivity
```

Capture screenshots with `adb shell screencap` plus `adb pull`, then inspect them with the image viewer. Verify bottom navigation, data-table axes, compact rows, template controls, button press colors, settings radios, and system-bar insets.

- [ ] **Step 4: Check the diff and commit final corrections**

```powershell
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "fix: polish three-page SMS workflow"
```

Expected: source changes are committed; generated screenshots and `gradle/gradle-daemon-jvm.properties` remain untracked.
