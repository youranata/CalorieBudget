package com.example.caloriewidget

import com.example.caloriewidget.domain.CalorieMath as Math
import org.junit.Assert.*
import org.junit.Test

class CalorieMathTest {
    @Test fun mixedUnitsShareOneFixedPrecisionBalance() {
        val kcal = Math.parseAmount("350", "kcal")
        val kj = Math.parseAmount("1000", "kJ")
        assertEquals(239006L, kj.amountMilliKcal)
        assertEquals(1_000_000L, kj.originalAmountMilli)
        assertEquals("1410.99", Math.milliKcalToDisplay(2_000_000 - kcal.amountMilliKcal - kj.amountMilliKcal))
    }
    @Test fun zeroRemainsAValidRecord() { assertEquals(0L, Math.parseAmount("0", "kcal").amountMilliKcal) }
    @Test fun rejectsNegativeInvalidAndOverflowingInput() {
        listOf("-1", "NaN", "1e9", "999999999999999999999", "1.2345", "").forEach {
            assertTrue(it, runCatching { Math.parseAmount(it, "kcal") }.isFailure)
        }
        assertTrue(runCatching { Math.parseAmount("0.001", "kJ") }.isFailure)
        assertTrue(runCatching { Math.parseAmount("2", "unknown") }.isFailure)
    }
    @Test fun preciseEditorValueAndDecimalCommaArePreserved() {
        assertEquals(1234L, Math.parseAmount("1,234", "kcal").amountMilliKcal)
        assertEquals("1.234", Math.inputValue(1234L))
        assertEquals("4.18", Math.milliKjToDisplay(1000L))
        assertTrue(runCatching { Math.parseBudget("0") }.isFailure)
    }
}
