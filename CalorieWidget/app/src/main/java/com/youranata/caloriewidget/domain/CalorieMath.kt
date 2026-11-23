package com.youranata.caloriewidget.domain

import java.math.BigDecimal
import java.math.RoundingMode

object CalorieMath {
    const val DEFAULT_BUDGET_MILLI_KCAL = 2_000_000L
    const val MAX_AMOUNT_MILLI_KCAL = 1_000_000_000L // 1 million kcal per entry/budget
    private val factor = BigDecimal("4.184")

    fun parseAmount(value: String, unit: String): ParsedAmount {
        require(unit == "kcal" || unit == "kJ") { "不支持的单位" }
        val input = value.trim()
        require(input.matches(Regex("[0-9]+([.,][0-9]{1,3})?"))) { "请输入非负数字，最多三位小数" }
        val amount = input.replace(',', '.').toBigDecimal()
        require(amount <= BigDecimal("4184000")) { "单次热量不能超过 1,000,000 kcal" }
        val original = amount.movePointRight(3).longValueExact()
        val unified = if (unit == "kJ") amount.divide(factor, 12, RoundingMode.HALF_UP)
            .movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact() else original
        require(unified <= MAX_AMOUNT_MILLI_KCAL) { "单次热量不能超过 1,000,000 kcal" }
        require(amount.signum() == 0 || unified > 0) { "换算后热量应至少为 0.001 kcal" }
        return ParsedAmount(unified, original, unit)
    }

    fun parseBudget(value: String): Long = parseAmount(value, "kcal").amountMilliKcal.also {
        require(it > 0) { "预算必须大于 0" }
    }
    fun inputValue(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
    fun milliKcalToDisplay(milli: Long): String = BigDecimal.valueOf(milli, 3)
        .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    fun milliKjToDisplay(milli: Long): String = BigDecimal.valueOf(milli, 3).multiply(factor)
        .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    data class ParsedAmount(val amountMilliKcal: Long, val originalAmountMilli: Long, val originalUnit: String)
}
