package net.helcel.cowspent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.annotation.WorkerThread
import com.nextcloud.android.sso.QueryParam
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import net.helcel.cowspent.model.DBProject
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection

@WorkerThread
class NextcloudClient(
    private val url: String,
    private val username: String,
    private val password: String,
    private val nextcloudAPI: NextcloudAPI?,
    private val context: Context
) {

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getAccountProjects(useOcsApi: Boolean): ServerResponse.AccountProjectsResponse {
        val target = if (useOcsApi)
            "/ocs/v2.php/apps/cospend/api/v1/projects"
        else
            "/index.php/apps/cospend/getProjects"
        val method = if (useOcsApi) METHOD_GET else METHOD_POST
        return if (nextcloudAPI != null) {
            Log.d(javaClass.simpleName, "using SSO to get/sync account projects")
//            Log.d(javaClass.simpleName, "Sync projects target $target")
            ServerResponse.AccountProjectsResponse(
                requestServerWithSSO(nextcloudAPI, target, method, null, useOcsApi),
                useOcsApi
            )
        } else {
//            Log.d(javaClass.simpleName, "Sync projects target $target")
            ServerResponse.AccountProjectsResponse(
                requestServer(target, method, null, "", true, useOcsApi),
                useOcsApi
            )
        }
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getCapabilities(project: DBProject?): ServerResponse.CapabilitiesResponse {
        val target: String = if (project == null || url != "") {
            "/ocs/v2.php/cloud/capabilities"
        } else {
            val realServerUrl = project.serverUrl!!
                .replace("/apps/cospend", "")
                .replace("/index.php", "")
            "$realServerUrl/ocs/v2.php/cloud/capabilities"
        }
        return if (nextcloudAPI != null) {
            Log.d(javaClass.simpleName, "using SSO to get color")
            ServerResponse.CapabilitiesResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, true))
        } else {
            ServerResponse.CapabilitiesResponse(requestServer(target, METHOD_GET, null, null,
                needLogin = true,
                isOCSRequest = true
            ))
        }
    }

    @Throws(JSONException::class, IOException::class, TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    fun getAvatar(otherUserName: String?): ServerResponse.AvatarResponse {
        val targetUserName = otherUserName ?: username
        val target = "/index.php/avatar/$targetUserName/45"
        return if (nextcloudAPI != null) {
            Log.d(javaClass.simpleName, "using SSO to get avatar")
            ServerResponse.AvatarResponse(imageRequestServerWithSSO(nextcloudAPI, target, METHOD_GET, null))
        } else {
            ServerResponse.AvatarResponse(imageRequestServer(target, METHOD_GET, null, null,
                needLogin = true,
                isOCSRequest = false
            ))
        }
    }

    @Throws(TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    private fun requestServerWithSSO(
        nextcloudAPI: NextcloudAPI,
        target: String,
        method: String,
        params: Collection<QueryParam>?,
        isOCSRequest: Boolean
    ): VersatileProjectSyncClient.ResponseData {
        val result = StringBuilder()
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
            Log.d(javaClass.simpleName, "RES $result")
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
        return VersatileProjectSyncClient.ResponseData(result.toString(), "", 0)
    }

    @Throws(TokenMismatchException::class, NextcloudHttpRequestFailedException::class)
    private fun imageRequestServerWithSSO(
        nextcloudAPI: NextcloudAPI,
        target: String,
        method: String,
        params: Collection<QueryParam>?
    ): VersatileProjectSyncClient.ResponseData {
        var strBase64 = ""
        val nextcloudRequest: NextcloudRequest = if (params == null) {
            NextcloudRequest.Builder()
                .setMethod(method)
                .setUrl(target)
                .build()
        } else {
            NextcloudRequest.Builder()
                .setMethod(method)
                .setUrl(target)
                .setParameter(params)
                .build()
        }
        try {
            val response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest)
            val inputStream = response.body
            val selectedImage = BitmapFactory.decodeStream(inputStream)
            val stream = ByteArrayOutputStream()
            selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()
            strBase64 = Base64.encodeToString(byteArray, 0)
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
        return VersatileProjectSyncClient.ResponseData(strBase64, "", 0)
    }

    @Throws(IOException::class, NextcloudHttpRequestFailedException::class)
    private fun requestServer(
        target: String,
        method: String, params: JSONObject?, lastETag: String?, needLogin: Boolean, isOCSRequest: Boolean
    ): VersatileProjectSyncClient.ResponseData {
        val result = StringBuilder()
        val targetURL = url + target.replace("^/".toRegex(), "")
//        Log.d(javaClass.simpleName, "method and target URL: $method $targetURL")
        val httpCon = SupportUtil.getHttpURLConnection(targetURL)
        httpCon.requestMethod = method
        if (needLogin) {
            httpCon.setRequestProperty(
                "Authorization",
                "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            )
        }
        httpCon.setRequestProperty("Connection", "Close")
        httpCon.setRequestProperty("User-Agent", "cowspent-android/" + SupportUtil.getAppVersionName(context))
        if (lastETag != null && METHOD_GET == method) {
            httpCon.setRequestProperty("If-None-Match", lastETag)
        }
        if (isOCSRequest) {
            httpCon.setRequestProperty("OCS-APIRequest", "true")
            httpCon.setRequestProperty("Accept", "application/json")
        }
        httpCon.connectTimeout = 10 * 1000 // 10 seconds
        var paramData: ByteArray? = null
        if (params != null) {
            paramData = params.toString().toByteArray()
//            Log.d(javaClass.simpleName, "Params: $params")
            httpCon.setFixedLengthStreamingMode(paramData.size)
            httpCon.setRequestProperty("Content-Type", application_json)
            httpCon.doOutput = true
            val os = httpCon.outputStream
            os.write(paramData)
            os.flush()
            os.close()
        }
        val responseCode = httpCon.responseCode
        Log.d(javaClass.simpleName, "HTTP response code: $responseCode")
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw ServerResponse.NotModifiedException()
        }
        if (responseCode >= 400) {
            throw NextcloudHttpRequestFailedException(context, responseCode, IOException(""))
        }
        Log.i(TAG, "METHOD : $method")
        val rd = BufferedReader(InputStreamReader(httpCon.inputStream))
        var line: String?
        while (rd.readLine().also { line = it } != null) {
            result.append(line)
        }
        val etag = httpCon.getHeaderField("ETag")
        val lastModified = httpCon.getHeaderFieldDate("Last-Modified", 0) / 1000
        Log.i(
            javaClass.simpleName,
            "Result length:  " + result.length + (if (paramData == null) "" else "; Request length: " + paramData.size)
        )
        Log.d(javaClass.simpleName, "ETag: $etag; Last-Modified: $lastModified (${httpCon.getHeaderField("Last-Modified")})")
        return VersatileProjectSyncClient.ResponseData(result.toString(), "", 0)
    }

    @Throws(IOException::class, NextcloudHttpRequestFailedException::class)
    private fun imageRequestServer(
        target: String,
        method: String, params: JSONObject?, lastETag: String?, needLogin: Boolean, isOCSRequest: Boolean
    ): VersatileProjectSyncClient.ResponseData {
        var strBase64: String
        val targetURL = url + target.replace("^/".toRegex(), "")
        val httpCon = SupportUtil.getHttpURLConnection( targetURL)
        httpCon.requestMethod = method
        if (needLogin) {
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
        }
        httpCon.connectTimeout = 10 * 1000 // 10 seconds
//        Log.d(javaClass.simpleName, "$method $targetURL")
        var paramData: ByteArray?
        if (params != null) {
            paramData = params.toString().toByteArray()
//            Log.d(javaClass.simpleName, "Params: $params")
            httpCon.setFixedLengthStreamingMode(paramData.size)
            httpCon.setRequestProperty("Content-Type", application_json)
            httpCon.doOutput = true
            val os = httpCon.outputStream
            os.write(paramData)
            os.flush()
            os.close()
        }
        val responseCode = httpCon.responseCode
        Log.d(javaClass.simpleName, "HTTP response code: $responseCode")
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw ServerResponse.NotModifiedException()
        }
        if (responseCode >= 400) {
            throw NextcloudHttpRequestFailedException(context, responseCode, IOException(""))
        }
        Log.i(TAG, "METHOD : $method")
        val selectedImage = BitmapFactory.decodeStream(httpCon.inputStream)
        val stream = ByteArrayOutputStream()
        selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        strBase64 = Base64.encodeToString(byteArray, 0)
        return VersatileProjectSyncClient.ResponseData(strBase64, "", 0)
    }

    companion object {
        private val TAG = NextcloudClient::class.java.simpleName
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
        private const val application_json = "application/json"
    }
}
