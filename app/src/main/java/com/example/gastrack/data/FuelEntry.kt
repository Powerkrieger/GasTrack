package com.example.gastrack.data

data class FuelEntry(
    val id: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val stationName: String,
    val liters: Double,
    val euros: Double,
    val pricePerLiter: Double,
    val kilometers: Double,
    val receiptPath: String?
)
