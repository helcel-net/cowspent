package net.helcel.cowspent.util

import android.util.Log
import net.helcel.cowspent.model.DBAccountProject
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBBillOwer
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBCurrency
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBPaymentMode
import net.helcel.cowspent.model.DBProject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException


/**
 * Provides entity classes for handling server responses
 */
@Suppress("unused")
open class ServerResponse(
    private val response: VersatileProjectSyncClient.ResponseData,
    protected val isOcsResponse: Boolean
) {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    class NotModifiedException : IOException()

    protected val content: String
        get() = response.content

    val lastModified: Long
        get() = response.lastModified

    @Throws(JSONException::class)
    fun getResponseObjectData(): JSONObject {
        val rawData = JSONObject(content)
        if (!isOcsResponse) {
            return rawData
        }
        val data = rawData.getJSONObject("ocs")
        return data.getJSONObject("data")
    }

    @Throws(JSONException::class)
    fun getResponseArrayData(): JSONArray {
        if (!isOcsResponse) {
            return JSONArray(content)
        }
        val rawData = JSONObject(content)
        val data = rawData.getJSONObject("ocs")
        return data.getJSONArray("data")
    }

    @Throws(JSONException::class)
    fun getResponseStringData(): String {
        if (!isOcsResponse) {
            return content
        }
        val rawData = JSONObject(content)
        val data = rawData.getJSONObject("ocs")
        return data.getString("data")
    }

    class ProjectResponse(response: VersatileProjectSyncClient.ResponseData, isOcsResponse: Boolean) :
        ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val email: String
            get() = getEmailFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val name: String
            get() = getNameFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val deletionDisabled: Boolean
            get() = getDeletionDisabledFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val archivedTs: Long?
            get() = getArchivedTsFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val myAccessLevel: Int
            get() = getMyAccessLevelFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val currencyName: String
            get() = getCurrencyNameFromJSON(getResponseObjectData())

        @Throws(JSONException::class)
        fun getMembers(projId: Long): List<DBMember> {
            return getMembersFromJSON(getResponseObjectData(), projId)
        }

        @Throws(JSONException::class)
        fun getCategories(projId: Long): List<DBCategory> {
            return getCategoriesFromJSON(getResponseObjectData(), projId)
        }

        @Throws(JSONException::class)
        fun getPaymentModes(projId: Long): List<DBPaymentMode> {
            return getPaymentModesFromJSON(getResponseObjectData(), projId)
        }

        @Throws(JSONException::class)
        fun getCurrencies(projId: Long): List<DBCurrency> {
            return getCurrenciesFromJSON(getResponseObjectData(), projId)
        }
    }

    class CreateRemoteMemberResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean,
        private val isJsonMember: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()

        @get:Throws(JSONException::class)
        val remoteMemberId: Long
            get() = if (isJsonMember)
                getRemoteMemberIdFromJSON(getResponseObjectData())
            else
                getResponseStringData().toLong()
    }

    class CreateRemoteCurrencyResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class EditRemoteCurrencyResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class DeleteRemoteCurrencyResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class EditRemoteProjectResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class EditRemoteMemberResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @Throws(JSONException::class)
        fun getRemoteId(projectId: Long): Long {
            return getMemberFromJSON(getResponseObjectData(), projectId).remoteId
        }
    }

    class EditRemoteBillResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class CreateRemoteBillResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class DeleteRemoteBillResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class DeleteRemoteProjectResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class CreateRemoteProjectResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @get:Throws(JSONException::class)
        val stringContent: String
            get() = getResponseStringData()
    }

    class BillsResponse(response: VersatileProjectSyncClient.ResponseData, isOcsResponse: Boolean) :
        ServerResponse(response, isOcsResponse) {

        @Throws(JSONException::class)
        fun getBillsCospend(projId: Long, memberRemoteIdToId: Map<Long, Long>): List<DBBill> {
            return getBillsFromJSONObject(getResponseObjectData(), projId, memberRemoteIdToId)
        }

        @Throws(JSONException::class)
        fun getBillsIHM(projId: Long, memberRemoteIdToId: Map<Long, Long>): List<DBBill> {
            return getBillsFromJSONArray(JSONArray(content), projId, memberRemoteIdToId)
        }


        @get:Throws(JSONException::class)
        val allBillIds: List<Long>
            get() = getAllBillIdsFromJSON(getResponseObjectData())

        @get:Throws(JSONException::class)
        val syncTimestamp: Long
            get() = getSyncTimestampFromJSON(getResponseObjectData())
    }

    class MembersResponse(response: VersatileProjectSyncClient.ResponseData, isOcsResponse: Boolean) :
        ServerResponse(response, isOcsResponse) {

        @Throws(JSONException::class)
        fun getMembers(projId: Long): List<DBMember> {
            return getMembersFromJSONArray(getResponseArrayData(), projId)
        }
    }

    class AccountProjectsResponse(
        response: VersatileProjectSyncClient.ResponseData,
        isOcsResponse: Boolean
    ) : ServerResponse(response, isOcsResponse) {

        @Throws(JSONException::class)
        fun getAccountProjects(ncUrl: String): List<DBAccountProject> {
            return getAccountProjectsFromJSONArray(getResponseArrayData(), ncUrl)
        }
    }

    class CapabilitiesResponse(response: VersatileProjectSyncClient.ResponseData) :
        ServerResponse(response, true) {

        @get:Throws(IOException::class, JSONException::class)
        val color: String?
            get() = getColorFromJsonContent(JSONObject(content))

        @get:Throws(JSONException::class)
        val cospendVersion: String?
            get() = getCospendVersionFromCapabilitiesContent(JSONObject(content))
    }

    class AvatarResponse(response: VersatileProjectSyncClient.ResponseData) :
        ServerResponse(response, false) {

        @get:Throws(IOException::class)
        val avatarString: String
            get() = content
    }

    @Throws(JSONException::class)
    protected fun getPublicTokenFromJSON(json: JSONObject): String? {
        if (json.has("code") && json.has("sharetoken")) {
            val done = json.getInt("code")
            val publicToken = json.getString("sharetoken")
            if (done == 1) {
                return publicToken
            }
        }
        return null
    }

    @Throws(JSONException::class)
    protected fun getNameFromJSON(json: JSONObject): String {
        return if (json.has("name") && !json.isNull("name")) {
            json.getString("name")
        } else ""
    }

    @Throws(JSONException::class)
    protected fun getDeletionDisabledFromJSON(json: JSONObject): Boolean {
        return if (json.has("deletiondisabled")) {
            json.getBoolean("deletiondisabled")
        } else false
    }

    @Throws(JSONException::class)
    protected fun getArchivedTsFromJSON(json: JSONObject): Long? {
        return if (json.has("archived_ts") && !json.isNull("archived_ts")) {
            val ts = json.optLong("archived_ts", 0)
            if (ts > 0) ts else null
        } else null
    }

    @Throws(JSONException::class)
    protected fun getMyAccessLevelFromJSON(json: JSONObject): Int {
        return if (json.has("myaccesslevel")) {
            json.getInt("myaccesslevel")
        } else DBProject.ACCESS_LEVEL_UNKNOWN
    }

    @Throws(JSONException::class)
    protected fun getCurrencyNameFromJSON(json: JSONObject): String {
        return if (json.has("currencyname") && !json.isNull("currencyname")) {
            json.getString("currencyname")
        } else ""
    }

    @Throws(JSONException::class)
    protected fun getEmailFromJSON(json: JSONObject): String {
        return if (json.has("contact_email") && !json.isNull("contact_email")) {
            json.getString("contact_email")
        } else ""
    }

    @Throws(JSONException::class)
    protected fun getMembersFromJSONArray(jsonMs: JSONArray, projId: Long): List<DBMember> {
        val members: MutableList<DBMember> = ArrayList()
        for (i in 0 until jsonMs.length()) {
            val jsonM = jsonMs.getJSONObject(i)
            members.add(getMemberFromJSON(jsonM, projId))
        }
        return members
    }

    @Throws(JSONException::class)
    protected fun getCategoriesFromJSON(json: JSONObject, projId: Long): List<DBCategory> {
        val categories: MutableList<DBCategory> = ArrayList()
        if (json.has("categories") && json.get("categories") is JSONObject) {
            val jsonCats = json.getJSONObject("categories")
            val keys = jsonCats.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (jsonCats.get(key) is JSONObject) {
                    categories.add(getCategoryFromJSON(jsonCats.getJSONObject(key), key, projId))
                }
            }
        }
        return categories
    }

    @Throws(JSONException::class)
    protected fun getCategoryFromJSON(json: JSONObject, remoteIdStr: String, projId: Long): DBCategory {
        val remoteId = remoteIdStr.toLong()
        var name = ""
        var color = ""
        var icon = ""
        if (json.has("color") && !json.isNull("color")) {
            color = json.getString("color")
        }
        if (json.has("icon") && !json.isNull("icon")) {
            icon = json.getString("icon")
        }
        if (json.has("name") && !json.isNull("name")) {
            name = json.getString("name")
        }
        return DBCategory(0, remoteId, projId, name, icon, color)
    }

    @Throws(JSONException::class)
    protected fun getPaymentModesFromJSON(json: JSONObject, projId: Long): List<DBPaymentMode> {
        val paymentModes: MutableList<DBPaymentMode> = ArrayList()
        if (json.has("paymentmodes") && json.get("paymentmodes") is JSONObject) {
            val jsonPms = json.getJSONObject("paymentmodes")
            val keys = jsonPms.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (jsonPms.get(key) is JSONObject) {
                    paymentModes.add(getPaymentModeFromJSON(jsonPms.getJSONObject(key), key, projId))
                }
            }
        }
        return paymentModes
    }

    @Throws(JSONException::class)
    protected fun getPaymentModeFromJSON(json: JSONObject, remoteIdStr: String, projId: Long): DBPaymentMode {
        val remoteId = remoteIdStr.toLong()
        var name = ""
        var color = ""
        var icon = ""
        if (json.has("color") && !json.isNull("color")) {
            color = json.getString("color")
        }
        if (json.has("icon") && !json.isNull("icon")) {
            icon = json.getString("icon")
        }
        if (json.has("name") && !json.isNull("name")) {
            name = json.getString("name")
        }
        return DBPaymentMode(0, remoteId, projId, name, icon, color)
    }

    @Throws(JSONException::class)
    protected fun getCurrenciesFromJSON(json: JSONObject, projId: Long): List<DBCurrency> {
        val currencies: MutableList<DBCurrency> = ArrayList()
        if (json.has("currencies") && json.get("currencies") is JSONArray) {
            val jsonCurs = json.getJSONArray("currencies")
            for (i in 0 until jsonCurs.length()) {
                if (jsonCurs.get(i) is JSONObject) {
                    currencies.add(getCurrencyFromJSON(jsonCurs.getJSONObject(i), projId))
                }
            }
        }
        return currencies
    }

    @Throws(JSONException::class)
    protected fun getCurrencyFromJSON(json: JSONObject, projId: Long): DBCurrency {
        var remoteId: Long = 0
        var name = ""
        var exchangeRate = 1.0
        if (json.has("exchange_rate") && !json.isNull("exchange_rate")) {
            exchangeRate = json.getDouble("exchange_rate")
        }
        if (json.has("id") && !json.isNull("id")) {
            remoteId = json.getLong("id")
        }
        if (json.has("name") && !json.isNull("name")) {
            name = json.getString("name")
        }
        return DBCurrency(0, remoteId, projId, name, exchangeRate, DBBill.STATE_OK)
    }

    @Throws(JSONException::class)
    protected fun getMembersFromJSON(json: JSONObject, projId: Long): List<DBMember> {
        val members: MutableList<DBMember> = ArrayList()
        if (json.has("members")) {
            val jsonMs = json.getJSONArray("members")
            for (i in 0 until jsonMs.length()) {
                val jsonM = jsonMs.getJSONObject(i)
                members.add(getMemberFromJSON(jsonM, projId))
            }
        }
        return members
    }

    @Throws(JSONException::class)
    protected fun getMemberFromJSON(json: JSONObject, projId: Long): DBMember {
        var activated = true
        var weight = 1.0
        var remoteId: Long = 0
        var name = ""
        var r: Int? = null
        var g: Int? = null
        var b: Int? = null
        var ncUserId: String? = null
        if (!json.isNull("id")) {
            remoteId = json.getLong("id")
        }
        if (!json.isNull("weight")) {
            weight = json.getDouble("weight")
        }
        if (!json.isNull("activated")) {
            activated = json.getBoolean("activated")
        }
        if (!json.isNull("name")) {
            name = json.getString("name")
        }
        if (json.has("color") && !json.isNull("color")) {
            val obj = json.get("color")
            if (obj is String) {
                val color = json.getString("color").replace("#", "")
                if (color.length == 6) {
                    r = color.substring(0, 2).toInt(16)
                    g = color.substring(2, 4).toInt(16)
                    b = color.substring(4, 6).toInt(16)
                }
            } else if (obj is JSONObject) {
                val color = json.getJSONObject("color")
                if (color.has("r") && !color.isNull("r")) {
                    r = color.getInt("r")
                }
                if (color.has("g") && !color.isNull("g")) {
                    g = color.getInt("g")
                }
                if (color.has("b") && !color.isNull("b")) {
                    b = color.getInt("b")
                }
            }
        }
        if (json.has("userid") && !json.isNull("userid")) {
            ncUserId = json.getString("userid")
        }
        return DBMember(
            0, remoteId, projId, name, activated, weight, DBBill.STATE_OK,
            r, g, b, ncUserId, null
        )
    }

    @Throws(JSONException::class)
    protected fun getAllBillIdsFromJSON(json: JSONObject): List<Long> {
        val billIds: MutableList<Long> = ArrayList()
        if (json.has("allBillIds") && !json.isNull("allBillIds")) {
            val jsonBillIds = json.getJSONArray("allBillIds")
            for (i in 0 until jsonBillIds.length()) {
                billIds.add(jsonBillIds.getLong(i))
            }
        }
        return billIds
    }

    @Throws(JSONException::class)
    protected fun getSyncTimestampFromJSON(json: JSONObject): Long {
        var ts = 0L
        if (json.has("timestamp") && !json.isNull("timestamp")) {
            ts = json.getLong("timestamp")
        }
        return ts
    }

    @Throws(JSONException::class)
    protected fun getBillsFromJSONArray(
        json: JSONArray,
        projId: Long,
        memberRemoteIdToId: Map<Long, Long>
    ): List<DBBill> {
        val bills: MutableList<DBBill> = ArrayList()
        for (i in 0 until json.length()) {
            val jsonBill = json.getJSONObject(i)
            bills.add(getBillFromJSON(jsonBill, projId, memberRemoteIdToId))
        }
        return bills
    }

    @Throws(JSONException::class)
    protected fun getBillsFromJSONObject(
        json: JSONObject,
        projId: Long,
        memberRemoteIdToId: Map<Long, Long>
    ): List<DBBill> {
        val bills: List<DBBill>
        if (json.has("bills") && !json.isNull("bills")) {
            val jsonBills = json.getJSONArray("bills")
            bills = getBillsFromJSONArray(jsonBills, projId, memberRemoteIdToId)
        } else {
            bills = ArrayList()
        }
        return bills
    }

    @Throws(JSONException::class)
    protected fun getBillFromJSON(
        json: JSONObject,
        projId: Long,
        memberRemoteIdToId: Map<Long, Long>
    ): DBBill {
        var remoteId: Long = 0
        var payerRemoteId: Long
        var payerId: Long = 0
        var amount = 0.0
        var dateStr: String
        var date: Date
        var timestamp: Long = 0
        var what = ""
        var comment = ""
        var repeat = DBBill.NON_REPEATED
        var paymentMode = DBBill.PAYMODE_NONE
        var paymentModeRemoteId = DBBill.PAYMODE_ID_NONE
        var categoryId = DBBill.CATEGORY_NONE
        if (!json.isNull("id")) {
            remoteId = json.getLong("id")
        }
        if (!json.isNull("payer_id")) {
            payerRemoteId = json.getLong("payer_id")
            payerId = memberRemoteIdToId[payerRemoteId] ?: 0
        } else if (!json.isNull("payer")) {
            payerRemoteId = json.getLong("payer")
            payerId = memberRemoteIdToId[payerRemoteId] ?: 0
        }
        if (!json.isNull("amount")) {
            amount = json.getDouble("amount")
        }
        // get timestamp in priority
        if (!json.isNull("timestamp")) {
            timestamp = json.getLong("timestamp")
        } else if (!json.isNull("date")) {
            dateStr = json.getString("date")
            try {
                date = sdf.parse(dateStr)!!
                timestamp = date.time / 1000
            } catch (_: Exception) {
                timestamp = 0
            }
        }
        if (!json.isNull("what")) {
            what = json.getString("what")
        } else if (!json.isNull("label")) {
            what = json.getString("label")
        }
        if (!json.isNull("comment")) {
            comment = json.getString("comment")
        }
        if (json.has("repeat") && !json.isNull("repeat")) {
            repeat = json.getString("repeat")
        }
        if (json.has("paymentmode") && !json.isNull("paymentmode")) {
            paymentMode = json.getString("paymentmode")
        }
        if (json.has("categoryid") && !json.isNull("categoryid")) {
            categoryId = json.getInt("categoryid")
            Log.d("PLOP", "LOADED CATTTTTTTTTTTT $categoryId")
        }
        if (json.has("paymentmodeid") && !json.isNull("paymentmodeid")) {
            paymentModeRemoteId = json.getInt("paymentmodeid")
        }
        // old MB, new Cospend is ok as Cospend provides the old pm ID
        // new MB, old Cospend => set payment mode ID from old one
        if (DBBill.PAYMODE_NONE != paymentMode && "" != paymentMode && paymentModeRemoteId == DBBill.PAYMODE_ID_NONE) {
            Log.d("PaymentMode", "old: $paymentMode and new: 0")
            paymentModeRemoteId = DBBill.oldPmIdToNew[paymentMode] ?: DBBill.PAYMODE_ID_NONE
        }
        val bill = DBBill(
            0, remoteId, projId, payerId, amount, timestamp, what,
            DBBill.STATE_OK, repeat, paymentMode, categoryId, comment, paymentModeRemoteId
        )
        bill.billOwers = getBillOwersFromJson(json, memberRemoteIdToId)
        return bill
    }

    @Throws(JSONException::class)
    protected fun getBillOwersFromJson(
        json: JSONObject,
        memberRemoteIdToId: Map<Long, Long>
    ): List<DBBillOwer> {
        val billOwers: MutableList<DBBillOwer> = ArrayList()
        if (json.has("owers")) {
            val jsonOs = json.getJSONArray("owers")
            for (i in 0 until jsonOs.length()) {
                val obj = jsonOs.get(i)
                val memberRemoteId = if (obj is JSONObject) {
                    obj.getLong("id")
                } else {
                    jsonOs.getLong(i)
                }
                val memberLocalId = memberRemoteIdToId[memberRemoteId] ?: 0
                billOwers.add(DBBillOwer(0, 0, memberLocalId))
            }
        }
        return billOwers
    }

    @Throws(JSONException::class)
    protected fun getAccountProjectsFromJSONArray(jsonMs: JSONArray, ncUrl: String): List<DBAccountProject> {
        val accountProjects: MutableList<DBAccountProject> = ArrayList()
        for (i in 0 until jsonMs.length()) {
            val jsonAP = jsonMs.getJSONObject(i)
            accountProjects.add(getAccountProjectFromJSON(jsonAP, ncUrl))
        }
        return accountProjects
    }

    @Throws(JSONException::class)
    protected fun getAccountProjectFromJSON(json: JSONObject, accountNcUrl: String): DBAccountProject {
        var remoteId = ""
        var name = ""
        var ncUrl = ""
        if (!json.isNull("name")) {
            name = json.getString("name")
        }
        if (!json.isNull("id")) {
            remoteId = json.getString("id")
        }
        if (!json.isNull("ncurl")) {
            ncUrl = json.getString("ncUrl")
        }
        val archivedTs: Long? = getArchivedTsFromJSON(json)
        if (ncUrl.isEmpty()) {
            ncUrl = accountNcUrl
        }
        return DBAccountProject(0, remoteId, null, name, ncUrl, archivedTs)
    }

    @Throws(IOException::class)
    protected fun getColorFromContent(content: String): String? {
        var result: String? = null
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val stream: InputStream = ByteArrayInputStream(content.toByteArray())
            val doc = db.parse(stream)
            doc.documentElement.normalize()
            // Locate the Tag Name
            val nodeList = doc.getElementsByTagName("color")
            if (nodeList.length > 0) {
                result = nodeList.item(0).textContent
                Log.i(TAG, "I GOT THE COLOR from server: $result")
            }
        } catch (_: ParserConfigurationException) {
        } catch (_: SAXException) {
        }
        return result
    }

    protected fun getColorFromJsonContent(json: JSONObject): String? {
        return try {
            val ocs = json.getJSONObject("ocs")
            val data = ocs.getJSONObject("data")
            val capabilities = data.getJSONObject("capabilities")
            val theming = capabilities.getJSONObject("theming")
            val color = theming.getString("color")
            Log.i(TAG, "I GOT THE COLOR from server's JSON response: $color")
            color
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to get the color from OCS capabilities response $e")
            null
        }
    }

    protected fun getCospendVersionFromCapabilitiesContent(json: JSONObject): String? {
        return try {
            val ocs = json.getJSONObject("ocs")
            val data = ocs.getJSONObject("data")
            val capabilities = data.getJSONObject("capabilities")
            val cospend = capabilities.getJSONObject("cospend")
            val version = cospend.getString("version")
            Log.i(TAG, "I GOT THE Cospend version: $version")
            version
        } catch (e: JSONException) {
            Log.i(TAG, "Failed to get the Cospend version$e")
            null
        }
    }

    @Throws(JSONException::class)
    protected fun getRemoteMemberIdFromJSON(json: JSONObject): Long {
        return json.getLong("id")
    }

    companion object {
        private val TAG = ServerResponse::class.java.simpleName
    }
}
