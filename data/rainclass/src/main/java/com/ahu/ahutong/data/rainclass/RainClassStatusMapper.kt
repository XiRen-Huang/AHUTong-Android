package com.ahu.ahutong.data.rainclass

/**
 * `content_info.status` → 完成状态的保守映射。未知值一律返回 null，
 * 详情页会显示“完成状态未知”，避免把未确认的字符串当成已完成。
 */
object RainClassStatusMapper {
    private val doneValues = setOf(
        "finished", "completed", "complete", "done", "submitted",
        "已提交", "已完成", "已批阅", "已批改", "待批阅", "待批改"
    )
    private val todoValues = setOf(
        "not_started", "unstarted", "in_progress", "unsubmitted",
        "未开始", "进行中", "未提交", "未完成"
    )

    fun map(raw: String?): Boolean? {
        val key = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (key) {
            in doneValues -> true
            in todoValues -> false
            else -> null
        }
    }
}
