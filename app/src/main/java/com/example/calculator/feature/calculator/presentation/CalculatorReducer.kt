package com.example.calculator.feature.calculator.presentation

import com.example.calculator.feature.calculator.domain.EvalResult
import com.example.calculator.feature.calculator.domain.ExpressionEvaluator
import com.example.calculator.feature.calculator.domain.Operation
import com.example.calculator.feature.calculator.domain.toDisplayString
import javax.inject.Inject

private const val MAX_DIGITS_PER_NUMBER = 15

/** Pure state transition: (state, action) -> new state. No Android dependencies. */
class CalculatorReducer @Inject constructor(
    private val evaluator: ExpressionEvaluator,
) {

    fun reduce(state: CalculatorUiState, action: CalculatorAction): CalculatorUiState = when (action) {
        is CalculatorAction.Digit -> appendDigit(state, action.value)
        CalculatorAction.Decimal -> appendDecimal(state)
        is CalculatorAction.Operator -> appendOperator(state, action.operation)
        CalculatorAction.Percent -> appendPercent(state)
        CalculatorAction.Delete -> delete(state)
        CalculatorAction.Clear -> CalculatorUiState()
        CalculatorAction.Equals -> equals(state)
    }

    /** A digit or '.' after a final result or an error starts a brand new expression. */
    private fun startingExpression(state: CalculatorUiState): String =
        if (state.error != null || state.showsFinalResult) "" else state.expression

    private fun appendDigit(state: CalculatorUiState, digit: Int): CalculatorUiState {
        val expression = startingExpression(state)
        if (expression.endsWith('%')) return state
        val number = expression.takeLastWhile(::isNumberChar)
        if (number == "0") {
            return if (digit == 0) state else withExpression(expression.dropLast(1) + digit)
        }
        if (number.count { it != '.' } >= MAX_DIGITS_PER_NUMBER) return state
        return withExpression(expression + digit)
    }

    private fun appendDecimal(state: CalculatorUiState): CalculatorUiState {
        val expression = startingExpression(state)
        if (expression.endsWith('%')) return state
        val number = expression.takeLastWhile(::isNumberChar)
        if ('.' in number) return state
        return withExpression(if (number.isEmpty()) expression + "0." else "$expression.")
    }

    private fun appendOperator(state: CalculatorUiState, operation: Operation): CalculatorUiState {
        if (state.error != null) return state
        val expression = state.expression
        return when {
            expression.isEmpty() ->
                if (operation == Operation.Subtract) withExpression("-") else state
            expression == "-" -> state
            else -> {
                val trimmed = if (expression.endsWith('.') || Operation.isOperator(expression.last())) {
                    expression.dropLast(1)
                } else {
                    expression
                }
                withExpression(trimmed + operation.symbol)
            }
        }
    }

    private fun appendPercent(state: CalculatorUiState): CalculatorUiState {
        if (state.error != null) return state
        val expression = state.expression.removeSuffix(".")
        if (expression.isEmpty() || expression.endsWith('%') || Operation.isOperator(expression.last())) {
            return state
        }
        return withExpression("$expression%")
    }

    private fun delete(state: CalculatorUiState): CalculatorUiState = when {
        state.error != null -> CalculatorUiState()
        state.expression.isEmpty() -> state
        else -> withExpression(state.expression.dropLast(1))
    }

    private fun equals(state: CalculatorUiState): CalculatorUiState {
        if (state.error != null || state.expression.isEmpty() || state.showsFinalResult) return state
        val trimmed = trimTrailing(state.expression)
        if (trimmed.isEmpty()) return state
        return when (val result = evaluator.evaluate(trimmed)) {
            is EvalResult.Success -> CalculatorUiState(
                expression = result.value.toDisplayString(),
                showsFinalResult = true,
            )
            EvalResult.DivisionByZero -> CalculatorUiState(
                expression = trimmed,
                error = CalculatorError.DivisionByZero,
            )
            EvalResult.InvalidExpression -> CalculatorUiState(
                expression = trimmed,
                error = CalculatorError.InvalidExpression,
            )
        }
    }

    private fun withExpression(expression: String): CalculatorUiState =
        CalculatorUiState(expression = expression, preview = previewFor(expression))

    /** The preview is shown only when there is an operator or '%' after the first character. */
    private fun previewFor(expression: String): String {
        val trimmed = trimTrailing(expression)
        if (trimmed.drop(1).none { it == '%' || Operation.isOperator(it) }) return ""
        val result = evaluator.evaluate(trimmed)
        return if (result is EvalResult.Success) result.value.toDisplayString() else ""
    }

    private fun trimTrailing(expression: String): String =
        expression.trimEnd { it == '.' || Operation.isOperator(it) }

    private fun isNumberChar(c: Char): Boolean = c in '0'..'9' || c == '.'
}
