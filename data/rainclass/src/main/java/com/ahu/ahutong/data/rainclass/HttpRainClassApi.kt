package com.ahu.ahutong.data.rainclass

import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HttpRainClassApi(
    private val client: OkHttpClient,
    private val json: Json = RainClassJson
) : RainClassApi {

    override fun getCourses(): CourseListResponse {
        val request = Request.Builder()
            .url(RainClassEndpoints.COURSES.toHttpUrl())
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            json.decodeFromString<CourseListResponse>(body).also { response ->
                ensureSuccess(
                    response.success,
                    response.errcode,
                    response.errmsg,
                    response.msg
                )
            }
        }
    }

    override fun getChapters(classroomId: Long): ChapterResponse {
        val url = RainClassEndpoints.CHAPTER.toHttpUrl().newBuilder()
            .addQueryParameter("cid", classroomId.toString())
            .addQueryParameter("classroom_id", classroomId.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header(RainClassEndpoints.HEADER_XTBZ, RainClassEndpoints.XTBZ_MAIN)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            json.decodeFromString<ChapterResponse>(body).also { response ->
                ensureSuccess(
                    response.success,
                    response.errcode,
                    response.errmsg,
                    response.msg
                )
            }
        }
    }

    override fun getLeafDetail(classroomId: Long, leafId: Long): LeafDetailResponse {
        // 详情接口要求末尾斜杠：直接拼路径，避免空 path segment 的编码差异。
        val url = "${RainClassEndpoints.LEAF_INFO}/$classroomId/$leafId/".toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .header(RainClassEndpoints.HEADER_XTBZ, RainClassEndpoints.XTBZ_MAIN)
            .header(RainClassEndpoints.HEADER_CLASSROOM_ID, classroomId.toString())
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            json.decodeFromString<LeafDetailResponse>(body)
        }
    }

    private fun <T> execute(request: Request, decode: (String) -> T): T {
        client.newCall(request).execute().use { response ->
            checkResponse(response)
            val body = response.body?.string().orEmpty()
            return decode(body)
        }
    }

    private fun checkResponse(response: Response) {
        if (response.code == 302 || response.code == 401 || response.code == 403) {
            throw RainClassSessionExpiredException("HTTP ${response.code}")
        }
        if (!response.isSuccessful) {
            throw IOException("雨课堂请求失败：HTTP ${response.code}")
        }
    }

    private fun ensureSuccess(
        success: Boolean?,
        errcode: Long,
        vararg messages: String
    ) {
        if (success != false && errcode == 0L) return
        val message = messages.firstOrNull { it.isNotBlank() }.orEmpty()
        if (looksLikeSessionError(message)) {
            throw RainClassSessionExpiredException(message)
        }
        throw IOException(message.ifBlank { "雨课堂请求失败" })
    }

    private fun looksLikeSessionError(message: String): Boolean {
        val value = message.lowercase()
        return value.contains("登录") ||
            value.contains("login") ||
            value.contains("session") ||
            value.contains("unauthorized") ||
            value.contains("auth")
    }
}
