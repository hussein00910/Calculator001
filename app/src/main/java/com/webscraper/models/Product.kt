package com.webscraper.models

data class Product(
    val title: String,
    val description: String,
    val imageUrl: String,
    val localImagePath: String = ""
)
