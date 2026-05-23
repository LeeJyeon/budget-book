package com.budget.recurring

import com.budget.auth.User
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.tag.Tag
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "recurring_transactions")
class RecurringTransaction(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    var section: Section,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    var type: TxType,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

    @Column(name = "day_of_month")
    var dayOfMonth: Short? = null,

    @Column(name = "memo", length = 500)
    var memo: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recurring_transaction_tags",
        joinColumns = [JoinColumn(name = "recurring_transaction_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    var tags: MutableSet<Tag> = mutableSetOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
) {
    init {
        require(amount > 0) { "amount는 양수여야 합니다" }
        require(dayOfMonth == null || dayOfMonth!!.toInt() in 1..31) { "day_of_month는 1~31 사이여야 합니다" }
    }
}
