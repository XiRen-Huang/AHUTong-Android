package com.ahu.ahutong.data.rainclass

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient

class RainClassRepositoryTest {

    private val course1 = RainClassCourse(1, 101, "课程一")
    private val course2 = RainClassCourse(2, 102, "课程二")
    private val course3 = RainClassCourse(3, 103, "课程三")

    @Test
    fun `single course failure keeps old cache and removed courses are dropped`() = runTest {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val api = FakeApi(
            courseResponse = CourseListResponse(
                success = true,
                data = CourseListData(list = listOf(courseNode(course1), courseNode(course2)))
            ),
            chapters = mapOf(
                1L to chapter(leaf(id = 10, end = nowSeconds + 3_600)),
                2L to chapter()
            )
        )
        api.chapterFailures += 2L
        val cache = FakeCache(
            assignmentList = mutableListOf(
                assignment(course2, leafId = 20, endSeconds = nowSeconds + 7_200),
                assignment(course3, leafId = 30, endSeconds = nowSeconds + 7_200)
            )
        )
        val progress = mutableListOf<String>()

        val result = repository(api, cache, StandardTestDispatcher(testScheduler))
            .syncAssignments { _, _, message ->
                progress += message
            }

        assertEquals(2, result.totalCourses)
        assertEquals(1, result.succeededCourses)
        assertEquals(1, result.failedCourses)
        assertEquals(
            listOf("rainclass:1:10", "rainclass:2:20"),
            cache.assignmentList.map { it.id }.sorted()
        )
        assertTrue(progress.any { it.contains("同步课程") })
    }

    @Test
    fun `empty course list clears cached assignments`() = runTest {
        val cache = FakeCache(
            assignmentList = mutableListOf(assignment(course1, 10, 2_000))
        )
        val result = repository(
            FakeApi(CourseListResponse(success = true, data = CourseListData())),
            cache,
            StandardTestDispatcher(testScheduler)
        ).syncAssignments { _, _, _ -> }

        assertEquals(0, result.totalCourses)
        assertTrue(cache.assignmentList.isEmpty())
        assertTrue(cache.courseList.isEmpty())
    }

    @Test
    fun `session expiry clears only the session marker and keeps cached data`() = runTest {
        val api = FakeApi(CourseListResponse(success = true, data = CourseListData()))
        api.coursesError = RainClassSessionExpiredException("HTTP 302")
        val cached = assignment(course1, 10, 2_000)
        val cache = FakeCache(assignmentList = mutableListOf(cached))
        cache.active = true

        assertFailsWith<RainClassSessionExpiredException> {
            repository(api, cache, StandardTestDispatcher(testScheduler))
                .syncAssignments { _, _, _ -> }
        }

        assertEquals(false, cache.active)
        assertEquals(listOf(cached), cache.assignmentList)
    }

    @Test
    fun `status enrichment applies known values and ignores detail failures`() = runTest {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val api = FakeApi(
            courseResponse = CourseListResponse(
                success = true,
                data = CourseListData(list = listOf(courseNode(course1)))
            ),
            chapters = mapOf(
                1L to chapter(
                    leaf(10, nowSeconds + 3_600),
                    leaf(11, nowSeconds + 3_600)
                )
            ),
            details = mapOf(
                (1L to 10L) to LeafDetailResponse(
                    success = true,
                    data = LeafDetailData(contentInfo = ContentInfo(JsonPrimitive("finished")))
                )
            )
        )
        api.detailFailures += 1L to 11L
        val cache = FakeCache()

        repository(api, cache, StandardTestDispatcher(testScheduler))
            .syncAssignments { _, _, _ -> }

        val byId = cache.assignmentList.associateBy { it.id }
        assertEquals(true, assertNotNull(byId["rainclass:1:10"]).done)
        assertNull(assertNotNull(byId["rainclass:1:11"]).done)
    }

