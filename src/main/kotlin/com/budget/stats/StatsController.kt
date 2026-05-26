package com.budget.stats

import com.budget.asset.AssetTrendService
import com.budget.common.Section
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate
import java.time.ZoneId

@Controller
@RequestMapping("/stats")
class StatsController(
    private val statsService: StatsService,
    private val assetTrendService: AssetTrendService,
) {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

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
        val donut = statsService.donutSlices(from, to)
        val monthly = statsService.monthlyTrend()
        val dailyCumulative = statsService.sectionDailyCumulative(section, from, to)
        val assetTrend = assetTrendService.trend(12, LocalDate.now(zone))

        model.addAttribute("from", from)
        model.addAttribute("to", to)
        model.addAttribute("section", section)
        model.addAttribute("sections", Section.all())
        model.addAttribute("summary", summary)

        // Original tag bar attributes (kept for backward compatibility)
        model.addAttribute("tagBreakdown", tagBreakdown)
        model.addAttribute("tagLabels", tagBreakdown.map { it.tagName })
        model.addAttribute("tagValues", tagBreakdown.map { it.total })
        model.addAttribute("tagColors", tagBreakdown.map { it.color })

        // Donut (top 8 + 기타)
        model.addAttribute("donut", donut)
        model.addAttribute("donutLabels", donut.map { it.tagName })
        model.addAttribute("donutValues", donut.map { it.total })
        model.addAttribute("donutColors", donut.map { it.color })

        // Monthly grouped bar (YYYY-MM labels)
        model.addAttribute("monthlyLabels", monthly.map { it.label })
        model.addAttribute("monthlyIncome", monthly.map { it.income })
        model.addAttribute("monthlyExpense", monthly.map { it.expense })

        // Asset trend line (12 months)
        model.addAttribute("assetTrendLabels", assetTrend.map { it.yearMonth.toString() })
        model.addAttribute("assetTrendNetWorth", assetTrend.map { it.netWorth })
        model.addAttribute("assetTrendAssets", assetTrend.map { it.totalAssets })
        model.addAttribute("assetTrendSources", assetTrend.map { it.source.name })

        // Daily cumulative line
        model.addAttribute("dailyLabels", dailyCumulative.map { it.date.toString() })
        model.addAttribute("dailyValues", dailyCumulative.map { it.cumulative })

        return "stats/index"
    }
}
