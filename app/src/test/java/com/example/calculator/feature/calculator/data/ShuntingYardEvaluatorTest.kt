package com.example.calculator.feature.calculator.data

import com.example.calculator.feature.calculator.domain.EvalResult
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuntingYardEvaluatorTest {

    private val evaluator = ShuntingYardEvaluator()

    private fun assertValue(expected: String, expression: String) {
        val result = evaluator.evaluate(expression)
        assertTrue("Expected success for '$expression' but was $result", result is EvalResult.Success)
        val value = (result as EvalResult.Success).value
        assertEquals("'$expression' gave $value", 0, BigDecimal(expected).compareTo(value))
    }

    private fun assertInvalid(expression: String) {
        assertEquals("'$expression'", EvalResult.InvalidExpression, evaluator.evaluate(expression))
    }

    @Test
    fun respectsOperatorPrecedence() = assertValue("14", "2+3×4")

    @Test
    fun dividesWithDecimalResult() = assertValue("2.5", "10÷4")

    @Test
    fun addsDecimalsExactly() = assertValue("0.3", "0.1+0.2")

    @Test
    fun divisionByZeroIsReported() {
        assertEquals(EvalResult.DivisionByZero, evaluator.evaluate("5÷0"))
        assertEquals(EvalResult.DivisionByZero, evaluator.evaluate("0÷0"))
    }

    @Test
    fun trailingOperatorIsInvalid() = assertInvalid("5+")

    @Test
    fun leadingUnaryMinus() = assertValue("-2", "-5+3")

    @Test
    fun percentIsDividedByHundred() = assertValue("0.5", "50%")

    @Test
    fun unaryMinusAfterOperator() = assertValue("-6", "2×-3")

    @Test
    fun subtractionIsLeftAssociative() = assertValue("3", "8-3-2")

    @Test
    fun divisionIsLeftAssociative() = assertValue("1", "12÷4÷3")

    @Test
    fun invalidExpressionsAreRejected() {
        listOf("", "5 5", "1.2.3", ".", "-", "×5", "5%5", "--5", "5+-", "abc").forEach(::assertInvalid)
    }
}