    @Test
    fun `assignments are deduplicated and sorted by deadline`() = runTest {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val api = FakeApi(
            courseResponse = CourseListResponse(
                success = true,
                data = CourseListData(list = listOf(courseNode(course1)))
            ),
            chapters = mapOf(
                1L to chapter(
                    leaf(10, nowSeconds + 7_200),
                    leaf(11, nowSeconds + 3_600)
                )
            )
        )
        val cache = FakeCache()

        repository(api, cache, StandardTestDispatcher(testScheduler))
            .syncAssignments { _, _, _ -> }

        assertEquals(
            listOf("rainclass:1:11", "rainclass:1:10"),
            cache.assignmentList.map { it.id }
        )
    }

    private fun repository(
        api: RainClassApi,
        cache: FakeCache,
        dispatcher: CoroutineDispatcher
    ): RainClassRepository =
        RainClassRepository(
            api = api,
            qrLogin = RainClassQrLogin(OkHttpClient()),
            cookieJar = RainClassCookieJar(MemoryPersistence()),
            cache = cache,
            ioDispatcher = dispatcher
        )

    private fun courseNode(course: RainClassCourse): CourseNode = CourseNode(
        classroomId = course.classroomId,
        term = course.term,
        name = course.name,
        course = CourseData(id = course.courseId, name = course.name)
    )

    private fun chapter(vararg leaves: LeafNode): ChapterResponse = ChapterResponse(
        success = true,
        data = ChapterData(chapters = listOf(ChapterNode(leaves = leaves.toList())))
    )

    private fun leaf(id: Long, end: Long, start: Long = 0): LeafNode = LeafNode(
        id = id,
        name = "任务",
        leafType = 3,
        startTime = start,
        endTime = end
    )

    private fun assignment(
        course: RainClassCourse,
        leafId: Long,
        endSeconds: Long
    ): RainClassAssignment = RainClassAssignment(
        id = "rainclass:${course.classroomId}:$leafId",
        classroomId = course.classroomId,
        courseId = course.courseId,
        courseName = course.name,
        leafId = leafId,
        title = "旧任务",
        leafType = 3,
        startTs = null,
        endTs = endSeconds * 1_000L,
        scoreDeadlineTs = null
    )

    private class MemoryPersistence : RainClassCookiePersistence {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    private class FakeCache(
        var courseList: MutableList<RainClassCourse> = mutableListOf(),
        var assignmentList: MutableList<RainClassAssignment> = mutableListOf(),
        var lastSyncValue: Long = 0L,
        var active: Boolean = false
    ) : RainClassCache {
        override fun courses(): List<RainClassCourse> = courseList.toList()
        override fun assignments(): List<RainClassAssignment> = assignmentList.toList()
        override fun lastSync(): Long = lastSyncValue

        override fun setDone(id: String, done: Boolean) {
            val index = assignmentList.indexOfFirst { it.id == id }
            if (index >= 0) assignmentList[index] = assignmentList[index].copy(done = done)
        }

        override fun save(
            courses: List<RainClassCourse>,
            assignments: List<RainClassAssignment>,
            lastSync: Long
        ) {
            courseList = courses.toMutableList()
            assignmentList = assignments.toMutableList()
            lastSyncValue = lastSync
        }

        override fun isSessionActive(): Boolean = active
        override fun setSessionActive(active: Boolean) {
            this.active = active
        }

        override fun clearAll() {
            courseList.clear()
            assignmentList.clear()
            lastSyncValue = 0L
            active = false
        }
    }

    private class FakeApi(
        var courseResponse: CourseListResponse,
        val chapters: Map<Long, ChapterResponse> = emptyMap(),
        val details: Map<Pair<Long, Long>, LeafDetailResponse> = emptyMap()
    ) : RainClassApi {
        var coursesError: Exception? = null
        val chapterFailures = mutableSetOf<Long>()
        val detailFailures = mutableSetOf<Pair<Long, Long>>()

        override fun getCourses(): CourseListResponse {
            coursesError?.let { throw it }
            return courseResponse
        }

        override fun getChapters(classroomId: Long): ChapterResponse {
            if (classroomId in chapterFailures) throw IOException("chapter failed")
            return chapters[classroomId] ?: ChapterResponse(success = true, data = ChapterData())
        }

        override fun getLeafDetail(classroomId: Long, leafId: Long): LeafDetailResponse {
            val key = classroomId to leafId
            if (key in detailFailures) throw IOException("detail failed")
            return details[key] ?: LeafDetailResponse()
        }
    }
}
