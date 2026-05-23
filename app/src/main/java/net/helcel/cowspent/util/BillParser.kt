package net.helcel.cowspent.util

import net.helcel.cowspent.model.parsed.AustrianBillQrCode
import net.helcel.cowspent.model.parsed.CroatianBillQrCode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round
import androidx.core.net.toUri

object BillParser {
    private val austrianQrCodeDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    private val croatianQrCodeDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    @Throws(ParseException::class)
    fun parseAustrianBillFromQrCode(scannedBill: String): AustrianBillQrCode {
        val splitBill = scannedBill.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (splitBill.size < 10) {
            throw ParseException("Could not parse bill to Austrian format!", 0)
        }

        val date = austrianQrCodeDateFormat.parse(splitBill[4]) ?: throw ParseException("Could not parse date", 0)
        var totalAmount = 0.0
        for (i in 1..5) {
            totalAmount += SupportUtil.commaNumberFormat.parse(splitBill[4 + i])?.toDouble() ?: 0.0
        }
        // some amounts may be negative that's why we have to round here
        return AustrianBillQrCode(splitBill[2], date, round(totalAmount * 100.0) / 100.0)
    }

    @Throws(ParseException::class)
    fun parseCroatianBillFromQrCode(scannedBill: String): CroatianBillQrCode {
        val uri = scannedBill.toUri()

        // Be defensive, and only allow the known host
        if (uri.host == null || uri.host != "porezna.gov.hr") {
            throw ParseException("Does not look like a Croatian QR code", 0)
        }

        val dates = uri.getQueryParameters("datv")
        val amounts = uri.getQueryParameters("izn")

        var date: LocalDateTime? = null
        if (!dates.isEmpty()) {
            date = LocalDateTime.parse(dates[0], croatianQrCodeDateFormat)
        }

        var amount: Double? = null
        if (!amounts.isEmpty()) {
            try {
                amount = SupportUtil.commaNumberFormat.parse(amounts[0])?.toDouble()
            } catch (_: NullPointerException) {
                // failed to parse as double
            }
        }

        if (date == null && amount == null) {
            throw ParseException("Could not parse bill to Croatian format!", 0)
        }
        return CroatianBillQrCode(
            date,
            amount ?: 0.0
        )
    }
}
