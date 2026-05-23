package com.budget.stats

import com.budget.common.Section
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Controller
@RequestMapping("/stats")
class StatsController(
    private val statsService: StatsService,
) {

    @GetMapping
    fun index(
        @RequestParam(name = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromParam: LocalDate?,
        @RequestParam(name = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toParam: LocalDate?,
        @RequestParam(name = "section", required = false) sectionParam: Section?,
        model: Model,
    ): String {
        val (defaultFrom, defaultTo) = statsService.defaultMonthRange()
        val from = fromParam ?: defaultFrom
        val to = toParam ?: defaultTo
        val section = sectionParam ?: Section.SHARED

        val summary = statsService.sectionSummary(from, to)
        val tagBreakdown = statsService.tagBreakdown(from, to)
        val monthly = statsService.monthlyTrend()
        val dailyCumulative = statsService.sectionDailyCumulative(section, from, to)

        model.addAttribute("from", from)
        model.addAttribute("to", to)
        model.addAttribute("section", section)
        model.addAttribute("sections", Section.all())
        model.addAttribute("summary", summary)
        model.addAttribute("tagBreakdown", tagBreakdown)
        model.addAttribute("tagLabels", tagBreakdown.map { it.tagName })
        model.addAttribute("tagValues", tagBreakdown.map { it.total })
        model.addAttribute("tagColors", tagBreakdown.map { it.color })
        model.addAttribute("monthlyLabels", monthly.map { it.label })
        model.addAttribute("monthlyIncome", monthly.map { it.income })
        model.addAttribute("monthlyExpense", monthly.map { it.expense })
        model.addAttribute("dailyLabels", dailyCumulative.map { it.date.toString() })
        model.addAttribute("dailyValues", dailyCumulative.map { it.cumulative })

        return "stats/index"
    }
}
