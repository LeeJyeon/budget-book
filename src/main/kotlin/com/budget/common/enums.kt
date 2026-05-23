package com.budget.common

enum class Section(val label: String) {
    SHARED("공용"),
    HUSBAND("남편"),
    WIFE("아내");

    companion object {
        fun all(): List<Section> = listOf(SHARED, HUSBAND, WIFE)
    }
}

enum class TxType(val label: String) {
    INCOME("수입"),
    EXPENSE("지출")
}

enum class TagType(val label: String) {
    INCOME("수입"),
    EXPENSE("지출"),
    BOTH("공통");

    fun appliesTo(txType: TxType): Boolean = when (this) {
        BOTH -> true
        INCOME -> txType == TxType.INCOME
        EXPENSE -> txType == TxType.EXPENSE
    }
}

enum class UserRole(val label: String) {
    HUSBAND("남편"),
    WIFE("아내")
}
