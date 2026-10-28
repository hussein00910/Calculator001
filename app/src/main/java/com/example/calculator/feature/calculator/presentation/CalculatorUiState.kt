package com.example.calculator.feature.calculator.presentation

data class CalculatorUiState(
    val expression: String = "",
    val preview: String = "",
    val error: CalculatorError? = null,
    val showsFinalResult: Boolean = false,
)

enum class CalculatorError {
    DivisionByZero,
    InvalidExpression,
}
