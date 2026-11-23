package com.youranata.caloriewidget

import com.youranata.caloriewidget.domain.CalorieMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieMathTest {
    @Test fun convertsKjToKcalWithFixedPrecision() {
        val parsed = CalorieMath.parsePositive("1000", "kJ")
        assertEquals(239006L, parsed.amountMilliKcal)
        assertEquals(1000000L, parsed.originalAmountMilli)
    }
    @Test fun keepsZeroRecordAndRejectsNegative() {
        assertEquals(0L, CalorieMath.parsePositive("0", "kcal").amountMilliKcal)
        assertTrue(runCatching { CalorieMath.parsePositive("-2", "kcal") }.isFailure)
    }

    @Test fun rejectsValuesThatDoNotFitFixedPrecision() {
        assertTrue(runCatching { CalorieMath.parsePositive("999999999999999999999", "kcal") }.isFailure)
    }
    @Test fun allowsNegativeRemaining() {
        assertEquals(-200000L, 2_000_000L - 2_200_000L)
    }
}
