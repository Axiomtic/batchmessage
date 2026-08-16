# 批量短信助手 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个可从 `.xlsx` 或剪贴板导入动态表格、编辑和模板化短信，并通过指定 SIM 在前台服务中批量发送最多 100 条短信的离线 Android 应用。

**Architecture:** 单 Activity + Jetpack Compose，功能按导入、模板、短信草稿、持久化队列和系统短信网关拆分。Room 是任务与发送状态的唯一事实来源，前台服务只消费已冻结的队列；Android 系统 API 通过小接口隔离，使绝大多数测试不触发真实短信。

**Tech Stack:** Kotlin、AGP 9.3.1 内置 Kotlin、Kotlin Compose Compiler Plugin 2.2.10、Gradle 9.5.0、Jetpack Compose BOM 2026.02.01、Room 2.8.4、KSP 2.3.4、Coroutines、Android `SmsManager`、JUnit 4、Compose UI Test。

**Spec:** `docs/superpowers/specs/2026-08-16-android-bulk-sms-design.md`

## Global Constraints

- 应用名固定为“批量短信助手”，包名固定为 `com.local.bulksms`。
- `compileSdk = 37`、`targetSdk = 37`、`minSdk = 26`，使用本机 Android Studio JBR 与 SDK。
- 单次导入最多 100 个数据行；超限必须拒绝，不得静默截断。
- 首版只读取 `.xlsx` 的第一个可见工作表，不回写源文件或剪贴板。
- 应用不预设业务列；无表头时使用“列1、列2……”作为列名和模板变量名。
- 发送前冻结手机号与正文；队列默认每 2 秒提交一条。
- 自动化测试必须使用假的 `SmsGateway`，不得发送真实短信。
- 失败和状态不确定的短信不得自动重试。
- 系统日志不得输出完整手机号或短信正文。

## File Structure

```text
settings.gradle.kts                         仓库与模块声明
build.gradle.kts                            AGP、Compose、KSP、Room 插件版本
gradle.properties                           AndroidX、JVM 与构建配置
gradle/wrapper/*                            Gradle 9.5.0 Wrapper
app/build.gradle.kts                        Android、依赖和测试配置
app/src/main/AndroidManifest.xml            权限、Activity、前台服务与接收器声明
app/src/main/java/com/local/bulksms/
  BulkSmsApplication.kt                     依赖装配与数据库单例
  MainActivity.kt                           Compose 入口和运行时权限结果桥接
  navigation/AppNavHost.kt                  三个主入口与发送步骤导航
  model/ImportedTable.kt                    动态列、行和单元格模型
  model/MessageDraft.kt                     同步/手动短信草稿模型
  model/SendModels.kt                       队列状态、SIM 和发送结果类型
  importdata/TabularTextParser.kt           剪贴板表格解析
  importdata/HeaderDetector.kt              表头检测与唯一列名生成
  importdata/PhoneColumnDetector.kt         手机号列推荐与号码验证
  importdata/XlsxImporter.kt                第一个可见工作表的 OOXML 读取
  template/TemplateRenderer.kt              变量验证、渲染和草稿同步
  data/AppDatabase.kt                       Room 数据库与类型转换
  data/Entities.kt                          模板、任务、草稿和尝试实体
  data/Daos.kt                              Flow 查询和事务更新
  data/BulkSmsRepository.kt                 业务持久化边界
  ui/send/SendFlowViewModel.kt              导入到最终确认的状态机
  ui/send/ImportScreen.kt                    来源选择、表头与手机号列确认
  ui/send/EditableTable.kt                   双向滑动可编辑表格
  ui/send/MessageReviewScreen.kt             简洁短信文本区域列表与同步开关
  ui/template/TemplateViewModel.kt           模板增删改状态
  ui/template/TemplateScreen.kt              模板管理界面
  ui/history/HistoryViewModel.kt             记录筛选与显式重试
  ui/history/HistoryScreen.kt                任务和单条结果界面
  sms/SimSubscriptionProvider.kt             活动 SIM 读取
  sms/SmsGateway.kt                          系统短信抽象
  sms/AndroidSmsGateway.kt                   SmsManager 分段提交与结果聚合
  sms/SmsResultReceiver.kt                   sent PendingIntent 回调入口
  sms/SmsSendService.kt                      specialUse 前台发送服务
  sms/SendNotificationFactory.kt             进度通知与停止动作
  sms/SendQueueCoordinator.kt                持久化状态机与防重复规则
```

---

### Task 1: 可构建的 Compose 项目骨架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/local/bulksms/BulkSmsApplication.kt`
- Create: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Create: `app/src/test/java/com/local/bulksms/ProjectSmokeTest.kt`
- Create: `.gitignore`

**Interfaces:**
- Consumes: 本机 AGP `9.3.1`、Gradle `9.5.0`、API 37。
- Produces: `BulkSmsApplication`、`MainActivity` 和可被后续任务扩展的 `app` 模块。

