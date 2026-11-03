package com.ahu.ahutong.data.rainclass

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal sealed interface RainClassPollResult {
    data object Waiting : RainClassPollResult
    data object Scanned : RainClassPollResult
    data object Expired : RainClassPollResult
    data object Cancelled : RainClassPollResult
    data class Success(val wxCode: String) : RainClassPollResult
}

/**
 * 雨课堂微信扫码登录的 6 步状态机。所有请求都走共享的、关闭自动重定向的客户端。
 */
class RainClassQrLogin(
    private val client: OkHttpClient,
    private val json: Json = RainClassJson
) {
    @Volatile
    private var generation = 0

    fun cancel() {
        generation++
    }

    internal fun start(): RainClassQrCode {
        val token = ++generation

        // 1. 拿 csrftoken。未登录时主站可能回 302，这里只关心 Set-Cookie 是否落下。
        primeCookies(RainClassEndpoints.CSRF_PRIMER.toHttpUrl())
        ensureCurrent(token)

        // 2. 拿 appId/state。
        val body = "{}".toRequestBody("application/json".toMediaType())
        val oauth = client.newCall(
            Request.Builder()
                .url(RainClassEndpoints.WECHAT_AUTH_PARAM.toHttpUrl())
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute().use { response ->
            checkHttp(response)
            json.decodeFromString<OAuthResponse>(response.body?.string().orEmpty())
        }
        if (oauth.code != 0L) {
            throw IOException(oauth.msg.ifBlank { "雨课堂登录参数获取失败" })
        }
        val oauthData = oauth.data ?: throw IOException("雨课堂登录参数为空")
        val appId = oauthData.appId.takeIf { it.isNotBlank() }
            ?: throw IOException("雨课堂登录参数缺少 appId")
        val state = oauthData.state.takeIf { it.isNotBlank() }
            ?: throw IOException("雨课堂登录参数缺少 state")
        ensureCurrent(token)

        // 3. 拿二维码 uid。
        val connectUrl = RainClassEndpoints.WECHAT_QR_CONNECT.toHttpUrl().newBuilder()
            .addQueryParameter("appid", appId)
            .addQueryParameter("scope", "snsapi_login")
            .addQueryParameter("redirect_uri", RainClassEndpoints.WECHAT_CALLBACK)
            .addQueryParameter("state", state)
            .addQueryParameter("login_type", "jssdk")
            .addQueryParameter("self_redirect", "true")
            .addQueryParameter("styletype", "")
            .addQueryParameter("sizetype", "")
            .addQueryParameter("bgcolor", "")
            .addQueryParameter("rst", "")
            .addQueryParameter("ts", "")
            .addQueryParameter("stylelite", "1")
            .build()
        val html = get(connectUrl, "text/html,application/xhtml+xml")
        ensureCurrent(token)
        val uid = UID_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("未找到雨课堂二维码 uid")
        rainClassLogD(TAG, "start: uid=…${uid.takeLast(4)} len=${uid.length}")

        // 4. 拿二维码图片。
        val imageUrl = RainClassEndpoints.WECHAT_QR_IMAGE.toHttpUrl().newBuilder()
            .addPathSegment(uid)
            .build()
        val (png, contentType) = getBytes(imageUrl)
        ensureCurrent(token)
        if (!contentType.startsWith("image/", ignoreCase = true)) {
            throw IOException("雨课堂二维码响应不是图片：$contentType")
        }

        return RainClassQrCode(
            png = png,
            expiresAtMillis = System.currentTimeMillis() + RainClassEndpoints.QR_TIMEOUT_MS,
            uid = uid,
            state = state
        )
    }

    internal fun poll(ticket: RainClassQrCode): RainClassPollResult {
        val url = RainClassEndpoints.WECHAT_QR_POLL.toHttpUrl().newBuilder()
            .addQueryParameter("uuid", ticket.uid)
            .build()
        val startedAt = System.currentTimeMillis()
        val text = get(url, "*/*")
        val elapsed = System.currentTimeMillis() - startedAt
        val errcode = WX_ERRCODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        WX_CODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { code ->
            if (code.isNotBlank()) {
                rainClassLogD(TAG, "poll: errcode=$errcode wx_code=present")
                return RainClassPollResult.Success(code)
            }
        }
        // 微信扫码长轮询的真实语义（与 qrconnect 页面自带 JS 一致）：
        //   408 等待扫码；404 已扫码待确认；405 已确认（响应里带 wx_code）；
        //   402 二维码过期；403 用户在手机上取消。
        // 之前把 404 当过期，会导致“一扫就过期”。
        val result = when (errcode) {
            408 -> RainClassPollResult.Waiting
            404 -> RainClassPollResult.Scanned
            405 -> RainClassPollResult.Scanned
            402 -> RainClassPollResult.Expired
            403 -> RainClassPollResult.Cancelled
            else -> RainClassPollResult.Waiting
        }
        // 只记录状态码和脱敏后的响应摘要，不记录二维码内容、wx_code 或账号信息。
        rainClassLogD(
            TAG,
            "poll: uid=…${ticket.uid.takeLast(4)} errcode=$errcode " +
                "elapsed=${elapsed}ms result=$result body=${redact(text)}"
        )
        return result
    }

    /** 去掉 wx_code 的值，只保留结构，便于排查协议字段。 */
    private fun redact(text: String): String =
        text.replace(WX_CODE_REGEX, "window.wx_code='***'")
            .replace(Regex("\\s+"), " ")
            .take(160)

    /** 第 6 步：302 即成功；Cookie 已由 CookieJar 在返回前保存。 */
    internal fun complete(ticket: RainClassQrCode, wxCode: String): Boolean {
        val url = RainClassEndpoints.WECHAT_CALLBACK.toHttpUrl().newBuilder()
            .addQueryParameter("path", "/authorize/wx-qrlogin?success=1")
            .addQueryParameter("code", wxCode)
            .addQueryParameter("state", ticket.state)
            .build()
        return client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .build()
        ).execute().use { response ->
            rainClassLogD(TAG, "complete: http=${response.code}")
            response.code == 302 || response.code == 303
        }
    }

    private fun ensureCurrent(token: Int) {
        if (token != generation) throw CancellationException("雨课堂登录已取消")
    }

    private fun get(url: HttpUrl, accept: String): String =
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", accept)
                .build()
        ).execute().use { response ->
            checkHttp(response)
            response.body?.string().orEmpty()
        }

    /** 只为了拿到 csrftoken Cookie：3xx 不视为失败。 */
    private fun primeCookies(url: HttpUrl) {
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful && response.code !in 300..399) {
                throw IOException("雨课堂登录请求失败：HTTP ${response.code}")
            }
        }
    }

    private fun getBytes(url: HttpUrl): Pair<ByteArray, String> =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            checkHttp(response)
            val bytes = response.body?.bytes() ?: throw IOException("雨课堂二维码为空")
            bytes to response.header("Content-Type").orEmpty()
        }

    private fun checkHttp(response: okhttp3.Response) {
        if (!response.isSuccessful) {
            throw IOException("雨课堂登录请求失败：HTTP ${response.code}")
        }
    }

    private companion object {
        const val TAG = "RainClassLogin"
        val UID_REGEX = Regex("/connect/qrcode/([A-Za-z0-9]+)")
        val WX_CODE_REGEX = Regex("""window\.wx_code\s*=\s*'([^']+)'""")
        val WX_ERRCODE_REGEX = Regex("""window\.wx_errcode\s*=\s*(\d+)""")
    }
}
