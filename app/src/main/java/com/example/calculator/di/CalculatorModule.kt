package com.example.calculator.di

import com.example.calculator.feature.calculator.data.ShuntingYardEvaluator
import com.example.calculator.feature.calculator.domain.ExpressionEvaluator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CalculatorModule {

    @Binds
    abstract fun bindExpressionEvaluator(impl: ShuntingYardEvaluator): ExpressionEvaluator
}
