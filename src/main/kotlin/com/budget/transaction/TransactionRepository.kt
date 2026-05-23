package com.budget.transaction

import com.budget.common.Section
import com.budget.common.TxType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface TransactionRepository : JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    fun findTop10ByOrderByOccurredAtDescIdDesc(): List<Transaction>

    @Query(
        """
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.section = :section AND t.type = :type AND t.occurredAt >= :from
        """
    )
    fun sumAmountSince(
        @Param("section") section: Section,
        @Param("type") type: TxType,
        @Param("from") from: LocalDateTime,
    ): Long

    @Query(
        """
        SELECT t.section AS section, t.type AS type, COALESCE(SUM(t.amount), 0) AS total
        FROM Transaction t
        WHERE t.occurredAt BETWEEN :from AND :to
        GROUP BY t.section, t.type
        """
    )
    fun sumByPeriodGroupedBySectionAndType(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<SectionTypeAggregate>

    @Query(
        """
        SELECT tag.id AS tagId, tag.name AS tagName, tag.color AS color,
               COALESCE(SUM(t.amount), 0) AS total
        FROM Transaction t JOIN t.tags tag
        WHERE t.type = :type AND t.occurredAt BETWEEN :from AND :to
        GROUP BY tag.id, tag.name, tag.color
        ORDER BY total DESC
        """
    )
    fun sumByPeriodGroupedByTag(
        @Param("type") type: TxType,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<TagAggregate>

    @Query(
        """
        SELECT EXTRACT(YEAR FROM t.occurredAt) AS year,
               EXTRACT(MONTH FROM t.occurredAt) AS month,
               t.type AS type,
               COALESCE(SUM(t.amount), 0) AS total
        FROM Transaction t
        WHERE t.occurredAt BETWEEN :from AND :to
        GROUP BY EXTRACT(YEAR FROM t.occurredAt), EXTRACT(MONTH FROM t.occurredAt), t.type
        ORDER BY year ASC, month ASC
        """
    )
    fun monthlyTotals(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<MonthlyTypeAggregate>

    @Query(
        """
        SELECT EXTRACT(YEAR FROM t.occurredAt) AS year,
               EXTRACT(MONTH FROM t.occurredAt) AS month,
               EXTRACT(DAY FROM t.occurredAt) AS day,
               t.type AS type,
               COALESCE(SUM(t.amount), 0) AS total
        FROM Transaction t
        WHERE t.section = :section AND t.occurredAt BETWEEN :from AND :to
        GROUP BY EXTRACT(YEAR FROM t.occurredAt), EXTRACT(MONTH FROM t.occurredAt), EXTRACT(DAY FROM t.occurredAt), t.type
        ORDER BY year, month, day
        """
    )
    fun dailyTotalsForSection(
        @Param("section") section: Section,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<DailyTypeAggregate>
}

interface SectionTypeAggregate {
    val section: Section
    val type: TxType
    val total: Long
}

interface TagAggregate {
    val tagId: Long
    val tagName: String
    val color: String
    val total: Long
}

interface MonthlyTypeAggregate {
    val year: Int
    val month: Int
    val type: TxType
    val total: Long
}

interface DailyTypeAggregate {
    val year: Int
    val month: Int
    val day: Int
    val type: TxType
    val total: Long
}
