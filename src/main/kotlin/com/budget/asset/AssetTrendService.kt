package com.budget.asset

import com.budget.balance.InitialBalanceRepository
import com.budget.transaction.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 월별 자산 추이 1포인트.
 * - source = SNAPSHOT: 사용자가 [asset_snapshots]에 직접 입력한 값
 * - source = COMPUTED: 직전 스냅샷 + 그 사이의 거래 누적으로 보간한 추정값
 *   (스냅샷이 하나도 없으면 [initial_balances] 합계에서 시작)
 */
data class AssetTrendPoint(
    val yearMonth: YearMonth,
    val totalAssets: Long,
    val debt: Long,
    val source: Source,
) {
    val netWorth: Long get() = totalAssets - debt

    enum class Source { SNAPSHOT, COMPUTED }
}

@Service
@Transactional(readOnly = true)
class AssetTrendService(
    private val snapshotRepository: AssetSnapshotRepository,
    private val initialBalanceRepository: InitialBalanceRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    /** 마지막 [months] 개월(현재 달 포함) 자산 추이를 반환. 가장 오래된 달이 0번째. */
    fun trend(months: Int = 12, today: LocalDate = LocalDate.now(zone)): List<AssetTrendPoint> {
        require(months in 1..36) { "months는 1~36 사이" }
        val endMonth: YearMonth = YearMonth.from(today)
        val startMonth: YearMonth = endMonth.minusMonths((months - 1).toLong())

        val snapshotsByYm: Map<YearMonth, AssetSnapshot> = snapshotRepository
            .findAllByYearMonthBetweenOrderByYearMonthAsc(startMonth.toString(), endMonth.toString())
            .associateBy { YearMonth.parse(it.yearMonth) }

        // computed 베이스라인: (직전 스냅샷 + 이후 거래 누적) | (스냅샷 0건이면 기초자산 합계 + 모든 거래 누적)
        val priorSnapshot: AssetSnapshot? = snapshotRepository.findTopByOrderByYearMonthDesc()
            ?.takeIf { YearMonth.parse(it.yearMonth) < startMonth }
            ?: snapshotRepository.findAllByOrderByYearMonthAsc()
                .lastOrNull { YearMonth.parse(it.yearMonth) < startMonth }

        val initialSum: Long = initialBalanceRepository.findAll().sumOf { it.amount }
        val initialAsset: Long = priorSnapshot?.totalAssets ?: initialSum
        val initialDebt: Long = priorSnapshot?.debt ?: 0L

        val result = mutableListOf<AssetTrendPoint>()
        var cursor = startMonth
        var runningAsset = initialAsset
        var runningDebt = initialDebt

        while (!cursor.isAfter(endMonth)) {
            val snap: AssetSnapshot? = snapshotsByYm[cursor]
            if (snap != null) {
                runningAsset = snap.totalAssets
                runningDebt = snap.debt
                result += AssetTrendPoint(cursor, runningAsset, runningDebt, AssetTrendPoint.Source.SNAPSHOT)
            } else {
                val from = cursor.atDay(1).atStartOfDay()
                val to = cursor.atEndOfMonth().atTime(23, 59, 59)
                val net = monthlyNetCashFlow(from, to)
                runningAsset += net
                result += AssetTrendPoint(cursor, runningAsset, runningDebt, AssetTrendPoint.Source.COMPUTED)
            }
            cursor = cursor.plusMonths(1)
        }

        return result
    }

    private fun monthlyNetCashFlow(
        from: java.time.LocalDateTime,
        to: java.time.LocalDateTime,
    ): Long {
        val rows = transactionRepository.sumByPeriodGroupedBySectionAndType(from, to)
        var income = 0L
        var expense = 0L
        rows.forEach { r ->
            when (r.type) {
                com.budget.common.TxType.INCOME -> income += r.total
                com.budget.common.TxType.EXPENSE -> expense += r.total
            }
        }
        return income - expense
    }

    /** 가장 최근 순자산 — 우선 최신 스냅샷, 없으면 trend의 마지막 포인트. */
    fun latestNetWorth(today: LocalDate = LocalDate.now(zone)): Long {
        val latest = snapshotRepository.findTopByOrderByYearMonthDesc()
        if (latest != null) return latest.netWorth
        val trend = trend(months = 1, today = today)
        return trend.lastOrNull()?.netWorth ?: 0L
    }
}
