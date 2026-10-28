package com.example.calculator.feature.calculator.presentation

import com.example.calculator.feature.calculator.domain.Operation

sealed interface CalculatorAction {
    data class Digit(val value: Int) : CalculatorAction {
        init {
            require(value in 0..9) { "Digit must be in 0..9 but was $value" }
        }
    }

    data class Operator(val operation: Operation) : CalculatorAction
    data object Decimal : CalculatorAction
    data object Percent : CalculatorAction
    data object Delete : CalculatorAction
    data object Clear : CalculatorAction
    data object Equals : CalculatorAction
}
