package com.ahu.ahutong.data.rainclass

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class RainClassQrLoginTest {

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

    private fun enqueueStart(server: MockWebServer, uid: String = "UID123") {
        server.enqueue(MockResponse().setResponseCode(200)) // csrf primer
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"data":{"appId":"app","state":"state"}}""")
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""<img src="/connect/qrcode/$uid" />""")
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "image/png")
                .setBody("PNG")
        )
    }

    @Test
    fun `six step start extracts uid and returns png`() {
        MockWebServer().use { server ->
            server.start()
            enqueueStart(server)
            val login = RainClassQrLogin(clientFor(server))

            val ticket = login.start()
            assertTrue(ticket.png.isNotEmpty())
            assertTrue(ticket.expiresAtMillis > System.currentTimeMillis())

            take(server) // csrf primer
            take(server) // wechat auth param
            val connect = take(server)
            assertEquals("/connect/qrconnect", connect.requestUrl?.encodedPath)
            assertEquals("app", connect.requestUrl?.queryParameter("appid"))
            assertEquals("state", connect.requestUrl?.queryParameter("state"))
            assertEquals(
                "https://changjiang.yuketang.cn/api/v3/user/login/wechat-web-callback",
                connect.requestUrl?.queryParameter("redirect_uri")
            )

            val image = take(server)
            assertEquals("/connect/qrcode/UID123", image.requestUrl?.encodedPath)
        }
    }

    @Test
    fun `poll maps waiting scanned expired and success`() = withServer { server ->
        val login = RainClassQrLogin(clientFor(server))
        enqueueStart(server)
        val ticket = login.start()
        repeat(4) { take(server) }

        server.enqueue(MockResponse().setBody("window.wx_errcode=408;"))
        assertEquals(RainClassPollResult.Waiting, login.poll(ticket))

        // 404 在微信长轮询里表示“已扫码，等待手机确认”，不是过期。
        server.enqueue(MockResponse().setBody("window.wx_errcode=404;"))
        assertEquals(RainClassPollResult.Scanned, login.poll(ticket))

        // 402 才是二维码过期。
        server.enqueue(MockResponse().setBody("window.wx_errcode=402;"))
        assertEquals(RainClassPollResult.Expired, login.poll(ticket))

        server.enqueue(MockResponse().setBody("window.wx_errcode=403;"))
        assertEquals(RainClassPollResult.Cancelled, login.poll(ticket))

        server.enqueue(
            MockResponse().setBody("window.wx_code='CODE'; window.wx_errcode=405;")
        )
        assertEquals(RainClassPollResult.Success("CODE"), login.poll(ticket))

        val request = take(server)
        assertEquals("UID123", request.requestUrl?.queryParameter("uuid"))
    }

    @Test
    fun `callback succeeds only on redirect`() = withServer { server ->
        val login = RainClassQrLogin(clientFor(server))
        enqueueStart(server)
        val ticket = login.start()
        repeat(4) { take(server) }

        server.enqueue(MockResponse().setResponseCode(302))
        assertTrue(login.complete(ticket, "CODE"))

        val request = take(server)
        assertEquals("CODE", request.requestUrl?.queryParameter("code"))
        assertEquals("state", request.requestUrl?.queryParameter("state"))
        assertEquals("/authorize/wx-qrlogin?success=1", request.requestUrl?.queryParameter("path"))
    }

    @Test
    fun `callback 200 is not success`() = withServer { server ->
        val login = RainClassQrLogin(clientFor(server))
        enqueueStart(server)
        val ticket = login.start()
        repeat(4) { take(server) }

        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(!login.complete(ticket, "CODE"))
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private fun take(server: MockWebServer): RecordedRequest {
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        return request
    }
}
