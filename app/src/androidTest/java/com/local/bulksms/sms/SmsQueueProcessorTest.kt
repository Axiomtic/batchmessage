package com.local.bulksms.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.bulksms.data.AppDatabase
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsQueueProcessorTest {
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
    fun processorContinuesAfterFailureAndCompletesQueue() = runTest {
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
        val taskId = repository.freezeQueue("import-1", 7, setOf(1L, 2L))
        val results = ArrayDeque(
            listOf(
                SmsSubmissionResult(success = true),
                SmsSubmissionResult(success = false, errorCode = 42),
            ),
        )
        val gateway = object : SmsGateway {
            override suspend fun submit(submission: SmsSubmission): SmsSubmissionResult = results.removeFirst()
            override fun segmentCount(body: String, subscriptionId: Int): Int = 1
        }

        SmsQueueProcessor(repository, gateway).process(taskId)

        assertEquals(
            listOf(SendStatus.SUBMITTED, SendStatus.FAILED),
            database.sendDao().itemsOnce(taskId).map { it.status },
        )
    }

    @Test
    fun intervalIsAppliedOnlyBetweenAdjacentMessages() = runTest {
        fun draft(rowId: Long) = MessageDraft(
            rowId = rowId,
            phoneNumber = "1380013800$rowId",
            generatedBody = "正文$rowId",
            currentBody = "正文$rowId",
            columnNames = listOf("手机号"),
            phoneColumnIndex = 0,
        )
        repository.saveDrafts("import-interval", listOf(draft(1L), draft(2L), draft(3L)))
        val taskId = repository.freezeQueue("import-interval", 7, setOf(1L, 2L, 3L))
        val waits = mutableListOf<Long>()
        val gateway = object : SmsGateway {
            override suspend fun submit(submission: SmsSubmission) = SmsSubmissionResult(success = true)
            override fun segmentCount(body: String, subscriptionId: Int): Int = 1
        }

        SmsQueueProcessor(
            repository = repository,
            gateway = gateway,
            sendIntervalMillis = 1_500L,
            waitBetweenMessages = { waits += it },
        ).process(taskId)

        assertEquals(listOf(1_500L, 1_500L), waits)
    }
}
