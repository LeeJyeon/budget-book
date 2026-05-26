package com.budget.asset

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.Optional

class AssetSnapshotServiceTest {

    private lateinit var snapshotRepository: AssetSnapshotRepository
    private lateinit var service: AssetSnapshotService

    @BeforeEach
    fun setUp() {
        snapshotRepository = mockk()
        service = AssetSnapshotService(snapshotRepository)
    }

    @Test
    fun `upsert creates a new snapshot when none exists`() {
        val ym = "2026-05"
        every { snapshotRepository.findById(ym) } returns Optional.empty()
        val captured = slot<AssetSnapshot>()
        every { snapshotRepository.save(capture(captured)) } answers { captured.captured }

        val result = service.upsert(
            yearMonth = ym,
            form = AssetSnapshotForm(totalAssets = 10_000_000L, debt = 2_000_000L, memo = "신규"),
        )

        assertThat(result.yearMonth).isEqualTo(ym)
        assertThat(result.totalAssets).isEqualTo(10_000_000L)
        assertThat(result.debt).isEqualTo(2_000_000L)
        assertThat(result.memo).isEqualTo("신규")
        verify(exactly = 1) { snapshotRepository.save(any()) }
    }

    @Test
    fun `upsert updates existing managed entity in place`() {
        val ym = "2026-05"
        val existing = AssetSnapshot(yearMonth = ym, totalAssets = 1L, debt = 0L, memo = "old")
        every { snapshotRepository.findById(ym) } returns Optional.of(existing)
        every { snapshotRepository.save(any()) } answers { firstArg() }

        val updated = service.upsert(
            yearMonth = ym,
            form = AssetSnapshotForm(totalAssets = 5_000_000L, debt = 1_000_000L, memo = "new"),
        )

        assertThat(updated).isSameAs(existing)
        assertThat(updated.totalAssets).isEqualTo(5_000_000L)
        assertThat(updated.debt).isEqualTo(1_000_000L)
        assertThat(updated.memo).isEqualTo("new")
        verify(exactly = 1) { snapshotRepository.save(existing) }
    }

    @Test
    fun `upsert rejects invalid yearMonth format`() {
        assertThatThrownBy {
            service.upsert("2026-5", AssetSnapshotForm(totalAssets = 1L, debt = 0L))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("YYYY-MM")
    }

    @Test
    fun `upsert rejects null totalAssets`() {
        assertThatThrownBy {
            service.upsert("2026-05", AssetSnapshotForm(totalAssets = null, debt = 0L))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `upsert defaults debt to zero when form debt is null`() {
        val ym = "2026-05"
        every { snapshotRepository.findById(ym) } returns Optional.empty()
        every { snapshotRepository.save(any()) } answers { firstArg() }

        val saved = service.upsert(ym, AssetSnapshotForm(totalAssets = 100L, debt = null))

        assertThat(saved.debt).isEqualTo(0L)
    }

    @Test
    fun `delete removes existing snapshot`() {
        val ym = "2026-05"
        every { snapshotRepository.existsById(ym) } returns true
        every { snapshotRepository.deleteById(ym) } returns Unit

        service.delete(ym)

        verify(exactly = 1) { snapshotRepository.deleteById(ym) }
    }

    @Test
    fun `delete throws when snapshot not found`() {
        val ym = "2026-05"
        every { snapshotRepository.existsById(ym) } returns false

        assertThatThrownBy { service.delete(ym) }
            .isInstanceOf(AssetSnapshotNotFoundException::class.java)
        verify(exactly = 0) { snapshotRepository.deleteById(any<String>()) }
    }

    @Test
    fun `listInRange delegates to repository with stringified year months`() {
        val from = YearMonth.of(2026, 1)
        val to = YearMonth.of(2026, 6)
        every {
            snapshotRepository.findAllByYearMonthBetweenOrderByYearMonthAsc("2026-01", "2026-06")
        } returns listOf(AssetSnapshot(yearMonth = "2026-03", totalAssets = 100L))

        val rows = service.listInRange(from, to)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].yearMonth).isEqualTo("2026-03")
    }

    @Test
    fun `listInRange rejects inverted range`() {
        assertThatThrownBy {
            service.listInRange(YearMonth.of(2026, 6), YearMonth.of(2026, 1))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
