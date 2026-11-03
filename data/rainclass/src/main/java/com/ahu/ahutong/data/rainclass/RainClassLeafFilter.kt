package com.ahu.ahutong.data.rainclass

/**
 * 只保留真正的作业/测验/考试，把课件和视频排除在日历之外。
 *
 * 2026-10 用真实账号校准过的 `leaf_type`：
 * - `5`：作业（如《力学》第二章作业、数理导论第一章作业）；
 * - `7`：混合“学习活动”，作业和视频都可能是 7，`is_score=true` 的才是计分作业；
 * - `8`：课件/PPT（如 LA_1、10-9），`end_time` 是课程结束时间（2027-01-31），
 *   进日历会把整月铺满。
 *
 * 未知类型先保留，避免服务端新增作业类型时漏掉。只改这一处。
 */
object RainClassLeafFilter {

    private const val TYPE_ACTIVITY = 7L
    private const val TYPE_COURSEWARE = 8L

    fun keep(leafType: Long, isScore: Boolean, endTs: Long): Boolean {
        if (endTs <= 0L) return false
        return when (leafType) {
            TYPE_COURSEWARE -> false
            TYPE_ACTIVITY -> isScore
            else -> true
        }
    }
}
