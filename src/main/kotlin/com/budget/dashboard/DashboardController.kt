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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

    @GetMapping("/")
    fun index(model: Model): String {
        val today = LocalDate.now(zone)
        val ym = YearMonth.from(today)
        val monthFrom: LocalDate = ym.atDay(1)
        val monthTo: LocalDate = ym.atEndOfMonth()

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

        val recent: List<Transaction> = transactionRepository.findTop10ByOrderByOccurredAtDescIdDesc()

        model.addAttribute("today", today)
        model.addAttribute("monthFrom", monthFrom)
        model.addAttribute("monthTo", monthTo)
        model.addAttribute("balances", balances)
        model.addAttribute("balanceCards", cards)
        model.addAttribute("monthSummary", monthSummary)
        model.addAttribute("recentTransactions", recent)

        return "dashboard/index"
    }
}
