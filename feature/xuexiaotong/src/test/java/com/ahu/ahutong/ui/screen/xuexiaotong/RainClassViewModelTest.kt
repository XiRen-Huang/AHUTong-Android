package com.ahu.ahutong.ui.screen.xuexiaotong

import com.ahu.ahutong.data.rainclass.RainClassAssignment
import com.ahu.ahutong.data.rainclass.RainClassLoginResult
import com.ahu.ahutong.data.rainclass.RainClassQrCode
import com.ahu.ahutong.data.rainclass.RainClassQrStatus
import com.ahu.ahutong.data.rainclass.RainClassSession
import com.ahu.ahutong.data.rainclass.RainClassSessionExpiredException
import com.ahu.ahutong.data.rainclass.RainClassStore
import com.ahu.ahutong.data.rainclass.RainClassSyncResult
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.Work
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RainClassViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = System.currentTimeMillis()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached assignments are restored without waiting for sync`() = runTest(dispatcher) {
        val store = FakeStore(assignments = mutableListOf(assignment()), lastSyncValue = now)
        val session = FakeSession(loggedIn = true)
        val reminders = FakeReminders()

        val viewModel = RainClassViewModel(session, store, reminders, dispatcher)
        advanceUntilIdle()

        assertTrue(viewModel.loggedIn.value)
        assertEquals(1, viewModel.works.value.size)
        assertEquals(now, viewModel.lastSync.value)
        assertEquals(0, session.syncCalls)
    }

    @Test
    fun `login success syncs and reschedules reminders`() = runTest(dispatcher) {
        val store = FakeStore(lastSyncValue = now)
        val session = FakeSession(loggedIn = false)
        val reminders = FakeReminders()
        val viewModel = RainClassViewModel(session, store, reminders, dispatcher)
        advanceUntilIdle()

        viewModel.beginLogin()
        advanceUntilIdle()

        assertTrue(viewModel.loggedIn.value)
        assertTrue(session.syncCalls > 0)
        assertTrue(reminders.reschedules > 0)
        assertEquals(null, viewModel.qrPng.value)
    }

    @Test
    fun `login failure exposes an error and stays signed out`() = runTest(dispatcher) {
        val session = FakeSession(loggedIn = false).apply {
            loginResult = RainClassLoginResult.Failure("二维码失效")
        }
        val viewModel = RainClassViewModel(
            session,
            FakeStore(lastSyncValue = now),
            FakeReminders(),
            dispatcher
        )
        advanceUntilIdle()

        viewModel.beginLogin()
        advanceUntilIdle()

        assertEquals(false, viewModel.loggedIn.value)
        assertEquals("二维码失效", viewModel.error.value)
    }

    @Test
    fun `expired session sync clears sign in and reschedules`() = runTest(dispatcher) {
        val session = FakeSession(loggedIn = true).apply {
            syncError = RainClassSessionExpiredException("HTTP 302")
        }
        val reminders = FakeReminders()
        val viewModel = RainClassViewModel(
            session,
            FakeStore(lastSyncValue = now),
            reminders,
            dispatcher
        )
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        assertEquals(false, viewModel.loggedIn.value)
        assertEquals("登录已过期，请重新登录", viewModel.error.value)
        assertTrue(reminders.reschedules > 0)
    }

    @Test
    fun `logout clears data and reschedules`() = runTest(dispatcher) {
        val session = FakeSession(loggedIn = true)
        val reminders = FakeReminders()
        val store = FakeStore(
            assignments = mutableListOf(assignment()),
            lastSyncValue = now
        )
        val viewModel = RainClassViewModel(session, store, reminders, dispatcher)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(false, viewModel.loggedIn.value)
        assertEquals(true, session.clearedWithCachedData)
        assertTrue(viewModel.works.value.isEmpty())
        assertTrue(reminders.reschedules > 0)
    }

    private fun assignment(): RainClassAssignment = RainClassAssignment(
        id = "rainclass:1:2",
        classroomId = 1,
        courseId = 10,
        courseName = "课程",
        leafId = 2,
        title = "作业",
        leafType = 3,
        startTs = null,
        endTs = now + 3_600_000L,
        scoreDeadlineTs = null
    )

    private class FakeStore(
        var assignments: MutableList<RainClassAssignment> = mutableListOf(),
        var lastSyncValue: Long = 0L
    ) : RainClassStore {
        override fun courses() = emptyList<com.ahu.ahutong.data.rainclass.RainClassCourse>()
        override fun assignments(): List<RainClassAssignment> = assignments.toList()
        override fun lastSync(): Long = lastSyncValue

        override fun setDone(id: String, done: Boolean) {
            val index = assignments.indexOfFirst { it.id == id }
            if (index >= 0) assignments[index] = assignments[index].copy(done = done)
        }
    }

    private class FakeReminders : ChaoxingReminders {
        var reschedules = 0
        override fun cancelAll() = Unit
        override fun scheduleAll() = Unit
        override fun rescheduleAll() {
            reschedules++
        }

        override fun sendTest() = Unit
    }

    private class FakeSession(var loggedIn: Boolean) : RainClassSession {
        var loginResult: RainClassLoginResult = RainClassLoginResult.Success
        var syncResult = RainClassSyncResult(0, 0, 0, 0, 0L)
        var syncError: Exception? = null
        var syncCalls = 0
        var clearedWithCachedData = false

        override fun hasSession(): Boolean = loggedIn

        override suspend fun startQrLogin(): RainClassQrCode = RainClassQrCode(
            png = byteArrayOf(1, 2, 3),
            expiresAtMillis = System.currentTimeMillis() + 120_000L
        )

        override suspend fun awaitQrLogin(
            ticket: RainClassQrCode,
            onStatus: (RainClassQrStatus) -> Unit
        ): RainClassLoginResult {
            onStatus(RainClassQrStatus.Scanned)
            if (loginResult == RainClassLoginResult.Success) loggedIn = true
            return loginResult
        }

        override fun cancelQrLogin() = Unit

        override suspend fun syncAssignments(
            onProgress: (done: Int, total: Int, message: String) -> Unit
        ): RainClassSyncResult {
            syncCalls++
            syncError?.let { throw it }
            return syncResult
        }

        override fun clearSession(clearCachedData: Boolean) {
            clearedWithCachedData = clearCachedData
            loggedIn = false
        }
    }
}
