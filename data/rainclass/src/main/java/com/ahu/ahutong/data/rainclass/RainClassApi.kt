package com.ahu.ahutong.data.rainclass

/**
 * 雨课堂主站只读接口。全部是阻塞调用，由 Repository 切到 IO 调度器。
 */
interface RainClassApi {
    fun getCourses(): CourseListResponse
    fun getChapters(classroomId: Long): ChapterResponse
    fun getLeafDetail(classroomId: Long, leafId: Long): LeafDetailResponse
}
