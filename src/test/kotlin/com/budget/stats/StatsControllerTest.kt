package com.budget.stats

import com.budget.common.Section
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
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

@WebMvcTest(StatsController::class)
@Import(StatsControllerTest.MinimalSecurityConfig::class)
@WithMockUser
class StatsControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var statsService: StatsService

    @org.springframework.boot.test.context.TestConfiguration
    class MinimalSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity):
            org.springframework.security.web.SecurityFilterChain =
            http.authorizeHttpRequests { it.anyRequest().authenticated() }
                .csrf { /* default on */ }
                .build()
    }

    private fun stubAll(from: LocalDate, to: LocalDate, section: Section) {
        val summary = PeriodSummary(
            from = from,
            to = to,
            perSection = Section.all().map { SectionTypeSummary(it, 0L, 0L) },
        )
        every { statsService.sectionSummary(from, to) } returns summary
        every { statsService.tagBreakdown(from, to) } returns emptyList()
        every { statsService.monthlyTrend() } returns listOf(
            MonthlyPoint(2026, 1, 100L, 50L),
            MonthlyPoint(2026, 2, 200L, 80L),
        )
        every { statsService.sectionDailyCumulative(section, from, to) } returns emptyList()
    }

    @Test
    fun `GET stats with default period uses current month range from service`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        every { statsService.defaultMonthRange() } returns (from to to)
        stubAll(from, to, Section.SHARED)

        mockMvc.perform(get("/stats"))
            .andExpect(status().isOk)
            .andExpect(model().attributeExists("summary", "tagBreakdown", "monthlyLabels", "dailyLabels"))
            .andExpect(model().attribute("from", from))
            .andExpect(model().attribute("to", to))
            .andExpect(content().string(containsString("통계")))

        verify(exactly = 1) { statsService.defaultMonthRange() }
        verify(exactly = 1) { statsService.sectionSummary(from, to) }
    }

    @Test
    fun `GET stats with from and to query params uses those dates`() {
        val from = LocalDate.of(2026, 3, 1)
        val to = LocalDate.of(2026, 3, 31)
        every { statsService.defaultMonthRange() } returns (LocalDate.of(2026, 5, 1) to LocalDate.of(2026, 5, 31))
        stubAll(from, to, Section.HUSBAND)

        mockMvc.perform(
            get("/stats")
                .param("from", "2026-03-01")
                .param("to", "2026-03-31")
                .param("section", "HUSBAND"),
        )
            .andExpect(status().isOk)
            .andExpect(model().attribute("from", from))
            .andExpect(model().attribute("to", to))
            .andExpect(model().attribute("section", Section.HUSBAND))

        verify(exactly = 1) { statsService.sectionSummary(from, to) }
        verify(exactly = 1) { statsService.sectionDailyCumulative(Section.HUSBAND, from, to) }
    }

    @Test
    fun `GET stats exposes chart-friendly model attributes`() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 31)
        every { statsService.defaultMonthRange() } returns (from to to)

        val summary = PeriodSummary(from, to, Section.all().map { SectionTypeSummary(it, 10L, 5L) })
        every { statsService.sectionSummary(from, to) } returns summary
        every { statsService.tagBreakdown(from, to) } returns listOf(
            TagBreakdownItem(1L, "식비", "#FF0000", 1_000L),
            TagBreakdownItem(2L, "외식", "#00FF00", 500L),
        )
        every { statsService.monthlyTrend() } returns listOf(MonthlyPoint(2026, 5, 1L, 2L))
        every { statsService.sectionDailyCumulative(Section.SHARED, from, to) } returns
            listOf(DailyCumulativePoint(LocalDate.of(2026, 5, 1), 999L))

        mockMvc.perform(get("/stats"))
            .andExpect(status().isOk)
            .andExpect(model().attribute("tagLabels", listOf("식비", "외식")))
            .andExpect(model().attribute("tagValues", listOf(1_000L, 500L)))
            .andExpect(model().attribute("monthlyLabels", listOf("2026-05")))
            .andExpect(model().attribute("dailyValues", listOf(999L)))
    }
}
