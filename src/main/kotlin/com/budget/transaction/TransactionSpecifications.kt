package com.budget.transaction

import com.budget.common.Section
import com.budget.common.TxType
import com.budget.tag.Tag
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.time.LocalDateTime

data class TransactionFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val section: Section? = null,
    val type: TxType? = null,
    val tagId: Long? = null,
    val q: String? = null,
)

object TransactionSpecifications {

    fun filter(criteria: TransactionFilter): Specification<Transaction> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            criteria.from?.let {
                predicates += cb.greaterThanOrEqualTo(
                    root.get<LocalDateTime>("occurredAt"),
                    it.atStartOfDay(),
                )
            }
            criteria.to?.let {
                predicates += cb.lessThan(
                    root.get<LocalDateTime>("occurredAt"),
                    it.plusDays(1).atStartOfDay(),
                )
            }
            criteria.section?.let {
                predicates += cb.equal(root.get<Section>("section"), it)
            }
            criteria.type?.let {
                predicates += cb.equal(root.get<TxType>("type"), it)
            }
            criteria.q?.takeIf { it.isNotBlank() }?.let { q ->
                predicates += cb.like(
                    cb.lower(root.get("memo")),
                    "%${q.lowercase()}%",
                )
            }
            criteria.tagId?.let { tagId ->
                // Avoid duplicate rows when joining many-to-many
                query?.distinct(true)
                val tagsJoin = root.join<Transaction, Tag>("tags", JoinType.INNER)
                predicates += cb.equal(tagsJoin.get<Long>("id"), tagId)
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }
}
