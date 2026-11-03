package com.ahu.ahutong.data.rainclass

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * DTO → 领域模型。字段容错、去重、排序都集中在这里。
 */
object RainClassMapper {

    fun mapCourses(response: CourseListResponse): List<RainClassCourse> =
        response.data?.list.orEmpty().mapNotNull { node ->
            val classroomId = node.classroomId.takeIf { it > 0 }
                ?: node.course?.id?.takeIf { it > 0 }
                ?: return@mapNotNull null
            val courseId = node.course?.id?.takeIf { it > 0 } ?: classroomId
            val name = node.course?.name?.takeIf { it.isNotBlank() }
                ?: node.name.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            RainClassCourse(
                classroomId = classroomId,
                courseId = courseId,
                name = name,
                term = node.term,
                teacher = node.teacher?.name.orEmpty()
            )
        }.distinctBy { it.classroomId }

    fun mapAssignments(
        course: RainClassCourse,
        response: ChapterResponse
    ): List<RainClassAssignment> {
        val data = response.data
        val courseName = course.name.ifBlank { data?.courseName.orEmpty() }
        return data?.chapters.orEmpty()
            .flatMap { it.leaves }
            .mapNotNull { leaf ->
                if (leaf.isShow == false) return@mapNotNull null
                val endRaw = leaf.endTime.takeIf { it > 0 }
                    ?: leaf.scoreDeadline.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val endTs = endRaw.toEpochMillis()
                if (!RainClassLeafFilter.keep(leaf.leafType, leaf.isScore, endTs)) {
                    return@mapNotNull null
                }
                val leafId = leaf.id.takeIf { it > 0 }
                    ?: leaf.leafInfoId.takeIf { it > 0 }
                    ?: return@mapNotNull null

                RainClassAssignment(
                    id = "rainclass:${course.classroomId}:$leafId",
                    classroomId = course.classroomId,
                    courseId = course.courseId,
                    courseName = courseName,
                    leafId = leafId,
                    title = leaf.name.ifBlank { "雨课堂任务" },
                    leafType = leaf.leafType,
                    startTs = leaf.startTime.takeIf { it > 0 }?.toEpochMillis(),
                    endTs = endTs,
                    scoreDeadlineTs = leaf.scoreDeadline.takeIf { it > 0 }?.toEpochMillis(),
                    locked = leaf.isLocked
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.endTs }
    }

    fun applyStatus(
        assignment: RainClassAssignment,
        rawStatus: String?
    ): RainClassAssignment = assignment.copy(
        done = RainClassStatusMapper.map(rawStatus),
        statusRaw = rawStatus?.takeIf { it.isNotBlank() }
    )

    /**
     * 详情接口的 `content_info.status` 可能是字符串，也可能是数字；拿不到就返回 null。
     */
    fun contentStatus(response: LeafDetailResponse?): String? =
        (response?.data?.contentInfo?.status as? JsonPrimitive)?.contentOrNull

    /**
     * 服务端时间戳通常是秒；若已经给到毫秒也不再乘 1000。
     * 阈值取 1e11：秒级时间戳要到公元 5138 年才会越过它。
     */
    private fun Long.toEpochMillis(): Long =
        if (this < 100_000_000_000L) this * 1_000L else this
}
