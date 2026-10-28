package com.example.calculator.feature.calculator.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.calculator.R
import com.example.calculator.core.ui.theme.CalculatorTheme
import com.example.calculator.feature.calculator.domain.Operation

@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { innerPadding ->
        CalculatorContent(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun CalculatorContent(
    state: CalculatorUiState,
    onAction: (CalculatorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The keypad layout stays the same in RTL locales; Arabic text still resolves its own direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Display(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
            Keypad(onAction = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Display(state: CalculatorUiState, modifier: Modifier = Modifier) {
    val secondaryText = when (state.error) {
        CalculatorError.DivisionByZero -> stringResource(R.string.error_division_by_zero)
        CalculatorError.InvalidExpression -> stringResource(R.string.error_invalid_expression)
        null -> state.preview
    }
    val secondaryColor = if (state.error != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = state.expression.ifEmpty { "0" }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.titleLarge,
            color = secondaryColor,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            fontSize = displayFontSize(text.length),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
            textAlign = TextAlign.End,
        )
    }
}

private fun displayFontSize(length: Int) = when {
    length <= 8 -> 56.sp
    length <= 12 -> 44.sp
    length <= 16 -> 34.sp
    else -> 26.sp
}

private enum class KeyKind { Number, Function, Operator, Equals }

@Composable
private fun Keypad(onAction: (CalculatorAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyRow {
            CalcKey("C", stringResource(R.string.cd_clear), KeyKind.Function) { onAction(CalculatorAction.Clear) }
            CalcKey("⌫", stringResource(R.string.cd_delete), KeyKind.Function) { onAction(CalculatorAction.Delete) }
            CalcKey("%", stringResource(R.string.cd_percent), KeyKind.Function) { onAction(CalculatorAction.Percent) }
            CalcKey("÷", stringResource(R.string.cd_divide), KeyKind.Operator) {
                onAction(CalculatorAction.Operator(Operation.Divide))
            }
        }
        KeyRow {
            DigitKey(7, onAction)
            DigitKey(8, onAction)
            DigitKey(9, onAction)
            CalcKey("×", stringResource(R.string.cd_multiply), KeyKind.Operator) {
                onAction(CalculatorAction.Operator(Operation.Multiply))
            }
        }
        KeyRow {
            DigitKey(4, onAction)
            DigitKey(5, onAction)
            DigitKey(6, onAction)
            CalcKey("−", stringResource(R.string.cd_subtract), KeyKind.Operator) {
                onAction(CalculatorAction.Operator(Operation.Subtract))
            }
        }
        KeyRow {
            DigitKey(1, onAction)
            DigitKey(2, onAction)
            DigitKey(3, onAction)
            CalcKey("+", stringResource(R.string.cd_add), KeyKind.Operator) {
                onAction(CalculatorAction.Operator(Operation.Add))
            }
        }
        KeyRow {
            DigitKey(0, onAction, weight = 2f)
            CalcKey(".", stringResource(R.string.cd_decimal), KeyKind.Number) { onAction(CalculatorAction.Decimal) }
            CalcKey("=", stringResource(R.string.cd_equals), KeyKind.Equals) { onAction(CalculatorAction.Equals) }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(digit: Int, onAction: (CalculatorAction) -> Unit, weight: Float = 1f) {
    CalcKey(
        label = digit.toString(),
        description = null,
        kind = KeyKind.Number,
        weight = weight,
    ) { onAction(CalculatorAction.Digit(digit)) }
}

@Composable
private fun RowScope.CalcKey(
    label: String,
    description: String?,
    kind: KeyKind,
    weight: Float = 1f,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        KeyKind.Number -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyKind.Function -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyKind.Operator -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KeyKind.Equals -> scheme.primary to scheme.onPrimary
    }
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .heightIn(min = 64.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text = label, fontSize = 26.sp)
    }
}

@Preview(showBackground = true)
@Composable
private fun CalculatorContentPreview() {
    CalculatorTheme(dynamicColor = false) {
        CalculatorContent(
            state = CalculatorUiState(expression = "12+3×4", preview = "24"),
            onAction = {},
        )
    }
}
