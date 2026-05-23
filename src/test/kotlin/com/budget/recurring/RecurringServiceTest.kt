package com.budget.recurring

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
import java.util.Optional

class RecurringServiceTest {

    private lateinit var recurringRepository: RecurringTransactionRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var service: RecurringService

    private val sampleUser = User(
        email = "husband@example.com",
        displayName = "남편",
        role = UserRole.HUSBAND,
        id = 1L,
    )

    @BeforeEach
    fun setUp() {
        recurringRepository = mockk()
        tagRepository = mockk()
        service = RecurringService(recurringRepository, tagRepository)
    }

    @Test
    fun `create saves trimmed entity with loaded tags`() {
        val tag = Tag(name = "통신비", color = "#000000", type = TagType.EXPENSE, id = 7L)
        every { tagRepository.findAllById(listOf(7L)) } returns listOf(tag)
        val captured = slot<RecurringTransaction>()
        every { recurringRepository.save(capture(captured)) } answers {
            captured.captured.apply { id = 42L }
        }

        val created = service.create(
            name = "  넷플릭스  ",
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 9_500L,
            dayOfMonth = 13,
            memo = "  ",
            tagIds = listOf(7L),
            active = true,
            createdBy = sampleUser,
        )

        assertThat(created.id).isEqualTo(42L)
        assertThat(captured.captured.name).isEqualTo("넷플릭스")
        assertThat(captured.captured.dayOfMonth).isEqualTo(13.toShort())
        assertThat(captured.captured.memo).isNull()
        assertThat(captured.captured.tags).extracting<Long?> { it.id }.containsExactly(7L)
        verify(exactly = 1) { recurringRepository.save(any()) }
    }

    @Test
    fun `update mutates fields and replaces tag set`() {
        val tagOld = Tag(name = "구독", color = "#111111", type = TagType.EXPENSE, id = 1L)
        val tagNew = Tag(name = "월세", color = "#222222", type = TagType.EXPENSE, id = 2L)
        val existing = RecurringTransaction(
            name = "옛 이름",
            section = Section.HUSBAND,
            type = TxType.EXPENSE,
            amount = 5_000L,
            createdBy = sampleUser,
            dayOfMonth = 1,
            memo = "old memo",
            active = true,
            tags = mutableSetOf(tagOld),
            id = 10L,
        )
        every { recurringRepository.findById(10L) } returns Optional.of(existing)
        every { tagRepository.findAllById(listOf(2L)) } returns listOf(tagNew)

        val updated = service.update(
            id = 10L,
            name = "새 이름",
            section = Section.WIFE,
            type = TxType.INCOME,
            amount = 12_000L,
            dayOfMonth = 25,
            memo = "new memo",
            tagIds = listOf(2L),
            active = false,
        )

        assertThat(updated.name).isEqualTo("새 이름")
        assertThat(updated.section).isEqualTo(Section.WIFE)
        assertThat(updated.type).isEqualTo(TxType.INCOME)
        assertThat(updated.amount).isEqualTo(12_000L)
        assertThat(updated.dayOfMonth).isEqualTo(25.toShort())
        assertThat(updated.memo).isEqualTo("new memo")
        assertThat(updated.active).isFalse()
        assertThat(updated.tags).extracting<Long?> { it.id }.containsExactly(2L)
    }

    @Test
    fun `delete throws when item missing`() {
        every { recurringRepository.existsById(99L) } returns false

        assertThatThrownBy { service.delete(99L) }
            .isInstanceOf(RecurringNotFoundException::class.java)

        verify(exactly = 0) { recurringRepository.deleteById(any()) }
    }

    @Test
    fun `toggleActive flips the active flag`() {
        val item = RecurringTransaction(
            name = "월세",
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 1_000_000L,
            createdBy = sampleUser,
            dayOfMonth = 25,
            active = true,
            id = 5L,
        )
        every { recurringRepository.findById(5L) } returns Optional.of(item)

        val afterFirstToggle = service.toggleActive(5L).active
        assertThat(afterFirstToggle).isFalse()

        val afterSecondToggle = service.toggleActive(5L).active
        assertThat(afterSecondToggle).isTrue()
    }

    @Test
    fun `buildPrefillUrl encodes name and includes sorted tag ids`() {
        val tagA = Tag(name = "통신비", color = "#000000", type = TagType.EXPENSE, id = 7L)
        val tagB = Tag(name = "구독", color = "#000000", type = TagType.BOTH, id = 3L)
        val item = RecurringTransaction(
            name = "넷플릭스",
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 9_500L,
            createdBy = sampleUser,
            dayOfMonth = 13,
            tags = mutableSetOf(tagA, tagB),
            id = 1L,
        )
        every { recurringRepository.findById(1L) } returns Optional.of(item)

        val url = service.buildPrefillUrl(1L)

        assertThat(url).startsWith("/transactions/new?")
        assertThat(url).contains("section=SHARED")
        assertThat(url).contains("type=EXPENSE")
        assertThat(url).contains("amount=9500")
        // Korean name must be percent-encoded
        assertThat(url).contains("memo=%EB%84%B7%ED%94%8C%EB%A6%AD%EC%8A%A4")
        // Tag ids sorted ascending, comma-separated, comma not percent-encoded
        assertThat(url).contains("tagIds=3,7")
    }

    @Test
    fun `buildPrefillUrl omits tagIds when no tags attached`() {
        val item = RecurringTransaction(
            name = "월세",
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 1_500_000L,
            createdBy = sampleUser,
            dayOfMonth = 25,
            tags = mutableSetOf(),
            id = 2L,
        )
        every { recurringRepository.findById(2L) } returns Optional.of(item)

        val url = service.buildPrefillUrl(2L)

        assertThat(url).doesNotContain("tagIds=")
        assertThat(url).contains("memo=%EC%9B%94%EC%84%B8")
    }
}
