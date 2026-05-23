package com.budget.balance

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class BalanceForm(
    @field:NotNull(message = "금액을 입력해 주세요.")
    @field:PositiveOrZero(message = "금액은 0 이상이어야 합니다.")
    val amount: Long? = null,

    @field:NotNull(message = "기준일을 입력해 주세요.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val asOfDate: LocalDate? = null,
)
