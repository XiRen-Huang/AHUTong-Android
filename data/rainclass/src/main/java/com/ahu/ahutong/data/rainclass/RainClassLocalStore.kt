package com.ahu.ahutong.data.rainclass

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 雨课堂缓存。JSON 解析失败或模型升级不兼容时按空缓存处理并清掉坏 key，
 * 不让一次坏数据把启动流程带崩。
 */
class RainClassLocalStore(context: Context) : RainClassCache {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun courses(): List<RainClassCourse> = readList(KEY_COURSES)

    @Synchronized
    override fun assignments(): List<RainClassAssignment> {
        val overrides = doneOverrides()
        val cached = readList<RainClassAssignment>(KEY_ASSIGNMENTS)
        if (overrides.isEmpty()) return cached
        return cached.map { assignment ->
            overrides[assignment.id]?.let { assignment.copy(done = it) } ?: assignment
        }
    }

    /** 手动标记的提交状态只存本地，重新同步不丢，也不回写服务端。 */
    @Synchronized
    override fun setDone(id: String, done: Boolean) {
        val overrides = doneOverrides().toMutableMap()
        overrides[id] = done
        preferences.edit()
            .putString(KEY_DONE_OVERRIDES, RainClassJson.encodeToString(overrides))
            .apply()
    }

    @Synchronized
    private fun doneOverrides(): Map<String, Boolean> {
        val raw = preferences.getString(KEY_DONE_OVERRIDES, null) ?: return emptyMap()
        return runCatching {
            RainClassJson.decodeFromString<Map<String, Boolean>>(raw)
        }.getOrElse {
            preferences.edit().remove(KEY_DONE_OVERRIDES).apply()
            emptyMap()
        }
    }

    @Synchronized
    override fun lastSync(): Long = preferences.getLong(KEY_LAST_SYNC, 0L)

    @Synchronized
    override fun save(
        courses: List<RainClassCourse>,
        assignments: List<RainClassAssignment>,
        lastSync: Long
    ) {
        preferences.edit()
            .putString(KEY_COURSES, RainClassJson.encodeToString(courses))
            .putString(KEY_ASSIGNMENTS, RainClassJson.encodeToString(assignments))
            .putLong(KEY_LAST_SYNC, lastSync)
            .apply()
    }

    override fun isSessionActive(): Boolean =
        preferences.getBoolean(KEY_SESSION_ACTIVE, false)

    override fun setSessionActive(active: Boolean) {
        preferences.edit().putBoolean(KEY_SESSION_ACTIVE, active).apply()
    }

    @Synchronized
    override fun clearAll() {
        preferences.edit().clear().apply()
    }

    private inline fun <reified T> readList(key: String): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            RainClassJson.decodeFromString<List<T>>(raw)
        }.getOrElse {
            preferences.edit().remove(key).apply()
            emptyList()
        }
    }

    private companion object {
        const val PREFS_NAME = "ahutong_rc_prefs"
        const val KEY_COURSES = "rc_courses"
        const val KEY_ASSIGNMENTS = "rc_assignments"
        const val KEY_LAST_SYNC = "rc_last_sync"
        const val KEY_SESSION_ACTIVE = "rc_session_active"
        const val KEY_DONE_OVERRIDES = "rc_done_overrides"
    }
}
