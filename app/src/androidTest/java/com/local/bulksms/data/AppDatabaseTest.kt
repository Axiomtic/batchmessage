package com.local.bulksms.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
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

        val taskId = repository.freezeQueue(importId, simSubscriptionId = 7)
        val items = database.sendDao().items(taskId).first()

        assertEquals(1, items.size)
        assertEquals("13800138000", items.single().phoneNumber)
        assertEquals("张三您好，金额120", items.single().body)
        assertEquals(SendStatus.PENDING, items.single().status)
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
