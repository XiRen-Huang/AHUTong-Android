package com.ahu.ahutong.ui.screen.xuexiaotong

import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.Work
import java.util.Calendar

data class CalendarCell(
    val day: Int,
    val outside: Boolean,
    val isToday: Boolean,
    val ts: Long
)

data class CalendarBlock(
    val work: Work,
    val colStart: Int,
    val colEnd: Int,
    val continueNext: Boolean,
    val lane: Int = 0,
    /** 该任务在本月网格里第一次出现在这一行；续行不重复显示名字。 */
    val firstSegment: Boolean = false,
    /** 本行实际显示的文字；续行只在上一行放不下时才有内容。 */
    val label: String = ""
)

/** 合并条里的单个任务；`colorIndex` 在同一组内保证不重复。 */
data class MergedBarEntry(
    val work: Work,
    val colorIndex: Int
)

/**
 * 合并组里按天活动集合相同的一段连续日期。
 * `entryIndices` 指向 `CalendarMergedBar.entries`，按截止时间升序；UI 用这些任务的颜色算叠加色。
 */
data class MergedBarSegment(
    val colStart: Int,
    val colEnd: Int,
    val entryIndices: List<Int>
)

/** 合并条里的一段标题：从某个任务（或同起始列的多个任务）的起始列开始，到槽的右边界结束。 */
data class MergedBarTitle(
    val colStart: Int,
    val colEnd: Int,
    val text: String
)

/**
 * 雨课堂作业的合并组：同一周内时间重叠的任务归为一组，整组只占一行 16dp。
 * 重叠部分按“当天活动任务集合”铺叠加色，标题按起始列分槽。
 */
data class CalendarMergedBar(
    val entries: List<MergedBarEntry>,
    val colStart: Int,
    val colEnd: Int,
    val segments: List<MergedBarSegment>,
    val titles: List<MergedBarTitle>,
    /** 相对颜色层顶部的偏移（dp）。 */
    val y: Int,
    /** 整组高度（dp）。 */
    val height: Int = CalendarModel.BLOCK_H
)

data class CalendarRow(
    val cells: List<CalendarCell>,
    val blocks: List<CalendarBlock>,
    val layerHeight: Int,
    val mergedBars: List<CalendarMergedBar> = emptyList()
)

data class MonthModel(
    val rows: List<CalendarRow>,
    val noWorks: Boolean
)

object CalendarModel {

    private const val DAY_MS = 86400000L
    private const val MERGED_PALETTE_SIZE = 12
    const val BLOCK_H = 16
    private const val BLOCK_GAP = 3
    const val BLOCK_STEP = BLOCK_H + BLOCK_GAP
    const val LAYER_PAD = 4

