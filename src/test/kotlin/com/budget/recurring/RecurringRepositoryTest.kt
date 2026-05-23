package com.budget.recurring

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
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("local")
class RecurringRepositoryTest @Autowired constructor(
    private val recurringRepository: RecurringTransactionRepository,
    private val userRepository: UserRepository,
    private val tagRepository: TagRepository,
) {

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        user = userRepository.save(
            User(
                email = "husband@example.com",
                displayName = "남편",
                role = UserRole.HUSBAND,
            ),
        )
    }

    private fun newItem(
        name: String,
        active: Boolean = true,
        dayOfMonth: Short? = null,
        section: Section = Section.SHARED,
        type: TxType = TxType.EXPENSE,
        amount: Long = 10_000L,
    ) = RecurringTransaction(
        name = name,
        section = section,
        type = type,
        amount = amount,
        createdBy = user,
        dayOfMonth = dayOfMonth,
        active = active,
    )

    @Test
    fun `findAllByOrderByActiveDescDayOfMonthAscNameAsc puts active items first then sorts by dayOfMonth then name`() {
        recurringRepository.save(newItem(name = "넷플릭스", active = true, dayOfMonth = 13))
        recurringRepository.save(newItem(name = "월세", active = true, dayOfMonth = 25))
        recurringRepository.save(newItem(name = "공과금", active = true, dayOfMonth = 13))
        recurringRepository.save(newItem(name = "옛 적금", active = false, dayOfMonth = 1))

        val ordered = recurringRepository.findAllByOrderByActiveDescDayOfMonthAscNameAsc()

        // active=true items come first regardless of dayOfMonth value
        assertThat(ordered.map { it.active }).containsExactly(true, true, true, false)
        // active group: ties on dayOfMonth (13) resolved by name asc: "공과금" < "넷플릭스" < "월세"(25)
        assertThat(ordered.map { it.name }).containsExactly("공과금", "넷플릭스", "월세", "옛 적금")
    }

    @Test
    fun `findAllByOrderByActiveDescDayOfMonthAscNameAsc includes items with null dayOfMonth`() {
        recurringRepository.save(newItem(name = "유튜브", active = true, dayOfMonth = null))
        recurringRepository.save(newItem(name = "넷플릭스", active = true, dayOfMonth = 13))

        val ordered = recurringRepository.findAllByOrderByActiveDescDayOfMonthAscNameAsc()

        // Both items are returned; we don't assert specific null position (varies by DB null-ordering config)
        assertThat(ordered).hasSize(2)
        assertThat(ordered.map { it.name }).containsExactlyInAnyOrder("유튜브", "넷플릭스")
    }

    @Test
    fun `findAllByActiveTrueOrderByDayOfMonthAscNameAsc only returns active items in dayOfMonth then name order`() {
        recurringRepository.save(newItem(name = "활성-A", active = true, dayOfMonth = 5))
        recurringRepository.save(newItem(name = "활성-B", active = true, dayOfMonth = 5))
        recurringRepository.save(newItem(name = "활성-Z", active = true, dayOfMonth = 25))
        recurringRepository.save(newItem(name = "비활성-X", active = false, dayOfMonth = 1))

        val activeOnly = recurringRepository.findAllByActiveTrueOrderByDayOfMonthAscNameAsc()

        // Inactive items must be filtered out
        assertThat(activeOnly).hasSize(3)
        assertThat(activeOnly).allSatisfy { assertThat(it.active).isTrue() }
        // Same dayOfMonth tie-broken by name ASC, then dayOfMonth=25 last
        assertThat(activeOnly.map { it.name }).containsExactly("활성-A", "활성-B", "활성-Z")
    }

    @Test
    fun `tags ManyToMany association persists and can be navigated`() {
        val tagA = tagRepository.save(Tag(name = "통신비", color = "#112233", type = TagType.EXPENSE))
        val tagB = tagRepository.save(Tag(name = "구독", color = "#445566", type = TagType.BOTH))

        val saved = recurringRepository.save(
            newItem(name = "넷플릭스", dayOfMonth = 13).apply {
                tags.add(tagA)
                tags.add(tagB)
            },
        )
        recurringRepository.flush()

        val reloaded = recurringRepository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.tags.map { it.name }).containsExactlyInAnyOrder("통신비", "구독")
    }

    @Test
    fun `dayOfMonth nullability is supported and round-trips through repository`() {
        val saved = recurringRepository.save(newItem(name = "미정 항목", dayOfMonth = null))
        recurringRepository.flush()

        val reloaded = recurringRepository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.dayOfMonth).isNull()
    }

    @Test
    fun `deleteById removes recurring item but leaves tags intact`() {
        val tag = tagRepository.save(Tag(name = "월세태그", color = "#000000", type = TagType.EXPENSE))
        val saved = recurringRepository.save(
            newItem(name = "월세", dayOfMonth = 25).apply { tags.add(tag) },
        )
        recurringRepository.flush()

        recurringRepository.deleteById(saved.id!!)
        recurringRepository.flush()

        assertThat(recurringRepository.findById(saved.id!!)).isEmpty
        assertThat(tagRepository.findById(tag.id!!)).isPresent
    }
}
