package com.budget.balance

import com.budget.common.Section
import com.budget.common.TxType
import com.budget.transaction.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

class InitialBalanceNotFoundException(val section: Section) :
    RuntimeException("기초 자산이 없습니다: ${section.label}")

data class SectionBalanceView(
    val section: Section,
    val initialAmount: Long,
    val asOfDate: LocalDate,
    val incomeSince: Long,
    val expenseSince: Long,
) {
    val netChange: Long get() = incomeSince - expenseSince
    val currentBalance: Long get() = initialAmount + netChange
}

@Service
@Transactional
class BalanceService(
    private val initialBalanceRepository: InitialBalanceRepository,
    private val transactionRepository: TransactionRepository,
) {

    @Transactional(readOnly = true)
    fun listAll(): List<SectionBalanceView> = Section.all().map { compute(it) }

    @Transactional(readOnly = true)
    fun compute(section: Section): SectionBalanceView {
        val initial = initialBalanceRepository.findById(section).orElseThrow {
            InitialBalanceNotFoundException(section)
        }
        val from: LocalDateTime = initial.asOfDate.atStartOfDay()
        val income = transactionRepository.sumAmountSince(section, TxType.INCOME, from)
        val expense = transactionRepository.sumAmountSince(section, TxType.EXPENSE, from)
        return SectionBalanceView(
            section = section,
            initialAmount = initial.amount,
            asOfDate = initial.asOfDate,
            incomeSince = income,
            expenseSince = expense,
        )
    }

    @Transactional(readOnly = true)
    fun currentBalance(section: Section): Long = compute(section).currentBalance

    fun update(section: Section, amount: Long, asOfDate: LocalDate): SectionBalanceView {
        val initial = initialBalanceRepository.findById(section).orElseThrow {
            InitialBalanceNotFoundException(section)
        }
        initial.amount = amount
        initial.asOfDate = asOfDate
        // managed entity is updated; explicit save not required but harmless
        initialBalanceRepository.save(initial)
        return compute(section)
    }
}
