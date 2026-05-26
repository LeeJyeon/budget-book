package com.budget.asset

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("local")
class AssetSnapshotRepositoryTest @Autowired constructor(
    private val snapshotRepository: AssetSnapshotRepository,
) {

    private fun snap(yearMonth: String, totalAssets: Long, debt: Long = 0L, memo: String? = null) =
        AssetSnapshot(yearMonth = yearMonth, totalAssets = totalAssets, debt = debt, memo = memo)

    @Test
    fun `save and findById round-trip`() {
        snapshotRepository.save(snap("2026-05", 10_000_000L, 2_000_000L, "투자계좌 평가"))
        snapshotRepository.flush()

        val found = snapshotRepository.findById("2026-05").orElseThrow()
        assertThat(found.totalAssets).isEqualTo(10_000_000L)
        assertThat(found.debt).isEqualTo(2_000_000L)
        assertThat(found.netWorth).isEqualTo(8_000_000L)
        assertThat(found.memo).isEqualTo("투자계좌 평가")
    }

    @Test
    fun `findAllByOrderByYearMonthAsc returns ascending`() {
        snapshotRepository.save(snap("2026-03", 100L))
        snapshotRepository.save(snap("2026-01", 200L))
        snapshotRepository.save(snap("2026-02", 300L))

        val rows = snapshotRepository.findAllByOrderByYearMonthAsc()

        assertThat(rows.map { it.yearMonth }).containsExactly("2026-01", "2026-02", "2026-03")
    }

    @Test
    fun `findTopByOrderByYearMonthDesc returns latest`() {
        snapshotRepository.save(snap("2025-11", 1_000L))
        snapshotRepository.save(snap("2026-05", 5_000L))
        snapshotRepository.save(snap("2026-02", 3_000L))

        val latest = snapshotRepository.findTopByOrderByYearMonthDesc()

        assertThat(latest).isNotNull
        assertThat(latest!!.yearMonth).isEqualTo("2026-05")
        assertThat(latest.totalAssets).isEqualTo(5_000L)
    }

    @Test
    fun `findAllByYearMonthBetween inclusive filters and orders`() {
        snapshotRepository.save(snap("2025-12", 1L))
        snapshotRepository.save(snap("2026-01", 2L))
        snapshotRepository.save(snap("2026-03", 3L))
        snapshotRepository.save(snap("2026-05", 4L))

        val rows = snapshotRepository.findAllByYearMonthBetweenOrderByYearMonthAsc("2026-01", "2026-03")

        assertThat(rows.map { it.yearMonth }).containsExactly("2026-01", "2026-03")
    }

    @Test
    fun `deleteById removes the snapshot`() {
        snapshotRepository.save(snap("2026-04", 9_999L))
        assertThat(snapshotRepository.existsById("2026-04")).isTrue()

        snapshotRepository.deleteById("2026-04")
        snapshotRepository.flush()

        assertThat(snapshotRepository.existsById("2026-04")).isFalse()
    }

    @Test
    fun `entity init block rejects invalid yearMonth format`() {
        assertThatThrownBy { AssetSnapshot(yearMonth = "2026-5", totalAssets = 1L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("YYYY-MM")
    }

    @Test
    fun `entity init block rejects negative amounts`() {
        assertThatThrownBy { AssetSnapshot(yearMonth = "2026-05", totalAssets = -1L) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy { AssetSnapshot(yearMonth = "2026-05", totalAssets = 1L, debt = -5L) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
