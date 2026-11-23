package com.youranata.caloriewidget.domain

import java.math.BigDecimal
import java.math.RoundingMode

object CalorieMath {
    const val DEFAULT_BUDGET_MILLI_KCAL = 2_000_000L
    const val KCAL_TO_KJ = 4.184
    private val KCAL_TO_KJ_DECIMAL = BigDecimal("4.184")
    private const val MAX_MILLI_KCAL = 9_000_000_000_000L

    fun parsePositive(value: String, unit: String): ParsedAmount {
        val amount = value.trim().toBigDecimalOrNull()
            ?: throw IllegalArgumentException("请输入有效数字")
        if (amount < BigDecimal.ZERO) throw IllegalArgumentException("热量不能为负数")
        try {
            val originalMilli = amount.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
            val kcalMilli = if (unit == "kJ") {
                amount.divide(KCAL_TO_KJ_DECIMAL, 12, RoundingMode.HALF_UP)
                    .movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
            } else originalMilli
            if (kcalMilli < 0 || kcalMilli > MAX_MILLI_KCAL) {
                throw IllegalArgumentException("热量数值超出允许范围")
            }
            return ParsedAmount(kcalMilli, originalMilli, unit)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("热量数值超出允许范围", e)
        }
    }

    fun kcalToKj(kcalMilli: Long): Double = kcalMilli / 1000.0 * KCAL_TO_KJ
    fun milliKcalToDisplay(milli: Long): String = BigDecimal(milli).movePointLeft(3)
        .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    fun milliKcalToWholeDisplay(milli: Long): String = BigDecimal(milli).movePointLeft(3)
        .setScale(0, RoundingMode.HALF_UP).toPlainString()
    fun milliKjToDisplay(kcalMilli: Long): String = BigDecimal.valueOf(kcalToKj(kcalMilli))
        .setScale(0, RoundingMode.HALF_UP).toPlainString()

    data class ParsedAmount(val amountMilliKcal: Long, val originalAmountMilli: Long, val originalUnit: String)
}
