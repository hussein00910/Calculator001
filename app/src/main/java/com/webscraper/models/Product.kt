package com.webscraper.models

data class Product(
    val title: String,
    val description: String,
    val imageUrl: String,
    var localImagePath: String = ""
)
