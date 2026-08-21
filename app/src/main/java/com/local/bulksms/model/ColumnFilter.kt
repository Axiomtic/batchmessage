package com.local.bulksms.model

/** Comparison operators for a column filter condition (string / smart-numeric). */
enum class FilterOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER,
    LESS,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
}

/** How the (up to two) conditions of one column combine. */
enum class FilterCombine { AND, OR }

data class FilterCondition(
    val operator: FilterOperator,
    val value: String,
)

/** Per-column filter: 0..2 conditions combined with AND/OR. */
data class ColumnFilter(
    val columnIndex: Int,
    val conditions: List<FilterCondition> = emptyList(),
    val combine: FilterCombine = FilterCombine.AND,
) {
    /** Conditions with a non-blank value; blank conditions are ignored. */
    val activeConditions: List<FilterCondition>
        get() = conditions.filter { it.value.isNotBlank() }
}
