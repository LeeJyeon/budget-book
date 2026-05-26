package com.budget.asset

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
import java.time.YearMonth

@WebMvcTest(AssetSnapshotController::class)
@Import(AssetSnapshotControllerTest.MinimalSecurityConfig::class)
@WithMockUser
class AssetSnapshotControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var snapshotService: AssetSnapshotService

    @MockkBean
    private lateinit var trendService: AssetTrendService

    @org.springframework.boot.test.context.TestConfiguration
    class MinimalSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity):
            org.springframework.security.web.SecurityFilterChain =
            http.authorizeHttpRequests { it.anyRequest().authenticated() }
                .csrf { /* default on */ }
                .build()
    }

    private fun stubTrend12() {
        // 12 monthly points ending 2026-05
        val end = YearMonth.of(2026, 5)
        val points = (0 until 12).map { i ->
            val ym = end.minusMonths((11 - i).toLong())
            AssetTrendPoint(
                yearMonth = ym,
                totalAssets = 10_000_000L + i * 100_000L,
                debt = 1_000_000L,
                source = if (i == 11) AssetTrendPoint.Source.SNAPSHOT else AssetTrendPoint.Source.COMPUTED,
            )
        }
        every { trendService.trend(any(), any()) } returns points
        every { trendService.latestNetWorth(any()) } returns points.last().netWorth
        every { snapshotService.listInRange(any(), any()) } returns emptyList()
    }

    @Test
    fun `GET assets renders 12 month rows and latest net worth`() {
        stubTrend12()

        mockMvc.perform(get("/assets"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("자산 추이")))
            .andExpect(content().string(containsString("최근 자산 추이")))
            .andExpect(content().string(containsString("직접 입력")))
            .andExpect(content().string(containsString("추정")))
            // header asks for both ym inputs
            .andExpect(content().string(containsString("name=\"from\"")))
            .andExpect(content().string(containsString("name=\"to\"")))
    }

    @Test
    fun `GET assets accepts from and to range params`() {
        stubTrend12()

        mockMvc.perform(get("/assets").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)

        verify { trendService.trend(months = 6, today = any()) }
    }

    @Test
    fun `POST assets yearMonth upserts and redirects with flash`() {
        val formSlot = slot<AssetSnapshotForm>()
        every { snapshotService.upsert(eq("2026-05"), capture(formSlot)) } returns
            AssetSnapshot(yearMonth = "2026-05", totalAssets = 12_000_000L, debt = 2_000_000L, memo = "test")

        mockMvc.perform(
            post("/assets/2026-05")
                .with(csrf())
                .param("totalAssets", "12000000")
                .param("debt", "2000000")
                .param("memo", "test"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/assets"))
            .andExpect(flash().attributeExists("flashMessage"))

        verify(exactly = 1) { snapshotService.upsert("2026-05", any()) }
        org.assertj.core.api.Assertions.assertThat(formSlot.captured.totalAssets).isEqualTo(12_000_000L)
        org.assertj.core.api.Assertions.assertThat(formSlot.captured.debt).isEqualTo(2_000_000L)
    }

    @Test
    fun `POST assets with negative totalAssets surfaces validation error`() {
        mockMvc.perform(
            post("/assets/2026-05")
                .with(csrf())
                .param("totalAssets", "-100")
                .param("debt", "0"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/assets"))
            .andExpect(flash().attributeExists("errorMessage"))

        verify(exactly = 0) { snapshotService.upsert(any(), any()) }
    }

    @Test
    fun `POST assets delete redirects with flash`() {
        every { snapshotService.delete("2026-05") } returns Unit

        mockMvc.perform(post("/assets/2026-05/delete").with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/assets"))
            .andExpect(flash().attributeExists("flashMessage"))

        verify(exactly = 1) { snapshotService.delete("2026-05") }
    }

    @Test
    fun `POST assets delete surfaces not-found as error`() {
        every { snapshotService.delete("2026-04") } throws AssetSnapshotNotFoundException("2026-04")

        mockMvc.perform(post("/assets/2026-04/delete").with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/assets"))
            .andExpect(flash().attributeExists("errorMessage"))
    }
}
