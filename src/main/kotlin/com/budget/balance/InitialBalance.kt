package com.budget.balance

import com.budget.common.Section
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "initial_balances")
class InitialBalance(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "section", length = 20)
    var section: Section,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Column(name = "as_of_date", nullable = false)
    var asOfDate: LocalDate,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
