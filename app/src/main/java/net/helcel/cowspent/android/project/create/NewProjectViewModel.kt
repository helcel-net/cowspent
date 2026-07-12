package net.helcel.cowspent.android.project.create

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.model.DBAccountProject
import net.helcel.cowspent.model.ProjectType

class NewProjectViewModel : ViewModel() {
    var whatTodoIsCreate by mutableStateOf(false)
    
    private var _projectType by mutableStateOf(ProjectType.COSPEND)
    var projectType: ProjectType
        get() = _projectType
        set(value) {
            _projectType = value
            if (value == ProjectType.IHATEMONEY && (projectUrl.isEmpty() || projectUrl == defaultNcUrl)) {
                projectUrl = "https://ihatemoney.org"
            } else if (value == ProjectType.COSPEND && (projectUrl == "https://ihatemoney.org" || projectUrl.isEmpty())) {
                projectUrl = defaultNcUrl
            }
        }

    var projectUrl by mutableStateOf("")
    var defaultNcUrl by mutableStateOf("")
    var projectId by mutableStateOf("")
    var projectPassword by mutableStateOf("")
    var projectName by mutableStateOf("")
    var projectEmail by mutableStateOf("")

    var isAuthenticatedAccount by mutableStateOf(false)

    var showAuthWarningDialog by mutableStateOf(false)
    var showNextcloudProjectDialog by mutableStateOf(false)
    var nextcloudProjects by mutableStateOf<List<DBAccountProject>>(emptyList())

    var isCreatingRemoteProject by mutableStateOf(false)
    var errorDialogMessage by mutableStateOf<String?>(null)

    fun isFormValid(): Boolean {
        if (whatTodoIsCreate) {
            if (projectType == ProjectType.LOCAL) {
                return projectId.isNotEmpty()
            } else {
                if (projectUrl.isEmpty()) return false
                if (projectName.isEmpty()) return false

                if (projectType == ProjectType.COSPEND && isAuthenticatedAccount) {
                    return true
                }

                if (projectId.isEmpty()) return false
                if (projectType == ProjectType.IHATEMONEY) {
                    if (projectEmail.isEmpty()) return false
                    if (projectPassword.isEmpty()) return false
                } else {
                    if (projectEmail.isEmpty()) return false
                }
            }
        } else {
            // Join
            if (projectType == ProjectType.LOCAL) return false
            if (projectId.isEmpty() && !isCospendSchemeLink(projectUrl)) return false
            if (projectUrl.isEmpty() && !isCospendSchemeLink(projectUrl)) return false
            if (projectPassword.isEmpty() && !isCospendSchemeLink(projectUrl)) return false
        }
        return true
    }

    private fun isCospendSchemeLink(url: String): Boolean {
        return url.startsWith("cospend://") || url.startsWith("cospend+http://") ||
                url.startsWith("cowspent://") || url.startsWith("cowspent+http://") ||
                url.startsWith("ihatemoney://") || url.startsWith("ihatemoney+http://")
    }

    fun updateFromUri(data: Uri) {
        if ((data.scheme == "cospend" || data.scheme == "cospend+http" ||
                    data.scheme == "cowspent" || data.scheme == "cowspent+http" ||
                    data.scheme == "ihatemoney" || data.scheme == "ihatemoney+http")
            && data.pathSegments.isNotEmpty()
        ) {
            val password: String
            val pid: String
            if (data.path!!.endsWith("/")) {
                password = ""
                pid = data.lastPathSegment!!
            } else {
                password = data.lastPathSegment!!
                pid = data.pathSegments[data.pathSegments.size - 2]
            }
            var protocol = "https"
            if (data.scheme == "cospend+http" || data.scheme == "cowspent+http" || data.scheme == "ihatemoney+http") {
                protocol = "http"
            }
            val url = protocol + "://" + data.host
            val port = if (data.port != -1) ":${data.port}" else ""
            val fullUrl = "$url$port${data.path!!.replace(("/$pid/$password$").toRegex(), "")}"

            projectPassword = password
            projectId = pid
            projectUrl = fullUrl
            projectType = if (data.scheme?.startsWith("ihatemoney") == true) ProjectType.IHATEMONEY else ProjectType.COSPEND
            whatTodoIsCreate = false
        }
    }
}
