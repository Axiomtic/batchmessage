package com.local.bulksms.importdata

import com.local.bulksms.model.ColumnFilter
import com.local.bulksms.model.FilterCombine
import com.local.bulksms.model.FilterCondition
import com.local.bulksms.model.FilterOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMatcherTest {

    @Test
    fun equalityOperatorsMatchExactStrings() {
        val filter = ColumnFilter(0, listOf(FilterCondition(FilterOperator.EQUALS, "张三")))
        assertTrue(FilterMatcher.matches("张三", filter))
        assertFalse(FilterMatcher.matches("张三 ", filter))
        assertFalse(FilterMatcher.matches("李四", filter))

        val notEquals = ColumnFilter(0, listOf(FilterCondition(FilterOperator.NOT_EQUALS, "张三")))
        assertTrue(FilterMatcher.matches("李四", notEquals))
        assertFalse(FilterMatcher.matches("张三", notEquals))
    }

    @Test
    fun relationalOperatorsUseSmartNumericComparison() {
        // 10 > 9 numerically even though "10" < "9" lexicographically.
        assertTrue(FilterMatcher.matches("10", ColumnFilter(0, listOf(FilterCondition(FilterOperator.GREATER, "9")))))
        assertFalse(FilterMatcher.matches("8", ColumnFilter(0, listOf(FilterCondition(FilterOperator.GREATER, "9")))))
        assertTrue(FilterMatcher.matches("9", ColumnFilter(0, listOf(FilterCondition(FilterOperator.GREATER_OR_EQUAL, "9")))))
        assertTrue(FilterMatcher.matches("5", ColumnFilter(0, listOf(FilterCondition(FilterOperator.LESS, "9")))))
        assertFalse(FilterMatcher.matches("10", ColumnFilter(0, listOf(FilterCondition(FilterOperator.LESS, "9")))))
        assertTrue(FilterMatcher.matches("9", ColumnFilter(0, listOf(FilterCondition(FilterOperator.LESS_OR_EQUAL, "9")))))
    }

    @Test
    fun relationalOperatorsFallBackToLexicographicForText() {
        assertTrue(FilterMatcher.matches("banana", ColumnFilter(0, listOf(FilterCondition(FilterOperator.GREATER, "apple")))))
        assertFalse(FilterMatcher.matches("apple", ColumnFilter(0, listOf(FilterCondition(FilterOperator.GREATER, "banana")))))
    }

    @Test
    fun blankConditionsAreIgnored() {
        val filter = ColumnFilter(
            0,
            listOf(
                FilterCondition(FilterOperator.EQUALS, "张三"),
                FilterCondition(FilterOperator.GREATER, "   "),
            ),
        )
        // The blank second condition must not make the row fail.
        assertTrue(FilterMatcher.matches("张三", filter))
    }

    @Test
    fun andCombineRequiresBothConditions() {
        val filter = ColumnFilter(
            0,
            listOf(
                FilterCondition(FilterOperator.GREATER, "10"),
                FilterCondition(FilterOperator.LESS, "20"),
            ),
            FilterCombine.AND,
        )
        assertTrue(FilterMatcher.matches("15", filter))
        assertFalse(FilterMatcher.matches("25", filter))
        assertFalse(FilterMatcher.matches("5", filter))
    }

    @Test
    fun orCombineAcceptsEitherCondition() {
        val filter = ColumnFilter(
            0,
            listOf(
                FilterCondition(FilterOperator.EQUALS, "北京"),
                FilterCondition(FilterOperator.EQUALS, "上海"),
            ),
            FilterCombine.OR,
        )
        assertTrue(FilterMatcher.matches("北京", filter))
        assertTrue(FilterMatcher.matches("上海", filter))
        assertFalse(FilterMatcher.matches("广州", filter))
    }

    @Test
    fun emptyFilterMatchesEverything() {
        val filter = ColumnFilter(0, emptyList())
        assertTrue(FilterMatcher.matches("任意", filter))
    }
}
