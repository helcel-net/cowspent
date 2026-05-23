package net.helcel.cowspent.android.helper

import net.helcel.cowspent.util.SupportUtil
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

fun formatShortValue(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000).replace(".0", "")
        value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000).replace(".0", "")
        else -> String.format(Locale.ROOT, "%.0f", value)
    }
}

fun formatBalance(balance: Double): String {
    val rbalance = round(abs(balance) * 100.0) / 100.0
    val balanceSign = if (balance > 0.01) "+" else if (balance < -0.01) "-" else ""
    return if (rbalance == 0.0) "" else "$balanceSign${SupportUtil.normalNumberFormat.format(rbalance)}"
}
