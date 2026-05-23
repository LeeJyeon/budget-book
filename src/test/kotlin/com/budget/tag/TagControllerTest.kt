package com.budget.tag

import com.budget.common.TagType
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TagController::class])
@WithMockUser
class TagControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @MockkBean
    private lateinit var tagService: TagService

    @Test
    fun `GET tags renders list view with grouped tags`() {
        every { tagService.listGrouped() } returns mapOf(
            TagType.INCOME to listOf(Tag(name = "급여", color = "#00FF00", type = TagType.INCOME, id = 1L)),
            TagType.EXPENSE to listOf(Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 2L)),
            TagType.BOTH to emptyList(),
        )

        mockMvc.perform(get("/tags"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("태그 관리")))
            .andExpect(content().string(containsString("급여")))
            .andExpect(content().string(containsString("식비")))
    }

    @Test
    fun `POST tags creates tag and redirects with flash message`() {
        every {
            tagService.create(name = "식비", color = "#FF0000", type = TagType.EXPENSE)
        } returns Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 10L)

        mockMvc.perform(
            post("/tags")
                .with(csrf())
                .param("name", "식비")
                .param("color", "#FF0000")
                .param("type", "EXPENSE"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/tags"))
            .andExpect(flash().attribute("flashMessage", "태그를 추가했어요."))

        verify(exactly = 1) {
            tagService.create(name = "식비", color = "#FF0000", type = TagType.EXPENSE)
        }
    }

    @Test
    fun `POST tags surfaces duplicate error as flash error`() {
        every {
            tagService.create(name = "식비", color = "#FF0000", type = TagType.EXPENSE)
        } throws DuplicateTagException("식비", TagType.EXPENSE)

        mockMvc.perform(
            post("/tags")
                .with(csrf())
                .param("name", "식비")
                .param("color", "#FF0000")
                .param("type", "EXPENSE"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/tags"))
            .andExpect(flash().attributeExists("errorMessage"))
    }

    @Test
    fun `POST tags with invalid color redirects with validation error`() {
        mockMvc.perform(
            post("/tags")
                .with(csrf())
                .param("name", "식비")
                .param("color", "not-a-color")
                .param("type", "EXPENSE"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/tags"))
            .andExpect(flash().attributeExists("errorMessage"))

        verify(exactly = 0) { tagService.create(any(), any(), any()) }
    }

    @Test
    fun `POST tags id delete removes tag and redirects`() {
        every { tagService.delete(7L) } returns Unit

        mockMvc.perform(
            post("/tags/7/delete").with(csrf()),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/tags"))
            .andExpect(flash().attribute("flashMessage", "태그를 삭제했어요."))

        verify(exactly = 1) { tagService.delete(7L) }
    }

    @Test
    fun `GET tags search returns JSON list`() {
        every { tagService.search("식") } returns listOf(
            Tag(name = "식비", color = "#FF0000", type = TagType.EXPENSE, id = 1L),
            Tag(name = "외식", color = "#FF00FF", type = TagType.EXPENSE, id = 2L),
        )

        mockMvc.perform(get("/tags/search").param("q", "식"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("식비"))
            .andExpect(jsonPath("$[0].color").value("#FF0000"))
            .andExpect(jsonPath("$[0].type").value("EXPENSE"))
    }
}
