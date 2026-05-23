package com.budget.balance

import com.budget.common.Section
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest(BalanceController::class)
@Import(BalanceControllerTest.MinimalSecurityConfig::class)
@WithMockUser
class BalanceControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var balanceService: BalanceService

    @org.springframework.boot.test.context.TestConfiguration
    class MinimalSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity):
            org.springframework.security.web.SecurityFilterChain =
            http.authorizeHttpRequests { it.anyRequest().authenticated() }
                .csrf { /* default on */ }
                .build()
    }

    @Test
    fun `GET balances renders three section cards`() {
        val asOf = LocalDate.of(2026, 1, 1)
        every { balanceService.listAll() } returns listOf(
            SectionBalanceView(Section.SHARED, 1_000_000L, asOf, 500_000L, 200_000L),
            SectionBalanceView(Section.HUSBAND, 2_000_000L, asOf, 1_000_000L, 400_000L),
            SectionBalanceView(Section.WIFE, 3_000_000L, asOf, 600_000L, 100_000L),
        )

        mockMvc.perform(get("/balances"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("기초 자산")))
            .andExpect(content().string(containsString("공용")))
            .andExpect(content().string(containsString("남편")))
            .andExpect(content().string(containsString("아내")))
    }

    @Test
    fun `POST balances section updates and redirects`() {
        val asOf = LocalDate.of(2026, 3, 1)
        every {
            balanceService.update(Section.SHARED, 1_500_000L, asOf)
        } returns SectionBalanceView(Section.SHARED, 1_500_000L, asOf, 0L, 0L)

        mockMvc.perform(
            post("/balances/SHARED")
                .with(csrf())
                .param("amount", "1500000")
                .param("asOfDate", "2026-03-01"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/balances"))
            .andExpect(flash().attributeExists("flashMessage"))

        verify(exactly = 1) { balanceService.update(Section.SHARED, 1_500_000L, asOf) }
    }

    @Test
    fun `POST balances with negative amount redirects with error and does not call service`() {
        mockMvc.perform(
            post("/balances/HUSBAND")
                .with(csrf())
                .param("amount", "-100")
                .param("asOfDate", "2026-03-01"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/balances"))
            .andExpect(flash().attributeExists("errorMessage"))

        verify(exactly = 0) { balanceService.update(any(), any(), any()) }
    }

    @Test
    fun `POST balances surfaces missing initial balance as error`() {
        val capturedDate = slot<LocalDate>()
        every {
            balanceService.update(Section.WIFE, any(), capture(capturedDate))
        } throws InitialBalanceNotFoundException(Section.WIFE)

        mockMvc.perform(
            post("/balances/WIFE")
                .with(csrf())
                .param("amount", "100")
                .param("asOfDate", "2026-04-01"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/balances"))
            .andExpect(flash().attributeExists("errorMessage"))
    }
}
