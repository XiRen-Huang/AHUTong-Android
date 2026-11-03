package com.ahu.ahutong.data.rainclass

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 只读同步：课程列表 → 每门课章节树 → 尽力补全未来 30 天任务的状态。
 *
 * 单课程失败不中断整次同步：成功课程替换缓存，失败课程保留旧缓存；
 * 课程列表本身失败时完全不写缓存，避免一次网络抖动清空日历。
 */
class RainClassRepository(
    private val api: RainClassApi,
    private val qrLogin: RainClassQrLogin,
    private val cookieJar: RainClassCookieJar,
    private val cache: RainClassCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RainClassSession {

    override fun hasSession(): Boolean =
        cache.isSessionActive() && cookieJar.hasCookies()

    override suspend fun startQrLogin(): RainClassQrCode = withContext(ioDispatcher) {
        // 新二维码不能继承旧会话。
        cookieJar.clear()
        cache.setSessionActive(false)
        qrLogin.start()
    }

    override suspend fun awaitQrLogin(
        ticket: RainClassQrCode,
        onStatus: (RainClassQrStatus) -> Unit
    ): RainClassLoginResult = withContext(ioDispatcher) {
        pollLogin(ticket, onStatus)
    }

    private suspend fun pollLogin(
        ticket: RainClassQrCode,
        onStatus: (RainClassQrStatus) -> Unit
    ): RainClassLoginResult {
        val deadline = minOf(
            ticket.expiresAtMillis,
            System.currentTimeMillis() + RainClassEndpoints.QR_TIMEOUT_MS
        )
        var lastStatus: RainClassQrStatus? = null
        var consecutiveFailures = 0

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                rainClassLogD(TAG, "pollLogin: local deadline reached")
                onStatus(RainClassQrStatus.Expired)
                return RainClassLoginResult.Expired
            }

            val poll = try {
                qrLogin.poll(ticket).also { consecutiveFailures = 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单次抖动不值得让 UI 进入“失败且刷新无效”的死角；连续失败才放弃。
                consecutiveFailures++
                rainClassLogD(TAG, "pollLogin: poll error ($consecutiveFailures): ${e.message}")
                if (consecutiveFailures >= 5) {
                    return RainClassLoginResult.Failure(
                        e.message ?: "二维码状态查询失败"
                    )
                }
                delay(RainClassEndpoints.QR_POLL_INTERVAL_MS)
                continue
            }

            when (poll) {
                RainClassPollResult.Waiting -> {
                    if (lastStatus != RainClassQrStatus.Waiting) {
                        onStatus(RainClassQrStatus.Waiting)
                        lastStatus = RainClassQrStatus.Waiting
                    }
                }

                RainClassPollResult.Scanned -> {
                    if (lastStatus != RainClassQrStatus.Scanned) {
                        onStatus(RainClassQrStatus.Scanned)
                        lastStatus = RainClassQrStatus.Scanned
                    }
                }

                RainClassPollResult.Expired -> {
                    rainClassLogD(TAG, "pollLogin: server reported expired")
                    onStatus(RainClassQrStatus.Expired)
                    return RainClassLoginResult.Expired
                }

                RainClassPollResult.Cancelled -> {
                    rainClassLogD(TAG, "pollLogin: cancelled on phone")
                    onStatus(RainClassQrStatus.Cancelled)
                    return RainClassLoginResult.Cancelled
                }

                is RainClassPollResult.Success -> {
                    val completed = try {
                        qrLogin.complete(ticket, poll.wxCode)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return RainClassLoginResult.Failure(
                            e.message ?: "雨课堂登录回调失败"
                        )
                    }
                    if (!completed) {
                        return RainClassLoginResult.Failure("雨课堂登录回调失败")
                    }
                    cache.setSessionActive(true)
                    return RainClassLoginResult.Success
                }
            }

            delay(RainClassEndpoints.QR_POLL_INTERVAL_MS)
        }
    }

    override fun cancelQrLogin() {
        qrLogin.cancel()
    }

    override suspend fun syncAssignments(
        onProgress: (done: Int, total: Int, message: String) -> Unit
    ): RainClassSyncResult = withContext(ioDispatcher) {
        val coursesResponse = try {
            api.getCourses()
        } catch (e: RainClassSessionExpiredException) {
            clearSession(clearCachedData = false)
            throw e
        }
        val courses = RainClassMapper.mapCourses(coursesResponse)

        if (courses.isEmpty()) {
            val now = System.currentTimeMillis()
            cache.save(emptyList(), emptyList(), now)
            RainClassDebug.log(emptyList(), emptyList(), 0)
            return@withContext RainClassSyncResult(
                totalCourses = 0,
                succeededCourses = 0,
                failedCourses = 0,
                assignments = 0,
                lastSync = now,
                message = "没有课程"
            )
        }

        val oldAssignments = cache.assignments()
        val succeededClassrooms = mutableSetOf<Long>()
        val freshAssignments = mutableListOf<RainClassAssignment>()
        var failedCourses = 0

        courses.forEachIndexed { index, course ->
            if (index > 0) delay(RainClassEndpoints.COURSE_THROTTLE_MS)
            onProgress(index, courses.size, "同步课程 ${index + 1}/${courses.size}")
            try {
                val chapters = api.getChapters(course.classroomId)
                RainClassDebug.logLeafShape(course.classroomId, chapters)
                freshAssignments += RainClassMapper.mapAssignments(course, chapters)
                succeededClassrooms += course.classroomId
            } catch (e: CancellationException) {
                throw e
            } catch (e: RainClassSessionExpiredException) {
                clearSession(clearCachedData = false)
                throw e
            } catch (e: Exception) {
                failedCourses++
            }
        }

        val currentClassrooms = courses.map { it.classroomId }.toSet()
        var merged = (
            freshAssignments +
                oldAssignments.filter {
                    it.classroomId in currentClassrooms &&
                        it.classroomId !in succeededClassrooms
                }
            )
            .distinctBy { it.id }
            .sortedBy { it.endTs }

        merged = enrichStatuses(merged, onProgress)

        val lastSync = System.currentTimeMillis()
        cache.save(courses, merged, lastSync)
        RainClassDebug.log(courses, merged, failedCourses)

        RainClassSyncResult(
            totalCourses = courses.size,
            succeededCourses = succeededClassrooms.size,
            failedCourses = failedCourses,
            assignments = merged.size,
            lastSync = lastSync,
            message = if (failedCourses > 0) "$failedCourses 门课程同步失败" else ""
        )
    }

    private suspend fun enrichStatuses(
        assignments: List<RainClassAssignment>,
        onProgress: (done: Int, total: Int, message: String) -> Unit
    ): List<RainClassAssignment> {
        val now = System.currentTimeMillis()
        val candidates = assignments
            .filter { it.done == null }
            .filter { it.endTs in now..(now + RainClassEndpoints.STATUS_WINDOW_MS) }
            .take(RainClassEndpoints.STATUS_BUDGET)
        if (candidates.isEmpty()) return assignments

        val byId = assignments.associateBy { it.id }.toMutableMap()
        candidates.forEachIndexed { index, assignment ->
            if (index > 0) delay(RainClassEndpoints.STATUS_THROTTLE_MS)
            onProgress(
                index,
                candidates.size,
                "补全完成状态 ${index + 1}/${candidates.size}"
            )
            val detail = try {
                api.getLeafDetail(assignment.classroomId, assignment.leafId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val status = RainClassMapper.contentStatus(detail)
            if (status != null) {
                byId[assignment.id] = RainClassMapper.applyStatus(assignment, status)
            }
        }
        return byId.values.sortedBy { it.endTs }
    }

    override fun clearSession(clearCachedData: Boolean) {
        cookieJar.clear()
        cache.setSessionActive(false)
        if (clearCachedData) cache.clearAll()
    }

    private companion object {
        const val TAG = "RainClassLogin"
    }
}
