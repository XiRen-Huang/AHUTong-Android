package com.ahu.ahutong.data.rainclass

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl

class RainClassApiTest {

    private fun clientFor(server: MockWebServer): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor { chain ->
                val original = chain.request()
                val target = server.url("/").newBuilder()
                    .encodedPath(original.url.encodedPath)
                    .encodedQuery(original.url.encodedQuery)
                    .build()
                chain.proceed(original.newBuilder().url(target).build())
            }
            .build()

    private fun server(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    @Test
    fun `course list tolerates numeric strings and maps the right field`() = server { server ->
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"success":true,"data":{"list":[
                      {"classroom_id":"42","term":"2","name":"fallback",
                       "course":{"id":7,"name":"数据结构"},"teacher":{"name":"张老师"}}
                    ]}}
                    """.trimIndent()
                )
        )

        val api = HttpRainClassApi(clientFor(server))
        val response = api.getCourses()
        val course = RainClassMapper.mapCourses(response).single()
        assertEquals(42L, course.classroomId)
        assertEquals(7L, course.courseId)
        assertEquals("数据结构", course.name)
        assertEquals(2L, course.term)
        assertEquals("张老师", course.teacher)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `chapter request sends xtbz and both course query parameters`() = server { server ->
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"success":true,"data":{"course_id":42,"course_name":"课程",
                      "course_chapter":[{"id":1,"section_leaf_list":[
                        {"id":9,"name":"作业","leaf_type":3,"end_time":1700000000}
                      ]}]}}
                    """.trimIndent()
                )
        )

        val api = HttpRainClassApi(clientFor(server))
        val response = api.getChapters(42)
        assertEquals(1, response.data?.chapters?.size)
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("ykt", request.getHeader("xtbz"))
        assertEquals("42", request.requestUrl?.queryParameter("cid"))
        assertEquals("42", request.requestUrl?.queryParameter("classroom_id"))
    }

    @Test
    fun `leaf detail sends classroom header and reads content status`() = server { server ->
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"success":true,"data":{"name":"作业","content_info":{"status":"finished"}}}"""
                )
        )

        val api = HttpRainClassApi(clientFor(server))
        val response = api.getLeafDetail(42, 9)
        assertEquals("finished", RainClassMapper.contentStatus(response))
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("42", request.getHeader("classroom-id"))
        assertEquals("ykt", request.getHeader("xtbz"))
        assertTrue(request.path.orEmpty().endsWith("/42/9/"))
    }

    @Test
    fun `redirect and unauthorized responses are session expiry`() = server { server ->
        server.enqueue(MockResponse().setResponseCode(302))
        val api = HttpRainClassApi(clientFor(server))
        assertFailsWith<RainClassSessionExpiredException> { api.getCourses() }
    }

    @Test
    fun `json login error with session wording is session expiry`() = server { server ->
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"success":false,"errcode":1,"errmsg":"登录已过期"}""")
        )
        val api = HttpRainClassApi(clientFor(server))
        assertFailsWith<RainClassSessionExpiredException> { api.getCourses() }
    }

    @Test
    fun `non session json errors are ordinary failures`() = server { server ->
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"success":false,"errcode":2,"errmsg":"参数错误"}""")
        )
        val api = HttpRainClassApi(clientFor(server))
        assertFailsWith<java.io.IOException> { api.getCourses() }
    }
}
