package com.budget.balance

import com.budget.common.Section
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
@RequestMapping("/balances")
class BalanceController(
    private val balanceService: BalanceService,
) {

    @GetMapping
    fun index(model: Model): String {
        val balances = balanceService.listAll()
        model.addAttribute("balances", balances)
        model.addAttribute("sections", Section.all())
        return "balance/index"
    }

    @PostMapping("/{section}")
    fun update(
        @PathVariable section: Section,
        @Valid @ModelAttribute("form") form: BalanceForm,
        bindingResult: BindingResult,
        redirectAttrs: RedirectAttributes,
    ): String {
        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute(
                "errorMessage",
                bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값을 확인해 주세요.",
            )
            return "redirect:/balances"
        }
        try {
            balanceService.update(
                section = section,
                amount = form.amount!!,
                asOfDate = form.asOfDate!!,
            )
            redirectAttrs.addFlashAttribute(
                "flashMessage",
                "${section.label} 섹션 기초 자산을 업데이트했어요.",
            )
        } catch (e: InitialBalanceNotFoundException) {
            redirectAttrs.addFlashAttribute("errorMessage", e.message)
        }
        return "redirect:/balances"
    }
}
