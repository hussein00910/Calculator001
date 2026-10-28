package com.example.calculator.feature.calculator.domain

enum class Operation(val symbol: Char, val precedence: Int) {
    Add('+', 1),
    Subtract('-', 1),
    Multiply('×', 2),
    Divide('÷', 2);

    companion object {
        fun fromSymbol(symbol: Char): Operation? = entries.firstOrNull { it.symbol == symbol }

        fun isOperator(symbol: Char): Boolean = fromSymbol(symbol) != null
    }
}
