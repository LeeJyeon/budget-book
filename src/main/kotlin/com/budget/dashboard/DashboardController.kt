package com.budget.dashboard

import com.budget.asset.AssetTrendPoint
import com.budget.asset.AssetTrendService
import com.budget.balance.BalanceService
import com.budget.balance.SectionBalanceView
import com.budget.common.Section
import com.budget.stats.PeriodSummary
import com.budget.stats.StatsService
import com.budget.transaction.Transaction
import com.budget.transaction.TransactionRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SectionBalanceCard(
    val section: Section,
    val currentBalance: Long,
    val monthChange: Long,
)

@Controller
class DashboardController(
    private val balanceService: BalanceService,
    private val statsService: StatsService,
    private val transactionRepository: TransactionRepository,
    private val assetTrendService: AssetTrendService,
) {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    @GetMapping("/")
    fun index(
        @RequestParam(required = false) month: String?,
        model: Model,
    ): String {
        val today: LocalDate = LocalDate.now(zone)
        val currentYm: YearMonth = YearMonth.from(today)
        val selectedYm: YearMonth = parseMonth(month) ?: currentYm
        val monthFrom: LocalDate = selectedYm.atDay(1)
        val monthTo: LocalDate = selectedYm.atEndOfMonth()
        val isCurrentMonth: Boolean = selectedYm == currentYm

        val balances: List<SectionBalanceView> = balanceService.listAll()
        val monthSummary: PeriodSummary = statsService.sectionSummary(monthFrom, monthTo)
        val perSectionMonthly = monthSummary.perSection.associateBy { it.section }
        val cards: List<SectionBalanceCard> = balances.map { b ->
            val ms = perSectionMonthly[b.section]
            val monthChange = if (ms != null) ms.income - ms.expense else 0L
            SectionBalanceCard(
                section = b.section,
                currentBalance = b.currentBalance,
                monthChange = monthChange,
            )
        }

        val recent: List<Transaction> = transactionRepository
            .findTop10ByOccurredAtBetweenOrderByOccurredAtDescIdDesc(
                monthFrom.atStartOfDay(),
                monthTo.atTime(23, 59, 59),
            )

        val totalCurrent: Long = balances.sumOf { it.currentBalance }

        // Asset trend (last 12 months) + net worth
        val trendPoints: List<AssetTrendPoint> = assetTrendService.trend(12, today)
        val assetTrendLabels: List<String> = trendPoints.map { it.yearMonth.format(monthFmt) }
        val assetTrendAssets: List<Long> = trendPoints.map { it.totalAssets }
        val assetTrendNetWorth: List<Long> = trendPoints.map { it.netWorth }
        val netWorth: Long = assetTrendService.latestNetWorth(today)

        // Saving rate: (income - expense) / income * 100, integer; null if income == 0
        val income: Long = monthSummary.totalIncome
        val expense: Long = monthSummary.totalExpense
        val savingRatePercent: Int? = if (income == 0L) null else ((income - expense) * 100 / income).toInt()
        val savingRate: String = savingRatePercent?.let { "$it%" } ?: "—"

        model.addAttribute("today", today)
        model.addAttribute("selectedYm", selectedYm)
        model.addAttribute("selectedMonth", selectedYm.format(monthFmt))
        model.addAttribute("prevMonth", selectedYm.minusMonths(1).format(monthFmt))
        model.addAttribute("nextMonth", selectedYm.plusMonths(1).format(monthFmt))
        model.addAttribute("isCurrentMonth", isCurrentMonth)
        model.addAttribute("monthFrom", monthFrom)
        model.addAttribute("monthTo", monthTo)
        model.addAttribute("balances", balances)
        model.addAttribute("balanceCards", cards)
        model.addAttribute("monthSummary", monthSummary)
        model.addAttribute("recentTransactions", recent)
        model.addAttribute("totalCurrentBalance", totalCurrent)
        model.addAttribute("netWorth", netWorth)
        model.addAttribute("assetTrendLabels", assetTrendLabels)
        model.addAttribute("assetTrendAssets", assetTrendAssets)
        model.addAttribute("assetTrendNetWorth", assetTrendNetWorth)
        model.addAttribute("savingRatePercent", savingRatePercent)
        model.addAttribute("savingRate", savingRate)

        return "dashboard/index"
    }

    private fun parseMonth(raw: String?): YearMonth? {
        if (raw.isNullOrBlank()) return null
        return runCatching { YearMonth.parse(raw, monthFmt) }.getOrNull()
    }
}