- [ ] **Step 1: 写入构建配置并生成 Gradle Wrapper**

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
    id("androidx.room") version "2.8.4" apply false
}
```

```kotlin
// app/build.gradle.kts（核心配置）
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.local.bulksms"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.local.bulksms"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

Run:

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat" wrapper --gradle-version 9.5.0
```

- [ ] **Step 2: 写一个会因入口尚不存在而失败的冒烟测试**

```kotlin
class ProjectSmokeTest {
    @Test fun applicationClassExists() {
        assertEquals("BulkSmsApplication", BulkSmsApplication::class.simpleName)
    }
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "com.local.bulksms.ProjectSmokeTest"`

Expected: FAIL，编译错误指出 `BulkSmsApplication` 尚不存在。

- [ ] **Step 4: 实现最小应用入口和主题**

```kotlin
class BulkSmsApplication : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("批量短信助手") } }
    }
}
```

Manifest 将 `BulkSmsApplication` 设为 `android:name`，将 `MainActivity` 设为 launcher；`.gitignore` 忽略 `.gradle/`、`.idea/`、`local.properties`、`**/build/`、`.firecrawl/` 和 `.superpowers/`。

- [ ] **Step 5: 验证骨架并提交**

Run: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: 三个任务均成功，生成 `app/build/outputs/apk/debug/app-debug.apk`。

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "build: scaffold Android bulk SMS app"
```

---

### Task 2: 动态表格、剪贴板解析与手机号列推荐

**Files:**
- Create: `app/src/main/java/com/local/bulksms/model/ImportedTable.kt`
- Create: `app/src/main/java/com/local/bulksms/importdata/TabularTextParser.kt`
- Create: `app/src/main/java/com/local/bulksms/importdata/HeaderDetector.kt`
- Create: `app/src/main/java/com/local/bulksms/importdata/PhoneColumnDetector.kt`
- Create: `app/src/test/java/com/local/bulksms/importdata/TabularImportTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `RawTable`, `ImportedTable`, `TabularTextParser.parse(String): RawTable`、`HeaderDetector.materialize(RawTable, Boolean): ImportedTable`、`PhoneColumnDetector.recommend(ImportedTable): Int?`、`PhoneColumnDetector.isValid(String): Boolean`。

- [ ] **Step 1: 为有/无表头、唯一列名、100 行限制和手机号推荐写失败测试**

```kotlin
@Test fun noHeaderUsesNumberedColumnsAndKeepsFirstRow() {
    val raw = TabularTextParser.parse("13800138000\t张三\r\n13900139000\t李四")
    val table = HeaderDetector.materialize(raw, firstRowIsHeader = false)
    assertEquals(listOf("列1", "列2"), table.columns.map { it.name })
    assertEquals("13800138000", table.rows.first().cells.first())
    assertEquals(0, PhoneColumnDetector.recommend(table))
}

@Test fun duplicateHeadersBecomeUnique() {
    val raw = TabularTextParser.parse("姓名\t姓名\t\n张三\tA\t1")
    val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)
    assertEquals(listOf("姓名", "姓名_2", "列3"), table.columns.map { it.name })
}

