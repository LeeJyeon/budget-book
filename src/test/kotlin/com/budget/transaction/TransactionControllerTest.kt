package com.budget.transaction

import com.budget.auth.User
import com.budget.common.CurrentUserResolver
import com.budget.common.Section
import com.budget.common.TagType
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.time.LocalDateTime

@WebMvcTest(TransactionController::class)
@WithMockUser(username = "tester@example.com")
class TransactionControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var transactionService: TransactionService

    @MockkBean
    private lateinit var tagRepository: TagRepository

    @MockkBean
    private lateinit var currentUserResolver: CurrentUserResolver

    private val sampleTag = Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 1L)
    private val sampleUser = User(
        email = "tester@example.com",
        displayName = "Tester",
        role = UserRole.HUSBAND,
        id = 1L,
    )

    @Test
    fun `GET transactions renders list with filter and tags`() {
        every {
            transactionService.list(any(), any())
        } returns PageImpl<Transaction>(emptyList())
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns listOf(sampleTag)

        mockMvc.perform(get("/transactions"))
            .andExpect(status().isOk)
            .andExpect(view().name("transaction/list"))
            .andExpect(model().attributeExists("transactions"))
            .andExpect(model().attributeExists("filter"))
            .andExpect(model().attributeExists("allTags"))

        verify {
            transactionService.list(any<TransactionFilter>(), any<Pageable>())
        }
    }

    @Test
    fun `GET transactions new without prefill returns form`() {
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns listOf(sampleTag)

        mockMvc.perform(get("/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("transaction/form"))
            .andExpect(model().attribute("mode", "new"))
            .andExpect(model().attributeExists("form"))
            .andExpect(model().attributeExists("allTags"))
    }

    @Test
    fun `GET transactions new with prefill populates form fields`() {
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns listOf(sampleTag)

        val mvcResult = mockMvc.perform(
            get("/transactions/new")
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "9500")
                .param("memo", "넷플릭스")
                .param("tagIds", "3,7"),
        )
            .andExpect(status().isOk)
            .andExpect(view().name("transaction/form"))
            .andExpect(model().attribute("mode", "new"))
            .andReturn()

        val form = mvcResult.modelAndView?.model?.get("form") as TransactionForm
        assert(form.section == Section.SHARED)
        assert(form.type == TxType.EXPENSE)
        assert(form.amount == 9500L)
        assert(form.memo == "넷플릭스")
        assert(form.tagIds == listOf(3L, 7L))
    }

    @Test
    fun `POST transactions creates and redirects to list`() {
        every { currentUserResolver.requireUser() } returns sampleUser
        every {
            transactionService.create(any(), any())
        } returns Transaction(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 1000L,
            occurredAt = LocalDateTime.of(2026, 5, 5, 10, 0),
            createdBy = sampleUser,
            id = 100L,
        )

        mockMvc.perform(
            post("/transactions")
                .with(csrf())
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "1000")
                .param("occurredAt", "2026-05-05T10:00")
                .param("memo", "테스트"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions"))
            .andExpect(flash().attribute("flashMessage", "거래를 추가했어요."))

        verify(exactly = 1) { transactionService.create(any(), any()) }
    }

    @Test
    fun `POST transactions with invalid amount returns form with error`() {
        every { tagRepository.findAllByOrderByTypeAscNameAsc() } returns emptyList()

        mockMvc.perform(
            post("/transactions")
                .with(csrf())
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "0")
                .param("occurredAt", "2026-05-05T10:00"),
        )
            .andExpect(status().isOk)
            .andExpect(view().name("transaction/form"))
            .andExpect(model().attributeExists("errorMessage"))

        verify(exactly = 0) { transactionService.create(any(), any()) }
    }

    @Test
    fun `POST transactions id updates and redirects`() {
        every {
            transactionService.update(eq(7L), any())
        } returns Transaction(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 1000L,
            occurredAt = LocalDateTime.of(2026, 5, 5, 10, 0),
            createdBy = sampleUser,
            id = 7L,
        )

        mockMvc.perform(
            post("/transactions/7")
                .with(csrf())
                .param("section", "SHARED")
                .param("type", "EXPENSE")
                .param("amount", "1000")
                .param("occurredAt", "2026-05-05T10:00"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions"))
            .andExpect(flash().attribute("flashMessage", "거래를 수정했어요."))

        verify(exactly = 1) { transactionService.update(7L, any()) }
    }

    @Test
    fun `POST transactions id delete removes and redirects`() {
        every { transactionService.delete(7L) } returns Unit

        mockMvc.perform(
            post("/transactions/7/delete").with(csrf()),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions"))
            .andExpect(flash().attribute("flashMessage", "거래를 삭제했어요."))

        verify(exactly = 1) { transactionService.delete(7L) }
    }
}
