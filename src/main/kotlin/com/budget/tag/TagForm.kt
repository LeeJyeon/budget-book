package com.budget.tag

import com.budget.common.TagType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class TagForm(
    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 60, message = "이름은 60자 이하로 입력해 주세요.")
    val name: String? = null,

    @field:NotBlank(message = "색상을 입력해 주세요.")
    @field:Pattern(
        regexp = "^#[0-9A-Fa-f]{6}$",
        message = "색상은 #RRGGBB 형식이어야 합니다.",
    )
    val color: String? = "#9CA3AF",

    @field:NotNull(message = "종류를 선택해 주세요.")
    val type: TagType? = null,
)
