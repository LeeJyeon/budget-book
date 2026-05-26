package com.budget.stats

import com.budget.common.Section
import com.budget.common.TxType
import com.budget.transaction.DailyTypeAggregate
import com.budget.transaction.MonthlyTypeAggregate
import com.budget.transaction.SectionTypeAggregate
import com.budget.transaction.TagAggregate
import com.budget.transaction.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatsServiceTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var statsService: StatsService

    @BeforeEach
    fun setUp() {
        transactionRepository = mockk()
        statsService = StatsService(transactionRepository)
    }

    private fun sectionType(s: Section, t: TxType, v: Long): SectionTypeAggregate = object : SectionTypeAggregate {
        override val section = s
        override val type = t
        override val total = v
    }

    private fun tagAgg(id: Long, name: String, color: String, v: Long): TagAggregate = object : TagAggregate {
        override val tagId = id
        override val tagName = name
        override val color = color
        override val total = v
    }

    private fun monthly(y: Int, m: Int, t: TxType, v: Long): MonthlyTypeAggregate = object : MonthlyTypeAggregate {
        override val year = y
        override val month = m
        override val type = t
        override val total = v
    }

    private fun daily(y: Int, m: Int, d: Int, t: TxType, v: Long): DailyTypeAggregate = object : DailyTypeAggregate {
        override val year = y
        override val month = m
        override val day = d
        override val type = t
        override val total = v
    }

    @Test
    fun `sectionSummary groups rows by section with income and expense and includes missing sections as zero`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        every {
            transactionRepository.sumByPeriodGroupedBySectionAndType(
                from.atStartOfDay(), to.atTime(23, 59, 59),
            )
        } returns listOf(
            sectionType(Section.SHARED, TxType.INCOME, 1_000L),
            sectionType(Section.SHARED, TxType.EXPENSE, 400L),
            sectionType(Section.HUSBAND, TxType.INCOME, 2_000L),
        )

        val summary = statsService.sectionSummary(from, to)

        assertThat(summary.from).isEqualTo(from)
        assertThat(summary.to).isEqualTo(to)
        assertThat(summary.perSection).hasSize(3)
        val shared = summary.perSection.first { it.section == Section.SHARED }
        assertThat(shared.income).isEqualTo(1_000L)
        assertThat(shared.expense).isEqualTo(400L)
        assertThat(shared.net).isEqualTo(600L)
        val wife = summary.perSection.first { it.section == Section.WIFE }
        assertThat(wife.income).isEqualTo(0L)
        assertThat(wife.expense).isEqualTo(0L)
        assertThat(summary.totalIncome).isEqualTo(3_000L)
        assertThat(summary.totalExpense).isEqualTo(400L)
        assertThat(summary.net).isEqualTo(2_600L)
    }

    @Test
    fun `tagBreakdown returns at most limit items in repository order`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        val rows = (1..12).map { i -> tagAgg(i.toLong(), "tag$i", "#000000", (1000 - i).toLong()) }
        every {
            transactionRepository.sumByPeriodGroupedByTag(
                TxType.EXPENSE, from.atStartOfDay(), to.atTime(23, 59, 59),
            )
        } returns rows

        val result = statsService.tagBreakdown(from, to, limit = 10)

        assertThat(result).hasSize(10)
        assertThat(result.first().tagName).isEqualTo("tag1")
        assertThat(result.first().total).isEqualTo(999L)
        assertThat(result.last().tagName).isEqualTo("tag10")
    }

    @Test
    fun `monthlyTrend fills in missing months with zeros for the requested window`() {
        val today = LocalDate.of(2026, 5, 23)
        // Only one row in March
        every {
            transactionRepository.monthlyTotals(any(), any())
        } returns listOf(
            monthly(2026, 3, TxType.INCOME, 500L),
            monthly(2026, 3, TxType.EXPENSE, 200L),
            monthly(2026, 5, TxType.EXPENSE, 700L),
        )

        val trend = statsService.monthlyTrend(monthsBack = 6, today = today)

        assertThat(trend).hasSize(6)
        assertThat(trend.map { it.label }).containsExactly(
            "2025-12", "2026-01", "2026-02", "2026-03", "2026-04", "2026-05",
        )
        val mar = trend.first { it.label == "2026-03" }
        assertThat(mar.income).isEqualTo(500L)
        assertThat(mar.expense).isEqualTo(200L)
        val apr = trend.first { it.label == "2026-04" }
        assertThat(apr.income).isEqualTo(0L)
        assertThat(apr.expense).isEqualTo(0L)
        val may = trend.first { it.label == "2026-05" }
        assertThat(may.income).isEqualTo(0L)
        assertThat(may.expense).isEqualTo(700L)
    }

    @Test
    fun `sectionDailyCumulative builds cumulative line from starting balance`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 5)
        every {
            transactionRepository.dailyTotalsForSection(
                Section.SHARED, from.atStartOfDay(), to.atTime(23, 59, 59),
            )
        } returns listOf(
            daily(2026, 5, 2, TxType.INCOME, 100L),
            daily(2026, 5, 3, TxType.EXPENSE, 30L),
            daily(2026, 5, 5, TxType.INCOME, 20L),
        )

        val points = statsService.sectionDailyCumulative(Section.SHARED, from, to, startingBalance = 1_000L)

        assertThat(points.map { it.date }).containsExactly(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 2),
            LocalDate.of(2026, 5, 3),
            LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5),
        )
        assertThat(points.map { it.cumulative }).containsExactly(
            1_000L,
            1_100L,
            1_070L,
            1_070L,
            1_090L,
        )
    }

    @Test
    fun `donutSlices keeps top N and folds the rest into a single 기타 bucket`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        // 11 tags, descending totals 1000..990
        val rows = (1..11).map { i -> tagAgg(i.toLong(), "tag$i", "#111111", (1001 - i).toLong()) }
        every {
            transactionRepository.sumByPeriodGroupedByTag(
                TxType.EXPENSE, from.atStartOfDay(), to.atTime(23, 59, 59),
            )
        } returns rows

        val slices = statsService.donutSlices(from, to, topN = 8)

        assertThat(slices).hasSize(9)
        assertThat(slices.take(8).map { it.tagName }).containsExactly(
            "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8",
        )
        val others = slices.last()
        assertThat(others.tagName).isEqualTo("기타")
        assertThat(others.tagId).isEqualTo(0L)
        assertThat(others.color).isEqualTo("#94a3b8")
        // tag9 + tag10 + tag11 = 992 + 991 + 990
        assertThat(others.total).isEqualTo(992L + 991L + 990L)
    }

    @Test
    fun `donutSlices returns all entries unchanged when count is within topN`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        val rows = (1..3).map { i -> tagAgg(i.toLong(), "tag$i", "#222222", (100 - i).toLong()) }
        every {
            transactionRepository.sumByPeriodGroupedByTag(
                TxType.EXPENSE, from.atStartOfDay(), to.atTime(23, 59, 59),
            )
        } returns rows

        val slices = statsService.donutSlices(from, to, topN = 8)

        assertThat(slices).hasSize(3)
        assertThat(slices.none { it.tagName == "기타" }).isTrue()
    }

    @Test
    fun `defaultMonthRange returns first and last day of current Seoul month`() {
        val (from, to) = statsService.defaultMonthRange()

        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
        val ym = java.time.YearMonth.from(today)
        assertThat(from).isEqualTo(ym.atDay(1))
        assertThat(to).isEqualTo(ym.atEndOfMonth())
    }
}
