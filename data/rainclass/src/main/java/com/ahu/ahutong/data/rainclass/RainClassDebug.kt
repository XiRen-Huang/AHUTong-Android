package com.ahu.ahutong.data.rainclass

import android.util.Log
import kotlinx.serialization.json.JsonNull

/**
 * 首版校准用的聚合日志：只输出数量分布，不输出标题、课程名、Cookie 或用户信息。
 *
 * 本地单测没有 Android Log 实现，所以这里吞掉异常，不影响同步。
 */
/** 单测环境没有 Android Log 实现，日志失败不能影响主流程。 */
internal fun rainClassLogD(tag: String, message: String) {
    runCatching { Log.d(tag, message) }
}

object RainClassDebug {
    /**
     * leaf_type 校准用：按考核标志聚合，判断哪些叶子是作业/测验。
     * 只输出计数和布尔位，不输出标题、课程名或用户信息。
     */
    fun logLeafShape(classroomId: Long, response: ChapterResponse) {
        val leaves = response.data?.chapters.orEmpty().flatMap { it.leaves }
        val byFlags = leaves.groupingBy { leaf ->
            "type=${leaf.leafType} score=${leaf.isScore} assessed=${leaf.isAssessed} " +
                "open=${leaf.isOpenType} " +
                "section=${leaf.sectionId != null && leaf.sectionId !is JsonNull} " +
                "scoreDeadline=${leaf.scoreDeadline > 0}"
        }.eachCount()
        rainClassLogD("RainClassShape", "classroom=$classroomId ${byFlags}")
    }

    private const val TAG = "RainClassSync"

    fun log(
        courses: List<RainClassCourse>,
        assignments: List<RainClassAssignment>,
        failedCourses: Int
    ) {
        val byType = assignments.groupingBy { it.leafType }.eachCount()
        val byStatus = assignments.mapNotNull { it.statusRaw }.groupingBy { it }.eachCount()
        runCatching {
            Log.d(TAG, "courses=${courses.size} assignments=${assignments.size} failed=$failedCourses")
            Log.d(TAG, "leaf_type=$byType")
            Log.d(TAG, "content_info.status=$byStatus")
            Log.d(
                TAG,
                "missing_start=${assignments.count { it.startTs == null }} " +
                    "missing_end=${assignments.count { it.endTs <= 0L }}"
            )
        }
    }
}
