package net.helcel.cowspent.android.helper

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Formats amount using k/M notation (e.g., 1.5k for 1500).
 */
fun formatShortValue(value: Double): String {
    val absValue = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absValue >= 1_000_000 -> sign + formatAmount(absValue / 1_000_000) + "M"
        absValue >= 1_000 -> sign + formatAmount(absValue / 1_000) + "k"
        else -> sign + formatAmount(absValue)
    }
}

/**
 * Formats balance with sign and locale-aware number formatting.
 */
fun formatBalance(balance: Double): String {
    val rbalance = round(abs(balance) * 100.0) / 100.0
    val balanceSign = if (balance > 0.01) "+" else if (balance < -0.01) "-" else ""
    return if (rbalance == 0.0) "" else "$balanceSign${formatAmount(rbalance)}"
}

/**
 * Formats amount using system locale or specified locale.
 */
fun formatAmount(value: Double, locale: Locale = Locale.getDefault()): String {
    val formatter = NumberFormat.getNumberInstance(locale)
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 0
    return formatter.format(value)
}

/**
 * Parses amount from string robustly.
 * Tries common separators and cleans up non-numeric characters (except separators).
 */
fun parseAmount(input: String?): Double? {
    if (input.isNullOrBlank()) return null
    
    // Remove non-numeric characters except for delimiters and minus sign
    val cleaned = input.replace(Regex("[^0-9,.-]"), "").trim()
    
    if (cleaned.isEmpty()) return null

    // If there's both a comma and a dot, we assume the last one is the decimal separator
    val lastDot = cleaned.lastIndexOf('.')
    val lastComma = cleaned.lastIndexOf(',')
    
    return if (lastDot != -1 && lastComma != -1) {
        if (lastDot > lastComma) {
            // Dot is decimal, comma is thousands
            cleaned.replace(",", "").toDoubleOrNull()
        } else {
            // Comma is decimal, dot is thousands
            cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
        }
    } else if (lastComma != -1) {
        // Only comma. Could be decimal or thousands. 
        if (cleaned.count { it == ',' } == 1) {
            cleaned.replace(',', '.').toDoubleOrNull()
        } else {
            // Multiple commas -> thousands
            cleaned.replace(",", "").toDoubleOrNull()
        }
    } else {
        // Only dots or no separators
        if (cleaned.count { it == '.' } > 1) {
            cleaned.replace(".", "").toDoubleOrNull()
        } else {
            cleaned.toDoubleOrNull()
        }
    }
}
