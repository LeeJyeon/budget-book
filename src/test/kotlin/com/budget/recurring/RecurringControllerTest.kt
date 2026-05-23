package com.budget.recurring

import com.budget.auth.User
import com.budget.common.CurrentUserResolver
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [RecurringController::class])
@Import(RecurringControllerTest.MinimalSecurityConfig::class)
@WithMockUser
class RecurringControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var recurringService: RecurringService

    @MockkBean
    private lateinit var tagRepository: TagRepository

    @MockkBean
    private lateinit var currentUserResolver: CurrentUserResolver

    @org.springframework.boot.test.context.TestConfiguration
    class MinimalSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity):
            org.springframework.security.web.SecurityFilterChain =
            http.authorizeHttpRequests { it.anyRequest().authenticated() }
                .csrf { /* default on */ }
                .build()
    }

    private val sampleUser = User(
        email = "husband@example.com",
        displayName = "남편",
        role = UserRole.HUSBAND,
        id = 1L,
    )

    private fun sampleItem(
        id: Long = 1L,
        name: String = "넷플릭스",
        section: Section = Section.SHARED,
        type: TxType = TxType.EXPENSE,
        amount: Long = 9_500L,
        dayOfMonth: Short? = 13,
        active: Boolean = true,
        tags: MutableSet<Tag> = mutableSetOf(),
    ) = RecurringTransaction(
        name = name,
        section = section,
        type = type,
        amount = amount,
        createdBy = sampleUser,
        dayOfMonth = dayOfMonth,
        active = active,
        tags = tags,
        id = id,
    )

    @Test
    fun `GET recurring renders list view with items`() {
        every { recurringService.listAll() } returns listOf(
            sampleItem(id = 1L, name = "넷플릭스", amount = 9_500L, dayOfMonth = 13),
            sampleItem(id = 2L, name = "월세", amount = 1_500_000L, dayOfMonth = 25),
        )

        mockMvc.perform(get("/recurring"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("정기 지출/수입 정리")))
            .andExpect(content().string(containsString("넷플릭스")))
            .andExpect(content().string(containsString("월세")))
            .andExpect(content().string(containsString("9,500원")))
            .andExpect(content().string(containsString("매월 13일")))
            .andExpect(content().string(containsString("기록하기")))
    }

    @Test
    fun `POST recurring creates item and redirects with flash`() {
        every { currentUserResolver.requireUser() } returns sampleUser
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns emptyList()
        every {
            recurringService.create(
                name = "넷플릭스",
                section = Section.SHARED,
                type = TxType.EXPENSE,
                amount = 9_500L,
                dayOfMonth = 13,
                memo = "구독 서비스",
                tagIds = listOf(3L, 7L),
                active = true,
                createdBy = sampleUser,
            )
        } returns sampleItem(id = 50L)

        mockMvc.perform(
            post("/recurring")
                .with(csrf())
                .param("name", "넷플릭스")
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "9500")
                .param("dayOfMonth", "13")
                .param("memo", "구독 서비스")
                .param("tagIds", "3", "7")
                .param("active", "true"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/recurring"))
            .andExpect(flash().attribute("flashMessage", "정기 항목을 추가했어요."))

        verify(exactly = 1) {
            recurringService.create(
                name = "넷플릭스",
                section = Section.SHARED,
                type = TxType.EXPENSE,
                amount = 9_500L,
                dayOfMonth = 13,
                memo = "구독 서비스",
                tagIds = listOf(3L, 7L),
                active = true,
                createdBy = sampleUser,
            )
        }
    }

    @Test
    fun `POST recurring with blank name re-renders form with error`() {
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns emptyList()

        mockMvc.perform(
            post("/recurring")
                .with(csrf())
                .param("name", "")
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "9500")
                .param("active", "true"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("정기 항목 추가")))

        verify(exactly = 0) {
            recurringService.create(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `POST recurring id delete removes item and redirects`() {
        every { recurringService.delete(7L) } returns Unit

        mockMvc.perform(post("/recurring/7/delete").with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/recurring"))
            .andExpect(flash().attribute("flashMessage", "정기 항목을 삭제했어요."))

        verify(exactly = 1) { recurringService.delete(7L) }
    }

    @Test
    fun `POST recurring id toggle flips active and redirects with flash`() {
        every { recurringService.toggleActive(3L) } returns sampleItem(id = 3L, active = false)

        mockMvc.perform(post("/recurring/3/toggle").with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/recurring"))
            .andExpect(flash().attribute("flashMessage", containsString("비활성화")))

        verify(exactly = 1) { recurringService.toggleActive(3L) }
    }

    @Test
    fun `GET recurring id log returns 302 with prefill location header`() {
        every { recurringService.buildPrefillUrl(5L) } returns
            "/transactions/new?section=SHARED&type=EXPENSE&amount=9500&memo=%EB%84%B7%ED%94%8C%EB%A6%AD%EC%8A%A4&tagIds=3,7"

        mockMvc.perform(get("/recurring/5/log"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string("Location", containsString("/transactions/new")))
            .andExpect(header().string("Location", containsString("section=SHARED")))
            .andExpect(header().string("Location", containsString("type=EXPENSE")))
            .andExpect(header().string("Location", containsString("amount=9500")))
            .andExpect(header().string("Location", containsString("memo=%EB%84%B7%ED%94%8C%EB%A6%AD%EC%8A%A4")))
            .andExpect(header().string("Location", containsString("tagIds=3,7")))
    }

    @Test
    fun `GET recurring id log with missing item redirects to list with error`() {
        every { recurringService.buildPrefillUrl(99L) } throws RecurringNotFoundException(99L)

        mockMvc.perform(get("/recurring/99/log"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/recurring"))
            .andExpect(flash().attributeExists("errorMessage"))
    }
}
