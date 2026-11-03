package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.rainclass.RainClassLoginResult
import com.ahu.ahutong.data.rainclass.RainClassQrStatus
import com.ahu.ahutong.data.rainclass.RainClassSession
import com.ahu.ahutong.data.rainclass.RainClassSessionExpiredException
import com.ahu.ahutong.data.rainclass.RainClassStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.feature.xuexiaotong.rainclass.toCalendarWork
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RainClassSyncProgress(
    val done: Int = 0,
    val total: Int = 0,
    val message: String = ""
)

@HiltViewModel
class RainClassViewModel @Inject constructor(
    private val session: RainClassSession,
    private val store: RainClassStore,
    private val reminders: ChaoxingReminders,
    @XuexiaotongDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _works = MutableStateFlow<List<Work>>(emptyList())
    val works: StateFlow<List<Work>> = _works.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(RainClassSyncProgress())
    val syncProgress: StateFlow<RainClassSyncProgress> = _syncProgress.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _qrPng = MutableStateFlow<ByteArray?>(null)
    val qrPng: StateFlow<ByteArray?> = _qrPng.asStateFlow()

    private val _qrStatus = MutableStateFlow<RainClassQrStatus?>(null)
    val qrStatus: StateFlow<RainClassQrStatus?> = _qrStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loginJob: Job? = null
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            val loggedIn = withContext(ioDispatcher) { session.hasSession() }
            val cached = withContext(ioDispatcher) {
                store.assignments().map { it.toCalendarWork() } to store.lastSync()
            }
            _loggedIn.value = loggedIn
            _works.value = cached.first
            _lastSync.value = cached.second
            if (loggedIn && System.currentTimeMillis() - cached.second > AUTO_SYNC_INTERVAL_MS) {
                syncNow(silent = true)
            }
        }
    }

    fun beginLogin() {
        if (loginJob?.isActive == true) return
        _error.value = null
        _qrStatus.value = RainClassQrStatus.Waiting
        loginJob = viewModelScope.launch {
            try {
                val ticket = session.startQrLogin()
                _qrPng.value = ticket.png
                _qrStatus.value = RainClassQrStatus.Waiting
                val result = session.awaitQrLogin(ticket) { status ->
                    _qrStatus.value = status
                }
                when (result) {
                    RainClassLoginResult.Success -> {
                        _qrPng.value = null
                        _qrStatus.value = null
                        _loggedIn.value = true
                        syncNow(silent = false)
                    }

                    RainClassLoginResult.Expired -> {
                        _qrPng.value = null
                        _qrStatus.value = RainClassQrStatus.Expired
                        _error.value = "二维码已过期，请刷新"
                    }

                    RainClassLoginResult.Cancelled -> {
                        _qrPng.value = null
                        _qrStatus.value = null
                    }

                    is RainClassLoginResult.Failure -> {
                        _qrPng.value = null
                        _qrStatus.value = RainClassQrStatus.Failed(result.message)
                        _error.value = result.message
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _qrPng.value = null
                _qrStatus.value = RainClassQrStatus.Failed(e.message ?: "登录失败")
                _error.value = e.message ?: "登录失败"
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        session.cancelQrLogin()
        _qrPng.value = null
        _qrStatus.value = null
    }

    fun syncNow() {
        syncNow(silent = false)
    }

    /** 雨课堂只读接口不返回提交状态，由用户手动标记；只改本地缓存。 */
    fun setSubmitted(workId: String, done: Boolean) {
        viewModelScope.launch {
            withContext(ioDispatcher) { store.setDone(workId, done) }
            refreshWorks()
            reminders.rescheduleAll()
        }
    }

    fun logout() {
        loginJob?.cancel()
        syncJob?.cancel()
        session.cancelQrLogin()
        viewModelScope.launch {
            withContext(ioDispatcher) {
                session.clearSession(clearCachedData = true)
            }
            _loggedIn.value = false
            _works.value = emptyList()
            _lastSync.value = 0L
            _error.value = null
            reminders.rescheduleAll()
        }
    }

    private fun syncNow(silent: Boolean) {
        if (syncJob?.isActive == true) return
        if (!_loggedIn.value && !silent) return
        syncJob = viewModelScope.launch {
            _syncing.value = true
            _error.value = null
            try {
                val result = session.syncAssignments { done, total, message ->
                    _syncProgress.value = RainClassSyncProgress(done, total, message)
                }
                refreshWorks()
                _lastSync.value = result.lastSync
                if (result.failedCourses > 0 && !silent) {
                    _error.value = "${result.failedCourses} 门课程同步失败"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RainClassSessionExpiredException) {
                _loggedIn.value = false
                refreshWorks()
                _error.value = "登录已过期，请重新登录"
            } catch (e: Exception) {
                if (!silent) _error.value = e.message ?: "同步失败"
            } finally {
                _syncing.value = false
                _syncProgress.value = RainClassSyncProgress()
                reminders.rescheduleAll()
            }
        }
    }

    private suspend fun refreshWorks() {
        val (works, lastSync) = withContext(ioDispatcher) {
            store.assignments().map { it.toCalendarWork() } to store.lastSync()
        }
        _works.value = works
        _lastSync.value = lastSync
    }

    override fun onCleared() {
        session.cancelQrLogin()
        super.onCleared()
    }

    private companion object {
        const val AUTO_SYNC_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
