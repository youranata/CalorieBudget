package com.youranata.caloriewidget.acceptance

import com.youranata.caloriewidget.domain.CalorieMath
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Random

/** Independent oracle: 1 / 4.184 = 125 / 523, using integer quotient/remainder. */
class CalorieCalculationAcceptanceTest {
    @Test fun specifiedMixedUnitExampleAndNegativeBalanceHaveExactValues() {
        val breakfast = CalorieMath.parseAmount("350", "kcal")
        val drink = CalorieMath.parseAmount("1000", "kJ")
        assertEquals(350_000L, breakfast.amountMilliKcal)
        assertEquals(239_006L, drink.amountMilliKcal)
        assertEquals(1_410_994L, 2_000_000L - breakfast.amountMilliKcal - drink.amountMilliKcal)
        assertEquals("1410.99", CalorieMath.milliKcalToDisplay(1_410_994L))
        assertEquals("5903.6", CalorieMath.milliKjToDisplay(1_410_994L))
        assertEquals("-200", CalorieMath.milliKcalToDisplay(2_000_000L - 2_200_000L))
        assertEquals("-836.8", CalorieMath.milliKjToDisplay(-200_000L))
    }

    @Test fun fiveHundredConversionVectorsMatchRationalOracleAndRetainOriginalInput() {
        val random = Random(417_023)
        val vectors = listOf(0L, 3, 4, 5, 523, 4_184, 418_400, 1_000_000, 4_184_000_000) +
            List(500) { Math.floorMod(random.nextLong(), 4_184_000_001L).coerceAtLeast(3) }
        for (raw in vectors) {
            val input = decimalInput(raw)
            val result = CalorieMath.parseAmount(input, "kJ")
            assertEquals("kJ input $input", roundedRatio(raw, 125, 523), result.amountMilliKcal)
            assertEquals(raw, result.originalAmountMilli)
            assertEquals("kJ", result.originalUnit)
            assertEquals(input, CalorieMath.inputValue(raw))
        }
    }

    @Test fun displayRoundingIsSymmetricAtHalfCentAndKjUsesTheSameBalance() {
        val vectors = listOf(0L, 1, 4, 5, 6, 995, 1005, 239_006, 1_410_994, 1_000_000_000)
        for (amount in vectors) for (signed in listOf(amount, -amount)) {
            assertEquals(centString(roundedRatio(signed, 1, 10)), CalorieMath.milliKcalToDisplay(signed))
            assertEquals(centString(roundedRatio(signed, 523, 1250)), CalorieMath.milliKjToDisplay(signed))
        }
    }

    @Test fun zeroLimitsAndInvalidInputAreHandledWithoutSilentTruncation() {
        assertEquals(0L, CalorieMath.parseAmount("0", "kcal").amountMilliKcal)
        assertEquals(0L, CalorieMath.parseAmount("0.000", "kJ").amountMilliKcal)
        assertEquals(1L, CalorieMath.parseAmount("0.003", "kJ").amountMilliKcal)
        assertEquals(1_000_000_000L, CalorieMath.parseAmount("1000000", "kcal").amountMilliKcal)
        assertEquals(1_000_000_000L, CalorieMath.parseAmount("4184000", "kJ").amountMilliKcal)
        for ((text, unit) in listOf("-1" to "kcal", "0.001" to "kJ", "0.002" to "kJ",
            "1000000.001" to "kcal", "4184000.001" to "kJ", "1.2345" to "kcal",
            "NaN" to "kJ", "1e3" to "kcal", "1" to "KJ", "" to "kcal")) {
            assertTrue("$text $unit must be rejected", runCatching { CalorieMath.parseAmount(text, unit) }.isFailure)
        }
        assertEquals(1234L, CalorieMath.parseAmount(" 1,234 ", "kcal").amountMilliKcal)
    }

    companion object {
        fun roundedRatio(value: Long, numerator: Long, denominator: Long): Long {
            val absolute = BigInteger.valueOf(value).abs().multiply(BigInteger.valueOf(numerator))
            val divisor = BigInteger.valueOf(denominator)
            val (quotient, remainder) = absolute.divideAndRemainder(divisor)
            val rounded = quotient + if (remainder.shiftLeft(1) >= divisor) BigInteger.ONE else BigInteger.ZERO
            return rounded.longValueExact() * if (value < 0) -1 else 1
        }
        fun decimalInput(value: Long): String {
            val whole = value / 1000
            val fraction = (value % 1000).toString().padStart(3, '0').trimEnd('0')
            return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
        }
        private fun centString(value: Long): String {
            val absolute = kotlin.math.abs(value)
            val fraction = (absolute % 100).toString().padStart(2, '0').trimEnd('0')
            val sign = if (value < 0) "-" else ""
            return sign + (absolute / 100) + if (fraction.isEmpty()) "" else ".$fraction"
        }
    }
}
