package com.budget.asset

import com.budget.auth.User
import com.budget.auth.UserRepository
import com.budget.balance.InitialBalance
import com.budget.balance.InitialBalanceRepository
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.transaction.Transaction
import com.budget.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@DataJpaTest
@ActiveProfiles("local")
class AssetTrendServiceTest @Autowired constructor(
    private val snapshotRepository: AssetSnapshotRepository,
    private val initialBalanceRepository: InitialBalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
) {

    private lateinit var trendService: AssetTrendService
    private lateinit var user: User
    private val today: LocalDate = LocalDate.of(2026, 5, 15)

    @BeforeEach
    fun setUp() {
        trendService = AssetTrendService(
            snapshotRepository = snapshotRepository,
            initialBalanceRepository = initialBalanceRepository,
            transactionRepository = transactionRepository,
        )
        user = userRepository.save(
            User(email = "trend@example.com", displayName = "Trend Tester", role = UserRole.HUSBAND),
        )
    }

    private fun saveTx(type: TxType, amount: Long, at: LocalDateTime) {
        transactionRepository.save(
            Transaction(
                section = Section.SHARED,
                type = type,
                amount = amount,
                occurredAt = at,
                createdBy = user,
            ),
        )
    }

    private fun saveSnap(yearMonth: String, totalAssets: Long, debt: Long = 0L) {
        snapshotRepository.save(
            AssetSnapshot(yearMonth = yearMonth, totalAssets = totalAssets, debt = debt),
        )
    }

    @Test
    fun `no snapshots and no transactions produces twelve computed zero points`() {
        val points = trendService.trend(months = 12, today = today)

        assertThat(points).hasSize(12)
        assertThat(points.first().yearMonth).isEqualTo(YearMonth.of(2025, 6))
        assertThat(points.last().yearMonth).isEqualTo(YearMonth.of(2026, 5))
        assertThat(points).allMatch { it.source == AssetTrendPoint.Source.COMPUTED }
        assertThat(points).allMatch { it.totalAssets == 0L && it.debt == 0L }
    }

    @Test
    fun `initial balances and transactions accumulate into computed running cashflow`() {
        // initial = 1,000,000 KRW seed
        initialBalanceRepository.save(
            InitialBalance(section = Section.SHARED, amount = 1_000_000L, asOfDate = LocalDate.of(2025, 1, 1)),
        )
        initialBalanceRepository.save(
            InitialBalance(section = Section.HUSBAND, amount = 500_000L, asOfDate = LocalDate.of(2025, 1, 1)),
        )

        // +200,000 in 2026-03, -50,000 in 2026-04
        saveTx(TxType.INCOME, 200_000L, LocalDateTime.of(2026, 3, 10, 12, 0))
        saveTx(TxType.EXPENSE, 50_000L, LocalDateTime.of(2026, 4, 20, 12, 0))

        val points = trendService.trend(months = 6, today = today)

        // 2025-12 .. 2026-05
        assertThat(points).hasSize(6)
        assertThat(points).allMatch { it.source == AssetTrendPoint.Source.COMPUTED }

        val base = 1_500_000L // 1,000,000 + 500,000
        // months before any tx (2025-12, 2026-01, 2026-02) = base
        assertThat(points[0].totalAssets).isEqualTo(base)
        assertThat(points[1].totalAssets).isEqualTo(base)
        assertThat(points[2].totalAssets).isEqualTo(base)
        // 2026-03: +200k
        assertThat(points[3].totalAssets).isEqualTo(base + 200_000L)
        // 2026-04: -50k (running)
        assertThat(points[4].totalAssets).isEqualTo(base + 200_000L - 50_000L)
        // 2026-05: unchanged
        assertThat(points[5].totalAssets).isEqualTo(base + 200_000L - 50_000L)
    }

    @Test
    fun `single mid-range snapshot is SNAPSHOT and neighbors are COMPUTED from it`() {
        // baseline 0; snapshot in 2026-02 sets total = 10,000,000
        saveSnap("2026-02", totalAssets = 10_000_000L, debt = 2_000_000L)
        // transactions in 2026-03 add net +300k
        saveTx(TxType.INCOME, 500_000L, LocalDateTime.of(2026, 3, 5, 9, 0))
        saveTx(TxType.EXPENSE, 200_000L, LocalDateTime.of(2026, 3, 15, 9, 0))

        val points = trendService.trend(months = 6, today = today)
        // 2025-12, 2026-01, 2026-02, 2026-03, 2026-04, 2026-05

        val byYm = points.associateBy { it.yearMonth }
        assertThat(byYm[YearMonth.of(2026, 2)]!!.source).isEqualTo(AssetTrendPoint.Source.SNAPSHOT)
        assertThat(byYm[YearMonth.of(2026, 2)]!!.totalAssets).isEqualTo(10_000_000L)
        assertThat(byYm[YearMonth.of(2026, 2)]!!.debt).isEqualTo(2_000_000L)

        // 2026-03 is COMPUTED off the snapshot (totalAssets += 300k, debt carried)
        val mar = byYm[YearMonth.of(2026, 3)]!!
        assertThat(mar.source).isEqualTo(AssetTrendPoint.Source.COMPUTED)
        assertThat(mar.totalAssets).isEqualTo(10_300_000L)
        assertThat(mar.debt).isEqualTo(2_000_000L)

        // pre-snapshot months: no prior snapshot and no initial balances => 0
        val jan = byYm[YearMonth.of(2026, 1)]!!
        assertThat(jan.source).isEqualTo(AssetTrendPoint.Source.COMPUTED)
        assertThat(jan.totalAssets).isEqualTo(0L)
    }

    @Test
    fun `two snapshots with a gap month interpolate via prior snapshot plus cashflow`() {
        // snapshot in 2026-01: 5,000,000
        saveSnap("2026-01", totalAssets = 5_000_000L)
        // snapshot in 2026-03: 6_000_000  (no debt)
        saveSnap("2026-03", totalAssets = 6_000_000L)
        // 2026-02 tx: +100,000 (which informs the gap month)
        saveTx(TxType.INCOME, 100_000L, LocalDateTime.of(2026, 2, 10, 9, 0))

        val points = trendService.trend(months = 5, today = today)
        // 2026-01 .. 2026-05
        val byYm = points.associateBy { it.yearMonth }

        assertThat(byYm[YearMonth.of(2026, 1)]!!.source).isEqualTo(AssetTrendPoint.Source.SNAPSHOT)
        assertThat(byYm[YearMonth.of(2026, 1)]!!.totalAssets).isEqualTo(5_000_000L)

        // 2026-02 is COMPUTED based on the 2026-01 snapshot + 2026-02 tx
        val feb = byYm[YearMonth.of(2026, 2)]!!
        assertThat(feb.source).isEqualTo(AssetTrendPoint.Source.COMPUTED)
        assertThat(feb.totalAssets).isEqualTo(5_100_000L)

        // 2026-03 is the second SNAPSHOT (overrides the running estimate)
        val mar = byYm[YearMonth.of(2026, 3)]!!
        assertThat(mar.source).isEqualTo(AssetTrendPoint.Source.SNAPSHOT)
        assertThat(mar.totalAssets).isEqualTo(6_000_000L)
    }

    @Test
    fun `latestNetWorth returns the most recent snapshot when present`() {
        saveSnap("2026-03", totalAssets = 8_000_000L, debt = 1_000_000L)
        saveSnap("2026-05", totalAssets = 9_000_000L, debt = 2_000_000L)

        assertThat(trendService.latestNetWorth(today)).isEqualTo(7_000_000L) // 9M - 2M
    }

    @Test
    fun `latestNetWorth falls back to computed trend when no snapshots exist`() {
        initialBalanceRepository.save(
            InitialBalance(section = Section.SHARED, amount = 3_000_000L, asOfDate = LocalDate.of(2025, 1, 1)),
        )
        // +250k income this month
        saveTx(TxType.INCOME, 250_000L, LocalDateTime.of(2026, 5, 3, 9, 0))

        // Only the latest 1 month is examined; cashflow up to the end of that month is included.
        assertThat(trendService.latestNetWorth(today)).isEqualTo(3_250_000L)
    }
}
