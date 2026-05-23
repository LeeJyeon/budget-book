package com.budget.balance

import com.budget.common.Section
import com.budget.common.TxType
import com.budget.transaction.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

class BalanceServiceTest {

    private lateinit var initialBalanceRepository: InitialBalanceRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var balanceService: BalanceService

    @BeforeEach
    fun setUp() {
        initialBalanceRepository = mockk()
        transactionRepository = mockk()
        balanceService = BalanceService(initialBalanceRepository, transactionRepository)
    }

    @Test
    fun `compute returns initial + income - expense for a section`() {
        val asOf = LocalDate.of(2026, 1, 1)
        val initial = InitialBalance(section = Section.SHARED, amount = 1_000_000L, asOfDate = asOf)
        every { initialBalanceRepository.findById(Section.SHARED) } returns Optional.of(initial)
        every {
            transactionRepository.sumAmountSince(Section.SHARED, TxType.INCOME, asOf.atStartOfDay())
        } returns 500_000L
        every {
            transactionRepository.sumAmountSince(Section.SHARED, TxType.EXPENSE, asOf.atStartOfDay())
        } returns 200_000L

        val view = balanceService.compute(Section.SHARED)

        assertThat(view.section).isEqualTo(Section.SHARED)
        assertThat(view.initialAmount).isEqualTo(1_000_000L)
        assertThat(view.asOfDate).isEqualTo(asOf)
        assertThat(view.incomeSince).isEqualTo(500_000L)
        assertThat(view.expenseSince).isEqualTo(200_000L)
        assertThat(view.netChange).isEqualTo(300_000L)
        assertThat(view.currentBalance).isEqualTo(1_300_000L)
    }

    @Test
    fun `listAll returns one balance per section`() {
        Section.all().forEach { section ->
            val asOf = LocalDate.of(2026, 1, 1)
            val initial = InitialBalance(section = section, amount = 100L, asOfDate = asOf)
            every { initialBalanceRepository.findById(section) } returns Optional.of(initial)
            every {
                transactionRepository.sumAmountSince(section, TxType.INCOME, asOf.atStartOfDay())
            } returns 10L
            every {
                transactionRepository.sumAmountSince(section, TxType.EXPENSE, asOf.atStartOfDay())
            } returns 5L
        }

        val results = balanceService.listAll()

        assertThat(results).hasSize(3)
        assertThat(results.map { it.section }).containsExactly(Section.SHARED, Section.HUSBAND, Section.WIFE)
        results.forEach {
            assertThat(it.currentBalance).isEqualTo(105L)
            assertThat(it.netChange).isEqualTo(5L)
        }
    }

    @Test
    fun `compute throws InitialBalanceNotFoundException when not seeded`() {
        every { initialBalanceRepository.findById(Section.WIFE) } returns Optional.empty()

        assertThatThrownBy { balanceService.compute(Section.WIFE) }
            .isInstanceOf(InitialBalanceNotFoundException::class.java)
    }

    @Test
    fun `update mutates and saves managed initial balance and returns refreshed view`() {
        val originalAsOf = LocalDate.of(2025, 12, 31)
        val newAsOf = LocalDate.of(2026, 3, 1)
        val initial = InitialBalance(section = Section.HUSBAND, amount = 100L, asOfDate = originalAsOf)
        every { initialBalanceRepository.findById(Section.HUSBAND) } returns Optional.of(initial)
        val saved = slot<InitialBalance>()
        every { initialBalanceRepository.save(capture(saved)) } answers { saved.captured }
        every {
            transactionRepository.sumAmountSince(Section.HUSBAND, TxType.INCOME, newAsOf.atStartOfDay())
        } returns 800L
        every {
            transactionRepository.sumAmountSince(Section.HUSBAND, TxType.EXPENSE, newAsOf.atStartOfDay())
        } returns 300L

        val view = balanceService.update(Section.HUSBAND, amount = 7_000L, asOfDate = newAsOf)

        assertThat(initial.amount).isEqualTo(7_000L)
        assertThat(initial.asOfDate).isEqualTo(newAsOf)
        assertThat(view.initialAmount).isEqualTo(7_000L)
        assertThat(view.asOfDate).isEqualTo(newAsOf)
        assertThat(view.currentBalance).isEqualTo(7_000L + 800L - 300L)
        verify(exactly = 1) { initialBalanceRepository.save(any()) }
    }

    @Test
    fun `currentBalance returns just the current value`() {
        val asOf = LocalDate.of(2026, 1, 1)
        val initial = InitialBalance(section = Section.WIFE, amount = 50L, asOfDate = asOf)
        every { initialBalanceRepository.findById(Section.WIFE) } returns Optional.of(initial)
        every {
            transactionRepository.sumAmountSince(Section.WIFE, TxType.INCOME, asOf.atStartOfDay())
        } returns 20L
        every {
            transactionRepository.sumAmountSince(Section.WIFE, TxType.EXPENSE, asOf.atStartOfDay())
        } returns 15L

        assertThat(balanceService.currentBalance(Section.WIFE)).isEqualTo(55L)
    }
}
