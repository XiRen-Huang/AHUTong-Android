package com.ahu.ahutong.data.rainclass

import com.ahu.ahutong.data.security.SecureStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 便于 JVM 单测替换掉 Android Keystore。 */
interface RainClassCookiePersistence {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

class SecureRainClassCookiePersistence : RainClassCookiePersistence {
    override fun read(): String? = runCatching { SecureStorage.getString(KEY) }.getOrNull()

    override fun write(value: String) {
        // 加密失败时至少保留内存会话，不让网络线程因存储异常崩溃。
        runCatching { SecureStorage.putString(KEY, value) }
    }

    override fun clear() {
        runCatching { SecureStorage.remove(KEY) }
    }

    private companion object {
        const val KEY = "rainclass_cookies_v1"
    }
}

/**
 * OkHttp CookieJar：按域名/路径保存 Cookie，并序列化到加密存储。
 *
 * 会话 Cookie（OkHttp 5：`persistent=false`）同样落盘；重建时不再设置 expiresAt，
 * 因此恢复后仍是会话 Cookie，但进程重启后还能从加密存储里恢复，符合“关掉 App 仍保持登录”的预期。
 */
class RainClassCookieJar(
    private val persistence: RainClassCookiePersistence
) : CookieJar {

    private val cookies = mutableListOf<Cookie>()
    private var loaded = false

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            this.cookies.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            if (cookie.expiresAt > now) this.cookies.add(cookie)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        val now = System.currentTimeMillis()
        return cookies.filter { it.expiresAt > now && it.matches(url) }
    }

    /** 只把“能发给雨课堂主站”的 Cookie 算作登录态，避免微信域 Cookie 误判。 */
    @Synchronized
    fun hasCookies(): Boolean {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val before = cookies.size
        cookies.removeAll { it.expiresAt <= now }
        if (cookies.size != before) persist()
        val mainUrl = RainClassEndpoints.HOST.toHttpUrl()
        return cookies.any { it.matches(mainUrl) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        loaded = true
        persistence.clear()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = persistence.read().orEmpty()
        if (raw.isBlank()) return
        val decoded = runCatching {
            RainClassJson.decodeFromString<List<CookieDto>>(raw)
        }.getOrNull()
        if (decoded == null) {
            runCatching { persistence.clear() }
            return
        }
        val now = System.currentTimeMillis()
        decoded.forEach { dto ->
            dto.toCookie()?.takeIf { it.expiresAt > now }?.let(cookies::add)
        }
    }

    private fun persist() {
        runCatching {
            persistence.write(RainClassJson.encodeToString(cookies.map { it.toDto() }))
        }
    }
}

@Serializable
private data class CookieDto(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    // OkHttp 5 用 persistent=false + MAX_DATE 表示会话 Cookie；这两个字段必须往返，
    // 否则恢复出来的 Cookie 会变成持久 Cookie，且与原始对象不再相等。
    val persistent: Boolean = false,
    val sameSite: String? = null
)

private fun Cookie.toDto(): CookieDto = CookieDto(
    name = name,
    value = value,
    domain = domain,
    path = path,
    expiresAt = expiresAt,
    secure = secure,
    httpOnly = httpOnly,
    hostOnly = hostOnly,
    persistent = persistent,
    sameSite = sameSite
)

private fun CookieDto.toCookie(): Cookie? = runCatching {
    Cookie.Builder()
        .name(name)
        .value(value)
        .path(path)
        .apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            // 会话 Cookie 不调用 expiresAt()，保持 Builder 的默认 MAX_DATE + persistent=false。
            if (persistent) expiresAt(expiresAt)
            if (secure) secure()
            if (httpOnly) httpOnly()
            if (sameSite != null) this.sameSite(sameSite)
        }
        .build()
}.getOrNull()
