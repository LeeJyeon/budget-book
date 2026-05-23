package com.budget.dashboard

import com.budget.auth.User
import com.budget.balance.BalanceService
import com.budget.balance.SectionBalanceView
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.common.UserRole
import com.budget.stats.PeriodSummary
import com.budget.stats.SectionTypeSummary
import com.budget.stats.StatsService
import com.budget.transaction.Transaction
import com.budget.transaction.TransactionRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@WebMvcTest(DashboardController::class)
@Import(DashboardControllerTest.MinimalSecurityConfig::class)
@WithMockUser
class DashboardControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var balanceService: BalanceService

    @MockkBean
    private lateinit var statsService: StatsService

    @MockkBean
    private lateinit var transactionRepository: TransactionRepository

    @org.springframework.boot.test.context.TestConfiguration
    class MinimalSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity):
            org.springframework.security.web.SecurityFilterChain =
            http.authorizeHttpRequests { it.anyRequest().authenticated() }
                .csrf { /* default on */ }
                .build()
    }

    private fun aUser(): User = User(
        email = "husband@example.com",
        displayName = "남편",
        role = UserRole.HUSBAND,
        id = 1L,
    )

    private fun balanceViews(asOf: LocalDate): List<SectionBalanceView> = listOf(
        SectionBalanceView(Section.SHARED, 1_000_000L, asOf, 100_000L, 30_000L),
        SectionBalanceView(Section.HUSBAND, 500_000L, asOf, 200_000L, 50_000L),
        SectionBalanceView(Section.WIFE, 400_000L, asOf, 80_000L, 20_000L),
    )

    private fun monthSummary(): PeriodSummary = PeriodSummary(
        from = LocalDate.of(2026, 5, 1),
        to = LocalDate.of(2026, 5, 31),
        perSection = listOf(
            SectionTypeSummary(Section.SHARED, 100_000L, 30_000L),
            SectionTypeSummary(Section.HUSBAND, 200_000L, 50_000L),
            SectionTypeSummary(Section.WIFE, 80_000L, 20_000L),
        ),
    )

    @Test
    fun `GET root renders dashboard with required model attributes`() {
        val asOf = LocalDate.of(2026, 1, 1)
        every { balanceService.listAll() } returns balanceViews(asOf)
        every { statsService.sectionSummary(any(), any()) } returns monthSummary()
        every { transactionRepository.findTop10ByOrderByOccurredAtDescIdDesc() } returns emptyList()

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(model().attributeExists("balances", "monthSummary", "recentTransactions", "balanceCards"))
            .andExpect(content().string(containsString("안녕하세요")))
            .andExpect(content().string(containsString("이번 달 요약")))
    }

    @Test
    fun `GET root computes month change per section card`() {
        val asOf = LocalDate.of(2026, 1, 1)
        every { balanceService.listAll() } returns balanceViews(asOf)
        every { statsService.sectionSummary(any(), any()) } returns monthSummary()
        every { transactionRepository.findTop10ByOrderByOccurredAtDescIdDesc() } returns emptyList()

        val result = mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val cards = result.modelAndView!!.model["balanceCards"] as List<SectionBalanceCard>
        val sharedCard = cards.first { it.section == Section.SHARED }
        val husbandCard = cards.first { it.section == Section.HUSBAND }
        val wifeCard = cards.first { it.section == Section.WIFE }
        assertThat(sharedCard.monthChange).isEqualTo(70_000L)
        assertThat(husbandCard.monthChange).isEqualTo(150_000L)
        assertThat(wifeCard.monthChange).isEqualTo(60_000L)
        // Current balances should reflect view computation
        assertThat(sharedCard.currentBalance).isEqualTo(1_070_000L)
    }

    @Test
    fun `GET root surfaces recent transactions in the model`() {
        val asOf = LocalDate.of(2026, 1, 1)
        val user = aUser()
        val tx = Transaction(
            section = Section.SHARED,
            type = TxType.EXPENSE,
            amount = 12_345L,
            occurredAt = LocalDateTime.of(2026, 5, 23, 12, 0),
            createdBy = user,
            memo = "마트",
            id = 99L,
        )
        every { balanceService.listAll() } returns balanceViews(asOf)
        every { statsService.sectionSummary(any(), any()) } returns monthSummary()
        every { transactionRepository.findTop10ByOrderByOccurredAtDescIdDesc() } returns listOf(tx)

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(model().attribute("recentTransactions", listOf(tx)))
            .andExpect(content().string(containsString("마트")))
    }

}
