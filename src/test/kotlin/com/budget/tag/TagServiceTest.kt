package com.budget.tag

import com.budget.common.TagType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class TagServiceTest {

    private lateinit var tagRepository: TagRepository
    private lateinit var tagService: TagService

    @BeforeEach
    fun setUp() {
        tagRepository = mockk()
        tagService = TagService(tagRepository)
    }

    @Test
    fun `create saves trimmed tag when no duplicate exists`() {
        every { tagRepository.findByNameAndType("식비", TagType.EXPENSE) } returns null
        val captured = slot<Tag>()
        every { tagRepository.save(capture(captured)) } answers { captured.captured.apply { id = 7L } }

        val created = tagService.create(name = "  식비  ", color = "#FF0000", type = TagType.EXPENSE)

        assertThat(created.id).isEqualTo(7L)
        assertThat(captured.captured.name).isEqualTo("식비")
        assertThat(captured.captured.color).isEqualTo("#FF0000")
        assertThat(captured.captured.type).isEqualTo(TagType.EXPENSE)
        verify(exactly = 1) { tagRepository.save(any()) }
    }

    @Test
    fun `create throws DuplicateTagException when name and type already used`() {
        every {
            tagRepository.findByNameAndType("식비", TagType.EXPENSE)
        } returns Tag(name = "식비", color = "#000000", type = TagType.EXPENSE, id = 1L)

        assertThatThrownBy {
            tagService.create(name = "식비", color = "#FF0000", type = TagType.EXPENSE)
        }.isInstanceOf(DuplicateTagException::class.java)

        verify(exactly = 0) { tagRepository.save(any()) }
    }

    @Test
    fun `update mutates fields when id exists and no duplicate`() {
        val existing = Tag(name = "old", color = "#000000", type = TagType.EXPENSE, id = 1L)
        every { tagRepository.findById(1L) } returns Optional.of(existing)
        every { tagRepository.findByNameAndType("new", TagType.INCOME) } returns null

        val updated = tagService.update(id = 1L, name = "new", color = "#FFFFFF", type = TagType.INCOME)

        assertThat(updated.name).isEqualTo("new")
        assertThat(updated.color).isEqualTo("#FFFFFF")
        assertThat(updated.type).isEqualTo(TagType.INCOME)
    }

    @Test
    fun `update throws DuplicateTagException when another tag has same name and type`() {
        val existing = Tag(name = "old", color = "#000000", type = TagType.EXPENSE, id = 1L)
        val other = Tag(name = "new", color = "#FFFFFF", type = TagType.INCOME, id = 2L)
        every { tagRepository.findById(1L) } returns Optional.of(existing)
        every { tagRepository.findByNameAndType("new", TagType.INCOME) } returns other

        assertThatThrownBy {
            tagService.update(id = 1L, name = "new", color = "#FFFFFF", type = TagType.INCOME)
        }.isInstanceOf(DuplicateTagException::class.java)
    }

    @Test
    fun `delete removes existing tag`() {
        every { tagRepository.existsById(5L) } returns true
        every { tagRepository.deleteById(5L) } returns Unit

        tagService.delete(5L)

        verify(exactly = 1) { tagRepository.deleteById(5L) }
    }

    @Test
    fun `delete throws TagNotFoundException when id missing`() {
        every { tagRepository.existsById(99L) } returns false

        assertThatThrownBy { tagService.delete(99L) }
            .isInstanceOf(TagNotFoundException::class.java)

        verify(exactly = 0) { tagRepository.deleteById(any()) }
    }

    @Test
    fun `search returns empty list for blank query without hitting repository`() {
        val result = tagService.search("   ")

        assertThat(result).isEmpty()
        verify(exactly = 0) { tagRepository.search(any()) }
    }

    @Test
    fun `listGrouped groups by all TagType values even when some are empty`() {
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns listOf(
            Tag(name = "급여", color = "#00FF00", type = TagType.INCOME, id = 1L),
            Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 2L),
        )

        val grouped = tagService.listGrouped()

        assertThat(grouped.keys).containsExactlyInAnyOrder(TagType.INCOME, TagType.EXPENSE, TagType.BOTH)
        assertThat(grouped[TagType.INCOME]).hasSize(1)
        assertThat(grouped[TagType.EXPENSE]).hasSize(1)
        assertThat(grouped[TagType.BOTH]).isEmpty()
    }
}
