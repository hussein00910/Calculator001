package com.example.calculator.feature.calculator.domain

import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_FRACTION_DIGITS = 10

/** Rounds to at most [MAX_FRACTION_DIGITS] fractional digits and drops trailing zeros. */
fun BigDecimal.toDisplayString(): String {
    val rounded = setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_EVEN)
    if (rounded.signum() == 0) return "0"
    return rounded.stripTrailingZeros().toPlainString()
}
