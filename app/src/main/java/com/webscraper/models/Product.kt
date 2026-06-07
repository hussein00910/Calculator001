package com.webscraper.models

data class Product(
    val title: String,
    val price: String = "",
    val productUrl: String = "",
    val imageUrl: String,
    val description: String = "",
    var localImagePath: String = ""
)
