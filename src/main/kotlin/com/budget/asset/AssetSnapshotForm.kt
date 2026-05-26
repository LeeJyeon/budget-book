package com.budget.asset

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AssetSnapshotForm(
    @field:NotNull(message = "총자산을 입력해 주세요.")
    @field:PositiveOrZero(message = "총자산은 0 이상이어야 합니다.")
    val totalAssets: Long? = null,

    @field:NotNull(message = "부채를 입력해 주세요.")
    @field:PositiveOrZero(message = "부채는 0 이상이어야 합니다.")
    val debt: Long? = 0L,

    @field:Size(max = 500, message = "메모는 500자 이내로 입력해 주세요.")
    val memo: String? = null,
)
