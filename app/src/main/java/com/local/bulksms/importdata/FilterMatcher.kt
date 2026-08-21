package com.local.bulksms.importdata

import com.local.bulksms.model.ColumnFilter
import com.local.bulksms.model.FilterCombine
import com.local.bulksms.model.FilterCondition
import com.local.bulksms.model.FilterOperator

/**
 * Pure string-filter matcher for the column filters.
 *
 * Comparisons are string based, but relational operators (>, <, >=, <=) use a
 * "smart numeric" comparison when both sides parse as numbers, so 10 > 9 while
 * "apple" < "banana". Equality is plain string equality (case-sensitive).
 */
object FilterMatcher {

    /** True when [cell] satisfies the whole column filter (all active conditions). */
    fun matches(cell: String, filter: ColumnFilter): Boolean {
        val active = filter.activeConditions
        if (active.isEmpty()) return true
        return when (filter.combine) {
            FilterCombine.AND -> active.all { matches(cell, it) }
            FilterCombine.OR -> active.any { matches(cell, it) }
        }
    }

    fun matches(cell: String, condition: FilterCondition): Boolean = when (condition.operator) {
        FilterOperator.EQUALS -> cell == condition.value
        FilterOperator.NOT_EQUALS -> cell != condition.value
        FilterOperator.GREATER -> compareSmart(cell, condition.value) > 0
        FilterOperator.LESS -> compareSmart(cell, condition.value) < 0
        FilterOperator.GREATER_OR_EQUAL -> compareSmart(cell, condition.value) >= 0
        FilterOperator.LESS_OR_EQUAL -> compareSmart(cell, condition.value) <= 0
    }

    /**
     * Numeric when both trimmed values parse as numbers, otherwise plain string
     * lexicographic comparison. Blank operands compare as strings (so "" sorts
     * before any non-empty value).
     */
    fun compareSmart(left: String, right: String): Int {
        val leftNumber = left.trim().toDoubleOrNull()
        val rightNumber = right.trim().toDoubleOrNull()
        return if (leftNumber != null && rightNumber != null) {
            leftNumber.compareTo(rightNumber)
        } else {
            left.compareTo(right)
        }
    }
}
