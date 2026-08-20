package com.local.bulksms.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendStatus
import com.local.bulksms.model.WorkspaceSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private lateinit var database: AppDatabase
    private lateinit var repository: BulkSmsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BulkSmsRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun migrationFromVersionOneKeepsTemplatesAndAddsWorkspace() {
        migrationHelper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO templates (id, name, body, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>("old-template", "旧模板", "旧正文", 1L, 1L),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "migration-test",
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { migrated ->
            migrated.query("SELECT name FROM templates WHERE id = 'old-template'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("旧模板", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM workspace").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFromVersionTwoAddsBackupPhoneColumns() {
        migrationHelper.createDatabase("migration-2", 2).apply {
            execSQL(
                "INSERT INTO workspace (id, importId, rawRowsJson, detectedHeader, firstRowIsHeader, " +
                    "phoneColumnIndex, selectedTemplateId, selectedTemplateName, selectedTemplateBody, " +
                    "selectedSubscriptionId, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "current",
                    "import-1",
                    "[[]]",
                    1,
                    1,
                    1,
                    null,
                    "",
                    "",
                    null,
                    1L,
                ),
            )
            execSQL(
                "INSERT INTO message_drafts (id, importId, rowId, phoneNumber, generatedBody, currentBody, " +
                    "syncWithTable, manuallyEdited, columnNames, phoneColumnIndex) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "draft-1",
                    "import-1",
                    0L,
                    "13800138000",
                    "正文",
                    "正文",
                    1,
                    0,
                    "[]",
                    0,
                ),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "migration-2",
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        ).use { migrated ->
            migrated.query("SELECT backupPhoneColumnIndex FROM workspace WHERE id = 'current'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(null, cursor.getString(0))
            }
            migrated.query("SELECT backupPhoneNumber, backupPhoneColumnIndex FROM message_drafts WHERE id = 'draft-1'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals(null, cursor.getString(1))
            }
        }
    }

    @Test
    fun migrationFromVersionThreeAddsSendHistoryTable() {
        migrationHelper.createDatabase("migration-3", 3).apply {
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "migration-3",
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        ).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM send_history").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun firstWorkspaceContainsSampleRowsAndDefaultTemplate() = runTest {
        val workspace = repository.loadOrCreateWorkspace()

        assertEquals(listOf("名字", "电话", "服务到期日期"), workspace.rawRows.first())
        assertEquals("张三", workspace.rawRows[1][0])
        assertEquals("李四", workspace.rawRows[2][0])
        assertEquals(5, workspace.rawRows.drop(1).size)
        assertEquals(
            "服务到期提醒",
            database.templateDao().byId(requireNotNull(workspace.selectedTemplateId))?.name,
        )
    }

    @Test
    fun savedWorkspaceRoundTripsNestedRows() = runTest {
        val saved = WorkspaceSnapshot.sample().copy(
            importId = "restored-import",
            rawRows = listOf(
                listOf("姓名", "备注"),
                listOf("王五", "已续期"),
            ),
            phoneColumnIndex = null,
            selectedSubscriptionId = 12,
        )

        repository.saveWorkspace(saved)

        assertEquals(saved, repository.loadOrCreateWorkspace())
    }

    @Test
    fun savingDraftListReplacesRowsRemovedByTableRefresh() = runTest {
        fun draft(rowId: Long) = MessageDraft(
            rowId = rowId,
            phoneNumber = "13800138000",
            generatedBody = "正文$rowId",
            currentBody = "正文$rowId",
            columnNames = listOf("手机号"),
            phoneColumnIndex = 0,
        )
        repository.saveDrafts("import-1", listOf(draft(1L), draft(2L)))

        repository.saveDrafts("import-1", listOf(draft(1L)))

        assertEquals(listOf(1L), database.draftDao().byImportOnce("import-1").map { it.rowId })
    }

    @Test
    fun messageDraftRoundTripPreservesRenderingContext() = runTest {
        val draft = MessageDraft(
            rowId = 10L,
            phoneNumber = "13800138000",
            generatedBody = "张三您好，金额120",
            currentBody = "张三您好，金额120",
            columnNames = listOf("手机号", "姓名", "金额"),
            phoneColumnIndex = 0,
        )

        repository.saveDraft("import-1", draft)

        val restored = database.draftDao().byImport("import-1").first().single()
        assertEquals(draft.columnNames, restored.columnNames)
        assertEquals(draft.phoneColumnIndex, restored.phoneColumnIndex)
        assertEquals(draft.currentBody, restored.currentBody)
    }

    @Test
    fun freezeQueueCopiesCurrentPhoneAndBody() = runTest {
        val importId = "import-1"
        repository.saveDraft(
            importId,
            MessageDraft(
                rowId = 10L,
                phoneNumber = "13800138000",
                generatedBody = "张三您好，金额100",
                currentBody = "张三您好，金额120",
                columnNames = listOf("手机号", "姓名", "金额"),
                phoneColumnIndex = 0,
            ),
        )

        val taskId = repository.freezeQueue(importId, simSubscriptionId = 7, selectedRowIds = setOf(10L))
        val items = database.sendDao().items(taskId).first()

        assertEquals(1, items.size)
        assertEquals("13800138000", items.single().phoneNumber)
        assertEquals("张三您好，金额120", items.single().body)
        assertEquals(SendStatus.PENDING, items.single().status)
    }

    @Test
    fun freezeQueueCopiesOnlySelectedDrafts() = runTest {
        fun draft(rowId: Long, phone: String) = MessageDraft(
            rowId = rowId,
            phoneNumber = phone,
            generatedBody = "正文$rowId",
            currentBody = "正文$rowId",
            columnNames = listOf("手机号"),
            phoneColumnIndex = 0,
        )
        repository.saveDrafts(
            "import-1",
            listOf(draft(1L, "13800138000"), draft(2L, "13900139000")),
        )

        val taskId = repository.freezeQueue("import-1", 7, setOf(2L))

        assertEquals(
            listOf("13900139000"),
            database.sendDao().itemsOnce(taskId).map { it.phoneNumber },
        )
    }

    @Test
    fun recoveryChangesSubmittingToUncertainOnly() = runTest {
        val task = SendTaskEntity(id = "task-1", importId = "import-1", simSubscriptionId = 7)
        database.sendDao().insertTask(task)
        database.sendDao().insertItems(
            listOf(
                SendItemEntity(
                    id = "submitting",
                    taskId = task.id,
                    ordinal = 0,
                    phoneNumber = "13800138000",
                    body = "正文",
                    status = SendStatus.SUBMITTING,
                ),
                SendItemEntity(
                    id = "pending",
                    taskId = task.id,
                    ordinal = 1,
                    phoneNumber = "13900139000",
                    body = "正文2",
                    status = SendStatus.PENDING,
                ),
            ),
        )

        repository.recoverInterruptedAttempts(task.id)

        assertEquals(SendStatus.UNCERTAIN, database.sendDao().item("submitting")?.status)
        assertEquals(SendStatus.PENDING, database.sendDao().item("pending")?.status)
    }

    @Test
    fun claimNextClaimsEachPendingItemAtMostOnce() = runTest {
        val task = SendTaskEntity(id = "task-1", importId = "import-1", simSubscriptionId = 7)
        database.sendDao().insertTask(task)
        database.sendDao().insertItems(
            listOf(
                SendItemEntity("first", task.id, 0, "13800138000", "一", SendStatus.PENDING),
                SendItemEntity("second", task.id, 1, "13900139000", "二", SendStatus.PENDING),
            ),
        )

        val first = repository.claimNext(task.id)
        val second = repository.claimNext(task.id)
        val third = repository.claimNext(task.id)

        assertNotNull(first)
        assertEquals("first", first?.id)
        assertEquals(SendStatus.SUBMITTING, first?.status)
        assertEquals("second", second?.id)
        assertEquals(SendStatus.SUBMITTING, second?.status)
        assertEquals(null, third)
    }
}
