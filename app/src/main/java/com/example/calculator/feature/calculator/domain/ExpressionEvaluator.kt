package com.example.calculator.feature.calculator.domain

import java.math.BigDecimal

fun interface ExpressionEvaluator {
    fun evaluate(expression: String): EvalResult
}

sealed interface EvalResult {
    data class Success(val value: BigDecimal) : EvalResult
    data object DivisionByZero : EvalResult
    data object InvalidExpression : EvalResult
}
