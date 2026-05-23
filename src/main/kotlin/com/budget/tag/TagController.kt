package com.budget.tag

import com.budget.common.TagType
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/tags")
class TagController(
    private val tagService: TagService,
) {

    @GetMapping
    fun list(model: Model): String {
        val grouped = tagService.listGrouped()
        model.addAttribute("grouped", grouped)
        model.addAttribute("tagTypes", TagType.values().toList())
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", TagForm(color = "#9CA3AF"))
        }
        return "tag/list"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute("form") form: TagForm,
        bindingResult: BindingResult,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "redirect:/tags"
        }
        try {
            tagService.create(
                name = form.name!!,
                color = form.color ?: "#9CA3AF",
                type = form.type!!,
            )
            redirectAttrs.addFlashAttribute("flashMessage", "태그를 추가했어요.")
        } catch (e: DuplicateTagException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/tags"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: TagForm,
        bindingResult: BindingResult,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "redirect:/tags"
        }
        try {
            tagService.update(
                id = id,
                name = form.name!!,
                color = form.color ?: "#9CA3AF",
                type = form.type!!,
            )
            redirectAttrs.addFlashAttribute("flashMessage", "태그를 수정했어요.")
        } catch (e: DuplicateTagException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        } catch (e: TagNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/tags"
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable id: Long,
        redirectAttrs: RedirectAttributes,
    ): String {
        try {
            tagService.delete(id)
            redirectAttrs.addFlashAttribute("flashMessage", "태그를 삭제했어요.")
        } catch (e: TagNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/tags"
    }

    @GetMapping("/search", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun search(@RequestParam("q", required = false, defaultValue = "") q: String): List<TagSearchResult> =
        tagService.search(q).map { TagSearchResult.from(it) }

    data class TagSearchResult(
        val id: Long,
        val name: String,
        val color: String,
        val type: TagType,
    ) {
        companion object {
            fun from(tag: Tag): TagSearchResult = TagSearchResult(
                id = tag.id!!,
                name = tag.name,
                color = tag.color,
                type = tag.type,
            )
        }
    }
}
