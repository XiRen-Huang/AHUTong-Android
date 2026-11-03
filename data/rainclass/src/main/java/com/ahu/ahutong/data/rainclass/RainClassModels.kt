package com.ahu.ahutong.data.rainclass

import java.io.IOException
import kotlinx.serialization.Serializable

/** 雨课堂课程。`classroomId` 是后续章节/详情请求使用的 id。 */
@Serializable
data class RainClassCourse(
    val classroomId: Long,
    val courseId: Long,
    val name: String,
    val term: Long = 0,
    val teacher: String = ""
)

/**
 * 一条可进日历的雨课堂任务。
 *
 * 时间全部是毫秒。`endTs` 一定大于 0；`startTs` 可能为空，映射到 [com.ahu.ahutong.data.xuexiaotong.Work]
 * 时会用 `endTs` 兜底，因为现有 CalendarModel 要求起止时间都存在。
 */
@Serializable
data class RainClassAssignment(
    val id: String,
    val classroomId: Long,
    val courseId: Long,
    val courseName: String,
    val leafId: Long,
    val title: String,
    val leafType: Long,
    val startTs: Long?,
    val endTs: Long,
    val scoreDeadlineTs: Long?,
    val done: Boolean? = null,
    val statusRaw: String? = null,
    val locked: Boolean = false
)

/** 二维码图片与过期时间；uid/state 只在数据层内部使用。 */
class RainClassQrCode internal constructor(
    val png: ByteArray,
    val expiresAtMillis: Long,
    internal val uid: String,
    internal val state: String
) {
    /** 仅用于测试或外部展示；uid/state 由登录流程内部携带。 */
    constructor(png: ByteArray, expiresAtMillis: Long) : this(
        png = png,
        expiresAtMillis = expiresAtMillis,
        uid = "",
        state = ""
    )
}

sealed interface RainClassQrStatus {
    data object Waiting : RainClassQrStatus
    data object Scanned : RainClassQrStatus
    data object Expired : RainClassQrStatus
    data object Cancelled : RainClassQrStatus
    data class Failed(val message: String) : RainClassQrStatus
}

sealed interface RainClassLoginResult {
    data object Success : RainClassLoginResult
    data object Expired : RainClassLoginResult
    data object Cancelled : RainClassLoginResult
    data class Failure(val message: String) : RainClassLoginResult
}

data class RainClassSyncResult(
    val totalCourses: Int,
    val succeededCourses: Int,
    val failedCourses: Int,
    val assignments: Int,
    val lastSync: Long,
    val message: String = ""
)

/** 主站读接口认为当前 Cookie 已失效。 */
class RainClassSessionExpiredException(message: String) : IOException(message)

interface RainClassSession {
    fun hasSession(): Boolean

    suspend fun startQrLogin(): RainClassQrCode

    suspend fun awaitQrLogin(
        ticket: RainClassQrCode,
        onStatus: (RainClassQrStatus) -> Unit
    ): RainClassLoginResult

    fun cancelQrLogin()

    suspend fun syncAssignments(
        onProgress: (done: Int, total: Int, message: String) -> Unit
    ): RainClassSyncResult

    fun clearSession(clearCachedData: Boolean = true)
}

interface RainClassStore {
    fun courses(): List<RainClassCourse>
    fun assignments(): List<RainClassAssignment>
    fun lastSync(): Long

    /**
     * 本地记录“已提交”状态。雨课堂的只读接口不返回作业提交状态，
     * 用户手动标记后由 [assignments] 覆盖到 `done` 上。
     */
    fun setDone(id: String, done: Boolean)
}

interface RainClassCache : RainClassStore {
    fun save(
        courses: List<RainClassCourse>,
        assignments: List<RainClassAssignment>,
        lastSync: Long
    )

    fun isSessionActive(): Boolean
    fun setSessionActive(active: Boolean)
    fun clearAll()
}
