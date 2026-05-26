package com.budget.stats

import com.budget.common.Section
import com.budget.common.TxType
import com.budget.transaction.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class SectionTypeSummary(
    val section: Section,
    val income: Long,
    val expense: Long,
) {
    val net: Long get() = income - expense
}

data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val perSection: List<SectionTypeSummary>,
) {
    val totalIncome: Long get() = perSection.sumOf { it.income }
    val totalExpense: Long get() = perSection.sumOf { it.expense }
    val net: Long get() = totalIncome - totalExpense
}

data class TagBreakdownItem(
    val tagId: Long,
    val tagName: String,
    val color: String,
    val total: Long,
)

data class MonthlyPoint(
    val year: Int,
    val month: Int,
    val income: Long,
    val expense: Long,
) {
    val label: String get() = "%04d-%02d".format(year, month)
}

data class DailyCumulativePoint(
    val date: LocalDate,
    val cumulative: Long,
)

@Service
@Transactional(readOnly = true)
class StatsService(
    private val transactionRepository: TransactionRepository,
) {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    fun defaultMonthRange(): Pair<LocalDate, LocalDate> {
        val ym = YearMonth.from(LocalDate.now(zone))
        return ym.atDay(1) to ym.atEndOfMonth()
    }

    fun sectionSummary(from: LocalDate, to: LocalDate): PeriodSummary {
        val rows = transactionRepository.sumByPeriodGroupedBySectionAndType(
            from.atStartOfDay(),
            to.atTime(23, 59, 59),
        )
        val bySection: Map<Section, Map<TxType, Long>> =
            rows.groupBy { it.section }.mapValues { (_, list) ->
                list.associate { it.type to it.total }
            }
        val perSection = Section.all().map { s ->
            val byType = bySection[s].orEmpty()
            SectionTypeSummary(
                section = s,
                income = byType[TxType.INCOME] ?: 0L,
                expense = byType[TxType.EXPENSE] ?: 0L,
            )
        }
        return PeriodSummary(from, to, perSection)
    }

    fun tagBreakdown(from: LocalDate, to: LocalDate, type: TxType = TxType.EXPENSE, limit: Int = 10): List<TagBreakdownItem> {
        val rows = transactionRepository.sumByPeriodGroupedByTag(
            type,
            from.atStartOfDay(),
            to.atTime(23, 59, 59),
        )
        return rows.take(limit).map {
            TagBreakdownItem(
                tagId = it.tagId,
                tagName = it.tagName,
                color = it.color,
                total = it.total,
            )
        }
    }

    /**
     * Donut-friendly tag breakdown: top [topN] entries kept as-is, the rest summed into a single
     * "기타" bucket (tagId = 0, color = #94a3b8). Returns an empty list when there is no data.
     */
    fun donutSlices(
        from: LocalDate,
        to: LocalDate,
        type: TxType = TxType.EXPENSE,
        topN: Int = 8,
    ): List<TagBreakdownItem> {
        val all = tagBreakdown(from, to, type = type, limit = Int.MAX_VALUE)
        if (all.size <= topN) return all
        val top = all.take(topN)
        val rest = all.drop(topN)
        val restTotal = rest.sumOf { it.total }
        if (restTotal == 0L) return top
        return top + TagBreakdownItem(
            tagId = 0L,
            tagName = "기타",
            color = "#94a3b8",
            total = restTotal,
        )
    }

    fun monthlyTrend(): List<MonthlyPoint> = monthlyTrend(6, LocalDate.now(zone))

    internal fun monthlyTrend(monthsBack: Int, today: LocalDate): List<MonthlyPoint> {
        val endMonth = YearMonth.from(today)
        val startMonth = endMonth.minusMonths((monthsBack - 1).toLong())
        val from = startMonth.atDay(1).atStartOfDay()
        val to = endMonth.atEndOfMonth().atTime(23, 59, 59)
        val rows = transactionRepository.monthlyTotals(from, to)
        val byYm: MutableMap<String, MutableMap<TxType, Long>> = mutableMapOf()
        rows.forEach { row ->
            val key = "%04d-%02d".format(row.year, row.month)
            val inner = byYm.getOrPut(key) { mutableMapOf() }
            inner[row.type] = row.total
        }
        val result = mutableListOf<MonthlyPoint>()
        var cursor = startMonth
        while (!cursor.isAfter(endMonth)) {
            val key = "%04d-%02d".format(cursor.year, cursor.monthValue)
            val inner = byYm[key].orEmpty()
            result += MonthlyPoint(
                year = cursor.year,
                month = cursor.monthValue,
                income = inner[TxType.INCOME] ?: 0L,
                expense = inner[TxType.EXPENSE] ?: 0L,
            )
            cursor = cursor.plusMonths(1)
        }
        return result
    }

    /**
     * Cumulative daily balance trend for [section] across [from, to].
     * Starts from `startingBalance` (use the section's initial amount if available).
     */
    fun sectionDailyCumulative(
        section: Section,
        from: LocalDate,
        to: LocalDate,
        startingBalance: Long = 0L,
    ): List<DailyCumulativePoint> {
        val rows = transactionRepository.dailyTotalsForSection(
            section,
            from.atStartOfDay(),
            to.atTime(23, 59, 59),
        )
        val deltaByDate: MutableMap<LocalDate, Long> = sortedMapOf()
        rows.forEach { r ->
            val d = LocalDate.of(r.year, r.month, r.day)
            val signed = when (r.type) {
                TxType.INCOME -> r.total
                TxType.EXPENSE -> -r.total
            }
            deltaByDate.merge(d, signed) { a, b -> a + b }
        }
        val result = mutableListOf<DailyCumulativePoint>()
        var running = startingBalance
        var cursor = from
        while (!cursor.isAfter(to)) {
            running += deltaByDate[cursor] ?: 0L
            result += DailyCumulativePoint(cursor, running)
            cursor = cursor.plusDays(1)
        }
        return result
    }
}
