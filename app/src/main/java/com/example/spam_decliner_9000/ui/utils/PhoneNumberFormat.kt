package com.example.spam_decliner_9000.ui.utils

fun formatPhoneNumber(number: String): String {
    val digits = number.removePrefix("+1").filter { it.isDigit() }
    if (number.startsWith("+1") && digits.length == 10) {
        return "+1 (${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    }
    return number
}
