package com.budget.tag

import com.budget.common.TagType
import com.budget.common.TxType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "tags",
    uniqueConstraints = [UniqueConstraint(name = "uq_tag_name_type", columnNames = ["name", "type"])],
)
class Tag(
    @Column(name = "name", nullable = false, length = 60)
    var name: String,

    @Column(name = "color", nullable = false, length = 7)
    var color: String = "#9CA3AF",

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    var type: TagType = TagType.BOTH,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
) {
    fun appliesTo(txType: TxType): Boolean = type.appliesTo(txType)
}
