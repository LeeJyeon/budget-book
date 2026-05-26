package com.budget.asset

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 한 행(월)의 표시용 모델. snapshot이 null이면 추정값(COMPUTED). */
data class AssetSnapshotRow(
    val yearMonth: YearMonth,
    val totalAssets: Long,
    val debt: Long,
    val memo: String?,
    val source: AssetTrendPoint.Source,
) {
    val netWorth: Long get() = totalAssets - debt
    val yearMonthString: String get() = yearMonth.toString()
    val isSnapshot: Boolean get() = source == AssetTrendPoint.Source.SNAPSHOT
}

@Controller
@RequestMapping("/assets")
class AssetSnapshotController(
    private val snapshotService: AssetSnapshotService,
    private val trendService: AssetTrendService,
) {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    @GetMapping
    fun index(
        @RequestParam(name = "from", required = false)
        @DateTimeFormat(pattern = "yyyy-MM")
        fromParam: YearMonth?,
        @RequestParam(name = "to", required = false)
        @DateTimeFormat(pattern = "yyyy-MM")
        toParam: YearMonth?,
        model: Model,
    ): String {
        val today: LocalDate = LocalDate.now(zone)
        val defaultTo: YearMonth = YearMonth.from(today)
        val defaultFrom: YearMonth = defaultTo.minusMonths(11)

        var from: YearMonth = fromParam ?: defaultFrom
        var to: YearMonth = toParam ?: defaultTo
        if (from.isAfter(to)) {
            // swap if user passed an inverted range
            val tmp = from; from = to; to = tmp
        }

        val months: Int = monthsBetween(from, to)
        val trendPoints: List<AssetTrendPoint> = trendService.trend(months = months, today = to.atEndOfMonth())
        val rows: List<AssetSnapshotRow> = trendPoints.map {
            AssetSnapshotRow(
                yearMonth = it.yearMonth,
                totalAssets = it.totalAssets,
                debt = it.debt,
                memo = null,
                source = it.source,
            )
        }
        // attach memo from snapshot rows when source = SNAPSHOT
        val snapshots = snapshotService.listInRange(from, to).associateBy { it.yearMonth }
        val rowsWithMemo: List<AssetSnapshotRow> = rows.map { r ->
            val s = snapshots[r.yearMonthString]
            if (s != null) r.copy(memo = s.memo) else r
        }

        val trendLabels: List<String> = trendPoints.map { it.yearMonth.toString() }
        val trendAssets: List<Long> = trendPoints.map { it.totalAssets }
        val trendNetWorth: List<Long> = trendPoints.map { it.netWorth }
        val latestPoint: AssetTrendPoint? = trendPoints.lastOrNull()

        model.addAttribute("rows", rowsWithMemo.sortedByDescending { it.yearMonth })
        model.addAttribute("fromYm", from.toString())
        model.addAttribute("toYm", to.toString())
        model.addAttribute("trendLabels", trendLabels)
        model.addAttribute("trendAssets", trendAssets)
        model.addAttribute("trendNetWorth", trendNetWorth)
        model.addAttribute(
            "latestNetWorth",
            latestPoint?.netWorth ?: trendService.latestNetWorth(today),
        )
        model.addAttribute("latestTotalAssets", latestPoint?.totalAssets ?: 0L)
        model.addAttribute("latestDebt", latestPoint?.debt ?: 0L)
        model.addAttribute("latestYearMonth", latestPoint?.yearMonth?.toString() ?: to.toString())

        return "asset/index"
    }

    @PostMapping("/{yearMonth}")
    fun upsert(
        @PathVariable yearMonth: String,
        @Valid @ModelAttribute("form") form: AssetSnapshotForm,
        bindingResult: BindingResult,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "redirect:/assets"
        }
        try {
            snapshotService.upsert(yearMonth, form)
            redirectAttrs.addFlashAttribute(
                "flashMessage",
                "${formatKoreanMonth(yearMonth)} 자산 스냅샷을 저장했어요.",
            )
        } catch (e: IllegalArgumentException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message ?: "잘못된 입력입니다.")
        }
        return "redirect:/assets"
    }

    @PostMapping("/{yearMonth}/delete")
    fun delete(
        @PathVariable yearMonth: String,
        redirectAttrs: RedirectAttributes,
    ): String {
        try {
            snapshotService.delete(yearMonth)
            redirectAttrs.addFlashAttribute(
                "flashMessage",
                "${formatKoreanMonth(yearMonth)} 자산 스냅샷을 삭제했어요.",
            )
        } catch (e: AssetSnapshotNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        } catch (e: IllegalArgumentException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message ?: "잘못된 입력입니다.")
        }
        return "redirect:/assets"
    }

    private fun monthsBetween(from: YearMonth, to: YearMonth): Int {
        val diff = (to.year - from.year) * 12 + (to.monthValue - from.monthValue) + 1
        return diff.coerceIn(1, 36)
    }

    private fun formatKoreanMonth(yearMonth: String): String {
        return try {
            val ym = YearMonth.parse(yearMonth)
            "${ym.year}년 ${ym.monthValue}월"
        } catch (e: Exception) {
            yearMonth
        }
    }
}
