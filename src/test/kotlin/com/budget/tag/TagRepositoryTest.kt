package com.budget.tag

import com.budget.common.TagType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class TagRepositoryTest @Autowired constructor(
    private val tagRepository: TagRepository,
) {

    @Test
    fun `findAllByOrderByTypeAscNameAsc returns tags ordered by type then name`() {
        tagRepository.save(Tag(name = "외식", color = "#FF0000", type = TagType.EXPENSE))
        tagRepository.save(Tag(name = "급여", color = "#00FF00", type = TagType.INCOME))
        tagRepository.save(Tag(name = "식비", color = "#0000FF", type = TagType.EXPENSE))
        tagRepository.save(Tag(name = "공통메모", color = "#888888", type = TagType.BOTH))

        val all = tagRepository.findAllByOrderByTypeAscNameAsc()

        assertThat(all).hasSize(4)
        // SQL string sort on @Enumerated(EnumType.STRING): BOTH < EXPENSE < INCOME (alphabetical)
        assertThat(all.map { it.name }).containsExactly("공통메모", "식비", "외식", "급여")
    }

    @Test
    fun `findByNameAndType returns matching tag and null otherwise`() {
        val saved = tagRepository.save(Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE))

        val hit = tagRepository.findByNameAndType("식비", TagType.EXPENSE)
        val missType = tagRepository.findByNameAndType("식비", TagType.INCOME)
        val missName = tagRepository.findByNameAndType("외식", TagType.EXPENSE)

        assertThat(hit?.id).isEqualTo(saved.id)
        assertThat(missType).isNull()
        assertThat(missName).isNull()
    }

    @Test
    fun `search returns case-insensitive partial matches`() {
        tagRepository.save(Tag(name = "외식", color = "#FF0000", type = TagType.EXPENSE))
        tagRepository.save(Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE))
        tagRepository.save(Tag(name = "Salary", color = "#00FF00", type = TagType.INCOME))

        val foodHits = tagRepository.search("식")
        val salaryHits = tagRepository.search("sal")

        assertThat(foodHits.map { it.name }).containsExactlyInAnyOrder("외식", "식비")
        assertThat(salaryHits.map { it.name }).containsExactly("Salary")
    }

    @Test
    fun `unique constraint blocks duplicate name and type`() {
        tagRepository.saveAndFlush(Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE))

        assertThatThrownBy {
            tagRepository.saveAndFlush(Tag(name = "식비", color = "#00FF00", type = TagType.EXPENSE))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `same name with different type is allowed`() {
        tagRepository.saveAndFlush(Tag(name = "상여", color = "#FF0000", type = TagType.INCOME))
        tagRepository.saveAndFlush(Tag(name = "상여", color = "#00FF00", type = TagType.EXPENSE))

        val all = tagRepository.findAllByOrderByTypeAscNameAsc()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.type }).containsExactlyInAnyOrder(TagType.INCOME, TagType.EXPENSE)
    }
}
