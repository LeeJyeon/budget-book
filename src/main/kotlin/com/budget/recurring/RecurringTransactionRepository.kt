package com.budget.recurring

import org.springframework.data.jpa.repository.JpaRepository

interface RecurringTransactionRepository : JpaRepository<RecurringTransaction, Long> {

    fun findAllByOrderByActiveDescDayOfMonthAscNameAsc(): List<RecurringTransaction>

    fun findAllByActiveTrueOrderByDayOfMonthAscNameAsc(): List<RecurringTransaction>
}
