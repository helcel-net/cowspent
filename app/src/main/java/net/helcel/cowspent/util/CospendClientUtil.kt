package net.helcel.cowspent.util

import android.util.Base64
import android.util.Log
import androidx.annotation.StringRes
import net.helcel.cowspent.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.SocketTimeoutException

object CospendClientUtil {

    enum class LoginStatus(@param:StringRes val str: Int) {
        OK(0),
        AUTH_FAILED(R.string.error_username_password_invalid),
        CONNECTION_FAILED(R.string.error_io),
        NO_NETWORK(R.string.error_no_network),
        JSON_FAILED(R.string.error_json),
        SERVER_FAILED(R.string.error_server),
        SSO_TOKEN_MISMATCH(R.string.error_token_mismatch),
        REQ_FAILED(R.string.error_req_failed)
    }

    fun isHttp(url: String?): Boolean {
        return url != null && url.length > 4 && url.startsWith("http") && url[4] != 's'
    }

    fun formatURL(urlParam: String): String {
        var url = urlParam
        if (!url.endsWith("/")) {
            url += "/"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val replacements = arrayOf("v0.2/", "api/", "apps/", "index.php/")
        for (replacement in replacements) {
            if (url.endsWith(replacement)) {
                url = url.substring(0, url.length - replacement.length)
            }
        }
        return url
    }

    fun isValidLogin(url: String, username: String, password: String): LoginStatus {
        return try {
            val targetURL = url + "index.php/apps/cospend/api/ping"
            val con = SupportUtil.getHttpURLConnection(targetURL)
            con.requestMethod = "GET"
            con.setRequestProperty(
                "Authorization",
                "Basic "
                        + String(
                    Base64.encode(
                        ("$username:$password").toByteArray(),
                        Base64.NO_WRAP
                    )
                )
            )
            con.connectTimeout = 10 * 1000 // 10 seconds
            con.connect()

            Log.v(CospendClientUtil::class.java.simpleName, "Establishing connection to server")
            when (con.responseCode) {
                200 -> {
                    Log.v(CospendClientUtil::class.java.simpleName, "" + con.responseMessage)
                    val result = StringBuilder()
                    val rd = BufferedReader(InputStreamReader(con.inputStream))
                    var line: String?
                    while ((rd.readLine().also { line = it }) != null) {
                        result.append(line)
                    }
                    Log.v(CospendClientUtil::class.java.simpleName, result.toString())
                    JSONArray(result.toString())
                    LoginStatus.OK
                }
                in 401..403 -> {
                    LoginStatus.AUTH_FAILED
                }
                else -> {
                    LoginStatus.SERVER_FAILED
                }
            }
        } catch (e: MalformedURLException) {
            Log.e(CospendClientUtil::class.java.simpleName, "Exception", e)
            LoginStatus.CONNECTION_FAILED
        } catch (e: SocketTimeoutException) {
            Log.e(CospendClientUtil::class.java.simpleName, "Exception", e)
            LoginStatus.CONNECTION_FAILED
        } catch (e: IOException) {
            Log.e(CospendClientUtil::class.java.simpleName, "Exception", e)
            LoginStatus.CONNECTION_FAILED
        } catch (e: JSONException) {
            Log.e(CospendClientUtil::class.java.simpleName, "Exception", e)
            LoginStatus.JSON_FAILED
        }
    }

    fun isValidURL(url: String): Boolean {
        val result = StringBuilder()
        return try {
            val con = SupportUtil.getHttpURLConnection( url + "status.php")
            con.requestMethod = VersatileProjectSyncClient.METHOD_GET
            con.connectTimeout = 10 * 1000 // 10 seconds
            val rd = BufferedReader(InputStreamReader(con.inputStream))
            var line: String?
            while ((rd.readLine().also { line = it }) != null) {
                result.append(line)
            }
            val response = JSONObject(result.toString())
            response.getBoolean("installed")
        } catch (_: Exception) {
            false
        }
    }
}
