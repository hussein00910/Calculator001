package com.example.calculator.feature.calculator.presentation

import com.example.calculator.feature.calculator.data.ShuntingYardEvaluator
import com.example.calculator.feature.calculator.domain.Operation
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorReducerTest {

    private val reducer = CalculatorReducer(ShuntingYardEvaluator())

    /** Digits, '.', '%', '+-×÷', '=' map to actions; 'D' is delete and 'C' is clear. */
    private fun type(text: String, from: CalculatorUiState = CalculatorUiState()): CalculatorUiState =
        text.fold(from) { state, char ->
            val action = when (char) {
                in '0'..'9' -> CalculatorAction.Digit(char - '0')
                '.' -> CalculatorAction.Decimal
                '%' -> CalculatorAction.Percent
                '=' -> CalculatorAction.Equals
                'D' -> CalculatorAction.Delete
                'C' -> CalculatorAction.Clear
                else -> CalculatorAction.Operator(requireNotNull(Operation.fromSymbol(char)) { "Unknown key '$char'" })
            }
            reducer.reduce(state, action)
        }

    @Test
    fun buildsNumbers() = assertEquals("123", type("123").expression)

    @Test
    fun showsPreviewOnlyWithOperator() {
        assertEquals("", type("23").preview)
        assertEquals("5", type("2+3").preview)
        assertEquals("", type("5+").preview)
        assertEquals("", type("-5").preview)
    }

    @Test
    fun noLeadingZeros() {
        assertEquals("7", type("007").expression)
        assertEquals("0", type("00").expression)
    }

    @Test
    fun rejectsSecondDecimalPoint() = assertEquals("1.23", type("1.2.3").expression)

    @Test
    fun decimalOnEmptyNumberAddsLeadingZero() = assertEquals("0.5", type(".5").expression)

    @Test
    fun operatorReplacesPreviousOperator() {
        assertEquals("5×", type("5+×").expression)
        assertEquals("5-", type("5×-").expression)
    }

    @Test
    fun danglingDecimalIsDroppedBeforeOperator() = assertEquals("1+", type("1.+").expression)

    @Test
    fun onlyMinusMayStartAnExpression() {
        assertEquals("", type("+").expression)
        assertEquals("-", type("-").expression)
        assertEquals("-", type("-+").expression)
    }

    @Test
    fun deleteRemovesLastCharacter() {
        assertEquals("1", type("12D").expression)
        assertEquals(CalculatorUiState(), type("1D"))
    }

    @Test
    fun clearResetsEverything() = assertEquals(CalculatorUiState(), type("12+3C"))

    @Test
    fun percentIsAppliedAndBlocksDigits() {
        val state = type("50%")
        assertEquals("50%", state.expression)
        assertEquals("0.5", state.preview)
        assertEquals("5%", type("5%3").expression)
        assertEquals("5%", type("5%.").expression)
    }

    @Test
    fun equalsProducesFinalResult() {
        val state = type("2+3=")
        assertEquals("5", state.expression)
        assertEquals("", state.preview)
        assertEquals(true, state.showsFinalResult)
        assertEquals("0.3", type("0.1+0.2=").expression)
    }

    @Test
    fun equalsTrimsTrailingOperator() = assertEquals("5", type("5+=").expression)

    @Test
    fun digitAfterResultStartsNewExpression() {
        val state = type("2+3=4")
        assertEquals("4", state.expression)
        assertEquals(false, state.showsFinalResult)
    }

    @Test
    fun operatorAfterResultContinuesFromIt() = assertEquals("5+4", type("2+3=+4").expression)

    @Test
    fun divisionByZeroSetsError() {
        val state = type("5÷0=")
        assertEquals(CalculatorError.DivisionByZero, state.error)
        assertEquals(false, state.showsFinalResult)
    }

    @Test
    fun errorIsClearedByDeleteOrNewDigit() {
        assertEquals(CalculatorUiState(), type("5÷0=D"))
        val state = type("5÷0=7")
        assertEquals("7", state.expression)
        assertEquals(null, state.error)
    }

    @Test
    fun limitsDigitsPerNumber() = assertEquals("123456789012345", type("1234567890123456").expression)
}
