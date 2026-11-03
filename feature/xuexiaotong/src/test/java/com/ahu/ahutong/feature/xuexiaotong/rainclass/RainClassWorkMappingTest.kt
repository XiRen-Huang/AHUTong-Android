package com.ahu.ahutong.feature.xuexiaotong.rainclass

import com.ahu.ahutong.data.rainclass.RainClassAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RainClassWorkMappingTest {

    private fun assignment(
        done: Boolean? = null,
        startTs: Long? = null,
        endTs: Long = 1_700_003_600_000L
    ) = RainClassAssignment(
        id = "rainclass:10:20",
        classroomId = 10,
        courseId = 100,
        courseName = "数据结构",
        leafId = 20,
        title = "第一次作业",
        leafType = 3,
        startTs = startTs,
        endTs = endTs,
        scoreDeadlineTs = null,
        done = done
    )

    @Test
    fun `work keeps rainclass id prefix and blue colors`() {
        val work = assignment().toCalendarWork()
        assertEquals("rainclass:10:20", work.workId)
        assertEquals("数据结构", work.courseName)
        assertEquals("第一次作业", work.title)
        assertEquals("#E3F2FD", work.colorBg)
        assertEquals("#0D47A1", work.colorText)
        assertEquals("10", work.courseId)
        assertFalse(work.isDone)
    }

    @Test
    fun `missing start time falls back to deadline for calendar compatibility`() {
        val work = assignment(startTs = null).toCalendarWork()
        assertEquals(work.endTs, work.startTs)
    }

    @Test
    fun `status maps known and unknown completion states`() {
        assertEquals("已完成", assignment(done = true).toCalendarWork().status)
        assertEquals("未完成", assignment(done = false).toCalendarWork().status)
        assertEquals("未知", assignment(done = null).toCalendarWork().status)
    }
}
