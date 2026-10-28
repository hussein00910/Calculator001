package com.example.calculator.feature.calculator.data

import com.example.calculator.feature.calculator.domain.EvalResult
import com.example.calculator.feature.calculator.domain.ExpressionEvaluator
import com.example.calculator.feature.calculator.domain.Operation
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

/**
 * Evaluates expressions made of ASCII digits, '.', the four [Operation] symbols and a postfix '%'.
 * A '-' at the start or right after an operator is a unary minus.
 */
class ShuntingYardEvaluator @Inject constructor() : ExpressionEvaluator {

    override fun evaluate(expression: String): EvalResult = try {
        EvalResult.Success(compute(tokenize(expression)))
    } catch (ignored: DivisionByZeroException) {
        EvalResult.DivisionByZero
    } catch (ignored: InvalidExpressionException) {
        EvalResult.InvalidExpression
    } catch (ignored: NumberFormatException) {
        EvalResult.InvalidExpression
    } catch (ignored: ArithmeticException) {
        EvalResult.InvalidExpression
    }

    private class DivisionByZeroException : Exception()

    private class InvalidExpressionException : Exception()

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val operation: Operation) : Token
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun tokenize(expression: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var expectOperand = true
        var negate = false
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            val operation = Operation.fromSymbol(c)
            when {
                isDigit(c) || c == '.' -> {
                    if (!expectOperand) throw InvalidExpressionException()
                    var end = i
                    while (end < expression.length && (isDigit(expression[end]) || expression[end] == '.')) end++
                    val text = expression.substring(i, end)
                    if (text.count { it == '.' } > 1 || text.none(::isDigit)) throw InvalidExpressionException()
                    var value = BigDecimal(text)
                    if (negate) {
                        value = value.negate()
                        negate = false
                    }
                    tokens += Token.Num(value)
                    expectOperand = false
                    i = end
                }
                c == '%' -> {
                    if (expectOperand) throw InvalidExpressionException()
                    val last = tokens.removeAt(tokens.lastIndex) as Token.Num
                    tokens += Token.Num(last.value.movePointLeft(2))
                    i++
                }
                operation != null -> {
                    if (expectOperand) {
                        if (operation == Operation.Subtract && !negate) negate = true else throw InvalidExpressionException()
                    } else {
                        tokens += Token.Op(operation)
                        expectOperand = true
                    }
                    i++
                }
                else -> throw InvalidExpressionException()
            }
        }
        if (expectOperand) throw InvalidExpressionException()
        return tokens
    }

    private fun compute(tokens: List<Token>): BigDecimal {
        val values = ArrayDeque<BigDecimal>()
        val operations = ArrayDeque<Operation>()

        fun applyTop() {
            val operation = operations.removeLast()
            val right = values.removeLast()
            val left = values.removeLast()
            values.addLast(applyOperation(operation, left, right))
        }

        for (token in tokens) {
            when (token) {
                is Token.Num -> values.addLast(token.value)
                is Token.Op -> {
                    while (operations.isNotEmpty() && operations.last().precedence >= token.operation.precedence) {
                        applyTop()
                    }
                    operations.addLast(token.operation)
                }
            }
        }
        while (operations.isNotEmpty()) applyTop()
        return values.single()
    }

    private fun applyOperation(operation: Operation, left: BigDecimal, right: BigDecimal): BigDecimal =
        when (operation) {
            Operation.Add -> left.add(right)
            Operation.Subtract -> left.subtract(right)
            Operation.Multiply -> left.multiply(right)
            Operation.Divide -> {
                if (right.signum() == 0) throw DivisionByZeroException()
                left.divide(right, MathContext.DECIMAL128)
            }
        }
}
