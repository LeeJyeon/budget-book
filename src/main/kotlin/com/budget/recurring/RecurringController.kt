package com.budget.recurring

import com.budget.common.CurrentUserResolver
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.tag.TagRepository
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/recurring")
class RecurringController(
    private val recurringService: RecurringService,
    private val tagRepository: TagRepository,
    private val currentUserResolver: CurrentUserResolver,
) {

    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("items", recurringService.listAll())
        return "recurring/list"
    }

    @GetMapping("/new")
    fun newForm(model: Model): String {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", RecurringForm(active = true))
        }
        populateFormReferences(model, editId = null)
        return "recurring/form"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute("form") form: RecurringForm,
        bindingResult: BindingResult,
        model: Model,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            populateFormReferences(model, editId = null)
            return "recurring/form"
        }
        try {
            recurringService.create(
                name = form.name!!,
                section = form.section!!,
                type = form.type!!,
                amount = form.amount!!,
                dayOfMonth = form.dayOfMonth,
                memo = form.memo,
                tagIds = form.tagIds,
                active = form.active,
                createdBy = currentUserResolver.requireUser(),
            )
            redirectAttrs.addFlashAttribute("flashMessage", "정기 항목을 추가했어요.")
            return "redirect:/recurring"
        } catch (e: IllegalArgumentException) {
            model.addAttribute("errorMessage", e.message ?: "입력값을 확인해 주세요.")
            populateFormReferences(model, editId = null)
            return "recurring/form"
        }
    }

    @GetMapping("/{id}")
    fun editForm(@PathVariable id: Long, model: Model, redirectAttrs: RedirectAttributes): String {
        val item = try {
            recurringService.get(id)
        } catch (e: RecurringNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
            return "redirect:/recurring"
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute(
                "form",
                RecurringForm(
                    name = item.name,
                    section = item.section,
                    type = item.type,
                    amount = item.amount,
                    dayOfMonth = item.dayOfMonth?.toInt(),
                    memo = item.memo,
                    tagIds = item.tags.mapNotNull { it.id },
                    active = item.active,
                ),
            )
        }
        populateFormReferences(model, editId = id)
        return "recurring/form"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: RecurringForm,
        bindingResult: BindingResult,
        model: Model,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            populateFormReferences(model, editId = id)
            return "recurring/form"
        }
        try {
            recurringService.update(
                id = id,
                name = form.name!!,
                section = form.section!!,
                type = form.type!!,
                amount = form.amount!!,
                dayOfMonth = form.dayOfMonth,
                memo = form.memo,
                tagIds = form.tagIds,
                active = form.active,
            )
            redirectAttrs.addFlashAttribute("flashMessage", "정기 항목을 수정했어요.")
            return "redirect:/recurring"
        } catch (e: RecurringNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
            return "redirect:/recurring"
        } catch (e: IllegalArgumentException) {
            model.addAttribute("errorMessage", e.message ?: "입력값을 확인해 주세요.")
            populateFormReferences(model, editId = id)
            return "recurring/form"
        }
    }

    @PostMapping("/{id}/delete")
    fun delete(@PathVariable id: Long, redirectAttrs: RedirectAttributes): String {
        try {
            recurringService.delete(id)
            redirectAttrs.addFlashAttribute("flashMessage", "정기 항목을 삭제했어요.")
        } catch (e: RecurringNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/recurring"
    }

    @PostMapping("/{id}/toggle")
    fun toggle(@PathVariable id: Long, redirectAttrs: RedirectAttributes): String {
        try {
            val toggled = recurringService.toggleActive(id)
            val state = if (toggled.active) "활성화" else "비활성화"
            redirectAttrs.addFlashAttribute("flashMessage", "정기 항목을 ${state}했어요.")
        } catch (e: RecurringNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/recurring"
    }

    @GetMapping("/{id}/log")
    fun log(@PathVariable id: Long, redirectAttrs: RedirectAttributes): String {
        return try {
            val url = recurringService.buildPrefillUrl(id)
            "redirect:$url"
        } catch (e: RecurringNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
            "redirect:/recurring"
        }
    }

    private fun populateFormReferences(model: Model, editId: Long?) {
        model.addAttribute("sections", Section.all())
        model.addAttribute("txTypes", TxType.values().toList())
        model.addAttribute("allTags", tagRepository.findAllByOrderByTypeAscNameAsc())
        model.addAttribute("editId", editId)
    }
}
