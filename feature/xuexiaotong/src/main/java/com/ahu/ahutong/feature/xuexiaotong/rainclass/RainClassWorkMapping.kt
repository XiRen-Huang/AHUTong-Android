package com.ahu.ahutong.feature.xuexiaotong.rainclass

import com.ahu.ahutong.data.rainclass.RainClassAssignment
import com.ahu.ahutong.data.xuexiaotong.Work

/**
 * 雨课堂任务 → 现有日历的 [Work]。
 *
 * `startTs` 用截止时间兜底：CalendarModel 要求起止都存在，否则整条会被跳过。
 * `workId` 已带 `rainclass:` 前缀，提醒 key 与超星/自建日程不会相撞。
 */
fun RainClassAssignment.toCalendarWork(): Work = Work(
    workId = id,
    courseId = classroomId.toString(),
    courseName = courseName,
    title = title,
    status = when (done) {
        true -> "已完成"
        false -> "未完成"
        null -> "未知"
    },
    startTs = startTs ?: endTs,
    endTs = endTs,
    colorBg = "#E3F2FD",
    colorText = "#0D47A1"
)