@Test fun moreThanOneHundredDataRowsIsRejected() {
    val text = (1..101).joinToString("\n") { "1380013%04d\t姓名$it".format(it) }
    val raw = TabularTextParser.parse(text)
    assertFailsWith<ImportLimitExceeded> {
        HeaderDetector.materialize(raw, firstRowIsHeader = false)
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TabularImportTest"`

Expected: FAIL，所列模型和解析器尚不存在。

- [ ] **Step 3: 实现动态表模型和剪贴板解析**

```kotlin
data class RawTable(
    val rows: List<List<String>>,
    val warnings: List<String> = emptyList(),
)
data class DynamicColumn(val id: Int, val name: String)
data class DynamicRow(val id: Long, val cells: List<String>)
data class ImportedTable(
    val columns: List<DynamicColumn>,
    val rows: List<DynamicRow>,
    val firstRowIsHeader: Boolean,
    val phoneColumnIndex: Int? = null,
)

object TabularTextParser {
    fun parse(text: String): RawTable {
        val rows = text.lineSequence()
            .map { it.removeSuffix("\r").split('\t').map(String::trim) }
            .filterNot { row -> row.all(String::isBlank) }
            .toList()
        require(rows.isNotEmpty()) { "剪贴板中没有表格数据" }
        val width = rows.maxOf(List<String>::size)
        return RawTable(rows.map { it + List(width - it.size) { "" } })
    }
}
```

`HeaderDetector.materialize` 在计算数据行数后执行 100 行限制，列名通过计数器生成唯一值；`PhoneColumnDetector` 去除空格、短横线和括号后按合法值占比推荐唯一最高列，平分时返回 `null`。

- [ ] **Step 4: 运行解析测试并确认通过**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TabularImportTest"`

Expected: PASS。

- [ ] **Step 5: 提交动态表格核心**

```bash
git add app/src/main/java/com/local/bulksms/model app/src/main/java/com/local/bulksms/importdata app/src/test/java/com/local/bulksms/importdata
git commit -m "feat: parse dynamic tabular imports"
```

---

### Task 3: 无第三方运行时依赖的 XLSX 导入器

**Files:**
- Create: `app/src/main/java/com/local/bulksms/importdata/TableImporter.kt`
- Create: `app/src/main/java/com/local/bulksms/importdata/XlsxImporter.kt`
- Create: `app/src/test/java/com/local/bulksms/importdata/XlsxImporterTest.kt`
- Create: `app/src/test/resources/xlsx/README.md`

**Interfaces:**
- Consumes: `RawTable`、Task 2 的 100 行限制。
- Produces: `TableImporter.import(InputStream): RawTable`、`XlsxImporter`。

- [ ] **Step 1: 写第一个可见工作表、共享字符串、内联字符串、数字和超限测试**

```kotlin
@Test fun readsFirstVisibleSheetAndSharedStrings() {
    val bytes = xlsxFixture(
        sheets = listOf(hiddenSheet("Hidden"), visibleSheet("Data", listOf(
            listOf(text("手机号"), text("姓名"), text("金额")),
            listOf(text("13800138000"), inline("张三"), number("120")),
        )))
    )
    assertEquals(
        listOf(listOf("手机号", "姓名", "金额"), listOf("13800138000", "张三", "120")),
        XlsxImporter().import(bytes.inputStream()).rows,
    )
}

@Test fun rejectsUnsupportedOrOversizedWorkbook() {
    assertFailsWith<ImportLimitExceeded> {
        XlsxImporter().import(xlsxWithRows(102).inputStream())
    }
}
```

测试夹具用 `ZipOutputStream` 在内存中生成最小 OOXML 包，覆盖 `workbook.xml`、关系文件、`sharedStrings.xml`、`styles.xml` 和工作表 XML，不提交二进制样本。

- [ ] **Step 2: 运行 XLSX 测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.XlsxImporterTest"`

Expected: FAIL，`XlsxImporter` 尚不存在。

- [ ] **Step 3: 实现 OOXML 读取管线**

```kotlin
fun interface TableImporter {
    fun import(input: InputStream): RawTable
}

class XlsxImporter : TableImporter {
    override fun import(input: InputStream): RawTable {
        val entries = unzipRequiredEntries(input, maxUncompressedBytes = 8 * 1024 * 1024)
        val workbook = parseWorkbook(entries.getValue("xl/workbook.xml"))
        val sheet = workbook.sheets.firstOrNull { !it.hidden }
            ?: error("工作簿没有可见工作表")
        val sheetPath = resolveSheetPath(entries.getValue("xl/_rels/workbook.xml.rels"), sheet.relationshipId)
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val dateStyles = entries["xl/styles.xml"]?.let(::parseDateStyleIndexes).orEmpty()
        return parseSheet(entries.getValue(sheetPath), shared, dateStyles)
    }
}
```

实现约束：

- Zip 条目只允许相对 OOXML 路径，拒绝 `..` 和绝对路径；累计解压上限 8 MiB。
- 单元格引用通过列字母转换为零基索引，缺失单元格补空字符串。
- 支持 shared string、inline string、boolean、普通数字和由样式标记的 Excel 日期。
- 公式单元格使用缓存值；没有缓存值时产生空字符串并返回可展示警告。
- 解出第 102 个原始行时立即抛出 `ImportLimitExceeded`；101 个原始行保留到表头确认阶段，由 `HeaderDetector` 判断它代表“1 个表头 + 100 个数据行”还是“101 个数据行”。

- [ ] **Step 4: 运行 XLSX 与全部导入测试**

Run: `./gradlew.bat testDebugUnitTest --tests "*.importdata.*"`

Expected: PASS，且恶意 Zip 路径、空工作簿和 101 个数据行均被拒绝。

- [ ] **Step 5: 提交 XLSX 导入器**

```bash
git add app/src/main/java/com/local/bulksms/importdata app/src/test/java/com/local/bulksms/importdata app/src/test/resources/xlsx
git commit -m "feat: import first visible XLSX worksheet"
```

---

### Task 4: 模板渲染与草稿同步状态机

**Files:**
- Create: `app/src/main/java/com/local/bulksms/model/MessageDraft.kt`
- Create: `app/src/main/java/com/local/bulksms/template/TemplateRenderer.kt`
- Create: `app/src/test/java/com/local/bulksms/template/TemplateRendererTest.kt`

**Interfaces:**
- Consumes: `ImportedTable`、动态列名和行 ID。
- Produces: `TemplateRenderer.validate`、`TemplateRenderer.render`、`DraftSynchronizer.regenerate`、`DraftSynchronizer.editBody`、`DraftSynchronizer.setSynced`。

- [ ] **Step 1: 写变量验证和同步语义失败测试**

```kotlin
@Test fun manualEditDisablesSyncAndTableRefreshPreservesBody() {
    val original = renderer.renderDraft(row, "{姓名}您好，金额{金额}")
    val edited = DraftSynchronizer.editBody(original, "张三您好，已延期")
    assertFalse(edited.syncWithTable)
    val refreshed = DraftSynchronizer.regenerate(edited, changedRow, template)
    assertEquals("张三您好，已延期", refreshed.currentBody)
}

@Test fun reenablingSyncImmediatelyOverwritesManualBody() {
    val synced = DraftSynchronizer.setSynced(editedDraft, true, changedRow, template)
    assertEquals("张三您好，金额200", synced.currentBody)
    assertTrue(synced.syncWithTable)
}

@Test fun missingVariableBlocksRendering() {
    assertEquals(setOf("日期"), renderer.validate("{姓名} {日期}", listOf("姓名")))
}
```

- [ ] **Step 2: 运行模板测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TemplateRendererTest"`

Expected: FAIL，模板与草稿类型尚不存在。

- [ ] **Step 3: 实现模板和不可变草稿转换**

```kotlin
data class MessageDraft(
    val rowId: Long,
    val phoneNumber: String,
    val generatedBody: String,
    val currentBody: String,
    val syncWithTable: Boolean = true,
    val manuallyEdited: Boolean = false,
)

class TemplateRenderer {
    private val token = Regex("\\{([^{}]+)}")
    fun validate(template: String, columns: List<String>): Set<String> =
        token.findAll(template).map { it.groupValues[1] }.toSet() - columns.toSet()

    fun render(template: String, values: Map<String, String>): String =
        token.replace(template) { match -> values.getValue(match.groupValues[1]) }
}
```

`DraftSynchronizer.regenerate` 只在 `syncWithTable == true` 时更新 `generatedBody` 和 `currentBody`；`editBody` 写入正文并同时设置 `syncWithTable = false`、`manuallyEdited = true`；`setSynced(true, ...)` 立即重新渲染并覆盖正文。

- [ ] **Step 4: 运行模板测试并确认通过**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TemplateRendererTest"`

Expected: PASS。

- [ ] **Step 5: 提交模板核心**

```bash
git add app/src/main/java/com/local/bulksms/model/MessageDraft.kt app/src/main/java/com/local/bulksms/template app/src/test/java/com/local/bulksms/template
git commit -m "feat: render templates and synchronize drafts"
```

---

### Task 5: Room 持久化模型与事务仓库

**Files:**
- Create: `app/src/main/java/com/local/bulksms/model/SendModels.kt`
- Create: `app/src/main/java/com/local/bulksms/data/Entities.kt`
- Create: `app/src/main/java/com/local/bulksms/data/Daos.kt`
- Create: `app/src/main/java/com/local/bulksms/data/AppDatabase.kt`
- Create: `app/src/main/java/com/local/bulksms/data/BulkSmsRepository.kt`
- Modify: `app/src/main/java/com/local/bulksms/BulkSmsApplication.kt`
- Test: `app/src/androidTest/java/com/local/bulksms/data/AppDatabaseTest.kt`

**Interfaces:**
- Consumes: `MessageDraft`。
- Produces: `SendStatus`、`TemplateEntity`、`ImportTaskEntity`、`MessageDraftEntity`、`SendTaskEntity`、`SendItemEntity`、`SendAttemptEntity`、`BulkSmsRepository.freezeQueue(...)`、`claimNext(...)`、`completeAttempt(...)`、`recoverInterruptedAttempts(...)`。

- [ ] **Step 1: 写数据库事务失败测试**

```kotlin
@Test fun freezeQueueCopiesCurrentPhoneAndBody() = runTest {
    val taskId = repository.freezeQueue(importId, simSubscriptionId = 7)
    val items = database.sendDao().items(taskId).first()
    assertEquals("13800138000", items.single().phoneNumber)
    assertEquals("张三您好，金额120", items.single().body)
    assertEquals(SendStatus.PENDING, items.single().status)
}

@Test fun recoveryChangesSubmittingToUncertainOnly() = runTest {
    repository.recoverInterruptedAttempts(taskId)
    assertEquals(SendStatus.UNCERTAIN, sendDao.item(submittingId).status)
    assertEquals(SendStatus.PENDING, sendDao.item(pendingId).status)
}
```

- [ ] **Step 2: 运行数据库测试并确认失败**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest`

Expected: FAIL，Room 实体和仓库尚不存在。

- [ ] **Step 3: 实现实体现状和 DAO 原子操作**

```kotlin
enum class SendStatus { PENDING, SUBMITTING, SUBMITTED, FAILED, UNCERTAIN, CANCELLED }

@Entity(
    tableName = "send_items",
    foreignKeys = [ForeignKey(
        entity = SendTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class SendItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val ordinal: Int,
    val phoneNumber: String,
    val body: String,
    val status: SendStatus,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
)

@Dao
interface SendDao {
    @Query("SELECT * FROM send_items WHERE taskId=:taskId ORDER BY ordinal")
    fun items(taskId: String): Flow<List<SendItemEntity>>

    @Query("UPDATE send_items SET status='SUBMITTING' WHERE id=:id AND status='PENDING'")
    suspend fun claimPending(id: String): Int

    @Query("UPDATE send_items SET status='UNCERTAIN' WHERE taskId=:taskId AND status='SUBMITTING'")
    suspend fun markInterruptedUncertain(taskId: String)
}
```

数据库版本从 1 开始并导出 schema。`freezeQueue` 在一个 `withTransaction` 中复制当前手机号和正文；`claimNext` 只接受 `claimPending(...) == 1` 的项目。

- [ ] **Step 4: 运行数据库与 JVM 测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.data.AppDatabaseTest`

Expected: PASS，并生成 `app/schemas/com.local.bulksms.data.AppDatabase/1.json`。

- [ ] **Step 5: 提交持久化层**

```bash
git add app/src/main/java/com/local/bulksms/data app/src/main/java/com/local/bulksms/model/SendModels.kt app/src/main/java/com/local/bulksms/BulkSmsApplication.kt app/src/androidTest app/schemas
git commit -m "feat: persist templates drafts and send queues"
```

---

### Task 6: 导入与可编辑表格界面

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/send/ImportScreen.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/send/EditableTable.kt`
- Create: `app/src/test/java/com/local/bulksms/ui/send/SendFlowViewModelTest.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/send/EditableTableTest.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`

**Interfaces:**
- Consumes: `TabularTextParser`、`XlsxImporter`、`HeaderDetector`、`PhoneColumnDetector`、`BulkSmsRepository`。
- Produces: `SendFlowUiState`、`SendFlowViewModel.importClipboard`、`importXlsx`、`setFirstRowIsHeader`、`selectPhoneColumn`、`editCell` 和 `EditableTable`。

- [ ] **Step 1: 写 ViewModel 与 Compose 失败测试**

```kotlin
@Test fun togglingHeaderRebuildsColumnsAndPreservesRawRows() = runTest {
    viewModel.importClipboard("手机号\t姓名\n13800138000\t张三")
    viewModel.setFirstRowIsHeader(false)
    val state = viewModel.state.value
    assertEquals(listOf("列1", "列2"), state.table!!.columns.map { it.name })
    assertEquals("手机号", state.table.rows.first().cells.first())
}
```

```kotlin
@Test fun tableScrollsBothDirectionsAndCellIsEditable() {
    composeRule.setContent { EditableTable(wideTable, onCellChanged = recorder::record) }
    composeRule.onNodeWithText("张三").performClick().performTextReplacement("张三丰")
    assertEquals(CellEdit(0, 1, "张三丰"), recorder.last)
}
```

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SendFlowViewModelTest"`

Expected: FAIL，ViewModel 和界面尚不存在。

- [ ] **Step 3: 实现发送流程状态和 SAF/剪贴板导入**

```kotlin
data class SendFlowUiState(
    val rawTable: RawTable? = null,
    val table: ImportedTable? = null,
    val detectedHeader: Boolean = false,
    val selectedPhoneColumn: Int? = null,
    val importWarnings: List<String> = emptyList(),
    val blockingError: String? = null,
)

fun editCell(rowId: Long, columnIndex: Int, value: String) = updateState { state ->
    state.copy(table = state.table?.copy(rows = state.table.rows.map { row ->
        if (row.id != rowId) row else row.copy(cells = row.cells.toMutableList().also { it[columnIndex] = value })
    }))
}
```

`ImportScreen` 使用 `ActivityResultContracts.OpenDocument()` 限制 MIME 为 XLSX，使用 `ClipboardManager` 读取文本。`EditableTable` 外层使用垂直滚动，行内容共享一个水平 `ScrollState`；列头可点选手机号列；单元格使用单行 `BasicTextField`。

- [ ] **Step 4: 运行 ViewModel、Compose 和导入回归测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.EditableTableTest`

Expected: PASS；表格横纵滚动、单元格修改和手机号列选择均可操作。

- [ ] **Step 5: 提交导入界面**

```bash
git add app/src/main/java/com/local/bulksms/ui/send app/src/main/java/com/local/bulksms/MainActivity.kt app/src/test/java/com/local/bulksms/ui/send app/src/androidTest/java/com/local/bulksms/ui/send
git commit -m "feat: add editable import table flow"
```

---

### Task 7: 模板管理与简洁短信确认页

**Files:**
- Create: `app/src/main/java/com/local/bulksms/ui/template/TemplateViewModel.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/template/TemplateScreen.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt`
- Create: `app/src/test/java/com/local/bulksms/ui/template/TemplateViewModelTest.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/ui/send/MessageReviewScreenTest.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`

**Interfaces:**
- Consumes: `TemplateRenderer`、`DraftSynchronizer`、Room 模板和草稿 DAO。
- Produces: 模板 CRUD；`SendFlowViewModel.selectTemplate`、`editDraft`、`setDraftSynced`；`MessageReviewScreen`。

- [ ] **Step 1: 写模板 CRUD 与手动编辑交互失败测试**

```kotlin
@Test fun deletingSelectedTemplateClearsSelectionButKeepsDraftsForReview() = runTest {
    viewModel.selectTemplate(template.id)
    viewModel.delete(template.id)
    assertNull(viewModel.state.value.selectedTemplateId)
}
```

```kotlin
@Test fun typingInBodyUnchecksSyncWithoutAddingNameCard() {
    composeRule.setContent { MessageReviewScreen(state, onEdit = onEdit, onSyncChanged = onSync) }
    composeRule.onNodeWithText("张三您好，金额120").performTextReplacement("张三您好，已延期")
    composeRule.onNodeWithText("与表同步").assertIsNotChecked()
    composeRule.onNodeWithTag("recipient-title-card").assertDoesNotExist()
}
```

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.TemplateViewModelTest"`

Expected: FAIL，模板 ViewModel 和确认页尚不存在。

- [ ] **Step 3: 实现模板管理和短信列表**

```kotlin
@Composable
fun MessageReviewItem(
    ordinal: Int,
    maskedPhone: String,
    draft: MessageDraft,
    onBodyChanged: (String) -> Unit,
    onSyncChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft.currentBody,
            onValueChange = onBodyChanged,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$ordinal · $maskedPhone", style = MaterialTheme.typography.labelSmall)
            Row { Checkbox(draft.syncWithTable, onCheckedChange = onSyncChanged); Text("与表同步") }
        }
    }
}
```

短信项不得添加用户名标题或外层 Card。模板编辑器在正文下显示本次导入可用变量按钮，点击插入光标位置；缺失变量在进入短信确认前阻断。

- [ ] **Step 4: 运行模板、草稿和 Compose 测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.ui.send.MessageReviewScreenTest`

Expected: PASS，且手动编辑取消同步、重新勾选立即覆盖。

- [ ] **Step 5: 提交模板和确认页**

```bash
git add app/src/main/java/com/local/bulksms/ui/template app/src/main/java/com/local/bulksms/ui/send app/src/test app/src/androidTest
git commit -m "feat: manage templates and review message drafts"
```

---

### Task 8: SIM 选择、权限和系统短信网关

**Files:**
- Create: `app/src/main/java/com/local/bulksms/sms/SimSubscriptionProvider.kt`
- Create: `app/src/main/java/com/local/bulksms/sms/SmsGateway.kt`
- Create: `app/src/main/java/com/local/bulksms/sms/AndroidSmsGateway.kt`
- Create: `app/src/main/java/com/local/bulksms/sms/SmsResultReceiver.kt`
- Create: `app/src/test/java/com/local/bulksms/sms/SmsResultAggregatorTest.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/sms/SimSubscriptionProviderTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`

**Interfaces:**
- Consumes: `SendItemEntity`、选中的 subscription ID。
- Produces: `SimOption`、`SimSubscriptionProvider.active(): List<SimOption>`、`SmsGateway.submit(SmsSubmission)`、`SmsSubmissionResult`。

- [ ] **Step 1: 写分段结果聚合和 SIM 列表失败测试**

```kotlin
@Test fun multipartSucceedsOnlyWhenEveryPartSucceeds() {
    val aggregator = SmsResultAggregator(expectedParts = 3)
    assertNull(aggregator.record(0, Activity.RESULT_OK))
    assertNull(aggregator.record(1, SmsManager.RESULT_ERROR_NO_SERVICE))
    val result = requireNotNull(aggregator.record(2, Activity.RESULT_OK))
    assertFalse(result.success)
    assertEquals(SmsManager.RESULT_ERROR_NO_SERVICE, result.errorCode)
}
```

```kotlin
@Test fun activeSubscriptionsMapToStableRadioOptions() {
    val options = provider.active()
    assertTrue(options.all { it.subscriptionId >= 0 && it.displayLabel.isNotBlank() })
}
```

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SmsResultAggregatorTest"`

Expected: FAIL，短信抽象和聚合器尚不存在。

- [ ] **Step 3: 实现权限、SIM 读取和 SmsManager 适配器**

```kotlin
data class SmsSubmission(val itemId: String, val subscriptionId: Int, val phone: String, val body: String)
data class SmsSubmissionResult(val success: Boolean, val errorCode: Int? = null)

interface SmsGateway {
    suspend fun submit(submission: SmsSubmission): SmsSubmissionResult
    fun segmentCount(body: String, subscriptionId: Int): Int
}

class AndroidSmsGateway(private val context: Context) : SmsGateway {
    private fun manager(subscriptionId: Int): SmsManager =
        context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)

    override fun segmentCount(body: String, subscriptionId: Int): Int =
        manager(subscriptionId).divideMessage(body).size
}
```

`submit` 为每个分段创建不可变且唯一 requestCode 的 `PendingIntent`，由 `SmsResultReceiver` 将 `itemId`、part index 和 resultCode 交给进程内聚合器；取消协程时注销等待对象但不宣称系统短信被撤回。

Manifest 明确声明 `SEND_SMS`、`READ_PHONE_STATE`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE` 和 `FOREGROUND_SERVICE_SPECIAL_USE`。权限界面按系统版本只请求适用项；拒绝关键权限时不冻结队列。

- [ ] **Step 4: 运行聚合、权限和 SIM 测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.sms.SimSubscriptionProviderTest`

Expected: PASS；单卡、双卡和无卡假数据都映射正确，任一短信分段失败会使整条失败。

- [ ] **Step 5: 提交系统短信边界**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/local/bulksms/sms app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt app/src/test/java/com/local/bulksms/sms app/src/androidTest/java/com/local/bulksms/sms
git commit -m "feat: select SIM and submit multipart SMS"
```

---

### Task 9: specialUse 前台服务和防重复队列

**Files:**
- Create: `app/src/main/java/com/local/bulksms/sms/SendQueueCoordinator.kt`
- Create: `app/src/main/java/com/local/bulksms/sms/SendNotificationFactory.kt`
- Create: `app/src/main/java/com/local/bulksms/sms/SmsSendService.kt`
- Create: `app/src/test/java/com/local/bulksms/sms/SendQueueCoordinatorTest.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/sms/SmsSendServiceTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `BulkSmsRepository.claimNext`、`SmsGateway.submit`、冻结的 `SendTaskEntity`。
- Produces: `SendQueueCoordinator.run(taskId)`、`SmsSendService.start(context, taskId)`、通知中的停止 action。

- [ ] **Step 1: 写顺序发送、两秒节流、停止和恢复失败测试**

```kotlin
@Test fun queueClaimsBeforeSubmitAndWaitsBetweenItems() = runTest(testDispatcher) {
    coordinator.run(taskId)
    advanceUntilIdle()
    assertEquals(listOf("claim:a", "submit:a", "delay:2000", "claim:b", "submit:b"), events)
}

@Test fun interruptedSubmittingBecomesUncertainAndIsNotResent() = runTest {
    repository.seed(item(status = SendStatus.SUBMITTING))
    coordinator.run(taskId)
    assertEquals(SendStatus.UNCERTAIN, repository.item(itemId).status)
    assertTrue(fakeGateway.submissions.isEmpty())
}

@Test fun stopCancelsOnlyPendingItems() = runTest {
    coordinator.stop(taskId)
    assertEquals(SendStatus.CANCELLED, repository.item(pendingId).status)
    assertEquals(SendStatus.SUBMITTED, repository.item(submittedId).status)
}
```

- [ ] **Step 2: 运行队列测试并确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SendQueueCoordinatorTest"`

Expected: FAIL，协调器和服务尚不存在。

- [ ] **Step 3: 实现持久化循环和前台服务**

```kotlin
class SendQueueCoordinator(
    private val repository: BulkSmsRepository,
    private val gateway: SmsGateway,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private val taskLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun run(taskId: String) = taskLocks.getOrPut(taskId) { Mutex() }.withLock {
        repository.recoverInterruptedAttempts(taskId)
        while (currentCoroutineContext().isActive) {
            val item = repository.claimNext(taskId) ?: break
            val result = gateway.submit(item.toSubmission())
            repository.completeAttempt(item.id, result)
            if (repository.hasPending(taskId)) delayMillis(2_000)
        }
        repository.completeTaskIfTerminal(taskId)
    }
}
```

`SmsSendService` 在 `onStartCommand` 首先调用 `ServiceCompat.startForeground`，类型为 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`，随后在 service scope 中运行协调器。服务维护 `taskId -> Job`，重复 start intent 不创建第二个 job；协调器的 keyed `Mutex` 提供第二层串行保护。通知每次状态变化时更新，并通过显式 service intent 处理停止。服务完成后调用 `stopForeground(STOP_FOREGROUND_REMOVE)` 和 `stopSelf()`。

Manifest 服务声明：

```xml
<service
    android:name=".sms.SmsSendService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-confirmed local SIM SMS batch queue" />
</service>
```

- [ ] **Step 4: 运行队列和服务生命周期测试**

Run: `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.sms.SmsSendServiceTest`

Expected: PASS；通知可见、停止 action 有效、服务重建不重发 `SUBMITTING` 项。

- [ ] **Step 5: 提交后台发送服务**

```bash
git add app/src/main/java/com/local/bulksms/sms app/src/main/AndroidManifest.xml app/src/test/java/com/local/bulksms/sms app/src/androidTest/java/com/local/bulksms/sms
git commit -m "feat: send persistent queue in foreground service"
```

---

### Task 10: 导航、最终确认、进度与历史重试

**Files:**
- Create: `app/src/main/java/com/local/bulksms/navigation/AppNavHost.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/local/bulksms/ui/history/HistoryScreen.kt`
- Create: `app/src/androidTest/java/com/local/bulksms/EndToEndFakeSmsTest.kt`
- Modify: `app/src/main/java/com/local/bulksms/MainActivity.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/SendFlowViewModel.kt`
- Modify: `app/src/main/java/com/local/bulksms/ui/send/MessageReviewScreen.kt`

**Interfaces:**
- Consumes: 前述全部模块。
- Produces: 完整“发送 / 模板 / 记录”应用流程、费用确认、进度观察、失败/不确定项显式重试。

- [ ] **Step 1: 写假短信端到端失败测试**

```kotlin
@Test fun importEditReviewChooseSimConfirmAndFinish() {
    launchWithFakeGateway()
    onNodeWithText("从剪贴板粘贴").performClick()
    onNodeWithText("张三").performTextReplacement("张三丰")
    onNodeWithText("保存表格并生成短信").performClick()
    onNodeWithText("SIM 1").performClick()
    onNodeWithText("确认并发送").performClick()
    onNodeWithText("已提交 1").assertExists()
    assertEquals("张三丰您好，金额120", fakeGateway.submissions.single().body)
}

@Test fun retryRequiresExplicitSelectionAndConfirmation() {
    seedFailedAndUncertainItems()
    onNodeWithText("记录").performClick()
    onNodeWithText("重试所选 2 条").assertIsNotEnabled()
    selectFailedAndUncertainRows()
    onNodeWithText("重试所选 2 条").performClick()
    onNodeWithText("确认重试").performClick()
    assertEquals(2, repository.newRetryAttemptCount())
}
```

- [ ] **Step 2: 运行端到端测试并确认失败**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.bulksms.EndToEndFakeSmsTest`

Expected: FAIL，完整导航和历史页尚未连接。

- [ ] **Step 3: 连接完整导航和最终确认**

```kotlin
data class SendConfirmation(
    val recipientCount: Int,
    val totalSmsSegments: Int,
    val selectedSim: SimOption,
)

fun confirmAndStart(context: Context) = viewModelScope.launch {
    val error = validateBeforeFreeze(state.value)
    if (error != null) return@launch publishError(error)
    val taskId = repository.freezeQueue(state.value.importId, state.value.selectedSimId!!)
    SmsSendService.start(context, taskId)
}
```

最终确认对话框展示收件人数、短信分段总数、所选 SIM 和“发送后无法撤回”。历史页默认按任务时间倒序，任务详情可筛选 `FAILED` 与 `UNCERTAIN`；用户勾选后创建新的尝试，不修改旧记录。

- [ ] **Step 4: 运行端到端测试和全量验证**

Run: `./gradlew.bat testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug`

Expected: 全部通过，Debug APK 生成；自动化日志中不包含测试用完整手机号或短信正文。

- [ ] **Step 5: 提交完整应用流程**

```bash
git add app/src/main app/src/test app/src/androidTest app/schemas
git commit -m "feat: complete bulk SMS workflow and history"
```

---

### Task 11: 真机安全验证与交付说明

**Files:**
- Create: `README.md`
- Create: `docs/testing/device-test-checklist.md`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: 可安装的 Debug APK 和具有实体 SIM 的 Android 手机。
- Produces: 可复现的安装/使用说明、真机检查清单和最终 APK 路径。

- [ ] **Step 1: 写交付文档的可检查内容**

```markdown
# 批量短信助手

## 安装
1. 在 Android Studio 中打开仓库并等待 Gradle 同步。
2. 连接自己的 Android 手机并允许 USB 调试。
3. 运行 `./gradlew.bat installDebug`。

## 首次安全测试
1. 只导入一个自己的手机号码。
2. 选择实际要计费的 SIM 卡。
3. 确认分段数为 1 后发送。
4. 熄屏等待，并核对通知与记录页均显示“已提交”。
```

设备清单必须逐项记录：权限拒绝/重新授权、单卡、双卡切换、飞行模式、长短信、熄屏、切换 App、停止剩余任务、失败后手动重试和服务异常恢复。

- [ ] **Step 2: 执行自动验证并记录输出**

Run: `./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: 安装到真机并只向自有号码发送一条短信**

Run: `./gradlew.bat installDebug`

Expected: 手机成功安装“批量短信助手”；用户手动授权后，一条短短信从选定 SIM 提交，熄屏后通知和记录状态保持一致。

- [ ] **Step 4: 完成设备检查清单并再次构建**

Run: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL，设备清单不存在未解释的失败项。

- [ ] **Step 5: 提交交付文档**

```bash
git add README.md docs/testing/device-test-checklist.md app/src/main/res/values/strings.xml
git commit -m "docs: add installation and device safety checks"
```

## Final Verification

- [ ] 运行 `./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug` 并确认成功。
- [ ] 在可用的 API 37.1 模拟器上运行 `./gradlew.bat connectedDebugAndroidTest`。
- [ ] 确认 `git status --short` 只包含用户明确保留的文件。
- [ ] 确认 `app/build/outputs/apk/debug/app-debug.apk` 存在且大小非零。
- [ ] 按 `docs/testing/device-test-checklist.md` 完成至少一次自有号码真机验证；未连接真机时明确标注该项仍需用户执行，不虚报结果。
