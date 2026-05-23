package com.budget.transaction

import com.budget.common.Section
import com.budget.common.TxType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

data class TransactionForm(
    @field:NotNull(message = "섹션을 선택해 주세요.")
    val section: Section? = null,

    @field:NotNull(message = "종류를 선택해 주세요.")
    val type: TxType? = null,

    @field:NotNull(message = "금액을 입력해 주세요.")
    @field:Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
    val amount: Long? = null,

    @field:NotNull(message = "발생 일시를 입력해 주세요.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val occurredAt: LocalDateTime? = null,

    val tagIds: List<Long> = emptyList(),

    @field:Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
    val memo: String? = null,
)
