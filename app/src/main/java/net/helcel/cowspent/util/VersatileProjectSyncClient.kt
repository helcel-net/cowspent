package net.helcel.cowspent.util

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.annotation.WorkerThread
import com.nextcloud.android.sso.QueryParam
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import com.nextcloud.android.sso.model.SingleSignOnAccount
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBCurrency
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBPaymentMode
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType
import org.json.JSONException
import java.io.*
import java.net.HttpURLConnection
import java.net.URLEncoder

@WorkerThread
class VersatileProjectSyncClient(
    private val url: String,
    private val username: String,
    private val password: String,
    private val nextcloudAPI: NextcloudAPI?,
    private val ssoAccount: SingleSignOnAccount?,
    cospendVersion: String?,
    private val context: Context
) {

    /**
     * This entity class is used to return relevant data of the HTTP response.
     */
    class ResponseData(val content: String, val eTag: String?, val lastModified: Long)

    private val cospendVersionGT161: Boolean = if (cospendVersion == null) {
        Log.i(TAG, "GT161 is FALSE")
        false
    } else {
        val gt = SupportUtil.compareVersions(cospendVersion, "1.6.1") >= 0
        Log.i(TAG, "GT161: $gt")
        gt
    }

    fun canAccessProjectWithNCLogin(project: DBProject): Boolean {
        return (project.password == ""
                && url.replace("/+$".toRegex(), "") != ""
                && project.serverUrl!!
            .replace("/index.php/apps/cospend", "") == url.replace("/+$".toRegex(), "")
                )
    }

    fun canAccessProjectWithSSO(project: DBProject): Boolean {
        return (project.password == ""
                && ssoAccount != null
                && project.serverUrl!!.replace("/index.php/apps/cospend", "") == ssoAccount.url
                )
    }

    @Throws(UnsupportedEncodingException::class)
    private fun getEncodedPassword(password: String): String {
        return URLEncoder.encode(password, "utf-8").replace("+", "%20")
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getProject(project: DBProject, lastModified: Long, lastETag: String?): ServerResponse.ProjectResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId
                return ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.ProjectResponse(
            requestServer(
                 target, METHOD_GET, null, null, lastETag,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemoteProject(
        project: DBProject, newName: String?, newEmail: String?, newPassword: String?,
        newMainCurrencyName: String?, newArchivedTs: Long? = null
    ): ServerResponse.EditRemoteProjectResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        if (newName != null) {
            paramKeys.add("name")
            paramValues.add(newName)
        }
        if (newEmail != null) {
            paramKeys.add("contact_email")
            paramValues.add(newEmail)
        }
        if (newPassword != null) {
            paramKeys.add("password")
            paramValues.add(newPassword)
        }
        if (newArchivedTs != null) {
            paramKeys.add("archived_ts")
            paramValues.add(newArchivedTs.toString())
        }

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (newMainCurrencyName != null) {
                paramKeys.add("currencyname")
                paramValues.add(newMainCurrencyName)
            }
            if (cospendVersionGT161) {
                paramKeys.clear()
                paramValues.clear()
                paramKeys.add("id")
                paramValues.add(project.remoteId)
                if (newName != null) {
                    paramKeys.add("name")
                    paramValues.add(newName)
                }
                if (newPassword != null) {
                    paramKeys.add("password")
                    paramValues.add(newPassword)
                }
                if (newEmail != null) {
                    paramKeys.add("contact_email")
                    paramValues.add(newEmail)
                }
                if (newMainCurrencyName != null) {
                    paramKeys.add("currencyName")
                    paramValues.add(newMainCurrencyName)
                }
                if (newArchivedTs != null) {
                    paramKeys.add("archived_ts")
                    paramValues.add(newArchivedTs.toString())
                }
            }
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                    ServerResponse.EditRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId
                    ServerResponse.EditRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.EditRemoteProjectResponse(
            requestServer(
                 target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemoteMember(project: DBProject, member: DBMember): ServerResponse.EditRemoteMemberResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(member.name)
        paramKeys.add("weight")
        paramValues.add(member.weight.toString())
        paramKeys.add("activated")
        paramValues.add(if (member.isActivated) "true" else "false")

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            val r = member.r
            val g = member.g
            val b = member.b
            if (r != null && g != null && b != null) {
                val hexColor = "#" + Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b)
                paramKeys.add("color")
                paramValues.add(hexColor)
            }
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/members/" + member.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/members/" + member.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/members/" + member.remoteId
                    ServerResponse.EditRemoteMemberResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/members/" + member.remoteId
                    ServerResponse.EditRemoteMemberResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members/" + member.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members/" + member.remoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/members/" + member.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.EditRemoteMemberResponse(
            requestServer(
                 target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemoteBill(
        project: DBProject,
        bill: DBBill,
        memberIdToRemoteId: Map<Long, Long>,
        categoryIdToRemoteId: Map<Long, Long>,
        paymentModeIdToRemoteId: Map<Long, Long>
    ): ServerResponse.EditRemoteBillResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("date")
        paramValues.add(bill.date)
        if (project.type == ProjectType.IHATEMONEY) {
            paramKeys.add("label")
        } else {
            paramKeys.add("what")
        }
        paramValues.add(bill.what)
        paramKeys.add("payer")
        paramValues.add(memberIdToRemoteId[bill.payerId].toString())
        paramKeys.add("amount")
        paramValues.add(SupportUtil.dotNumberFormat.format(bill.amount))

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            paramKeys.add("timestamp")
            paramValues.add(bill.timestamp.toString())
            paramKeys.add("comment")
            paramValues.add(bill.comment ?: "")
            paramKeys.add("repeat")
            paramValues.add(bill.repeat ?: "")

            if (cospendVersionGT161) {
                paramKeys.add("payedFor")
                paramKeys.add("paymentMode")
                paramKeys.add("categoryId")
                paramKeys.add("paymentModeId")
            } else {
                paramKeys.add("payed_for")
                paramKeys.add("paymentmode")
                paramKeys.add("categoryid")
                paramKeys.add("paymentmodeid")
            }
            var payedFor = ""
            for (boId in bill.billOwersIds) {
                payedFor += memberIdToRemoteId[boId].toString() + ","
            }
            payedFor = payedFor.replace(",$".toRegex(), "")
            paramValues.add(payedFor)
            paramValues.add(bill.paymentMode ?: "")
            paramValues.add((categoryIdToRemoteId[bill.categoryId] ?: 0L).toString())
            paramValues.add((paymentModeIdToRemoteId[bill.paymentModeId] ?: 0L).toString())

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/bills/" + bill.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/bills/" + bill.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/bills/" + bill.remoteId
                    Log.i(TAG, "using new API for editRemoteBill")
                    ServerResponse.EditRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/bills/" + bill.remoteId
                    ServerResponse.EditRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills/" + bill.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills/" + bill.remoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/bills/" + bill.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken

            val owerKey = if (project.type == ProjectType.IHATEMONEY) "owers" else "payed_for"
            for (boId in bill.billOwersIds) {
                paramKeys.add(owerKey)
                paramValues.add(memberIdToRemoteId[boId].toString())
            }
        }
        return ServerResponse.EditRemoteBillResponse(
            requestServer(
                 target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun deleteRemoteProject(project: DBProject): ServerResponse.DeleteRemoteProjectResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                    Log.i(TAG, "using new API for deleteRemoteProject")
                    ServerResponse.DeleteRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId
                    ServerResponse.DeleteRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.DeleteRemoteProjectResponse(
            requestServer(
                target, METHOD_DELETE, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun deleteRemoteBill(project: DBProject, billRemoteId: Long): ServerResponse.DeleteRemoteBillResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/bills/" + billRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/bills/" + billRemoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/bills/" + billRemoteId
                    Log.i(TAG, "using new API for deleteRemoteProject")
                    ServerResponse.DeleteRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/bills/" + billRemoteId
                    ServerResponse.DeleteRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills/" + billRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills/" + billRemoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/bills/" + billRemoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.DeleteRemoteBillResponse(
            requestServer(
                target, METHOD_DELETE, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, NextcloudHttpRequestFailedException::class)
    fun createAnonymousRemoteProject(project: DBProject): ServerResponse.CreateRemoteProjectResponse {
        val target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects"
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(project.name)
        paramKeys.add("contact_email")
        paramValues.add(project.email ?: "")
        paramKeys.add("password")
        paramValues.add(project.password)
        paramKeys.add("id")
        paramValues.add(project.remoteId)
        return ServerResponse.CreateRemoteProjectResponse(
            requestServer(
                 target, METHOD_POST, paramKeys, paramValues,
                null, null, null, null, false
            ), false
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createAuthenticatedRemoteProject(project: DBProject): ServerResponse.CreateRemoteProjectResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(project.name)
        paramKeys.add("contact_email")
        paramValues.add(project.email ?: "")
        paramKeys.add("password")
        paramValues.add(project.password)
        paramKeys.add("id")
        paramValues.add(project.remoteId)

        var target: String
        var username: String?
        var password: String?
        var useOcsApiRequest: Boolean
        if (ssoAccount != null) {
            return if (cospendVersionGT161) {
                target = "/ocs/v2.php/apps/cospend/api/v1/projects"
                Log.i(TAG, "using new API for createAuthenticatedRemoteProject")
                ServerResponse.CreateRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, true), true)
            } else {
                target = "/index.php/apps/cospend/api-priv/projects"
                ServerResponse.CreateRemoteProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, false), false)
            }
        } else {
            username = this.username
            password = this.password
            target = if (cospendVersionGT161)
                project.getRequestBaseUrl(true) + "/api/v1/projects"
            else
                project.getRequestBaseUrl(false) + "/api-priv/projects"
            useOcsApiRequest = cospendVersionGT161

            return ServerResponse.CreateRemoteProjectResponse(
                requestServer(
                    target, METHOD_POST, paramKeys, paramValues,
                    null, username, password, null, useOcsApiRequest
                ), useOcsApiRequest
            )
        }
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemoteBill(
        project: DBProject,
        bill: DBBill,
        memberIdToRemoteId: Map<Long, Long>,
        categoryIdToRemoteId: Map<Long, Long>,
        paymentModeIdToRemoteId: Map<Long, Long>
    ): ServerResponse.CreateRemoteBillResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("date")
        paramValues.add(bill.date)
        paramKeys.add("what")
        paramValues.add(bill.what)
        paramKeys.add("payer")
        paramValues.add(memberIdToRemoteId[bill.payerId].toString())
        paramKeys.add("amount")
        paramValues.add(SupportUtil.dotNumberFormat.format(bill.amount))

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            paramKeys.add("comment")
            paramValues.add(bill.comment ?: "")
            paramKeys.add("timestamp")
            paramValues.add(bill.timestamp.toString())
            paramKeys.add("repeat")
            paramValues.add(bill.repeat ?: "")
            if (cospendVersionGT161) {
                paramKeys.add("payedFor")
                paramKeys.add("paymentMode")
                paramKeys.add("categoryId")
                paramKeys.add("paymentModeId")
            } else {
                paramKeys.add("payed_for")
                paramKeys.add("paymentmode")
                paramKeys.add("categoryid")
                paramKeys.add("paymentmodeid")
            }
            var payedFor = ""
            for (boId in bill.billOwersIds) {
                payedFor += memberIdToRemoteId[boId].toString() + ","
            }
            payedFor = payedFor.replace(",$".toRegex(), "")
            paramValues.add(payedFor)
            paramValues.add(bill.paymentMode ?: "")
            paramValues.add((categoryIdToRemoteId[bill.categoryId] ?: 0L).toString())
            paramValues.add((paymentModeIdToRemoteId[bill.paymentModeId] ?: 0L).toString())

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/bills"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/bills"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/bills"
                    Log.i(TAG, "using new API for createRemoteBill")
                    ServerResponse.CreateRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/bills"
                    ServerResponse.CreateRemoteBillResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/bills"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken

            val owerKey = "payed_for"
            for (boId in bill.billOwersIds) {
                paramKeys.add(owerKey)
                paramValues.add(memberIdToRemoteId[boId].toString())
            }
        }

        return ServerResponse.CreateRemoteBillResponse(
            requestServer(
                target, METHOD_POST, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemoteMember(project: DBProject, member: DBMember): ServerResponse.CreateRemoteMemberResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(member.name)

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            val r = member.r
            val g = member.g
            val b = member.b
            if (r != null && g != null && b != null) {
                val hexColor = "#" + Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b)
                paramKeys.add("color")
                paramValues.add(hexColor)
            }
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/members"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/members"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/members"
                    ServerResponse.CreateRemoteMemberResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, true), isOcsResponse=true, isJsonMember=true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/members"
                    ServerResponse.CreateRemoteMemberResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, false), isOcsResponse=false, isJsonMember=false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/members"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.CreateRemoteMemberResponse(
            requestServer(
                 target, METHOD_POST, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest,
            ProjectType.COSPEND == project.type && cospendVersionGT161
        )
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getBills(
        project: DBProject,
        offset: Int? = null,
        limit: Int? = null,
        reverse: Boolean? = null,
        deleted: Int? = null
    ): ServerResponse.BillsResponse {
        var target: String
        var username: String?
        var password: String?
        var bearerToken: String?
        var useOcsApiRequest: Boolean

        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()

        if (ProjectType.COSPEND == project.type) {
            val tsLastSync = project.lastSyncedTimestamp
            if (offset == null) {
                if (cospendVersionGT161) {
                    paramKeys.add("lastChanged")
                } else {
                    paramKeys.add("lastchanged")
                }
                paramValues.add(tsLastSync.toString())
            } else {
                paramKeys.add("offset")
                paramValues.add(offset.toString())
                if (limit != null) {
                    paramKeys.add("limit")
                    paramValues.add(limit.toString())
                }
                if (reverse != null) {
                    paramKeys.add("reverse")
                    paramValues.add(reverse.toString())
                }
                if (deleted != null) {
                    paramKeys.add("deleted")
                    paramValues.add(deleted.toString())
                }
            }

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                useOcsApiRequest = cospendVersionGT161
                val baseUrl = project.getRequestBaseUrl(useOcsApiRequest)
                target = if (useOcsApiRequest)
                    "$baseUrl/api/v1/projects/${project.remoteId}/bills"
                else
                    "$baseUrl/api-priv/projects/${project.remoteId}/bills"

                if (paramKeys.isNotEmpty()) {
                    target += "?"
                    for (i in paramKeys.indices) {
                        if (i > 0) target += "&"
                        target += "${paramKeys[i]}=${paramValues[i]}"
                    }
                }

                return ServerResponse.BillsResponse(
                    requestServer(
                        target, METHOD_GET, null, null,
                        null, username, password, null, useOcsApiRequest
                    ),
                    useOcsApiRequest
                )
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/bills"
                    ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/bills"
                    ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                val baseUrl = project.getRequestBaseUrl(useOcsApiRequest)
                target = if (useOcsApiRequest)
                    "$baseUrl/api/v1/public/projects/${project.remoteId}/${getEncodedPassword(project.password)}/bills"
                else
                    "$baseUrl/apiv2/projects/${project.remoteId}/${getEncodedPassword(project.password)}/bills"

                if (paramKeys.isNotEmpty()) {
                    target += "?"
                    for (i in paramKeys.indices) {
                        if (i > 0) target += "&"
                        target += "${paramKeys[i]}=${paramValues[i]}"
                    }
                }

                return ServerResponse.BillsResponse(
                    requestServer(
                         target, METHOD_GET, null, null,
                        null, null, null, null, useOcsApiRequest
                    ),
                    useOcsApiRequest
                )
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/bills"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
            return ServerResponse.BillsResponse(
                requestServer(
                    target, METHOD_GET, null, null,
                    null, username, password, bearerToken, false
                ),
                false
            )
        }
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getCategories(project: DBProject): ServerResponse.CategoriesResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/categories"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/categories"
                return ServerResponse.CategoriesResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/categories"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/categories"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.CategoriesResponse(
            requestServer(
                target, METHOD_GET, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getPaymentModes(project: DBProject): ServerResponse.PaymentModesResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/paymentmodes"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/paymentmodes"
                return ServerResponse.PaymentModesResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password)
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmodes"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/paymentmodes"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.PaymentModesResponse(
            requestServer(
                target, METHOD_GET, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getMembers(project: DBProject): ServerResponse.MembersResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/members"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/members"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/members"
                    Log.i(TAG, "using new API for getMembers")
                    ServerResponse.MembersResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/members"
                    ServerResponse.MembersResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/members"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/members"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.MembersResponse(
            requestServer(
                target, METHOD_GET, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemoteCategory(project: DBProject, category: DBCategory): ServerResponse.CreateRemoteCategoryResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(category.name ?: "")
        paramKeys.add("icon")
        paramValues.add(category.icon)
        paramKeys.add("color")
        paramValues.add(category.color)

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/category"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/categories"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/category"
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/categories"
                return ServerResponse.CreateRemoteCategoryResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/category"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/categories"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/categories"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        val response = requestServer(
            target, METHOD_POST, paramKeys, paramValues, null,
            username, password, bearerToken, useOcsApiRequest
        )
        return ServerResponse.CreateRemoteCategoryResponse(response, useOcsApiRequest)
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemoteCategory(project: DBProject, category: DBCategory): ServerResponse.EditRemoteCategoryResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(category.name ?: "")
        paramKeys.add("icon")
        paramValues.add(category.icon)
        paramKeys.add("color")
        paramValues.add(category.color)

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/category/" + category.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/categories/" + category.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/category/" + category.remoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/categories/" + category.remoteId
                return ServerResponse.EditRemoteCategoryResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/category/" + category.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/categories/" + category.remoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/categories/" + category.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.EditRemoteCategoryResponse(
            requestServer(
                target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun deleteRemoteCategory(project: DBProject, categoryRemoteId: Long): ServerResponse.DeleteRemoteCategoryResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/category/" + categoryRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/categories/" + categoryRemoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/category/" + categoryRemoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/categories/" + categoryRemoteId
                return ServerResponse.DeleteRemoteCategoryResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/category/" + categoryRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/categories/" + categoryRemoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/categories/" + categoryRemoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.DeleteRemoteCategoryResponse(
            requestServer(
                target, METHOD_DELETE, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemotePaymentMode(project: DBProject, paymentMode: DBPaymentMode): ServerResponse.CreateRemotePaymentModeResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(paymentMode.name ?: "")
        paramKeys.add("icon")
        paramValues.add(paymentMode.icon)
        paramKeys.add("color")
        paramValues.add(paymentMode.color)

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/paymentmode"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/paymentmodes"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/paymentmode"
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/paymentmodes"
                return ServerResponse.CreateRemotePaymentModeResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmode"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmodes"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/paymentmodes"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.CreateRemotePaymentModeResponse(
            requestServer(
                target, METHOD_POST, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemotePaymentMode(project: DBProject, paymentMode: DBPaymentMode): ServerResponse.EditRemotePaymentModeResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(paymentMode.name ?: "")
        paramKeys.add("icon")
        paramValues.add(paymentMode.icon)
        paramKeys.add("color")
        paramValues.add(paymentMode.color)

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/paymentmode/" + paymentMode.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/paymentmodes/" + paymentMode.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/paymentmode/" + paymentMode.remoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/paymentmodes/" + paymentMode.remoteId
                return ServerResponse.EditRemotePaymentModeResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmode/" + paymentMode.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmodes/" + paymentMode.remoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/paymentmodes/" + paymentMode.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.EditRemotePaymentModeResponse(
            requestServer(
                target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun deleteRemotePaymentMode(project: DBProject, paymentModeRemoteId: Long): ServerResponse.DeleteRemotePaymentModeResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/paymentmode/" + paymentModeRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/paymentmodes/" + paymentModeRemoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                target = if (cospendVersionGT161)
                    "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/paymentmode/" + paymentModeRemoteId
                else
                    "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/paymentmodes/" + paymentModeRemoteId
                return ServerResponse.DeleteRemotePaymentModeResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_DELETE, null, null, cospendVersionGT161), cospendVersionGT161)
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmode/" + paymentModeRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/paymentmodes/" + paymentModeRemoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/paymentmodes/" + paymentModeRemoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }

        return ServerResponse.DeleteRemotePaymentModeResponse(
            requestServer(
                target, METHOD_DELETE, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemoteCurrency(project: DBProject, currency: DBCurrency): ServerResponse.CreateRemoteCurrencyResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(currency.name ?: "")
        paramKeys.add("rate")
        paramValues.add(currency.exchangeRate.toString())

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/currency"
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/currency"
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/currency"
                    Log.i(TAG, "using new API for createRemoteCurrency")
                    ServerResponse.CreateRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/currency"
                    ServerResponse.CreateRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_POST, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency"
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency"
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/currency"
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.CreateRemoteCurrencyResponse(
            requestServer(
                target, METHOD_POST, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun editRemoteCurrency(project: DBProject, currency: DBCurrency): ServerResponse.EditRemoteCurrencyResponse {
        val paramKeys: MutableList<String> = ArrayList()
        val paramValues: MutableList<String> = ArrayList()
        paramKeys.add("name")
        paramValues.add(currency.name ?: "")
        paramKeys.add("rate")
        paramValues.add(currency.exchangeRate.toString())

        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/currency/" + currency.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/currency/" + currency.remoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/currency/" + currency.remoteId
                    Log.i(TAG, "using new API for createRemoteCurrency")
                    ServerResponse.EditRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/currency/" + currency.remoteId
                    ServerResponse.EditRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency/" + currency.remoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency/" + currency.remoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/currency/" + currency.remoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.EditRemoteCurrencyResponse(
            requestServer(
                target, METHOD_PUT, paramKeys, paramValues, null,
                username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun deleteRemoteCurrency(project: DBProject, currencyRemoteId: Long): ServerResponse.DeleteRemoteCurrencyResponse {
        var target: String
        var username: String? = null
        var password: String? = null
        var bearerToken: String? = null
        var useOcsApiRequest = false
        if (ProjectType.COSPEND == project.type) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/currency/" + currencyRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/currency/" + currencyRemoteId
                useOcsApiRequest = cospendVersionGT161
            } else if (canAccessProjectWithSSO(project)) {
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/currency/" + currencyRemoteId
                    Log.i(TAG, "using new API for deleteRemoteCurrency")
                    ServerResponse.DeleteRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, null, null, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/currency/" + currencyRemoteId
                    ServerResponse.DeleteRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_PUT, null, null, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency/" + currencyRemoteId
                else
                    project.getRequestBaseUrl(false) + "/api/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/currency/" + currencyRemoteId
            }
        } else {
            target = project.serverUrl!!.replace("/+$".toRegex(), "") + "/api/projects/" + project.remoteId + "/currency/" + currencyRemoteId
            username = project.remoteId
            password = project.password
            bearerToken = project.bearerToken
        }
        return ServerResponse.DeleteRemoteCurrencyResponse(
            requestServer(
                target, METHOD_DELETE, null, null,
                null, username, password, bearerToken, useOcsApiRequest
            ), useOcsApiRequest
        )
    }

    @Throws(TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    private fun requestServerWithSSO(
        nextcloudAPI: NextcloudAPI, target: String, method: String,
        paramKeys: List<String>?, paramValues: List<String>?, isOCSRequest: Boolean
    ): ResponseData {
        var finalTarget = target
        if (finalTarget.contains("/ocs/v2.php") && !finalTarget.contains("format=json")) {
            finalTarget += if (finalTarget.contains("?")) "&format=json" else "?format=json"
        }
        val result = StringBuilder()
        var params: MutableList<QueryParam>? = null
        if (paramKeys != null && paramValues != null) {
            params = ArrayList()
            for (i in paramKeys.indices) {
                params.add(QueryParam(paramKeys[i], paramValues[i]))
            }
        }
        val headers: MutableMap<String, List<String>> = HashMap()
        if (isOCSRequest) {
            val acceptHeader: MutableList<String> = ArrayList()
            acceptHeader.add("application/json")
            headers["Accept"] = acceptHeader
        }
        val nextcloudRequest: NextcloudRequest = if (params == null) {
            NextcloudRequest.Builder()
                .setMethod(method)
                .setUrl(finalTarget)
                .setHeader(headers)
                .build()
        } else {
            NextcloudRequest.Builder()
                .setMethod(method)
                .setUrl(finalTarget)
                .setParameter(params)
                .setHeader(headers)
                .build()
        }
        try {
            val response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest)
            val inputStream = response.body
            val rd = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (rd.readLine().also { line = it } != null) {
                result.append(line)
            }
            Log.d(javaClass.simpleName, "RES versatile $result")
            inputStream.close()
        } catch (e: TokenMismatchException) {
            Log.d(javaClass.simpleName, "Mismatcho SSO server request error $e")
            throw e
        } catch (e: NextcloudHttpRequestFailedException) {
            Log.d(javaClass.simpleName, "SSO server HTTP request failed ${e.statusCode}")
            throw e
        } catch (e: Exception) {
            Log.d(javaClass.simpleName, "SSO server request error $e")
        }
        return ResponseData(result.toString(), "", 0)
    }

    @Throws(IOException::class, NextcloudHttpRequestFailedException::class)
    private fun requestServer(
        target: String, method: String,
        paramKeys: List<String>?, paramValues: List<String>?,
        lastETag: String?, username: String?, password: String?,
        bearerToken: String?, isOCSRequest: Boolean
    ): ResponseData {
        var finalTarget = target
        if (finalTarget.contains("/ocs/v2.php") && !finalTarget.contains("format=json")) {
            finalTarget += if (finalTarget.contains("?")) "&format=json" else "?format=json"
        }
        val result = StringBuilder()
        val httpCon = SupportUtil.getHttpURLConnection(finalTarget)
        httpCon.requestMethod = method
        if (bearerToken != null) {
            httpCon.setRequestProperty("Authorization", "Bearer $bearerToken")
        } else if (username != null) {
            httpCon.setRequestProperty(
                "Authorization",
                "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            )
        }
        httpCon.setRequestProperty("Connection", "Close")
        httpCon.setRequestProperty("User-Agent", "Cowspent-android/" + SupportUtil.getAppVersionName(context))
        if (lastETag != null && METHOD_GET == method) {
            httpCon.setRequestProperty("If-None-Match", lastETag)
        }
        if (isOCSRequest) {
            httpCon.setRequestProperty("OCS-APIRequest", "true")
            httpCon.setRequestProperty("Accept", "application/json")
        }
        httpCon.connectTimeout = 10 * 1000 // 10 seconds
        if (paramKeys != null && paramValues != null) {
            var dataString = ""
            for (i in paramKeys.indices) {
                val key = paramKeys[i]
                val value = paramValues[i]
                if (dataString.isNotEmpty()) {
                    dataString += "&"
                }
                dataString += URLEncoder.encode(key, "UTF-8") + "="
                dataString += URLEncoder.encode(value, "UTF-8")
            }
            val data = dataString.toByteArray()
            httpCon.setFixedLengthStreamingMode(data.size)
            httpCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            httpCon.setRequestProperty("Content-Length", data.size.toString())
            httpCon.doOutput = true
            val os = httpCon.outputStream
            os.write(data)
            os.flush()
            os.close()
        }
        val responseCode = httpCon.responseCode
        Log.d(javaClass.simpleName, "HTTP response code: $responseCode")
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw ServerResponse.NotModifiedException()
        }
        Log.d(TAG, "METHOD : $method")
        val rd: BufferedReader = if (responseCode in 200..399) {
            BufferedReader(InputStreamReader(httpCon.inputStream))
        } else {
            Log.e(TAG, "ERROR CODE : $responseCode")
            BufferedReader(InputStreamReader(httpCon.errorStream))
        }
        var line: String?
        while (rd.readLine().also { line = it } != null) {
            result.append(line)
        }
        if (responseCode >= 400) {
            throw NextcloudHttpRequestFailedException(context, responseCode, IOException(result.toString()))
        }
        val etag = httpCon.getHeaderField("ETag")
        val lastModified = httpCon.getHeaderFieldDate("Last-Modified", 0) / 1000
        Log.i(TAG, "Result length:  " + result.length + (if (paramKeys == null) "" else "; Request length: " + result.length))
        Log.d(TAG, "ETag: $etag; Last-Modified: $lastModified (${httpCon.getHeaderField("Last-Modified")})")
        return ResponseData(result.toString(), etag, lastModified)
    }

    companion object {
        private val TAG = VersatileProjectSyncClient::class.java.simpleName
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
        const val METHOD_PUT = "PUT"
        const val METHOD_DELETE = "DELETE"
    }
}
