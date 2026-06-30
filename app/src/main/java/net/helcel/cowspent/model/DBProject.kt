package net.helcel.cowspent.model

import java.io.Serializable

class DBProject(
    var id: Long,
    var remoteId: String,
    var password: String?,
    var name: String,
    var serverUrl: String?,
    var email: String?,
    var lastPayerId: Long?,
    var type: ProjectType,
    var lastSyncedTimestamp: Long?,
    var currencyName: String?,
    var isDeletionDisabled: Boolean,
    var myAccessLevel: Int,
    var bearerToken: String?,
    var archivedTs: Long? = null,
    var latestBillTs: Long = 0L
) : Serializable {

    val isArchived: Boolean
        get() = archivedTs != null && archivedTs!! > 0

    val isLocal: Boolean
        get() = ProjectType.LOCAL == type

    fun getRequestBaseUrl(isOcsRequest: Boolean): String {
        val url = serverUrl ?: ""
        return if (!isOcsRequest) {
            url.replace("/+$".toRegex(), "")
        } else {
            url.replace("/+$".toRegex(), "")
                .replace("/index.php/apps/cospend", "/ocs/v2.php/apps/cospend")
        }
    }

    override fun toString(): String {
        return "#DBProject$id/$remoteId,$name, $serverUrl, $email"
    }

    fun isShareable(): Boolean {
        return !serverUrl.isNullOrEmpty()
    }

    fun getShareUrl(): String {
        val url = serverUrl ?: ""
        val strippedUrl = url
            .replace("https://", "")
            .replace("http://", "")
            .replace("/index.php/apps/cospend", "")

        val protocol = if (type == ProjectType.IHATEMONEY) "ihatemoney" else "cospend"
        return "$protocol://$strippedUrl/$remoteId/$password"
    }

    fun getPublicWebUrl(): String {
        val url = serverUrl ?: ""
        return if (url.contains("index.php/apps/cospend")) {
            "$url/loginproject/$remoteId"
        } else {
            "$url/$remoteId"
        }
    }

    companion object {
        const val ACCESS_LEVEL_UNKNOWN = -1
        const val ACCESS_LEVEL_NONE = 0
        const val ACCESS_LEVEL_VIEWER = 1
        const val ACCESS_LEVEL_PARTICIPANT = 2
        const val ACCESS_LEVEL_MAINTAINER = 3
        const val ACCESS_LEVEL_ADMIN = 4
    }
}
