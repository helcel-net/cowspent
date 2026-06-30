package net.helcel.cowspent.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import net.helcel.cowspent.model.CreditDebt
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.Transaction
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.text.NumberFormat
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

object SupportUtil {

    @JvmField
    val normalNumberFormat: NumberFormat = NumberFormat.getInstance()

    @JvmField
    val dotNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.UK)

    @JvmField
    val commaNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.GERMANY)

    init {
        normalNumberFormat.maximumFractionDigits = 2
        normalNumberFormat
        dotNumberFormat.maximumFractionDigits = Int.MAX_VALUE
        dotNumberFormat.isGroupingUsed = false
        commaNumberFormat.maximumFractionDigits = Int.MAX_VALUE
        commaNumberFormat.isGroupingUsed = false
    }

    @JvmStatic
    @Throws(MalformedURLException::class, IOException::class)
    fun getHttpURLConnection(strUrl: String): HttpURLConnection {
        val url = URL(strUrl)
        val httpCon = url.openConnection() as HttpURLConnection
        if (url.protocol == "https") {
            val httpsCon = httpCon as HttpsURLConnection
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, null, null)
                httpsCon.sslSocketFactory = sslContext.socketFactory
            } catch (e: NoSuchAlgorithmException) {
                Log.e(SupportUtil::class.java.simpleName, "Exception", e)
            } catch (e: KeyManagementException) {
                Log.e(SupportUtil::class.java.simpleName, "Exception", e)
            }
        }
        return httpCon
    }

    @JvmStatic
    fun isDouble(s: String?): Boolean {
        if (s == null) return false
        return try {
            s.toDouble()
            true
        } catch (_: NumberFormatException) {
            false
        }
    }

    @JvmStatic
    fun isValidEmail(target: CharSequence?): Boolean {
        return if (target == null) false else android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    @JvmStatic
    fun getStatsOfProject(
        projId: Long, db: CowspentSQLiteOpenHelper,
        membersNbBills: MutableMap<Long, Int>,
        membersBalance: MutableMap<Long, Double>,
        membersPaid: MutableMap<Long, Double>,
        membersSpent: MutableMap<Long, Double>,
        catId: Int, paymentModeId: Int,
        dateMin: String?, dateMax: String?
    ): Int {
        return getStats(
            db.getMembersOfProject(projId, null),
            db.getBillsOfProject(projId),
            membersNbBills, membersBalance, membersPaid, membersSpent,
            catId, paymentModeId, dateMin, dateMax
        )
    }

    @JvmStatic
    fun getStats(
        dbMembers: List<DBMember>,
        dbBills: List<DBBill>,
        membersNbBills: MutableMap<Long, Int>,
        membersBalance: MutableMap<Long, Double>,
        membersPaid: MutableMap<Long, Double>,
        membersSpent: MutableMap<Long, Double>,
        catId: Int, paymentModeId: Int,
        dateMin: String?, dateMax: String?
    ): Int {
        val nbBillsTotal = 0
        val membersWeight: MutableMap<Long, Double> = HashMap()

        // init
        for (m in dbMembers) {
            membersNbBills[m.id] = 0
            membersBalance[m.id] = 0.0
            membersPaid[m.id] = 0.0
            membersSpent[m.id] = 0.0
            membersWeight[m.id] = m.weight
        }

        for (b in dbBills) {
            // don't take deleted bills and respect category filter
            if (b.state != DBBill.STATE_DELETED &&
                ((catId == -1000 || catId == -100 || b.categoryRemoteId == catId) &&
                        (catId != -100 || b.categoryRemoteId != DBBill.CATEGORY_REIMBURSEMENT) &&
                        (paymentModeId == -1000 || b.paymentModeRemoteId == paymentModeId)) &&
                (dateMin == null || b.date >= dateMin) &&
                (dateMax == null || b.date <= dateMax)
            ) {
                val nb = membersNbBills[b.payerId] ?: 0
                membersNbBills[b.payerId] = nb + 1
                val amount = b.amount
                val balPayer = membersBalance[b.payerId] ?: 0.0
                membersBalance[b.payerId] = balPayer + amount
                val paid = membersPaid[b.payerId] ?: 0.0
                membersPaid[b.payerId] = paid + amount
                
                var nbOwerShares = 0.0
                for (bo in b.billOwers) {
                    nbOwerShares += membersWeight[bo.memberId] ?: 0.0
                }
                for (bo in b.billOwers) {
                    val owerWeight = membersWeight[bo.memberId] ?: 0.0
                    val spent = if (nbOwerShares > 0) amount / nbOwerShares * owerWeight else 0.0
                    val balOwer = membersBalance[bo.memberId] ?: 0.0
                    membersBalance[bo.memberId] = balOwer - spent
                    val spentOwer = membersSpent[bo.memberId] ?: 0.0
                    membersSpent[bo.memberId] = spentOwer + spent
                }
            }
        }
        return nbBillsTotal
    }

    @JvmStatic
    fun round2(n: Double): Double {
        var r = round(abs(n) * 100.0) / 100.0
        if (n < 0.0) r = -r
        return r
    }

    const val SETTLE_OPTIMAL: Long = 0

    @JvmStatic
    fun settleBills(
        members: List<DBMember>, membersBalance: Map<Long, Double>,
        centerOnMemberId: Long
    ): List<Transaction> {
        return if (centerOnMemberId == SETTLE_OPTIMAL) {
            settleBillsOptimal(members, membersBalance)
        } else {
            val results: MutableList<Transaction> = ArrayList()
            for (mid in membersBalance.keys) {
                if (mid != centerOnMemberId) {
                    val balance = membersBalance[mid] ?: 0.0
                    if (balance > 0.0) {
                        results.add(Transaction(centerOnMemberId, mid, balance))
                    } else if (balance < 0.0) {
                        results.add(Transaction(mid, centerOnMemberId, -balance))
                    }
                }
            }
            results
        }
    }

    @JvmStatic
    fun settleBillsOptimal(members: List<DBMember>, membersBalance: Map<Long, Double>): List<Transaction> {
        val crediters: MutableList<CreditDebt> = ArrayList()
        val debiters: MutableList<CreditDebt> = ArrayList()

        // Create lists of credits and debts
        for (m in members) {
            val memberId = m.id
            val balance = membersBalance[memberId] ?: 0.0

            if (round2(balance) > 0.0) {
                crediters.add(CreditDebt(memberId, balance))
            } else if (round2(balance) < 0.0) {
                debiters.add(CreditDebt(memberId, balance))
            }
        }

        return reduceBalance(crediters, debiters, null)
    }

    @JvmStatic
    fun reduceBalance(
        crediters: MutableList<CreditDebt>,
        debiters: MutableList<CreditDebt>,
        resultsParam: MutableList<Transaction>?
    ): List<Transaction> {
        var results = resultsParam
        if (debiters.isEmpty() || crediters.isEmpty()) {
            return results ?: emptyList()
        }

        if (results == null) {
            results = ArrayList()
        }

        crediters.sortWith { cd2, cd1 ->
            if (cd1.balance == cd2.balance) {
                0
            } else {
                if (cd1.balance < cd2.balance) 1 else -1
            }
        }
        
        for (c in crediters) {
            Log.e(SupportUtil::class.java.simpleName, "* " + c.memberId + " : " + c.balance)
        }
        
        debiters.sortWith { cd2, cd1 ->
            if (cd1.balance == cd2.balance) {
                0
            } else {
                if (cd1.balance > cd2.balance) 1 else -1
            }
        }

        val deb = debiters.removeAt(debiters.size - 1)
        val debiter = deb.memberId
        val debiterBalance = deb.balance

        val cred = crediters.removeAt(crediters.size - 1)
        val crediter = cred.memberId
        val crediterBalance = cred.balance

        val amount: Double = if (abs(debiterBalance) > abs(crediterBalance)) {
            abs(crediterBalance)
        } else {
            abs(debiterBalance)
        }

        results.add(Transaction(debiter, crediter, amount))

        val newDebiterBalance = debiterBalance + amount
        if (newDebiterBalance < 0.0) {
            debiters.add(CreditDebt(debiter, newDebiterBalance))
            debiters.sortWith { cd2, cd1 ->
                if (cd1.balance == cd2.balance) {
                    0
                } else {
                    if (cd1.balance > cd2.balance) 1 else -1
                }
            }
        }

        val newCrediterBalance = crediterBalance - amount
        if (newCrediterBalance > 0.0) {
            crediters.add(CreditDebt(crediter, newCrediterBalance))
            crediters.sortWith { cd2, cd1 ->
                if (cd1.balance == cd2.balance) {
                    0
                } else {
                    if (cd1.balance < cd2.balance) 1 else -1
                }
            }
        }

        return reduceBalance(crediters, debiters, results)
    }

    @JvmStatic
    fun getVersionName(context: Context): String {
        var versionName = "0.0.0"
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return versionName
    }

    @JvmStatic
    fun getJsonObject(text: String?): JSONObject? {
        if (text == null) return null
        return try {
            JSONObject(text)
        } catch (_: JSONException) {
            null
        }
    }

    @JvmStatic
    fun compareVersions(version1: String, version2: String): Int {
        val levels1 = version1.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val levels2 = version2.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val length = max(levels1.size, levels2.size)
        for (i in 0 until length) {
            val v1 = if (i < levels1.size) levels1[i].toInt() else 0
            val v2 = if (i < levels2.size) levels2[i].toInt() else 0
            val compare = v1.compareTo(v2)
            if (compare != 0) {
                return compare
            }
        }
        return 0
    }

    @JvmStatic
    fun getAppVersionName(context: Context): String {
        var versionName = "???"
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(SupportUtil::class.java.simpleName, "Failed to get app version name", e)
            e.printStackTrace()
        }
        Log.d(SupportUtil::class.java.simpleName, "app version name is $versionName")
        return versionName
    }
}
