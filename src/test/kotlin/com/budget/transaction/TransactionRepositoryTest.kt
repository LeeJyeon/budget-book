package com.budget.transaction

import com.budget.auth.User
import com.budget.auth.UserRepository
import com.budget.common.Section
import com.budget.common.TagType
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@ActiveProfiles("local")
class TransactionRepositoryTest @Autowired constructor(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val userRepository: UserRepository,
) {

    private lateinit var user: User
    private lateinit var tagFood: Tag
    private lateinit var tagSalary: Tag

    @BeforeEach
    fun setUp() {
        user = userRepository.save(
            User(email = "tester@example.com", displayName = "Tester", role = UserRole.HUSBAND),
        )
        tagFood = tagRepository.save(Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE))
        tagSalary = tagRepository.save(Tag(name = "급여", color = "#00FF00", type = TagType.INCOME))
    }

    private fun saveTx(
        section: Section,
        type: TxType,
        amount: Long,
        occurredAt: LocalDateTime,
        memo: String? = null,
        tags: Set<Tag> = emptySet(),
    ): Transaction = transactionRepository.save(
        Transaction(
            section = section,
            type = type,
            amount = amount,
            occurredAt = occurredAt,
            createdBy = user,
            memo = memo,
            tags = tags.toMutableSet(),
        ),
    )

    @Test
    fun `findTop10 returns latest by occurredAt desc and id desc`() {
        val base = LocalDateTime.of(2026, 5, 1, 12, 0)
        repeat(12) { i ->
            saveTx(Section.SHARED, TxType.EXPENSE, 1000L + i, base.plusDays(i.toLong()))
        }

        val recent = transactionRepository.findTop10ByOrderByOccurredAtDescIdDesc()

        assertThat(recent).hasSize(10)
        // sorted DESC by occurredAt
        assertThat(recent.map { it.occurredAt }).isSortedAccordingTo(compareByDescending { it })
    }

    @Test
    fun `sumByPeriodGroupedBySectionAndType aggregates totals`() {
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 31, 23, 59)
        saveTx(Section.SHARED, TxType.EXPENSE, 1000, LocalDateTime.of(2026, 5, 5, 9, 0))
        saveTx(Section.SHARED, TxType.EXPENSE, 2000, LocalDateTime.of(2026, 5, 6, 9, 0))
        saveTx(Section.SHARED, TxType.INCOME, 5000, LocalDateTime.of(2026, 5, 7, 9, 0))
        saveTx(Section.HUSBAND, TxType.EXPENSE, 300, LocalDateTime.of(2026, 5, 8, 9, 0))
        // out of range
        saveTx(Section.SHARED, TxType.EXPENSE, 9999, LocalDateTime.of(2026, 6, 1, 9, 0))

        val rows = transactionRepository.sumByPeriodGroupedBySectionAndType(from, to)

        val sharedExpense = rows.firstOrNull { it.section == Section.SHARED && it.type == TxType.EXPENSE }
        val sharedIncome = rows.firstOrNull { it.section == Section.SHARED && it.type == TxType.INCOME }
        val husbandExpense = rows.firstOrNull { it.section == Section.HUSBAND && it.type == TxType.EXPENSE }

        assertThat(sharedExpense?.total).isEqualTo(3000L)
        assertThat(sharedIncome?.total).isEqualTo(5000L)
        assertThat(husbandExpense?.total).isEqualTo(300L)
    }

    @Test
    fun `sumByPeriodGroupedByTag aggregates by tag and type`() {
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 31, 23, 59)
        saveTx(Section.SHARED, TxType.EXPENSE, 1000, LocalDateTime.of(2026, 5, 5, 9, 0), tags = setOf(tagFood))
        saveTx(Section.SHARED, TxType.EXPENSE, 2500, LocalDateTime.of(2026, 5, 6, 9, 0), tags = setOf(tagFood))
        saveTx(Section.SHARED, TxType.INCOME, 5000, LocalDateTime.of(2026, 5, 7, 9, 0), tags = setOf(tagSalary))

        val expenseRows = transactionRepository.sumByPeriodGroupedByTag(TxType.EXPENSE, from, to)
        val incomeRows = transactionRepository.sumByPeriodGroupedByTag(TxType.INCOME, from, to)

        assertThat(expenseRows).hasSize(1)
        assertThat(expenseRows[0].tagName).isEqualTo("식비")
        assertThat(expenseRows[0].total).isEqualTo(3500L)

        assertThat(incomeRows).hasSize(1)
        assertThat(incomeRows[0].tagName).isEqualTo("급여")
        assertThat(incomeRows[0].total).isEqualTo(5000L)
    }

    @Test
    fun `monthlyTotals groups by year month and type`() {
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 12, 31, 23, 59)
        saveTx(Section.SHARED, TxType.EXPENSE, 1000, LocalDateTime.of(2026, 1, 5, 9, 0))
        saveTx(Section.SHARED, TxType.EXPENSE, 2000, LocalDateTime.of(2026, 1, 20, 9, 0))
        saveTx(Section.SHARED, TxType.INCOME, 5000, LocalDateTime.of(2026, 2, 10, 9, 0))

        val rows = transactionRepository.monthlyTotals(from, to)

        val jan = rows.firstOrNull { it.month == 1 && it.type == TxType.EXPENSE }
        val feb = rows.firstOrNull { it.month == 2 && it.type == TxType.INCOME }
        assertThat(jan?.total).isEqualTo(3000L)
        assertThat(feb?.total).isEqualTo(5000L)
    }

    @Test
    fun `dailyTotalsForSection filters by section`() {
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 31, 23, 59)
        saveTx(Section.SHARED, TxType.EXPENSE, 1000, LocalDateTime.of(2026, 5, 5, 9, 0))
        saveTx(Section.SHARED, TxType.EXPENSE, 2000, LocalDateTime.of(2026, 5, 5, 18, 0))
        saveTx(Section.HUSBAND, TxType.EXPENSE, 9999, LocalDateTime.of(2026, 5, 5, 12, 0))

        val rows = transactionRepository.dailyTotalsForSection(Section.SHARED, from, to)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].day).isEqualTo(5)
        assertThat(rows[0].total).isEqualTo(3000L)
    }

    @Test
    fun `specification filters by section, period, tag, and keyword`() {
        saveTx(Section.SHARED, TxType.EXPENSE, 1000, LocalDateTime.of(2026, 5, 5, 9, 0), memo = "마트 장보기", tags = setOf(tagFood))
        saveTx(Section.SHARED, TxType.EXPENSE, 2000, LocalDateTime.of(2026, 5, 10, 9, 0), memo = "넷플릭스")
        saveTx(Section.HUSBAND, TxType.EXPENSE, 3000, LocalDateTime.of(2026, 5, 12, 9, 0), memo = "장보기 추가", tags = setOf(tagFood))
        saveTx(Section.SHARED, TxType.INCOME, 5000, LocalDateTime.of(2026, 5, 20, 9, 0), memo = "월급")

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))

        val bySection = transactionRepository.findAll(
            TransactionSpecifications.filter(TransactionFilter(section = Section.SHARED)),
            pageable,
        )
        assertThat(bySection.totalElements).isEqualTo(3)

        val byPeriod = transactionRepository.findAll(
            TransactionSpecifications.filter(
                TransactionFilter(from = LocalDate.of(2026, 5, 1), to = LocalDate.of(2026, 5, 10)),
            ),
            pageable,
        )
        assertThat(byPeriod.totalElements).isEqualTo(2)

        val byTag = transactionRepository.findAll(
            TransactionSpecifications.filter(TransactionFilter(tagId = tagFood.id)),
            pageable,
        )
        assertThat(byTag.totalElements).isEqualTo(2)

        val byKeyword = transactionRepository.findAll(
            TransactionSpecifications.filter(TransactionFilter(q = "장보기")),
            pageable,
        )
        assertThat(byKeyword.totalElements).isEqualTo(2)

        val byType = transactionRepository.findAll(
            TransactionSpecifications.filter(TransactionFilter(type = TxType.INCOME)),
            pageable,
        )
        assertThat(byType.totalElements).isEqualTo(1)
    }
}
