package com.ahu.ahutong.data.rainclass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class RainClassCookieJarTest {

    private class MemoryPersistence : RainClassCookiePersistence {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    private val persistence = MemoryPersistence()
    private val mainUrl = "https://changjiang.yuketang.cn/v2/api/web/courses/list?identity=2".toHttpUrl()
    private val otherUrl = "https://changjiang.yuketang.cn/mooc-api/v1/lms/learn/course/chapter".toHttpUrl()
    private val wechatUrl = "https://open.weixin.qq.com/connect/qrcode/abc".toHttpUrl()

    @Test
    fun `cookies persist across restart with domain and path matching`() {
        val cookie = Cookie.Builder()
            .name("sessionid")
            .value("secret")
            .domain("yuketang.cn")
            .path("/")
            .secure()
            .httpOnly()
            .build()
        RainClassCookieJar(persistence).saveFromResponse(mainUrl, listOf(cookie))

        val restarted = RainClassCookieJar(persistence)
        assertEquals(listOf(cookie), restarted.loadForRequest(mainUrl))
        assertEquals(listOf(cookie), restarted.loadForRequest(otherUrl))
        assertTrue(restarted.loadForRequest(wechatUrl).isEmpty())
        assertTrue(restarted.hasCookies())
    }

    @Test
    fun `session cookies survive restart even without an expires attribute`() {
        val session = Cookie.Builder()
            .name("session")
            .value("v")
            .domain("yuketang.cn")
            .build()
        RainClassCookieJar(persistence).saveFromResponse(mainUrl, listOf(session))

        val restored = RainClassCookieJar(persistence).loadForRequest(mainUrl)
        assertEquals("session", restored.single().name)
        // OkHttp 5 的会话 Cookie 是 persistent=false + MAX_DATE，而不是 Long.MAX_VALUE。
        assertFalse(restored.single().persistent)
        assertEquals(session.expiresAt, restored.single().expiresAt)
    }

    @Test
    fun `expired and deleted cookies are removed from memory and disk`() {
        val subject = RainClassCookieJar(persistence)
        val live = Cookie.Builder()
            .name("keep")
            .value("1")
            .domain("yuketang.cn")
            .build()
        val doomed = Cookie.Builder()
            .name("drop")
            .value("1")
            .domain("yuketang.cn")
            .build()
        subject.saveFromResponse(mainUrl, listOf(live, doomed))
        subject.saveFromResponse(mainUrl, listOf(doomed.newBuilder().expiresAt(0).build()))

        assertEquals(listOf("keep"), subject.loadForRequest(mainUrl).map { it.name })
        assertEquals(
            listOf("keep"),
            RainClassCookieJar(persistence).loadForRequest(mainUrl).map { it.name }
        )
    }

    @Test
    fun `clear removes memory and persisted state`() {
        val subject = RainClassCookieJar(persistence)
        subject.saveFromResponse(
            mainUrl,
            listOf(
                Cookie.Builder().name("a").value("b").domain("yuketang.cn").build()
            )
        )
        subject.clear()

        assertFalse(subject.hasCookies())
        assertEquals(null, persistence.value)
        assertFalse(RainClassCookieJar(persistence).hasCookies())
    }
}
