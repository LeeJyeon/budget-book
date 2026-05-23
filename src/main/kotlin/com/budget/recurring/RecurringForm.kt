package com.budget.recurring

import com.budget.common.Section
import com.budget.common.TxType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class RecurringForm(
    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 100, message = "이름은 100자 이하로 입력해 주세요.")
    val name: String? = null,

    @field:NotNull(message = "섹션을 선택해 주세요.")
    val section: Section? = null,

    @field:NotNull(message = "종류를 선택해 주세요.")
    val type: TxType? = null,

    @field:NotNull(message = "예상 금액을 입력해 주세요.")
    @field:Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
    val amount: Long? = null,

    @field:Min(value = 1, message = "발생일은 1~31 사이여야 합니다.")
    @field:Max(value = 31, message = "발생일은 1~31 사이여야 합니다.")
    val dayOfMonth: Int? = null,

    @field:Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
    val memo: String? = null,

    val tagIds: List<Long> = emptyList(),

    val active: Boolean = true,
)
