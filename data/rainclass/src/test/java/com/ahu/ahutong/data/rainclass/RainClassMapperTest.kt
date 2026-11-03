package com.ahu.ahutong.data.rainclass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RainClassMapperTest {

    private val course = RainClassCourse(
        classroomId = 10,
        courseId = 100,
        name = "数据结构",
        term = 1,
        teacher = "老师"
    )

    @Test
    fun `course list falls back from classroom id to course id`() {
        val response = CourseListResponse(
            success = true,
            data = CourseListData(
                list = listOf(
                    CourseNode(
                        classroomId = 0,
                        term = 1,
                        name = "fallback",
                        course = CourseData(id = 77, name = "课程名"),
                        teacher = TeacherData("T")
                    ),
                    CourseNode(
                        classroomId = 0,
                        course = CourseData(id = 0, name = "无效")
                    )
                )
            )
        )

        val courses = RainClassMapper.mapCourses(response)
        assertEquals(1, courses.size)
        assertEquals(77, courses.single().classroomId)
        assertEquals("课程名", courses.single().name)
    }

    @Test
    fun `leaves are filtered by visibility and have stable ids with fallbacks`() {
        val response = ChapterResponse(
            success = true,
            data = ChapterData(
                courseId = 100,
                courseName = "课程",
                chapters = listOf(
                    ChapterNode(
                        id = 1,
                        leaves = listOf(
                            LeafNode(
                                id = 5,
                                name = "作业",
                                leafType = 3,
                                startTime = 1_700_000_000,
                                endTime = 1_700_003_600
                            ),
                            LeafNode(
                                id = 0,
                                leafInfoId = 9,
                                name = "测验",
                                leafType = 4,
                                endTime = 0,
                                scoreDeadline = 1_700_007_200
                            ),
                            LeafNode(
                                id = 6,
                                name = "隐藏",
                                isShow = false,
                                endTime = 1_700_003_600
                            ),
                            LeafNode(id = 7, name = "无截止", endTime = 0)
                        )
                    )
                )
            )
        )

        val assignments = RainClassMapper.mapAssignments(course, response)
        assertEquals(listOf("rainclass:10:5", "rainclass:10:9"), assignments.map { it.id })
        assertEquals(1_700_003_600_000L, assignments[0].endTs)
        assertEquals(1_700_000_000_000L, assignments[0].startTs)
        assertEquals(1_700_007_200_000L, assignments[1].endTs)
        assertNull(assignments[1].startTs)
        assertEquals(1_700_007_200_000L, assignments[1].scoreDeadlineTs)
        assertTrue(assignments.all { it.classroomId == 10L })
    }

    @Test
    fun `courseware and unscored activities do not reach the calendar`() {
        val response = ChapterResponse(
            success = true,
            data = ChapterData(
                chapters = listOf(
                    ChapterNode(
                        leaves = listOf(
                            LeafNode(id = 1, name = "课件", leafType = 8L, endTime = 2_000),
                            LeafNode(
                                id = 2,
                                name = "视频",
                                leafType = 7L,
                                isScore = false,
                                endTime = 2_000
                            ),
                            LeafNode(
                                id = 3,
                                name = "计分作业",
                                leafType = 7L,
                                isScore = true,
                                endTime = 2_000
                            ),
                            LeafNode(id = 4, name = "作业", leafType = 5L, endTime = 2_000),
                            LeafNode(id = 5, name = "未知类型", leafType = 9L, endTime = 2_000)
                        )
                    )
                )
            )
        )

        val assignments = RainClassMapper.mapAssignments(course, response)
        assertEquals(
            listOf("rainclass:10:3", "rainclass:10:4", "rainclass:10:5"),
            assignments.map { it.id }
        )
    }

    @Test
    fun `duplicate leaves are removed and results sorted by deadline`() {
        val leaf = LeafNode(id = 2, name = "B", endTime = 2_000)
        val response = ChapterResponse(
            success = true,
            data = ChapterData(
                chapters = listOf(
                    ChapterNode(leaves = listOf(leaf)),
                    ChapterNode(leaves = listOf(leaf, LeafNode(id = 1, name = "A", endTime = 1_000)))
                )
            )
        )

        val assignments = RainClassMapper.mapAssignments(course, response)
        assertEquals(listOf("rainclass:10:1", "rainclass:10:2"), assignments.map { it.id })
        assertEquals(listOf(1_000_000L, 2_000_000L), assignments.map { it.endTs })
    }

    @Test
    fun `millisecond timestamps are not multiplied again`() {
        val response = ChapterResponse(
            success = true,
            data = ChapterData(
                chapters = listOf(
                    ChapterNode(
                        leaves = listOf(
                            LeafNode(id = 1, name = "作业", endTime = 1_700_000_123_456L)
                        )
                    )
                )
            )
        )

        val assignment = RainClassMapper.mapAssignments(course, response).single()
        assertEquals(1_700_000_123_456L, assignment.endTs)
    }

    @Test
    fun `unknown status stays unknown and known status maps conservatively`() {
        val assignment = RainClassAssignment(
            id = "rainclass:10:1",
            classroomId = 10,
            courseId = 100,
            courseName = "课程",
            leafId = 1,
            title = "作业",
            leafType = 3,
            startTs = null,
            endTs = 1_000L,
            scoreDeadlineTs = null
        )

        assertNull(RainClassMapper.applyStatus(assignment, "mystery").done)
        assertEquals(true, RainClassMapper.applyStatus(assignment, "finished").done)
        assertEquals(false, RainClassMapper.applyStatus(assignment, "未提交").done)
        assertNull(RainClassStatusMapper.map(null))
    }
}
