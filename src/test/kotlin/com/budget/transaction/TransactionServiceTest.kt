package com.budget.transaction

import com.budget.auth.User
import com.budget.common.Section
import com.budget.common.TagType
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

class TransactionServiceTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var service: TransactionService

    private val user = User(
        email = "tester@example.com",
        displayName = "Tester",
        role = UserRole.HUSBAND,
        id = 1L,
    )

    @BeforeEach
    fun setUp() {
        transactionRepository = mockk()
        tagRepository = mockk()
        service = TransactionService(transactionRepository, tagRepository)
    }

    @Test
    fun `create saves transaction with resolved tags`() {
        val tag1 = Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 10L)
        val tag2 = Tag(name = "외식", color = "#FF8800", type = TagType.EXPENSE, id = 11L)
        every { tagRepository.findAllById(listOf(10L, 11L)) } returns listOf(tag1, tag2)
        val captured = slot<Transaction>()
        every { transactionRepository.save(capture(captured)) } answers {
            captured.captured.apply { id = 99L }
        }

        val form = TransactionForm(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 12345L,
            occurredAt = LocalDateTime.of(2026, 5, 23, 12, 0),
            tagIds = listOf(10L, 11L),
            memo = "마트",
        )

        val saved = service.create(form, user)

        assertThat(saved.id).isEqualTo(99L)
        assertThat(captured.captured.section).isEqualTo(Section.SHARED)
        assertThat(captured.captured.type).isEqualTo(TxType.EXPENSE)
        assertThat(captured.captured.amount).isEqualTo(12345L)
        assertThat(captured.captured.memo).isEqualTo("마트")
        assertThat(captured.captured.tags).containsExactlyInAnyOrder(tag1, tag2)
        assertThat(captured.captured.createdBy).isSameAs(user)
        verify(exactly = 1) { transactionRepository.save(any()) }
    }

    @Test
    fun `create with empty tagIds does not call tag repository`() {
        val captured = slot<Transaction>()
        every { transactionRepository.save(capture(captured)) } answers {
            captured.captured.apply { id = 5L }
        }

        val form = TransactionForm(
            section = Section.HUSBAND,
            type = TxType.INCOME,
            amount = 1000L,
            occurredAt = LocalDateTime.of(2026, 5, 1, 9, 0),
            tagIds = emptyList(),
            memo = null,
        )

        service.create(form, user)

        verify(exactly = 0) { tagRepository.findAllById(any<Iterable<Long>>()) }
        assertThat(captured.captured.tags).isEmpty()
        assertThat(captured.captured.memo).isNull()
    }

    @Test
    fun `update mutates existing transaction and replaces tags`() {
        val oldTag = Tag(name = "old", color = "#000000", type = TagType.EXPENSE, id = 1L)
        val newTag = Tag(name = "new", color = "#FFFFFF", type = TagType.EXPENSE, id = 2L)
        val existing = Transaction(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 100L,
            occurredAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            createdBy = user,
            memo = "before",
            tags = mutableSetOf(oldTag),
            id = 42L,
        )
        every { transactionRepository.findById(42L) } returns Optional.of(existing)
        every { tagRepository.findAllById(listOf(2L)) } returns listOf(newTag)

        val form = TransactionForm(
            section = Section.WIFE,
            type = TxType.INCOME,
            amount = 9999L,
            occurredAt = LocalDateTime.of(2026, 5, 5, 10, 0),
            tagIds = listOf(2L),
            memo = "after",
        )

        val updated = service.update(42L, form)

        assertThat(updated.section).isEqualTo(Section.WIFE)
        assertThat(updated.type).isEqualTo(TxType.INCOME)
        assertThat(updated.amount).isEqualTo(9999L)
        assertThat(updated.memo).isEqualTo("after")
        assertThat(updated.tags).containsExactly(newTag)
    }

    @Test
    fun `update throws TransactionNotFoundException when id missing`() {
        every { transactionRepository.findById(404L) } returns Optional.empty()

        val form = TransactionForm(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 100L,
            occurredAt = LocalDateTime.of(2026, 5, 1, 0, 0),
        )

        assertThatThrownBy { service.update(404L, form) }
            .isInstanceOf(TransactionNotFoundException::class.java)
    }

    @Test
    fun `delete removes existing transaction`() {
        every { transactionRepository.existsById(7L) } returns true
        every { transactionRepository.deleteById(7L) } returns Unit

        service.delete(7L)

        verify(exactly = 1) { transactionRepository.deleteById(7L) }
    }

    @Test
    fun `delete throws TransactionNotFoundException when id missing`() {
        every { transactionRepository.existsById(8L) } returns false

        assertThatThrownBy { service.delete(8L) }
            .isInstanceOf(TransactionNotFoundException::class.java)

        verify(exactly = 0) { transactionRepository.deleteById(any()) }
    }

    @Test
    fun `list delegates to repository with sort and filter`() {
        val pageable = PageRequest.of(0, 20)
        val emptyPage = PageImpl<Transaction>(emptyList())
        every {
            transactionRepository.findAll(any<Specification<Transaction>>(), any<Pageable>())
        } returns emptyPage

        val filter = TransactionFilter(
            from = LocalDate.of(2026, 5, 1),
            to = LocalDate.of(2026, 5, 31),
            section = Section.SHARED,
        )

        val result = service.list(filter, pageable)

        assertThat(result).isSameAs(emptyPage)
        verify(exactly = 1) {
            transactionRepository.findAll(any<Specification<Transaction>>(), any<Pageable>())
        }
    }
}
