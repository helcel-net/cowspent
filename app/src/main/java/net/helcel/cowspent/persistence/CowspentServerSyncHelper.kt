package net.helcel.cowspent.persistence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.account.AccountActivity
import net.helcel.cowspent.android.main.BillsListViewActivity
import net.helcel.cowspent.android.main.MainConstants
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.util.CospendClientUtil.LoginStatus
import net.helcel.cowspent.util.ICallback
import net.helcel.cowspent.util.IProjectCreationCallback
import net.helcel.cowspent.util.NextcloudClient
import net.helcel.cowspent.util.ServerResponse
import net.helcel.cowspent.util.SupportUtil
import net.helcel.cowspent.util.VersatileProjectSyncClient
import org.json.JSONException
import java.io.IOException

@Suppress("DEPRECATION")
class CowspentServerSyncHelper private constructor(private val dbHelper: CowspentSQLiteOpenHelper) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appContext: Context = dbHelper.context.applicationContext
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(dbHelper.context)
    private var networkConnected = false

    private val certService = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            if (isSyncPossible) {
                val lastId = PreferenceManager.getDefaultSharedPreferences(dbHelper.context).getLong("selected_project", 0)
                if (lastId != 0L) {
                    val proj = dbHelper.getProject(lastId)
                    if (proj != null) {
                        appContext.sendBroadcast(Intent(MainConstants.BROADCAST_SYNC_PROJECT))
                        appContext.sendBroadcast(Intent(MainConstants.BROADCAST_NETWORK_AVAILABLE))
                    }
                }
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }

    private var syncActive = false
    private var syncAccountProjectsActive = false

    private var callbacksPush: MutableList<ICallback> = ArrayList()
    private var callbacksPull: MutableList<ICallback> = ArrayList()

    init {

        updateNetworkStatus()
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        appContext.unbindService(certService)
    }

    val isSyncPossible: Boolean
        get() {
            updateNetworkStatus()
            return networkConnected
        }

    fun addCallbackPull(callback: ICallback) {
        callbacksPull.add(callback)
    }

    fun scheduleSync(onlyLocalChanges: Boolean, projId: Long): SyncTask? {
        Log.d(TAG, "Sync requested (${if (onlyLocalChanges) "onlyLocalChanges" else "full"}; ${if (syncActive) "sync active" else "sync NOT active"}) ...")
        updateNetworkStatus()
        if (isSyncPossible && (!syncActive || onlyLocalChanges)) {
            val project = dbHelper.getProject(projId)
            if (project != null) {
                Log.d(TAG, "... starting now")
                val syncTask = SyncTask(onlyLocalChanges, project)
                syncTask.addCallbacks(callbacksPush)
                callbacksPush = ArrayList()
                if (!onlyLocalChanges) {
                    syncTask.addCallbacks(callbacksPull)
                    callbacksPull = ArrayList()
                }
                return syncTask.execute()
            } else {
                Log.d(TAG, "sync asked for project $projId which does not exist : DOING NOTHING")
            }
        } else if (!onlyLocalChanges) {
            Log.d(TAG, "... scheduled")
            projectIdsToSync.add(projId)
            for (callback in callbacksPush) {
                callback.onScheduled()
            }
        } else {
            Log.d(TAG, "... do nothing")
            for (callback in callbacksPush) {
                callback.onScheduled()
            }
        }
        return null
    }

    private fun updateNetworkStatus() {
        val connMgr = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeInfo = connMgr.activeNetworkInfo
        if (activeInfo != null && activeInfo.isConnected) {
            Log.d(TAG, "Network connection established.")
            networkConnected = true
        } else {
            networkConnected = false
            Log.d(TAG, "No network connection.")
        }
    }

    inner class SyncTask(private val onlyLocalChanges: Boolean, private val project: DBProject) {
        private val callbacks: MutableList<ICallback> = ArrayList()
        private var nextcloudClient: NextcloudClient? = null
        private var client: VersatileProjectSyncClient? = null
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()
        private var nbPulledNewBills = 0
        private var nbPulledUpdatedBills = 0
        private var nbPulledDeletedBills = 0
        private var newBillsDialogText = ""
        private var updatedBillsDialogText = ""
        private var deletedBillsDialogText = ""

        private var deferred: Deferred<LoginStatus>? = null

        init {
            Log.i(TAG, "SYNC TASK project : ${project.remoteId}")
        }

        fun addCallbacks(callbacks: List<ICallback>) {
            this.callbacks.addAll(callbacks)
        }

        fun execute(): SyncTask {
            deferred = scope.async {
                syncActive = true
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
                syncActive = false
                status
            }
            return this
        }

        private fun doWork(): LoginStatus {
            var version: String? = null
            if (project.type == ProjectType.COSPEND) {
                nextcloudClient = createNextcloudClient()
                if (nextcloudClient != null) {
                    try {
                        val response = nextcloudClient!!.getCapabilities(project)
                        version = response.cospendVersion
                    } catch (e: Exception) {
                        Log.i(TAG, "Failed to get cospend version when syncing: $e")
                    }
                } else if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
                    return LoginStatus.SSO_TOKEN_MISMATCH
                }
            }

            Log.i(TAG, "Syncing, cospend version is: $version")

            client = createVersatileProjectSyncClient(version)
            if (client == null) {
                return LoginStatus.CONNECTION_FAILED
            }
            Log.i(TAG, "STARTING SYNCHRONIZATION with Cospend version($version)")
            var status = pushLocalChanges()
            if (status == LoginStatus.OK) {
                status = pullRemoteChanges()
            }
            Log.i(TAG, "SYNCHRONIZATION FINISHED")
            return status
        }

        fun get(): LoginStatus = runBlocking {
            deferred?.await() ?: LoginStatus.CONNECTION_FAILED
        }

        private fun pushLocalChanges(): LoginStatus {
            Log.d(TAG, "PUSH LOCAL CHANGES")

            return try {
                val membersResponse = client!!.getMembers(project)
                val remoteMembers = membersResponse.getMembers(project.id)
                val remoteMembersNames = remoteMembers.map { it.name }

                val membersToAdd = dbHelper.getMembersOfProjectWithState(project.id, DBBill.STATE_ADDED)
                for (mToAdd in membersToAdd) {
                    val searchIndex = remoteMembersNames.indexOf(mToAdd.name)
                    if (searchIndex != -1) {
                        val remoteMember = remoteMembers[searchIndex]
                        dbHelper.updateMember(
                            mToAdd.id, null,
                            remoteMember.weight, remoteMember.isActivated,
                            DBBill.STATE_OK, remoteMember.remoteId, remoteMember.r,
                            remoteMember.g, remoteMember.b,
                            remoteMember.ncUserId, ""
                        )
                    } else {
                        val createRemoteMemberResponse = client!!.createRemoteMember(project, mToAdd)
                        val newRemoteId = createRemoteMemberResponse.remoteMemberId
                        if (newRemoteId > 0) {
                            dbHelper.updateMember(
                                mToAdd.id, null,
                                null, null, DBBill.STATE_OK, newRemoteId,
                                null, null, null, null, null
                            )
                        }
                    }
                }

                val membersToEdit = dbHelper.getMembersOfProjectWithState(project.id, DBBill.STATE_EDITED)
                for (mToEdit in membersToEdit) {
                    try {
                        val editRemoteMemberResponse = client!!.editRemoteMember(project, mToEdit)
                        val remoteId = editRemoteMemberResponse.getRemoteId(project.id)
                        if (remoteId == mToEdit.remoteId) {
                            dbHelper.updateMember(
                                mToEdit.id, null,
                                null, null, DBBill.STATE_OK, null,
                                null, null, null, null, null
                            )
                        }
                    } catch (e: IOException) {
                        if (e.message == "{\"message\": \"Internal Server Error\"}") {
                            Log.d(TAG, "EDIT MEMBER FAILED : it does not exist remotely")
                        } else {
                            throw e
                        }
                    }
                }

                val members = dbHelper.getMembersOfProject(project.id, null)
                val memberIdToRemoteId = members.associate { it.id to it.remoteId }
                val categories = dbHelper.getCategories(project.id)
                val categoryIdToRemoteId = categories.associate { it.id to it.remoteId }
                val paymentModes = dbHelper.getPaymentModes(project.id)
                val paymentModeIdToRemoteId = paymentModes.associate { it.id to it.remoteId }

                val toDelete = dbHelper.getBillsOfProjectWithState(project.id, DBBill.STATE_DELETED)
                for (bToDel in toDelete) {
                    try {
                        val deleteRemoteBillResponse = client!!.deleteRemoteBill(project, bToDel.remoteId)
                        if (deleteRemoteBillResponse.stringContent == "OK") {
                            Log.d(TAG, "successfully deleted bill on remote project : delete it locally")
                            dbHelper.deleteBill(bToDel.id)
                        }
                    } catch (e: IOException) {
                        if (e.message == "\"Not Found\"") {
                            Log.d(TAG, "failed to delete bill on remote project : delete it locally anyway")
                            dbHelper.deleteBill(bToDel.id)
                        } else {
                            throw e
                        }
                    } catch (e: NextcloudHttpRequestFailedException) {
                        if (e.statusCode == 404) {
                            Log.d(TAG, "failed to delete bill on remote project : delete it locally anyway")
                            dbHelper.deleteBill(bToDel.id)
                        } else {
                            throw e
                        }
                    }
                }

                val toEdit = dbHelper.getBillsOfProjectWithState(project.id, DBBill.STATE_EDITED)
                for (bToEdit in toEdit) {
                    try {
                        val editRemoteBillResponse = client!!.editRemoteBill(project, bToEdit, memberIdToRemoteId, categoryIdToRemoteId, paymentModeIdToRemoteId)
                        if (editRemoteBillResponse.stringContent == bToEdit.remoteId.toString()) {
                            dbHelper.setBillState(bToEdit.id, DBBill.STATE_OK)
                            Log.d(TAG, "SUCCESSFUL remote bill edition (${editRemoteBillResponse.stringContent})")
                        } else {
                            Log.d(TAG, "FAILED to edit remote bill (${editRemoteBillResponse.stringContent})")
                        }
                    } catch (_: Exception) {
                        Log.d(TAG, "FAILED to edit remote bill: it probably does not exist remotely")
                    }
                }

                val toAdd = dbHelper.getBillsOfProjectWithState(project.id, DBBill.STATE_ADDED)
                for (bToAdd in toAdd) {
                    val createRemoteBillResponse = client!!.createRemoteBill(project, bToAdd, memberIdToRemoteId, categoryIdToRemoteId, paymentModeIdToRemoteId)
                    val newRemoteId = createRemoteBillResponse.stringContent.toLong()
                    if (newRemoteId > 0) {
                        dbHelper.updateBill(
                            bToAdd.id, newRemoteId, null,
                            null, null, null,
                            DBBill.STATE_OK, null,
                            null, null,
                            null, null
                        )
                    }
                }

                if (project.type == ProjectType.COSPEND) {
                    val currenciesToDelete = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_DELETED)
                    for (cToDel in currenciesToDelete) {
                        try {
                            val deleteRemoteCurrencyResponse = client!!.deleteRemoteCurrency(project, cToDel.remoteId)
                            if (deleteRemoteCurrencyResponse.stringContent == "OK") {
                                Log.d(TAG, "successfully deleted currency on remote project : delete it locally")
                                dbHelper.deleteCurrency(cToDel.id)
                            }
                        } catch (e: IOException) {
                            if (e.message == "\"Not Found\"") {
                                Log.d(TAG, "failed to delete currency on remote project : delete it locally anyway")
                                dbHelper.deleteCurrency(cToDel.id)
                            } else {
                                throw e
                            }
                        }
                    }
                } else {
                    val currenciesToDelete = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_DELETED)
                    for (cToDel in currenciesToDelete) {
                        dbHelper.deleteCurrency(cToDel.id)
                    }
                }

                if (project.type == ProjectType.COSPEND) {
                    val currenciesToEdit = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_EDITED)
                    for (cToEdit in currenciesToEdit) {
                        try {
                            val editRemoteCurrencyResponse = client!!.editRemoteCurrency(project, cToEdit)
                            if (editRemoteCurrencyResponse.stringContent == cToEdit.remoteId.toString()) {
                                dbHelper.setCurrencyState(cToEdit.id, DBBill.STATE_OK)
                                Log.d(TAG, "SUCCESSFUL remote currency edition (${editRemoteCurrencyResponse.stringContent})")
                            } else {
                                Log.d(TAG, "FAILED to edit remote currency (${editRemoteCurrencyResponse.stringContent})")
                            }
                        } catch (e: IOException) {
                            if (e.message == "{\"message\": \"Internal Server Error\"}") {
                                Log.d(TAG, "FAILED to edit remote currency : it does not exist remotely")
                            } else {
                                throw e
                            }
                        }
                    }
                } else {
                    val currenciesToEdit = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_EDITED)
                    for (cToEdit in currenciesToEdit) {
                        dbHelper.setCurrencyState(cToEdit.id, DBBill.STATE_OK)
                    }
                }

                if (project.type == ProjectType.COSPEND) {
                    val currencyToAdd = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_ADDED)
                    for (cToAdd in currencyToAdd) {
                        val createRemoteCurrencyResponse = client!!.createRemoteCurrency(project, cToAdd)
                        val newRemoteId = createRemoteCurrencyResponse.stringContent.toLong()
                        if (newRemoteId > 0) {
                            dbHelper.setCurrencyState(cToAdd.id, DBBill.STATE_OK)
                        }
                    }
                } else {
                    val currencyToAdd = dbHelper.getCurrenciesOfProjectWithState(project.id, DBBill.STATE_ADDED)
                    for (cToAdd in currencyToAdd) {
                        dbHelper.setCurrencyState(cToAdd.id, DBBill.STATE_OK)
                    }
                }

                LoginStatus.OK
            } catch (_: ServerResponse.NotModifiedException) {
                Log.d(TAG, "No changes, nothing to do.")
                LoginStatus.OK
            } catch (e: IOException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                Log.e(TAG, "Catch MISMATCHTOKEN", e)
                LoginStatus.SSO_TOKEN_MISMATCH
            } catch (e: NextcloudHttpRequestFailedException) {
                Log.e(TAG, "Catch SSO HTTP req FAILED", e)
                errorMessages.add(getErrorMessageFromException(e))
                e.cause?.let { exceptions.add(it) }
                LoginStatus.REQ_FAILED
            }
        }

        private fun pullRemoteChanges(): LoginStatus {
            Log.d(TAG, "pullRemoteChanges($project)")
            val lastETag: String? = null
            val lastModified: Long = 0
            return try {
                val projResponse = client!!.getProject(project, lastModified, lastETag)
                Log.d(TAG,projResponse.toString())
                val name = projResponse.name
                Log.i(TAG, "AAA getProjectInfo, project name: $name")
                val email = projResponse.email
                val currencyName = projResponse.currencyName
                val deletionDisabled = projResponse.deletionDisabled
                val myAccessLevel = projResponse.myAccessLevel
                val archivedTs = projResponse.archivedTs

                if (project.name == "" || name != project.name || project.email == null || project.email == "" || project.isDeletionDisabled != deletionDisabled || project.myAccessLevel != myAccessLevel || project.archivedTs != archivedTs || (project.currencyName == null) || (currencyName != project.currencyName) || email != project.email
                ) {
                    Log.d(TAG, "update local project : $project")
                    project.name = name
                    project.currencyName = currencyName
                    project.isDeletionDisabled = deletionDisabled
                    project.myAccessLevel = myAccessLevel
                    project.archivedTs = archivedTs
                    dbHelper.updateProject(
                        projId = project.id,
                        newName = name,
                        newEmail = email,
                        newPassword = null,
                        newLastPayerId = null,
                        newLastSyncedTimestamp = null,
                        newCurrencyName = currencyName,
                        newDeletionDisabled = deletionDisabled,
                        newMyAccessLevel = myAccessLevel,
                        newBearerToken = null,
                        newArchivedTs = archivedTs ?: 0L
                    )
                }

                val remotePaymentModes = projResponse.getPaymentModes(project.id)
                val remotePaymentModesByRemoteId = remotePaymentModes.associateBy { it.remoteId }

                for (pm in remotePaymentModes) {
                    val localPaymentMode = dbHelper.getPaymentMode(pm.remoteId, project.id)
                    if (localPaymentMode == null) {
                        Log.d(TAG, "Add local pm : $pm")
                        dbHelper.addPaymentMode(pm)
                    } else {
                        if (pm.name == localPaymentMode.name &&
                            pm.color == localPaymentMode.color &&
                            pm.icon == localPaymentMode.icon
                        ) {
                            Log.d(TAG, "Nothing to do for pm : $localPaymentMode")
                        } else {
                            Log.d(TAG, "Update local pm : $pm")
                            dbHelper.updatePaymentMode(localPaymentMode.id, pm.name, pm.icon, pm.color)
                        }
                    }
                }

                val localPaymentModes = dbHelper.getPaymentModes(project.id)
                for (localPaymentMode in localPaymentModes) {
                    if (!remotePaymentModesByRemoteId.containsKey(localPaymentMode.remoteId)) {
                        dbHelper.deletePaymentMode(localPaymentMode.id)
                        Log.d(TAG, "Delete local pm : $localPaymentMode")
                    }
                }

                val remoteCategories = projResponse.getCategories(project.id)
                val remoteCategoriesByRemoteId = remoteCategories.associateBy { it.remoteId }

                for (c in remoteCategories) {
                    val localCategory = dbHelper.getCategory(c.remoteId, project.id)
                    if (localCategory == null) {
                        Log.d(TAG, "Add local category : $c")
                        dbHelper.addCategory(c)
                    } else {
                        if (c.name == localCategory.name &&
                            c.color == localCategory.color &&
                            c.icon == localCategory.icon
                        ) {
                            Log.d(TAG, "Nothing to do for category : $localCategory")
                        } else {
                            Log.d(TAG, "Update local category : $c")
                            dbHelper.updateCategory(localCategory.id, c.name, c.icon, c.color)
                        }
                    }
                }

                val localCategories = dbHelper.getCategories(project.id)
                for (localCategory in localCategories) {
                    if (!remoteCategoriesByRemoteId.containsKey(localCategory.remoteId)) {
                        dbHelper.deleteCategory(localCategory.id)
                        Log.d(TAG, "Delete local category : $localCategory")
                    }
                }

                val remoteCurrencies = projResponse.getCurrencies(project.id)
                val remoteCurrenciesByRemoteId = remoteCurrencies.associateBy { it.remoteId }

                for (c in remoteCurrencies) {
                    val localCurrency = dbHelper.getCurrency(c.remoteId, project.id)
                    if (localCurrency == null) {
                        Log.d(TAG, "Add local currency : $c")
                        dbHelper.addCurrency(c)
                    } else {
                        if (c.name == localCurrency.name &&
                            c.exchangeRate == localCurrency.exchangeRate
                        ) {
                            Log.d(TAG, "Nothing to do for currency : $localCurrency")
                        } else {
                            Log.d(TAG, "Update local currency : $c")
                            dbHelper.updateCurrency(localCurrency.id, c.name, c.exchangeRate)
                        }
                    }
                }

                val localCurrencies = dbHelper.getCurrencies(project.id)
                for (localCurrency in localCurrencies) {
                    if (!remoteCurrenciesByRemoteId.containsKey(localCurrency.remoteId)) {
                        dbHelper.deleteCurrency(localCurrency.id)
                        Log.d(TAG, "Delete local currency : $localCurrencies")
                    }
                }

                val remoteMembers = projResponse.getMembers(project.id)
                val remoteMembersByRemoteId = remoteMembers.associateBy { it.remoteId }

                for (m in remoteMembers) {
                    val localMember = dbHelper.getMember(m.remoteId, project.id)
                    if (localMember == null) {
                        Log.d(TAG, "Add local member : $m")
                        val mid = dbHelper.addMember(m)
                        if (!m.ncUserId.isNullOrEmpty()) {
                            updateMemberAvatar(mid)
                        }
                    } else {
                        val ncUserIdChanged = (
                                (m.ncUserId == null && localMember.ncUserId != null) ||
                                (m.ncUserId != null && localMember.ncUserId == null) ||
                                (m.ncUserId != null && m.ncUserId != localMember.ncUserId)
                        )
                        Log.e("PULLREMOTE", "member NC user id : ${localMember.ncUserId} => ${m.ncUserId} ID changed $ncUserIdChanged")
                        if (ncUserIdChanged && m.ncUserId == null) {
                            m.ncUserId = ""
                        }
                        if (m.name == localMember.name &&
                            m.weight == localMember.weight &&
                            m.isActivated == localMember.isActivated &&
                            ((m.r == null && m.g == null && m.b == null) ||
                                    (m.r == localMember.r && m.g == localMember.g && m.b == localMember.b)) &&
                            !ncUserIdChanged
                        ) {
                            Log.d(TAG, "Nothing to do for member : $localMember")
                            if (!localMember.ncUserId.isNullOrEmpty() && localMember.avatar.isNullOrEmpty()) {
                                Log.d(TAG, "except updating avatar")
                                updateMemberAvatar(localMember.id)
                            }
                        } else {
                            Log.d(TAG, "Update local member : $m")
                            var r = m.r
                            var g = m.g
                            var b = m.b
                            if (m.r == null && m.g == null && m.b == null) {
                                r = localMember.r
                                g = localMember.g
                                b = localMember.b
                            }
                            val needAvatarUpdate = (ncUserIdChanged && !m.ncUserId.isNullOrEmpty())
                            val newAvatar = if (ncUserIdChanged) "" else null
                            dbHelper.updateMember(
                                localMember.id, m.name, m.weight,
                                m.isActivated, null, null,
                                r, g, b, m.ncUserId, newAvatar
                            )
                            if (needAvatarUpdate) {
                                Log.e("PLOP", "pullremote : update member avatar")
                                updateMemberAvatar(localMember.id)
                            }
                        }
                    }
                }

                val dbMembers = dbHelper.getMembersOfProject(project.id, null)
                val memberRemoteIdToId = dbMembers.associate { it.remoteId to it.id }

                val billsResponse = client!!.getBills(project)
                val isIHM = project.type == ProjectType.IHATEMONEY
                val serverSyncTimestamp = if (isIHM) 0L else billsResponse.syncTimestamp
                val remoteBills: List<DBBill> = if (isIHM) {
                    billsResponse.getBillsIHM(project.id, memberRemoteIdToId)
                } else {
                    billsResponse.getBillsCospend(project.id, memberRemoteIdToId)
                }
                val remoteAllBillIds: List<Long> = if (isIHM) {
                    remoteBills.map { it.remoteId }
                } else {
                    billsResponse.allBillIds
                }

                val remoteBillsByRemoteId = remoteBills.associateBy { it.remoteId }
                val localBills = dbHelper.getBillsOfProject(project.id)
                val localBillsByRemoteId = localBills.associateBy { it.remoteId }

                val syncedCategories = dbHelper.getCategories(project.id)
                val catRemoteToLocal = syncedCategories.associate { it.remoteId to it.id }
                val syncedPaymentModes = dbHelper.getPaymentModes(project.id)
                val pmRemoteToLocal = syncedPaymentModes.associate { it.remoteId to it.id }

                for (remoteBill in remoteBills) {
                    if (remoteBill.categoryId > 0) remoteBill.categoryId = catRemoteToLocal[remoteBill.categoryId] ?: 0
                    if (remoteBill.paymentModeId > 0) remoteBill.paymentModeId = pmRemoteToLocal[remoteBill.paymentModeId] ?: 0

                    if (!localBillsByRemoteId.containsKey(remoteBill.remoteId)) {
                        dbHelper.addBill(remoteBill)
                        nbPulledNewBills++
                        newBillsDialogText += "+ ${remoteBill.what}\n"
                        Log.d(TAG, "Add local bill : $remoteBill")
                    } else {
                        val localBill = localBillsByRemoteId[remoteBill.remoteId]!!
                        if (hasChanged(localBill, remoteBill)) {
                            dbHelper.updateBill(
                                localBill.id, null, remoteBill.payerId,
                                remoteBill.amount, remoteBill.timestamp,
                                remoteBill.what, DBBill.STATE_OK, remoteBill.repeat,
                                remoteBill.paymentMode, remoteBill.paymentModeId,
                                remoteBill.categoryId, remoteBill.comment
                            )
                            nbPulledUpdatedBills++
                            updatedBillsDialogText += "✏ ${remoteBill.what}\n"
                            Log.d(TAG, "Update local bill : $remoteBill")
                        } else {
                            Log.d(TAG, "Nothing to do for bill : $localBill")
                        }

                        val localBillOwersByIds = localBill.billOwers.associateBy { it.memberId }
                        val remoteBillOwersByIds = remoteBill.billOwers.associateBy { it.memberId }

                        for (rbo in remoteBill.billOwers) {
                            if (!localBillOwersByIds.containsKey(rbo.memberId)) {
                                dbHelper.addBillower(localBill.id, rbo.memberId)
                                Log.d(TAG, "Add local billOwer : $rbo")
                            }
                        }
                        for (lbo in localBill.billOwers) {
                            if (!remoteBillOwersByIds.containsKey(lbo.memberId)) {
                                dbHelper.deleteBillOwer(lbo.id)
                                Log.d(TAG, "Delete local billOwer : $lbo")
                            }
                        }
                    }
                }

                if (project.type == ProjectType.COSPEND || project.type == ProjectType.IHATEMONEY) {
                    for (localBill in localBills) {
                        if (!remoteAllBillIds.contains(localBill.remoteId)) {
                            dbHelper.deleteBill(localBill.id)
                            nbPulledDeletedBills++
                            deletedBillsDialogText += "🗑 ${localBill.what}\n"
                            Log.d(TAG, "Delete local bill : $localBill")
                        }
                    }
                } else {
                    for (localBill in localBills) {
                        if (!remoteBillsByRemoteId.containsKey(localBill.remoteId)) {
                            dbHelper.deleteBill(localBill.id)
                            nbPulledDeletedBills++
                            deletedBillsDialogText += "🗑 ${localBill.what}\n"
                            Log.d(TAG, "Delete local bill : $localBill")
                        }
                    }
                }

                val localMembers = dbHelper.getMembersOfProject(project.id, null)
                for (localMember in localMembers) {
                    if (!remoteMembersByRemoteId.containsKey(localMember.remoteId)) {
                        if (dbHelper.getBillsOfMember(localMember.id).isEmpty()
                            && dbHelper.getBillowersOfMember(localMember.id).isEmpty()
                        ) {
                            dbHelper.deleteMember(localMember.id)
                            Log.d(TAG, "Delete local member : $localMember")
                        } else {
                            Log.d(TAG, "WARNING local member : ${localMember.name} does not exist remotely but is still involved in some bills")
                        }
                    }
                }

                dbHelper.updateProject(
                    project.id, null, null,
                    null, null, serverSyncTimestamp,
                    null, null, null,
                    null
                )
                LoginStatus.OK
            } catch (_: ServerResponse.NotModifiedException) {
                Log.d(TAG, "No changes, nothing to do.")
                LoginStatus.OK
            } catch (e: IOException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                Log.e(TAG, "Catch MISMATCHTOKEN", e)
                LoginStatus.SSO_TOKEN_MISMATCH
            } catch (e: NextcloudHttpRequestFailedException) {
                Log.e(TAG, "Catch NC REQ failed", e)
                errorMessages.add(getErrorMessageFromException(e))
                e.cause?.let { exceptions.add(it) }
                LoginStatus.REQ_FAILED
            }
        }

        private fun onPostExecute(status: LoginStatus) {
            if (status != LoginStatus.OK) {
                var errorString = ""
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    val obj = SupportUtil.getJsonObject(e.message)
                    if (obj != null && obj.has("message")) {
                        try {
                            errorString += "${obj.getString("message")}\n"
                        } catch (_: JSONException) {
                        }
                    }
                }
                val intent = Intent(MainConstants.BROADCAST_PROJECT_SYNC_FAILED)
                intent.putExtra(MainConstants.BROADCAST_ERROR_MESSAGE, errorString)
                intent.putExtra(MainConstants.BROADCAST_PROJECT_ID, project.id)
                appContext.sendBroadcast(intent)
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    appContext.sendBroadcast(Intent(MainConstants.BROADCAST_SSO_TOKEN_MISMATCH))
                }
            } else {
                val intent = Intent(MainConstants.BROADCAST_PROJECT_SYNCED)
                intent.putExtra(MainConstants.BROADCAST_EXTRA_PARAM, project.name)
                appContext.sendBroadcast(intent)
            }
            syncActive = false
            for (callback in callbacks) {
                callback.onFinish()
            }
            if (projectIdsToSync.isNotEmpty()) {
                val pid = projectIdsToSync.removeAt(projectIdsToSync.size - 1)
                scheduleSync(false, pid)
            }
        }
    }

    fun getErrorMessageFromException(e: NextcloudHttpRequestFailedException): String {
        var message = ""
        when (e.statusCode) {
            503 -> message += appContext.getString(R.string.error_maintenance_mode)
            400 -> message += appContext.getString(R.string.error_400)
            401 -> message += appContext.getString(R.string.error_401)
            403 -> message += appContext.getString(R.string.error_403)
            404 -> message += appContext.getString(R.string.error_404)
        }
        message += "\n" + e.cause?.message
        return message
    }

    private fun createVersatileProjectSyncClient(cospendVersion: String?): VersatileProjectSyncClient? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val useSSO = preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)
        return if (useSSO) {
            try {
                val ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext)
                val nextcloudAPI = NextcloudAPI(appContext, ssoAccount, GsonBuilder().create(), apiCallback)
                VersatileProjectSyncClient("", "", "", nextcloudAPI, ssoAccount, cospendVersion, appContext)
            } catch (_: Exception) {
                null
            }
        } else {
            val url = preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS) ?: ""
            val username = preferences.getString(AccountActivity.SETTINGS_USERNAME, AccountActivity.DEFAULT_SETTINGS) ?: ""
            val password = preferences.getString(AccountActivity.SETTINGS_PASSWORD, AccountActivity.DEFAULT_SETTINGS) ?: ""
            VersatileProjectSyncClient(url, username, password, null, null, cospendVersion, appContext)
        }
    }

    fun canCreateAuthenticatedProject(project: DBProject): Boolean {
        val isCospend = ProjectType.COSPEND == project.type
        val projUrl = project.serverUrl?.replace("/index.php/apps/cospend", "")?.replace("/+$".toRegex(), "") ?: ""

        val accountUrl = if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
            try {
                val ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext)
                ssoAccount.url.replace("/+$".toRegex(), "")
            } catch (_: Exception) {
                return false
            }
        } else {
            preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS)?.replace("/$".toRegex(), "") ?: ""
        }

        Log.v(TAG, "proj url : $projUrl ; account url : $accountUrl")
        return isCospend && projUrl == accountUrl
    }

    fun editRemoteProject(projId: Long, newName: String?, newEmail: String?,
                          newPassword: String?, newMainCurrencyName: String?, callback: ICallback): Boolean {
        updateNetworkStatus()
        if (isSyncPossible) {
            EditRemoteProjectTask(projId, newName, newEmail, newPassword, newMainCurrencyName, callback).execute()
            return true
        }
        return false
    }

    private inner class EditRemoteProjectTask(
        projId: Long,
        private val newName: String?,
        private val newEmail: String?,
        private val newPassword: String?,
        private val newMainCurrencyName: String?,
        private val callback: ICallback
    ) {
        private val project: DBProject? = dbHelper.getProject(projId)
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()

        fun execute(): EditRemoteProjectTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val nextcloudClient = createNextcloudClient()
            var version: String? = null
            if (nextcloudClient != null) {
                try {
                    val response = nextcloudClient.getCapabilities(null)
                    version = response.cospendVersion
                } catch (e: Exception) {
                    Log.i(TAG, "Failed to get cospend version when syncing: $e")
                }
            }
            val client = createVersatileProjectSyncClient(version)
            if (BillsListViewActivity.DEBUG) {
                Log.i(TAG, "STARTING edit remote project")
            }
            var status = LoginStatus.OK
            try {
                val response = client!!.editRemoteProject(
                    project!!, newName, newEmail, newPassword, newMainCurrencyName
                )
                if (BillsListViewActivity.DEBUG) {
                    Log.i(TAG, "RESPONSE edit remote project : ${response.stringContent}")
                }
            } catch (e: IOException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception", e)
                exceptions.add(e)
                status = LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                Log.e(TAG, "Catch JSON exception", e)
                status = LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                Log.e(TAG, "Catch MISMATCHTOKEN", e)
                status = LoginStatus.SSO_TOKEN_MISMATCH
            } catch (e: NextcloudHttpRequestFailedException) {
                Log.e(TAG, "Catch NC REQ failed", e)
                status = LoginStatus.REQ_FAILED
                errorMessages.add(getErrorMessageFromException(e))
            }
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "FINISHED edit remote project")
            return status
        }

        private fun onPostExecute(status: LoginStatus) {
            var errorString = ""
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(R.string.error_sync, appContext.getString(status.str)) + "\n\n"
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    errorString += "${e.javaClass.name}: ${e.message}"
                }
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    appContext.sendBroadcast(Intent(MainConstants.BROADCAST_SSO_TOKEN_MISMATCH))
                }
            } else {
                dbHelper.updateProject(
                    project!!.id, newName, newEmail, newPassword,
                    null, null, null, null, null, null
                )
            }
            callback.onFinish(newName ?: "", errorString)
        }
    }

    fun deleteRemoteProject(projId: Long, callback: ICallback): Boolean {
        updateNetworkStatus()
        if (isSyncPossible) {
            DeleteRemoteProjectTask(projId, callback).execute()
            return true
        }
        return false
    }

    private inner class DeleteRemoteProjectTask(projId: Long, private val callback: ICallback) {
        private val project: DBProject? = dbHelper.getProject(projId)
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()

        fun execute(): DeleteRemoteProjectTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val nextcloudClient = createNextcloudClient()
            var version: String? = null
            if (nextcloudClient != null) {
                try {
                    val response = nextcloudClient.getCapabilities(null)
                    version = response.cospendVersion
                } catch (e: Exception) {
                    Log.i(TAG, "Failed to get cospend version when syncing: $e")
                }
            }
            val client = createVersatileProjectSyncClient(version)
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "STARTING delete remote project")
            var status = LoginStatus.OK
            try {
                val response = client!!.deleteRemoteProject(project!!)
                if (BillsListViewActivity.DEBUG) Log.i(TAG, "RESPONSE delete remote project : ${response.stringContent}")
            } catch (e: IOException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception", e)
                exceptions.add(e)
                status = LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                Log.e(TAG, "Catch JSONException", e)
                status = LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                Log.e(TAG, "Catch MISMATCHTOKEN", e)
                status = LoginStatus.SSO_TOKEN_MISMATCH
            } catch (e: NextcloudHttpRequestFailedException) {
                Log.e(TAG, "Catch NC REQ failed", e)
                status = LoginStatus.REQ_FAILED
                errorMessages.add(getErrorMessageFromException(e))
            }
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "FINISHED delete device")
            return status
        }

        private fun onPostExecute(status: LoginStatus) {
            var errorString = ""
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(R.string.error_sync, appContext.getString(status.str)) + "\n\n"
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    errorString += "${e.javaClass.name}: ${e.message}"
                }
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    appContext.sendBroadcast(Intent(MainConstants.BROADCAST_SSO_TOKEN_MISMATCH))
                }
            } else {
                dbHelper.deleteProject(project!!.id)
            }
            callback.onFinish(project?.id?.toString() ?: "", errorString)
        }
    }

    fun createRemoteProject(remoteId: String, name: String, email: String, password: String, ihmUrl: String, projectType: ProjectType, callback: IProjectCreationCallback): Boolean {
        if (isSyncPossible) {
            val proj = DBProject(
                0, remoteId,
                if (projectType == ProjectType.COSPEND) "" else password,
                name, ihmUrl, email,
                null, projectType, 0L, null,
                false, DBProject.ACCESS_LEVEL_UNKNOWN, null
            )
            CreateRemoteProjectTask(proj, callback).execute()
            return true
        }
        return false
    }

    private inner class CreateRemoteProjectTask(private val project: DBProject, private val callback: IProjectCreationCallback) {
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()
        private var usePrivateApi = false

        fun execute(): CreateRemoteProjectTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val nextcloudClient = createNextcloudClient()
            var version: String? = null
            if (nextcloudClient != null) {
                try {
                    val response = nextcloudClient.getCapabilities(null)
                    version = response.cospendVersion
                } catch (e: Exception) {
                    Log.i(TAG, "Failed to get cospend version when syncing: $e")
                }
            }
            val client = createVersatileProjectSyncClient(version)
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "STARTING create remote project")
            var status = LoginStatus.OK
            try {
                val response: ServerResponse.CreateRemoteProjectResponse
                if (canCreateAuthenticatedProject(project)) {
                    response = client!!.createAuthenticatedRemoteProject(project)
                    usePrivateApi = true
                } else {
                    response = client!!.createAnonymousRemoteProject(project)
                }
                if (BillsListViewActivity.DEBUG) Log.i(TAG, "RESPONSE create remote project : ${response.stringContent}")
            } catch (e: IOException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception", e)
                exceptions.add(e)
                status = LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "JSON Exception", e)
                exceptions.add(e)
                status = LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception", e)
                exceptions.add(e)
                status = LoginStatus.CONNECTION_FAILED
            } catch (e: NextcloudHttpRequestFailedException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception", e)
                exceptions.add(e)
                status = LoginStatus.REQ_FAILED
                errorMessages.add(getErrorMessageFromException(e))
            }
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "FINISHED create remote project")
            return status
        }

        private fun onPostExecute(status: LoginStatus) {
            var errorString = ""
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(R.string.error_sync, appContext.getString(status.str)) + "\n\n"
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    errorString += "${e.javaClass.name}: ${e.message}"
                }
            }
            callback.onFinish(project.remoteId, errorString, usePrivateApi)
        }
    }

    private fun hasChanged(localBill: DBBill, remoteBill: DBBill): Boolean {
        if (localBill.payerId == remoteBill.payerId &&
            localBill.amount == remoteBill.amount &&
            localBill.timestamp == remoteBill.timestamp &&
            localBill.what == remoteBill.what &&
            localBill.comment == remoteBill.comment &&
            localBill.paymentMode == remoteBill.paymentMode &&
            localBill.paymentModeId == remoteBill.paymentModeId &&
            localBill.categoryId == remoteBill.categoryId
        ) {
            val localRepeat = localBill.repeat ?: DBBill.NON_REPEATED
            val remoteRepeat = remoteBill.repeat ?: DBBill.NON_REPEATED
            return localRepeat != remoteRepeat
        }
        return true
    }

    fun updateMemberAvatar(memberId: Long) {
        updateNetworkStatus()
        if (isNextcloudAccountConfigured(appContext) && isSyncPossible) {
            UpdateMemberAvatarTask(memberId).execute()
        }
    }

    fun runAccountProjectsSync() {
        Log.d(TAG, "Account projects sync requested; ${if (syncAccountProjectsActive) "sync active" else "sync NOT active"}) ...")
        updateNetworkStatus()
        if (isNextcloudAccountConfigured(appContext) && isSyncPossible && !syncAccountProjectsActive) {
            SyncAccountProjectsTask().execute()
            if (preferences.getBoolean(appContext.getString(R.string.pref_key_use_server_color), true)) {
                GetNCColorTask().execute()
            }
            GetNCUserAvatarTask().execute()
        }
    }

    private val apiCallback = object : NextcloudAPI.ApiConnectedListener {
        override fun onConnected() {
            Log.d(TAG, "API connected!!!!")
        }

        override fun onError(ex: Exception) {}
    }

    private fun createNextcloudClient(): NextcloudClient? {
        val useSSO = preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)
        return if (useSSO) {
            try {
                val ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext)
                val nextcloudAPI = NextcloudAPI(appContext, ssoAccount, GsonBuilder().create(), apiCallback)
                NextcloudClient("", ssoAccount.userId, "", nextcloudAPI, appContext)
            } catch (_: Exception) {
                null
            }
        } else {
            val url = preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS) ?: ""
            val username = preferences.getString(AccountActivity.SETTINGS_USERNAME, AccountActivity.DEFAULT_SETTINGS) ?: ""
            val password = preferences.getString(AccountActivity.SETTINGS_PASSWORD, AccountActivity.DEFAULT_SETTINGS) ?: ""
            NextcloudClient(url, username, password, null, appContext)
        }
    }

    private inner class SyncAccountProjectsTask {
        private var client: NextcloudClient? = null
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()

        fun execute(): SyncAccountProjectsTask {
            scope.launch {
                syncAccountProjectsActive = true
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
                syncAccountProjectsActive = false
            }
            return this
        }

        private fun doWork(): LoginStatus {
            client = createNextcloudClient()
            Log.i(TAG, "STARTING account projects SYNCHRONIZATION")
            val status = client?.let { pullRemoteProjects(it) } ?: LoginStatus.SSO_TOKEN_MISMATCH
            Log.i(TAG, "SYNCHRONIZATION FINISHED")
            return status
        }

        private fun pullRemoteProjects(client: NextcloudClient): LoginStatus {
            Log.d(TAG, "pullRemoteProjects()")
            return try {
                val url = if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
                    SingleAccountHelper.getCurrentSingleSignOnAccount(appContext).url
                } else {
                    preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS) ?: ""
                }

                val localProjects = dbHelper.projects
                val capabilitiesResponse = client.getCapabilities(null)
                val cospendVersion = capabilitiesResponse.cospendVersion
                val useOcsApi = cospendVersion != null && SupportUtil.compareVersions(cospendVersion, "1.6.1") >= 0
                
                val response = client.getAccountProjects(useOcsApi)
                val remoteAccountProjects = response.getAccountProjects(url)
                dbHelper.clearAccountProjects()
                for (remoteAccountProject in remoteAccountProjects) {
                    dbHelper.addAccountProject(remoteAccountProject)
                    Log.v(TAG, "received account project $remoteAccountProject")
                    val existingProj = localProjects.find { 
                        it.remoteId == remoteAccountProject.remoteId && 
                        it.serverUrl?.replace("/+$".toRegex(), "") == remoteAccountProject.ncUrl.replace("/+$".toRegex(), "") + "/index.php/apps/cospend"
                    }
                    if (existingProj == null) {
                        val newProj = DBProject(0,
                            remoteAccountProject.remoteId,
                            "",
                            remoteAccountProject.name,
                            remoteAccountProject.ncUrl.replace("/+$".toRegex(), "") + "/index.php/apps/cospend",
                            "",
                            null,
                            ProjectType.COSPEND,
                            0L,
                            null,
                            false,
                            DBProject.ACCESS_LEVEL_UNKNOWN,
                            null,
                            remoteAccountProject.archivedTs
                        )
                        dbHelper.addProject(newProj)
                    } else if (existingProj.archivedTs != remoteAccountProject.archivedTs) {
                        dbHelper.updateProject(
                            projId = existingProj.id,
                            newName = null,
                            newEmail = null,
                            newPassword = null,
                            newLastPayerId = null,
                            newLastSyncedTimestamp = null,
                            newCurrencyName = null,
                            newDeletionDisabled = null,
                            newMyAccessLevel = null,
                            newBearerToken = null,
                            newArchivedTs = remoteAccountProject.archivedTs ?: 0L
                        )
                    }
                }
                LoginStatus.OK
            } catch (_: ServerResponse.NotModifiedException) {
                Log.d(TAG, "No changes, nothing to do.")
                LoginStatus.OK
            } catch (e: IOException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.CONNECTION_FAILED
            } catch (e: JSONException) {
                Log.e(TAG, "Exception", e)
                exceptions.add(e)
                LoginStatus.JSON_FAILED
            } catch (e: TokenMismatchException) {
                Log.e(TAG, "Catch MISMATCHTOKEN", e)
                LoginStatus.SSO_TOKEN_MISMATCH
            } catch (e: NextcloudHttpRequestFailedException) {
                Log.e(TAG, "Catch REQ FAILED", e)
                errorMessages.add(getErrorMessageFromException(e))
                LoginStatus.REQ_FAILED
            }
        }

        private fun onPostExecute(status: LoginStatus) {
            if (status != LoginStatus.OK) {
                var errorString = appContext.getString(R.string.error_sync, appContext.getString(status.str)) + "\n\n"
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    errorString += "${e.javaClass.name}: ${e.message}"
                }
                val intent = Intent(MainConstants.BROADCAST_ACCOUNT_PROJECTS_SYNC_FAILED)
                intent.putExtra(MainConstants.BROADCAST_ERROR_MESSAGE, errorString)
                appContext.sendBroadcast(intent)
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    appContext.sendBroadcast(Intent(MainConstants.BROADCAST_SSO_TOKEN_MISMATCH))
                }
            } else {
                appContext.sendBroadcast(Intent(MainConstants.BROADCAST_ACCOUNT_PROJECTS_SYNCED))
            }
        }
    }

    private inner class GetNCColorTask {
        fun execute(): GetNCColorTask {
            scope.launch {
                withContext(Dispatchers.IO) {
                    doWork()
                }
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val client = createNextcloudClient()
            Log.i(TAG, "STARTING get color")
            return if (client != null) {
                getNextcloudColor(client)
            } else {
                LoginStatus.SSO_TOKEN_MISMATCH
            }
        }

        private fun getNextcloudColor(client: NextcloudClient): LoginStatus {
            Log.d(TAG, "getNextcloudColor()")
            return try {
                val response = client.getCapabilities(null)
                var color = response.color

                if (!color.isNullOrEmpty() && color.startsWith("#")) {
                    if (color.length == 4) {
                        color = "#" + color[1] + color[1] + color[2] + color[2] + color[3] + color[3]
                    }
                    val intColor = color.toColorInt()
                    Log.d(TAG, "COLOR from server is $color")
                    preferences.edit {
                        putInt(
                            appContext.getString(R.string.pref_key_server_color),
                            intColor
                        )
                    }
                }
                LoginStatus.OK
            } catch (e: Exception) {
                Log.e(TAG, "Exception in get color", e)
                LoginStatus.CONNECTION_FAILED
            }
        }
    }

    private inner class GetNCUserAvatarTask {
        fun execute(): GetNCUserAvatarTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val client = createNextcloudClient()
            Log.i(TAG, "STARTING get account avatar")
            return if (client != null) {
                getNextcloudUserAvatar(client)
            } else {
                LoginStatus.SSO_TOKEN_MISMATCH
            }
        }

        private fun getNextcloudUserAvatar(client: NextcloudClient): LoginStatus {
            Log.d(TAG, "getNextcloudUserAvatar()")
            return try {
                val response = client.getAvatar(null)
                val avatar = response.avatarString

                if (avatar.isNotEmpty()) {
                    preferences.edit {
                        putString(
                            appContext.getString(R.string.pref_key_avatar),
                            avatar
                        )
                    }
                }
                LoginStatus.OK
            } catch (e: Exception) {
                Log.e(TAG, "Exception in get avatar", e)
                LoginStatus.CONNECTION_FAILED
            }
        }

        private fun onPostExecute(status: LoginStatus) {
            if (status == LoginStatus.OK) {
                appContext.sendBroadcast(Intent(MainConstants.BROADCAST_AVATAR_UPDATED))
            }
        }
    }

    private inner class UpdateMemberAvatarTask(private val memberId: Long) {
        fun execute(): UpdateMemberAvatarTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            val client = createNextcloudClient()
            Log.i(TAG, "STARTING get avatar for member")
            return if (client != null) {
                getNextcloudUserAvatar(client)
            } else {
                LoginStatus.SSO_TOKEN_MISMATCH
            }
        }

        private fun getNextcloudUserAvatar(client: NextcloudClient): LoginStatus {
            Log.d(TAG, "getNextcloudUserAvatar() $memberId")
            return try {
                val m = dbHelper.getMember(memberId)
                val targetUserName = m?.ncUserId
                if (!targetUserName.isNullOrEmpty()) {
                    val response = client.getAvatar(targetUserName)
                    val avatar = response.avatarString

                    if (avatar.isNotEmpty()) {
                        dbHelper.updateMember(
                            memberId, null, null, null,
                            null, null, null, null, null,
                            null, avatar
                        )
                        Log.d(TAG, "RECEIVED AVATAR for member $memberId length ${avatar.length}")
                    }
                }
                LoginStatus.OK
            } catch (e: Exception) {
                Log.e(TAG, "Exception in get member avatar", e)
                LoginStatus.CONNECTION_FAILED
            }
        }

        private fun onPostExecute(status: LoginStatus) {
            if (status == LoginStatus.OK) {
                val intent = Intent(MainConstants.BROADCAST_AVATAR_UPDATED)
                intent.putExtra(MainConstants.BROADCAST_AVATAR_UPDATED_MEMBER, memberId)
                appContext.sendBroadcast(intent)
            }
        }
    }

    fun getRemoteProjectInfo(project: DBProject, callback: ICallback): Boolean {
        if (isSyncPossible) {
            GetRemoteProjectInfoTask(project, callback).execute()
            return true
        }
        return false
    }

    private inner class GetRemoteProjectInfoTask(private val project: DBProject, private val callback: ICallback) {
        private val exceptions: MutableList<Throwable> = ArrayList()
        private val errorMessages: MutableList<String> = ArrayList()

        fun execute(): GetRemoteProjectInfoTask {
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    doWork()
                }
                onPostExecute(status)
            }
            return this
        }

        private fun doWork(): LoginStatus {
            var version: String? = null
            if (project.type == ProjectType.COSPEND) {
                val nextcloudClient = createNextcloudClient()
                if (nextcloudClient != null) {
                    try {
                        val response = nextcloudClient.getCapabilities(project)
                        version = response.cospendVersion
                    } catch (e: Exception) {
                        Log.i(TAG, "Failed to get cospend version when syncing: $e")
                    }
                }
            }
            val client = createVersatileProjectSyncClient(version)
            if (BillsListViewActivity.DEBUG) Log.i(TAG, "STARTING create remote project")
            return try {
                val projResponse = client!!.getProject(project, 0, null)
                val name = projResponse.name
                val email = projResponse.email
                Log.e(TAG, "Project info: $name and $email")
                LoginStatus.OK
            } catch (e: NextcloudHttpRequestFailedException) {
                if (BillsListViewActivity.DEBUG) Log.e(TAG, "Exception1", e)
                errorMessages.add(getErrorMessageFromException(e))
                LoginStatus.REQ_FAILED
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get project info: $e")
                exceptions.add(e)
                LoginStatus.CONNECTION_FAILED
            }
        }

        private fun onPostExecute(status: LoginStatus) {
            var errorString = ""
            if (status != LoginStatus.OK) {
                for (errorMessage in errorMessages) {
                    errorString += "$errorMessage\n"
                }
                errorString += "\n"
                for (e in exceptions) {
                    errorString += "${e.javaClass.name}: ${e.message}"
                }
            }
            callback.onFinish("", errorString)
        }
    }

    companion object {
        private val TAG = CowspentServerSyncHelper::class.java.simpleName

        private var instance: CowspentServerSyncHelper? = null
        private val projectIdsToSync: MutableList<Long> = ArrayList()

        @Synchronized
        fun getInstance(dbHelper: CowspentSQLiteOpenHelper): CowspentServerSyncHelper {
            if (instance == null) {
                instance = CowspentServerSyncHelper(dbHelper)
            }
            return instance!!
        }

        fun isNextcloudAccountConfigured(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return !preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS).isNullOrEmpty() ||
                    preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)
        }

        fun getNextcloudAccountServerUrl(context: Context): String {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
                try {
                    val ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context.applicationContext)
                    ssoAccount.url.replace("/+$".toRegex(), "")
                } catch (_: Exception) {
                    ""
                }
            } else {
                preferences.getString(AccountActivity.SETTINGS_URL, AccountActivity.DEFAULT_SETTINGS)?.replace("/+$".toRegex(), "") ?: ""
            }
        }
    }
}