    fun buildMonth(
        year: Int,
        month: Int,
        works: List<Work>,
        customEvents: List<CustomEvent>,
        showDone: Boolean = true
    ): MonthModel {
        val cal = Calendar.getInstance()
        val now = Calendar.getInstance()

        cal.set(year, month - 1, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.clear(Calendar.MINUTE); cal.clear(Calendar.SECOND); cal.clear(Calendar.MILLISECOND)
        val startWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val cells = mutableListOf<CalendarCell>()
        val prevCal = Calendar.getInstance().apply {
            set(year, month - 2, 1)
            set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        val prevDays = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in startWeekday - 1 downTo 0) {
            val d = prevDays - i
            val ts = prevCal.apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
            cells.add(CalendarCell(d, true, false, ts))
        }
        for (d in 1..daysInMonth) {
            val ts = cal.apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
            val isToday = now.get(Calendar.YEAR) == year && (now.get(Calendar.MONTH) + 1) == month && now.get(Calendar.DAY_OF_MONTH) == d
            cells.add(CalendarCell(d, false, isToday, ts))
        }
        var fill = 42 - cells.size
        val nextCal = Calendar.getInstance().apply {
            set(year, month, 1)
            set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        var d = 1
        while (fill > 0) {
            val ts = nextCal.apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
            cells.add(CalendarCell(d, true, false, ts))
            d++; fill--
        }

        val gridStart = cells.first().ts
        val gridEnd = cells.last().ts

        val filteredWorks = if (showDone) works else works.filter { !it.isDone }

        // 雨课堂任务不按跨度叠泳道：同一周内时间重叠的任务合并成一条，只占一行。
        val (rainClassWorks, regularWorks) = filteredWorks.partition {
            it.workId.startsWith("rainclass:")
        }

        val rowBlocks = Array(6) { mutableListOf<CalendarBlock>() }

        regularWorks.forEach { w ->
            val ws = w.startTs ?: return@forEach
            val we = w.endTs ?: return@forEach
            val s = maxOf(startOfDay(ws), gridStart)
            val e = minOf(startOfDay(we), gridEnd)
            if (s > e) return@forEach

            val sIdx = ((s - gridStart) / DAY_MS).toInt()
            val eIdx = ((e - gridStart) / DAY_MS).toInt()

            var idx = sIdx
            while (idx <= eIdx) {
                val row = idx / 7
                val rowStart = row * 7
                val segEnd = minOf(eIdx, rowStart + 6)
                rowBlocks[row].add(
                    CalendarBlock(
                        work = w,
                        colStart = idx - rowStart,
                        colEnd = segEnd - rowStart,
                        continueNext = segEnd < eIdx,
                        firstSegment = idx == sIdx
                    )
                )
                idx = segEnd + 1
            }
        }

        customEvents.forEach { ev ->
            val st = if (ev.startTs > 0) ev.startTs else (parseDateTs(ev.startDate) ?: return@forEach)
            val et = if (ev.endTs > 0) ev.endTs else (parseDateTs(ev.endDate) ?: st)
            val es = startOfDay(st).coerceAtLeast(gridStart)
            val ee = startOfDay(et).coerceAtMost(gridEnd)
            if (es > ee) return@forEach

            val item = Work(
                workId = "event_${ev.id}",
                courseId = "",
                courseName = "自定义日程",
                title = ev.title,
                status = if (ev.done) "已完成" else "未完成",
                startTs = st,
                endTs = et,
                colorBg = ev.colorBg,
                colorText = ev.colorText
            )

            val sIdx = ((es - gridStart) / DAY_MS).toInt()
            val eIdx = ((ee - gridStart) / DAY_MS).toInt()
            var idx = sIdx
            while (idx <= eIdx) {
                val row = idx / 7
                val rowStart = row * 7
                val segEnd = minOf(eIdx, rowStart + 6)
                rowBlocks[row].add(
                    CalendarBlock(
                        work = item,
                        colStart = idx - rowStart,
                        colEnd = segEnd - rowStart,
                        continueNext = segEnd < eIdx,
                        firstSegment = idx == sIdx
                    )
                )
                idx = segEnd + 1
            }
        }

        // 颜色按整月统一分配：同一任务跨周换行时颜色不变，相邻的组也不会撞色。
        val colorIndexByWorkId = assignColors(rainClassWorks)
            .associate { it.work.workId to it.colorIndex }

        val rowRanges = Array(6) { mutableListOf<RowRange>() }
        rainClassWorks.forEach { w ->
            val ws = w.startTs ?: return@forEach
            val we = w.endTs ?: return@forEach
            val s = maxOf(startOfDay(ws), gridStart)
            val e = minOf(startOfDay(we), gridEnd)
            if (s > e) return@forEach

            val sIdx = ((s - gridStart) / DAY_MS).toInt()
            val eIdx = ((e - gridStart) / DAY_MS).toInt()
            var idx = sIdx
            while (idx <= eIdx) {
                val row = idx / 7
                val rowStart = row * 7
                val segEnd = minOf(eIdx, rowStart + 6)
                rowRanges[row].add(
                    RowRange(w, (idx - rowStart)..(segEnd - rowStart), firstSegment = idx == sIdx)
                )
                idx = segEnd + 1
            }
        }

        val rows = mutableListOf<CalendarRow>()
        var hasAnyBlock = false
        for (r in 0 until 6) {
            val blocks = rowBlocks[r].sortedWith(
                compareBy<CalendarBlock> { it.colStart }.thenByDescending { it.colEnd }
            )
            val laneEnds = mutableListOf<Int>()
            val placed = blocks.map { b ->
                var lane = 0
                var found = false
                for (li in laneEnds.indices) {
                    if (b.colStart > laneEnds[li]) {
                        laneEnds[li] = b.colEnd
                        lane = li
                        found = true
                        break
                    }
                }
                if (!found) {
                    laneEnds.add(b.colEnd)
                    lane = laneEnds.size - 1
                }
                b.copy(lane = lane)
            }
            // 普通块按行占矩形；雨课堂组找不冲突的纵向空位放下，不相交的组可以并排。
            val rects = placed
                .map { PlacedRect(it.colStart, it.colEnd, it.lane * BLOCK_STEP, BLOCK_H) }
                .toMutableList()
            val bars = groupRanges(rowRanges[r]).map { pending ->
                buildMergedBar(pending, rects, colorIndexByWorkId)
            }
            if (placed.isNotEmpty() || bars.isNotEmpty()) hasAnyBlock = true
            val layerHeight = if (rects.isEmpty()) {
                LAYER_PAD
            } else {
                rects.maxOf { it.y + it.height } + LAYER_PAD
            }
            rows.add(
                CalendarRow(
                    cells = cells.slice(r * 7 until (r + 1) * 7),
                    blocks = placed,
                    layerHeight = layerHeight,
                    mergedBars = bars
                )
            )
        }

        return MonthModel(applyBarTitles(applyBlockLabels(rows), rowRanges), !hasAnyBlock)
    }

    /** 一周内某个任务占用的列范围。 */
    private data class RowRange(
        val work: Work,
        val range: IntRange,
        val firstSegment: Boolean
    )

    /** 色块在颜色层里占用的矩形（单位 dp）。 */
    private data class PlacedRect(
        val colStart: Int,
        val colEnd: Int,
        val y: Int,
        val height: Int
    )

    /**
     * 每个日期格大约能放下的文字宽度（以汉字为单位）。
     * 只是估算，UI 仍用省略号兜底；宁可多顺延，也不要吞掉标题。
     */
    private const val CELL_TEXT_UNITS = 5.0f

    /** 合并后的一组任务及其列范围。 */
    private data class RowBar(
        val ranges: List<RowRange>,
        val colStart: Int,
        val colEnd: Int
    )

    /** 把同一周内互相重叠的任务分成若干组。 */
    private fun groupRanges(ranges: List<RowRange>): List<RowBar> {
        val sorted = ranges.sortedWith(compareBy({ it.range.first }, { it.range.last }))
        val groups = mutableListOf<MutableList<RowRange>>()
        var currentEnd = -1
        sorted.forEach { item ->
            if (groups.isEmpty() || item.range.first > currentEnd) {
                groups += mutableListOf(item)
                currentEnd = item.range.last
            } else {
                groups.last().add(item)
                currentEnd = maxOf(currentEnd, item.range.last)
            }
        }
        return groups.map { group ->
            RowBar(
                ranges = group,
                colStart = group.minOf { it.range.first },
                colEnd = group.maxOf { it.range.last }
            )
        }
    }

    /**
     * 整组只占一行：按天求活动任务集合，相邻天集合相同就合并成一段。
     * 集合变化的日界会变成段边界，UI 据此画叠加色和分隔线。
     */
    private fun buildMergedBar(
        pending: RowBar,
        rects: MutableList<PlacedRect>,
        colorIndexByWorkId: Map<String, Int>
    ): CalendarMergedBar {
        val works = pending.ranges
            .map { it.work }
            .distinctBy { it.workId }
            .sortedWith(
                compareBy(
                    { it.endTs ?: Long.MAX_VALUE },
                    { it.startTs ?: Long.MAX_VALUE }
                )
            )
        val entries = works.map {
            MergedBarEntry(it, colorIndexByWorkId[it.workId] ?: 0)
        }
        val entryIndexByWorkId = entries
            .mapIndexed { index, entry -> entry.work.workId to index }
            .toMap()

        // entryIndices 已按截止时间升序，所以按 index 排序就是按截止时间排序。
        val segments = mutableListOf<MergedBarSegment>()
        var segStart = pending.colStart
        var segIndices: List<Int>? = null
        for (col in pending.colStart..pending.colEnd) {
            val active = pending.ranges
                .filter { col in it.range }
                .map { entryIndexByWorkId.getValue(it.work.workId) }
                .distinct()
                .sorted()
            if (segIndices == null) {
                segIndices = active
                segStart = col
            } else if (active != segIndices) {
                segments += MergedBarSegment(segStart, col - 1, segIndices)
                segStart = col
                segIndices = active
            }
        }
        segIndices?.let { segments += MergedBarSegment(segStart, pending.colEnd, it) }

        val y = firstFreeY(rects, pending.colStart, pending.colEnd, BLOCK_H)
        rects.add(PlacedRect(pending.colStart, pending.colEnd, y, BLOCK_H))
        return CalendarMergedBar(
            entries = entries,
            colStart = pending.colStart,
            colEnd = pending.colEnd,
            segments = segments,
            titles = emptyList(),
            y = y
        )
    }

    /** 自上而下找一段与已放置色块都不相交的空位；有冲突就推到冲突块下方。 */
    private fun firstFreeY(
        rects: List<PlacedRect>,
        colStart: Int,
        colEnd: Int,
        height: Int
    ): Int {
        var y = 0
        while (true) {
            val hit = rects.firstOrNull {
                it.colStart <= colEnd && it.colEnd >= colStart &&
                    it.y < y + height && it.y + it.height > y
            } ?: return y
            y = hit.y + hit.height + 1
        }
    }

    /**
     * 普通学习通块：每个任务只在第一次出现的那一行显示标题，跨行不重复；
     * 该行自己覆盖的范围放不下时，剩余文字顺延到下一行。
     */
    private fun applyBlockLabels(rows: List<CalendarRow>): List<CalendarRow> {
        val carry = mutableMapOf<String, String>()

        return rows.map { row ->
            val blocks = row.blocks.map { b ->
                val text = if (b.firstSegment) {
                    b.work.title
                } else {
                    carry.remove(b.work.workId).orEmpty()
                }
                val budget = (b.colEnd - b.colStart + 1) * CELL_TEXT_UNITS
                val (head, tail) = splitLabel(text, budget)
                if (tail.isNotEmpty()) carry[b.work.workId] = tail
                b.copy(label = head)
            }
            row.copy(blocks = blocks)
        }
    }

    /** 合并条的标题锚点：每个任务在本行第一次出现的列，或从上一行顺延下来的剩余文字。 */
    private data class TitleAnchor(
        val entryIndex: Int,
        val col: Int,
        val range: IntRange,
        val text: String,
        val isCarry: Boolean
    )

    /**
     * 标题按“起始列分槽”：
     * - 每个任务只在本行第一次出现时取自己的起始列作为槽起点；
     * - 槽的右边界是下一个任务起始列的前一格，或本任务/本组的最远结束列；
     * - 只有被自身范围末端截断的长标题才把剩余文字顺延到下一行；
     * - 同一起始列的多个任务合并成一个标题，放不下时退化为 `名字 +N`。
     */
    private fun applyBarTitles(
        rows: List<CalendarRow>,
        rowRanges: Array<MutableList<RowRange>>
    ): List<CalendarRow> {
        val carry = mutableMapOf<String, String>()

        return rows.mapIndexed { r, row ->
            val ranges = rowRanges.getOrNull(r).orEmpty()
            val nextRowIds = rowRanges.getOrNull(r + 1).orEmpty()
                .map { it.work.workId }.toSet()
            val bars = row.mergedBars.map { bar ->
                bar.copy(titles = buildBarTitles(bar, ranges, nextRowIds, carry))
            }
            row.copy(mergedBars = bars)
        }
    }

    private fun buildBarTitles(
        bar: CalendarMergedBar,
        ranges: List<RowRange>,
        nextRowIds: Set<String>,
        carry: MutableMap<String, String>
    ): List<MergedBarTitle> {
        val entryIndexByWorkId = bar.entries
            .mapIndexed { index, entry -> entry.work.workId to index }
            .toMap()
        val barRanges = ranges.filter { it.work.workId in entryIndexByWorkId }
        if (barRanges.isEmpty()) return emptyList()

        val newAnchors = barRanges.filter { it.firstSegment }.map { rr ->
            TitleAnchor(
                entryIndex = entryIndexByWorkId.getValue(rr.work.workId),
                col = rr.range.first,
                range = rr.range,
                text = rr.work.title,
                isCarry = false
            )
        }
        val carryAnchors = barRanges
            .filter { !it.firstSegment && carry.containsKey(it.work.workId) }
            .map { rr ->
                TitleAnchor(
                    entryIndex = entryIndexByWorkId.getValue(rr.work.workId),
                    col = rr.range.first,
                    range = rr.range,
                    text = carry.getValue(rr.work.workId),
                    isCarry = true
                )
            }

        // 续行第 0 列同时有新任务标题时，放弃 carry，优先新任务标题。
        val newCols = newAnchors.map { it.col }.toSet()
        carryAnchors.filter { it.col in newCols }.forEach {
            carry.remove(bar.entries[it.entryIndex].work.workId)
        }
        val anchorsByCol = (newAnchors + carryAnchors.filter { it.col !in newCols })
            .groupBy { it.col }
            .toSortedMap()
        val cols = anchorsByCol.keys.toList()

        val titles = mutableListOf<MergedBarTitle>()
        cols.forEachIndexed { index, col ->
            val group = anchorsByCol.getValue(col).sortedBy { it.entryIndex }
            val slotEnd = minOf(
                if (index + 1 < cols.size) cols[index + 1] - 1 else Int.MAX_VALUE,
                group.maxOf { it.range.last }
            )
            val budget = (slotEnd - col + 1) * CELL_TEXT_UNITS

            val newOnes = group.filter { !it.isCarry }
            if (newOnes.isEmpty()) {
                // 纯 carry 组：通常只有一个任务，可以继续顺延；多个 carry 拼接后不再顺延。
                val text = group.joinToString(" · ") { it.text }
                val (head, tail) = splitBarTitle(text, budget)
                if (head.isNotEmpty()) titles += MergedBarTitle(col, slotEnd, head)
                val anchor = group.singleOrNull()
                if (anchor != null) {
                    val id = bar.entries[anchor.entryIndex].work.workId
                    val continues = tail.isNotEmpty() &&
                        slotEnd == anchor.range.last &&
                        anchor.range.last == 6 &&
                        id in nextRowIds
                    if (continues) carry[id] = tail else carry.remove(id)
                }
            } else if (newOnes.size == 1) {
                val anchor = newOnes.single()
                val id = bar.entries[anchor.entryIndex].work.workId
                val (head, tail) = splitBarTitle(anchor.text, budget)
                if (head.isNotEmpty()) titles += MergedBarTitle(col, slotEnd, head)
                // 只有被自身范围末端截断、且下一行还有该任务时才顺延；
                // 被后面任务的起始列截断的剩余文字直接丢弃。
                val continues = tail.isNotEmpty() &&
                    slotEnd == anchor.range.last &&
                    anchor.range.last == 6 &&
                    id in nextRowIds
                if (continues) carry[id] = tail else carry.remove(id)
            } else {
                val names = newOnes.map { bar.entries[it.entryIndex].work.title }
                val text = fitJoinedTitle(names, budget)
                if (text.isNotEmpty()) titles += MergedBarTitle(col, slotEnd, text)
                // 合并标题不做 carry。
                newOnes.forEach { carry.remove(bar.entries[it.entryIndex].work.workId) }
            }
        }
        return titles
    }

    /** 同起始列多个任务：最多拼两个名字，其余用 ` +N` 表示；放不下就只留第一个名字 + 后缀。 */
    private fun fitJoinedTitle(names: List<String>, budget: Float): String {
        val joined = if (names.size <= 2) {
            names.joinToString(" · ")
        } else {
            names.take(2).joinToString(" · ") + " +${names.size - 2}"
        }
        if (textUnits(joined) <= budget) return joined

        val suffix = " +${names.size - 1}"
        val first = truncateWithEllipsis(names.first(), budget - textUnits(suffix))
        return first + suffix
    }

    private fun textUnits(text: String): Float {
        var units = 0f
        for (ch in text) {
            units += if (ch.code < 128) 0.55f else 1f
        }
        return units
    }

    /** 截断到能放进预算，并保留省略号作为“还有更多”的提示。 */
    private fun truncateWithEllipsis(text: String, budget: Float): String {
        if (textUnits(text) <= budget) return text
        val ellipsis = "…"
        var used = textUnits(ellipsis)
        val head = StringBuilder()
        for (ch in text) {
            val w = if (ch.code < 128) 0.55f else 1f
            if (used + w > budget) break
            head.append(ch)
            used += w
        }
        return if (head.isEmpty()) ellipsis else head.toString() + ellipsis
    }

    /**
     * 按字符宽度估算把文字切成「本行放得下」和「顺延到下一行」两部分。
     * 优先在 ` · ` 分隔符后断开，避免把标题从中间切开。
     */
    private fun splitLabel(text: String, budget: Float): Pair<String, String> {
        if (text.isEmpty() || budget <= 0f) return text to ""

        var units = 0f
        var cut = -1
        var lastSeparator = -1
        for (i in text.indices) {
            val w = if (text[i].code < 128) 0.55f else 1f
            if (units + w > budget) {
                cut = i
                break
            }
            units += w
            if (text.startsWith(" · ", i)) lastSeparator = i + 3
        }
        if (cut < 0) return text to ""

        // 断在分隔符上更易读，但不能把本行断成空行。
        val breakAt = if (lastSeparator in 1 until cut) lastSeparator else cut
        val head = text.substring(0, breakAt).trimEnd(' ', '·')
        val tail = text.substring(breakAt).trimStart(' ', '·')
        if (head.isEmpty()) return text to ""
        return head to tail
    }

    /**
     * 合并条标题的切分：只要本行放不下，就在可见文字末尾补省略号，
     * 让用户明确知道标题被截断（剩余部分可能顺延到下一行，也可能直接丢弃）。
     */
    private fun splitBarTitle(text: String, budget: Float): Pair<String, String> {
        val (head, tail) = splitLabel(text, budget)
        if (tail.isEmpty() || head.isEmpty() || head.endsWith("…")) return head to tail
        return "$head…" to tail
    }

    /**
     * 按 workId 稳定取色，整月内冲突时顺延，保证每个任务颜色不同。
     * 颜色本身由 UI 的调色板决定，这里只分配下标。
     */
    private fun assignColors(works: List<Work>): List<MergedBarEntry> {
        val used = BooleanArray(MERGED_PALETTE_SIZE)
        return works.map { work ->
            var index = (work.workId.hashCode() and Int.MAX_VALUE) % MERGED_PALETTE_SIZE
            var steps = 0
            while (used[index] && steps < MERGED_PALETTE_SIZE) {
                index = (index + 1) % MERGED_PALETTE_SIZE
                steps++
            }
            used[index] = true
            MergedBarEntry(work = work, colorIndex = index)
        }
    }

    private fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.set(Calendar.HOUR_OF_DAY, 0); c.clear(Calendar.MINUTE); c.clear(Calendar.SECOND); c.clear(Calendar.MILLISECOND)
        return c.timeInMillis
    }

    private fun parseDateTs(date: String): Long? {
        val parts = date.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return try {
            val c = Calendar.getInstance()
            c.clear()
            c.set(parts[0], parts[1] - 1, parts[2])
            c.timeInMillis
        } catch (e: Exception) { null }
    }
}
