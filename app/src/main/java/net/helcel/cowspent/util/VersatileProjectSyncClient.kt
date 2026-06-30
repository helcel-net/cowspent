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
import net.helcel.cowspent.model.DBCurrency
import net.helcel.cowspent.model.DBMember
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
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId
                    ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId
                    ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, null, null, false), false)
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
        newMainCurrencyName: String?
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
                if (newMainCurrencyName != null) {
                    paramKeys.add("currencyName")
                    paramValues.add(newMainCurrencyName)
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
    fun editRemoteBill(project: DBProject, bill: DBBill, memberIdToRemoteId: Map<Long, Long>): ServerResponse.EditRemoteBillResponse {
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
            paramValues.add(bill.categoryRemoteId.toString())
            paramValues.add(bill.paymentModeRemoteId.toString())

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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for editRemoteBill")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for deleteRemoteProject")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for deleteRemoteProject")
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
            if (cospendVersionGT161) {
                    }
            return ServerResponse.CreateRemoteProjectResponse(
                requestServer(
                    target, METHOD_POST, paramKeys, paramValues,
                    null, username, password, null, useOcsApiRequest
                ), useOcsApiRequest
            )
        }
    }

    @Throws(IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun createRemoteBill(project: DBProject, bill: DBBill, memberIdToRemoteId: Map<Long, Long>): ServerResponse.CreateRemoteBillResponse {
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
            paramValues.add(bill.categoryRemoteId.toString())
            paramValues.add(bill.paymentModeRemoteId.toString())

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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for createRemoteBill")
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
                    Log.i(TAG, "using new API for createRemoteBill")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for createRemoteBill")
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
    fun getBills(project: DBProject): ServerResponse.BillsResponse {
        var target: String
        var username: String?
        var password: String?
        var bearerToken: String?
        var useOcsApiRequest: Boolean
        if (ProjectType.COSPEND == project.type) {
            val tsLastSync = project.lastSyncedTimestamp
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username
                password = this.password
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.remoteId + "/bills?lastChanged=" + tsLastSync
                else
                    project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.remoteId + "/bills?lastchanged=" + tsLastSync
                useOcsApiRequest = cospendVersionGT161
                if (cospendVersionGT161) {
                            }
                return ServerResponse.BillsResponse(
                    requestServer(
                        target, METHOD_GET, null, null,
                        null, username, password, null, useOcsApiRequest
                    ),
                    useOcsApiRequest
                )
            } else if (canAccessProjectWithSSO(project)) {
                val paramKeys: MutableList<String> = ArrayList()
                val paramValues: MutableList<String> = ArrayList()
                if (cospendVersionGT161) {
                    paramKeys.add("lastChanged")
                } else {
                    paramKeys.add("lastchanged")
                }
                paramValues.add(tsLastSync.toString())
                return if (cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.remoteId + "/bills"
                    Log.i(TAG, "using new API for getBills")
                    ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, paramKeys, paramValues, true), true)
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.remoteId + "/bills"
                    ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI!!, target, METHOD_GET, paramKeys, paramValues, false), false)
                }
            } else {
                useOcsApiRequest = cospendVersionGT161
                target = if (cospendVersionGT161)
                    project.getRequestBaseUrl(true) + "/api/v1/public/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills?lastChanged=" + tsLastSync
                else
                    project.getRequestBaseUrl(false) + "/apiv2/projects/" + project.remoteId + "/" + getEncodedPassword(project.password) + "/bills?lastchanged=" + tsLastSync
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for getBills")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for getMembers")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for createRemoteCurrency")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for createRemoteCurrency")
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
                Log.i(TAG, "using public API, target is: ${SupportUtil.maskUrl(target)} for deleteRemoteCurrency")
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
                .setUrl(target)
                .setHeader(headers)
                .build()
        } else {
            NextcloudRequest.Builder()
                .setMethod(method)
                .setUrl(target)
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
        val result = StringBuilder()
        val httpCon = SupportUtil.getHttpURLConnection(target)
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
        httpCon.setRequestProperty("User-Agent", "Cowspent/" + SupportUtil.getAppVersionName(context))
        if (lastETag != null && METHOD_GET == method) {
            httpCon.setRequestProperty("If-None-Match", lastETag)
        }
        if (isOCSRequest) {
            httpCon.setRequestProperty("OCS-APIRequest", "true")
            httpCon.setRequestProperty("Accept", "application/json")
        }
        httpCon.connectTimeout = 10 * 1000 // 10 seconds
        Log.d(javaClass.simpleName, "$method ${SupportUtil.maskUrl(target)}")
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
            Log.d(javaClass.simpleName, "Params: ${SupportUtil.maskParams(dataString)}")
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
