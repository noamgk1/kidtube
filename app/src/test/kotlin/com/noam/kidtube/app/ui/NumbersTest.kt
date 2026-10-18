package com.noam.kidtube.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumbersTest {

    @Test
    fun `a large count is grouped`() {
        assertEquals("47,100,000", formatCount(47_100_000))
    }

    @Test
    fun `a small count is left as it is`() {
        assertEquals("0", formatCount(0))
        assertEquals("812", formatCount(812))
    }

    @Test
    fun `the grouping starts at four digits`() {
        assertEquals("999", formatCount(999))
        assertEquals("1,000", formatCount(1_000))
    }

    @Test
    fun `every digit survives the formatting`() {
        val formatted = formatCount(123_456_789)
        assertEquals("123456789", formatted.filter { it.isDigit() })
        assertTrue(formatted.count { it == ',' } == 2)
    }
}
