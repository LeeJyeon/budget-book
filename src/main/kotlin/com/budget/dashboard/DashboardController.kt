package com.budget.dashboard

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

        return "dashboard/index"
    }

    private fun parseMonth(raw: String?): YearMonth? {
        if (raw.isNullOrBlank()) return null
        return runCatching { YearMonth.parse(raw, monthFmt) }.getOrNull()
    }
}
