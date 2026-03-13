package com.example.gastrack.ui

sealed class Screen {
    object AddEntry : Screen()
    object History : Screen()
    object Stats : Screen()
    data class Detail(val entryId: String) : Screen()
}
