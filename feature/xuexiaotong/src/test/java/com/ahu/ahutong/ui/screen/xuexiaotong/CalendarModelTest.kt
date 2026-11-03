package com.ahu.ahutong.ui.screen.xuexiaotong

import com.ahu.ahutong.data.xuexiaotong.Work
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarModelTest {

    private fun ts(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis

    private fun work(
        id: String,
        start: Long,
        end: Long,
        rainClass: Boolean = true
    ): Work = Work(
        workId = if (rainClass) "rainclass:$id" else id,
        courseId = "1",
        courseName = "课程",
        title = id,
        status = "未知",
        startTs = start,
        endTs = end
    )

    private fun slots(bar: CalendarMergedBar): List<Pair<Int, Int>> =
        bar.titles.map { it.colStart to it.colEnd }

    @Test
    fun `overlapping rainclass works merge into one 16dp row with per-day segments`() {
        val works = listOf(
            work("数理", ts(2026, 10, 22), ts(2026, 11, 1)),
            work("LA7", ts(2026, 10, 26), ts(2026, 11, 2)),
            work("力学2", ts(2026, 10, 28), ts(2026, 11, 5)),
            work("LA8", ts(2026, 10, 29), ts(2026, 11, 5))
        )

        val row = CalendarModel.buildMonth(2026, 11, works, emptyList()).rows.first()

        assertEquals(1, row.mergedBars.size)
        assertTrue(row.blocks.isEmpty())

        val bar = row.mergedBars.single()
        assertEquals(0, bar.y)
        assertEquals(0, bar.colStart)
        assertEquals(4, bar.colEnd)
        assertEquals(16, bar.height)
        assertEquals(4, bar.entries.size)
        assertEquals(4, bar.entries.map { it.colorIndex }.distinct().size)
        // 截止越早的任务取色越靠前。
        assertEquals(
            listOf("数理", "LA7", "力学2", "LA8"),
            bar.entries.map { it.work.title }
        )
        // 每天的活动集合：11/1 四条、11/2 三条、11/3-5 两条。
        assertEquals(
            listOf(
                Triple(0, 0, listOf(0, 1, 2, 3)),
                Triple(1, 1, listOf(1, 2, 3)),
                Triple(2, 4, listOf(2, 3))
            ),
            bar.segments.map { Triple(it.colStart, it.colEnd, it.entryIndices) }
        )
        // 四个任务同一天开始：标题合并，只拼前两个，其余用 +N。
        val title = bar.titles.single()
        assertEquals(0, title.colStart)
        assertEquals(4, title.colEnd)
        assertEquals("数理 · LA7 +2", title.text)
    }

    @Test
    fun `disjoint rainclass groups sit side by side at the same height`() {
        val works = listOf(
            work("早", ts(2026, 11, 1), ts(2026, 11, 1)),
            work("晚", ts(2026, 11, 3), ts(2026, 11, 5))
        )

        val row = CalendarModel.buildMonth(2026, 11, works, emptyList()).rows.first()

        assertEquals(2, row.mergedBars.size)
        assertTrue(row.mergedBars.all { it.y == 0 })
        assertTrue(row.mergedBars.all { it.height == 16 })
        assertTrue(row.mergedBars.all { it.segments.size == 1 })
        assertTrue(row.mergedBars.all { it.titles.size == 1 })
    }

    @Test
    fun `title shows only on the first row of a two-row task`() {
        val works = listOf(work("任务A", ts(2026, 10, 8), ts(2026, 10, 13)))

        val model = CalendarModel.buildMonth(2026, 10, works, emptyList())

        // 10 月 8 日在第 2 行（10/4-10/10），10 月 11 日起进入第 3 行。
        assertEquals("任务A", model.rows[1].mergedBars.single().titles.single().text)
        assertTrue(model.rows[2].mergedBars.single().titles.isEmpty())
    }

    @Test
    fun `overflowing title continues in the next row`() {
        val title = "一二三四五六七八九十甲乙丙丁"
        val works = listOf(work(title, ts(2026, 10, 3), ts(2026, 10, 7)))

        val model = CalendarModel.buildMonth(2026, 10, works, emptyList())

        val first = model.rows[0].mergedBars.single()
        val second = model.rows[1].mergedBars.single()
        // 10 月 3 日只剩周六一格，放不下的部分顺延到下一行；本行用省略号提示被截断。
        assertEquals("一二三四五…", first.titles.single().text)
        assertEquals("六七八九十甲乙丙丁", second.titles.single().text)
    }

    @Test
    fun `continuation row only shows tasks that are new there`() {
        val works = listOf(
            work("A", ts(2026, 10, 8), ts(2026, 10, 13)),
            work("B", ts(2026, 10, 11), ts(2026, 10, 13))
        )

        val model = CalendarModel.buildMonth(2026, 10, works, emptyList())

        assertEquals("A", model.rows[1].mergedBars.single().titles.single().text)
        // 第二行里 A 是续行不再重复，只显示新出现的 B。
        assertEquals(
            listOf("B"),
            model.rows[2].mergedBars.single().titles.map { it.text }
        )
    }

    @Test
    fun `regular span block shows its title once`() {
        val works = listOf(
            work("超星作业", ts(2026, 10, 8), ts(2026, 10, 13), rainClass = false)
        )

        val model = CalendarModel.buildMonth(2026, 10, works, emptyList())

        assertEquals("超星作业", model.rows[1].blocks.single().label)
        assertEquals("", model.rows[2].blocks.single().label)
    }

    @Test
    fun `done works are hidden when showDone is off`() {
        val works = listOf(
            work("已完成任务", ts(2026, 11, 1), ts(2026, 11, 2)).copy(status = "已完成")
        )

        val hidden = CalendarModel.buildMonth(2026, 11, works, emptyList(), showDone = false)
        val shown = CalendarModel.buildMonth(2026, 11, works, emptyList(), showDone = true)

        assertTrue(hidden.rows.all { it.mergedBars.isEmpty() })
        assertEquals(1, shown.rows.first().mergedBars.size)
    }

    @Test
    fun `non rainclass works keep the old span layout`() {
        val works = listOf(
            work("学习通作业", ts(2026, 11, 1), ts(2026, 11, 5), rainClass = false),
            work("雨课堂", ts(2026, 11, 1), ts(2026, 11, 2))
        )

        val row = CalendarModel.buildMonth(2026, 11, works, emptyList()).rows.first()

        assertEquals(1, row.blocks.size)
        assertEquals(1, row.mergedBars.size)
        // 两者在 11 月 1-2 日重叠，普通作业占顶部 16dp，雨课堂组顺延到它下面。
        assertEquals(0, row.blocks.single().lane)
        assertEquals(17, row.mergedBars.single().y)
        assertEquals(16, row.mergedBars.single().height)
        assertEquals(17 + 16 + CalendarModel.LAYER_PAD, row.layerHeight)
    }

    @Test
    fun `adjacent days with the same active set form one segment`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 3)),
            work("B", ts(2026, 11, 2), ts(2026, 11, 3))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(
            listOf(Triple(0, 0, listOf(0)), Triple(1, 2, listOf(0, 1))),
            bar.segments.map { Triple(it.colStart, it.colEnd, it.entryIndices) }
        )
        assertEquals(listOf(0 to 0, 1 to 2), slots(bar))
    }

    @Test
    fun `crossing tasks get titles at their own starts`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 4)),
            work("B", ts(2026, 11, 3), ts(2026, 11, 6))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(
            listOf(
                Triple(0, 1, listOf(0)),
                Triple(2, 3, listOf(0, 1)),
                Triple(4, 5, listOf(1))
            ),
            bar.segments.map { Triple(it.colStart, it.colEnd, it.entryIndices) }
        )
        assertEquals(listOf(0 to 1, 2 to 5), slots(bar))
    }

    @Test
    fun `tasks starting on the same day share one joined title`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 4)),
            work("B", ts(2026, 11, 1), ts(2026, 11, 6))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(1, bar.titles.size)
        assertEquals(0 to 5, slots(bar).single())
        assertEquals("A · B", bar.titles.single().text)
    }

    @Test
    fun `contained task gets its own title slot`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 6)),
            work("B", ts(2026, 11, 3), ts(2026, 11, 4))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(listOf(0 to 1, 2 to 3), slots(bar))
        assertEquals(listOf("A", "B"), bar.titles.map { it.text })
    }

    @Test
    fun `same-end tasks get titles at their own starts`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 5)),
            work("B", ts(2026, 11, 3), ts(2026, 11, 5))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(listOf(0 to 1, 2 to 4), slots(bar))
    }

    @Test
    fun `chain overlaps keep one title per start`() {
        val works = listOf(
            work("A", ts(2026, 11, 1), ts(2026, 11, 3)),
            work("B", ts(2026, 11, 3), ts(2026, 11, 5)),
            work("C", ts(2026, 11, 5), ts(2026, 11, 7))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(listOf(0 to 1, 2 to 3, 4 to 6), slots(bar))
        assertEquals(listOf("A", "B", "C"), bar.titles.map { it.text })
    }

    @Test
    fun `narrow joined slot falls back to first title plus N`() {
        val works = listOf(
            work("甲", ts(2026, 11, 2), ts(2026, 11, 2)),
            work("乙", ts(2026, 11, 2), ts(2026, 11, 2)),
            work("丙", ts(2026, 11, 2), ts(2026, 11, 2)),
            work("丁", ts(2026, 11, 2), ts(2026, 11, 2))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(1 to 1, slots(bar).single())
        assertEquals("甲 +3", bar.titles.single().text)
    }

    @Test
    fun `narrow joined slot keeps two names when they fit`() {
        val works = listOf(
            work("甲乙丙", ts(2026, 11, 2), ts(2026, 11, 2)),
            work("丁戊己", ts(2026, 11, 2), ts(2026, 11, 2)),
            work("庚辛壬", ts(2026, 11, 2), ts(2026, 11, 3))
        )

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(1 to 2, slots(bar).single())
        assertEquals("甲乙丙 · 丁戊己 +1", bar.titles.single().text)
    }

    @Test
    fun `title cut by a later task start is not carried`() {
        val title = "一二三四五六七八九十甲乙"
        val works = listOf(
            work(title, ts(2026, 10, 4), ts(2026, 10, 12)),
            work("B", ts(2026, 10, 6), ts(2026, 10, 10))
        )

        val model = CalendarModel.buildMonth(2026, 10, works, emptyList())

        // 第一行 A 从 0 列开始，但 2 列有 B 的新标题，A 的槽到 1 列为止。
        val first = model.rows[1].mergedBars.single()
        val titleA = first.titles.single { it.text != "B" }
        assertEquals(0 to 1, titleA.colStart to titleA.colEnd)
        assertEquals("一二三四五六七八九十…", titleA.text)
        // 被后面任务的起始列截断，剩余文字不顺延到下一行。
        assertTrue(model.rows[2].mergedBars.single().titles.isEmpty())
    }

    @Test
    fun `cross-month clipped task gets a title in the new month`() {
        val works = listOf(work("跨月", ts(2026, 10, 28), ts(2026, 11, 3)))

        val bar = CalendarModel.buildMonth(2026, 11, works, emptyList())
            .rows.first().mergedBars.single()

        assertEquals(0, bar.colStart)
        assertEquals(2, bar.colEnd)
        assertEquals("跨月", bar.titles.single().text)
    }
}
