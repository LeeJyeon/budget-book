package com.budget.transaction

import com.budget.common.CurrentUserResolver
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.tag.TagRepository
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.LocalDateTime

@Controller
@RequestMapping("/transactions")
class TransactionController(
    private val transactionService: TransactionService,
    private val tagRepository: TagRepository,
    private val currentUserResolver: CurrentUserResolver,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) section: Section?,
        @RequestParam(required = false) tagId: Long?,
        @RequestParam(required = false) type: TxType?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        model: Model,
    ): String {
        val filter = TransactionFilter(
            from = from,
            to = to,
            section = section,
            type = type,
            tagId = tagId,
            q = q,
        )
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")),
        )
        val results = transactionService.list(filter, pageable)

        model.addAttribute("page", results)
        model.addAttribute("transactions", results.content)
        model.addAttribute("filter", filter)
        model.addAttribute("sections", Section.all())
        model.addAttribute("txTypes", TxType.values().toList())
        model.addAttribute("allTags", tagRepository.findAllByOrderByTypeAscNameAsc())
        model.addAttribute("queryFrom", from)
        model.addAttribute("queryTo", to)
        model.addAttribute("queryQ", q)
        model.addAttribute("pageSize", size)
        return "transaction/list"
    }

    @GetMapping("/new")
    fun newForm(
        @RequestParam(required = false) section: Section?,
        @RequestParam(required = false) type: TxType?,
        @RequestParam(required = false) amount: Long?,
        @RequestParam(required = false) memo: String?,
        @RequestParam(required = false) tagIds: String?,
        model: Model,
    ): String {
        if (!model.containsAttribute("form")) {
            val prefilledTagIds = parseCsvLongs(tagIds)
            val form = TransactionForm(
                section = section,
                type = type,
                amount = amount,
                occurredAt = LocalDateTime.now().withSecond(0).withNano(0),
                tagIds = prefilledTagIds,
                memo = memo,
            )
            model.addAttribute("form", form)
        }
        populateFormModel(model, mode = "new", id = null)
        return "transaction/form"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute("form") form: TransactionForm,
        bindingResult: BindingResult,
        model: Model,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, mode = "new", id = null)
            model.addAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "transaction/form"
        }
        try {
            val user = currentUserResolver.requireUser()
            transactionService.create(form, user)
            redirectAttrs.addFlashAttribute("flashMessage", "거래를 추가했어요.")
        } catch (e: IllegalStateException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message ?: "저장에 실패했어요.")
            return "redirect:/transactions/new"
        } catch (e: IllegalArgumentException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message ?: "저장에 실패했어요.")
            return "redirect:/transactions/new"
        }
        return "redirect:/transactions"
    }

    @GetMapping("/{id}")
    fun editForm(@PathVariable id: Long, model: Model): String {
        val tx = transactionService.get(id)
        if (!model.containsAttribute("form")) {
            model.addAttribute(
                "form",
                TransactionForm(
                    section = tx.section,
                    type = tx.type,
                    amount = tx.amount,
                    occurredAt = tx.occurredAt,
                    tagIds = tx.tags.mapNotNull { it.id },
                    memo = tx.memo,
                ),
            )
        }
        populateFormModel(model, mode = "edit", id = id)
        return "transaction/form"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: TransactionForm,
        bindingResult: BindingResult,
        model: Model,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, mode = "edit", id = id)
            model.addAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "transaction/form"
        }
        try {
            transactionService.update(id, form)
            redirectAttrs.addFlashAttribute("flashMessage", "거래를 수정했어요.")
        } catch (e: TransactionNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
            return "redirect:/transactions"
        } catch (e: IllegalStateException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message ?: "저장에 실패했어요.")
            return "redirect:/transactions/$id"
        }
        return "redirect:/transactions"
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable id: Long,
        redirectAttrs: RedirectAttributes,
    ): String {
        try {
            transactionService.delete(id)
            redirectAttrs.addFlashAttribute("flashMessage", "거래를 삭제했어요.")
        } catch (e: TransactionNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/transactions"
    }

    private fun populateFormModel(model: Model, mode: String, id: Long?) {
        model.addAttribute("mode", mode)
        model.addAttribute("editingId", id)
        model.addAttribute("sections", Section.all())
        model.addAttribute("txTypes", TxType.values().toList())
        model.addAttribute("allTags", tagRepository.findAllByOrderByTypeAscNameAsc())
    }

    private fun parseCsvLongs(csv: String?): List<Long> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toLongOrNull() }
    }
}
