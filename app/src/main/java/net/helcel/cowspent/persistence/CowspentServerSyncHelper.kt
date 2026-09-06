package net.helcel.cowspent.persistence

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import net.helcel.cowspent.model.DBMember
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

    private var syncActive = false
    private var syncAccountProjectsActive = false

    private var callbacksPush: MutableList<ICallback> = ArrayList()
    private var callbacksPull: MutableList<ICallback> = ArrayList()

    init {

        updateNetworkStatus()
    }

    val isSyncPossible: Boolean
        get() {
            updateNetworkStatus()
            val offlineMode = preferences.getBoolean(appContext.getString(R.string.pref_key_offline_mode), false)
            return networkConnected && !offlineMode
        }

    fun addCallbackPull(callback: ICallback) {
        // Callers register on every resume but the list is only drained when a task actually
        // starts, so refuse duplicates rather than letting the same callback pile up.
        if (!callbacksPull.contains(callback)) {
            callbacksPull.add(callback)
        }
    }

    fun removeCallbackPull(callback: ICallback) {
        callbacksPull.remove(callback)
    }

    fun scheduleSync(onlyLocalChanges: Boolean, projId: Long, forceFullSync: Boolean = false): SyncTask? =
        scheduleSync(onlyLocalChanges, projId, forceFullSync) { dbHelper.getProject(projId) }

    /**
     * Overload for callers that already hold the project. Resolving one by id costs a query plus
     * a blocking DataStore read and an AEAD decrypt in getProjectFromCursor, which adds up when
     * scheduling a sync for every project at app open.
     */
    fun scheduleSync(onlyLocalChanges: Boolean, project: DBProject, forceFullSync: Boolean = false): SyncTask? =
        scheduleSync(onlyLocalChanges, project.id, forceFullSync) { project }

    private fun scheduleSync(
        onlyLocalChanges: Boolean,
        projId: Long,
        forceFullSync: Boolean,
        resolveProject: () -> DBProject?
    ): SyncTask? {
        Log.d(TAG, "Sync requested (${if (onlyLocalChanges) "onlyLocalChanges" else "full"}; ${if (syncActive) "sync active" else "sync NOT active"}; forceFullSync=$forceFullSync) ...")
        updateNetworkStatus()
        if (isSyncPossible && (!syncActive || onlyLocalChanges)) {
            val project = resolveProject()
            if (project != null) {
                Log.d(TAG, "... starting now")
                val syncTask = SyncTask(onlyLocalChanges, project, forceFullSync)
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

    /** The server's ids for the things a bill points at, mapped to their local row ids. */
    private class RemoteIdMaps(
        val members: Map<Long, Long>,
        val categories: Map<Long, Long>,
        val paymentModes: Map<Long, Long>
    )

    /** One pull of the bills endpoint. */
    private class RemoteBills(
        val bills: List<DBBill>,
        /**
         * Every bill id the server holds. Only the complete fetch reports this; after a paged
         * walk it is empty, and nothing may be deleted locally on the strength of it.
         */
        val allIds: List<Long>,
        val syncTimestamp: Long?
    )

    inner class SyncTask(private val onlyLocalChanges: Boolean, private val project: DBProject, private val forceFullSync: Boolean = false) {
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

                val categoriesToAdd = dbHelper.getCategoriesOfProjectWithState(project.id, DBBill.STATE_ADDED)
                for (catToAdd in categoriesToAdd) {
                    try {
                        val categoriesResponse = client!!.getCategories(project)
                        val remoteCategories = categoriesResponse.getCategories(project.id)
                        val matchingRemote = remoteCategories.find { it.name == catToAdd.name }
                        
                        if (matchingRemote != null) {
                            dbHelper.updateCategory(catToAdd.id, null, null, null, DBBill.STATE_OK, matchingRemote.remoteId)
                            catToAdd.remoteId = matchingRemote.remoteId
                        } else {
                            val createResponse = client!!.createRemoteCategory(project, catToAdd)
                            val newRemoteId = createResponse.remoteCategoryId
                            if (newRemoteId > 0) {
                                dbHelper.updateCategory(catToAdd.id, null, null, null, DBBill.STATE_OK, newRemoteId)
                                catToAdd.remoteId = newRemoteId
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "CATEGORY SYNC FAILED for ${catToAdd.name}", e)
                    }
                }

                val categoriesToEdit = dbHelper.getCategoriesOfProjectWithState(project.id, DBBill.STATE_EDITED)
                for (catToEdit in categoriesToEdit) {
                    try {
                        client!!.editRemoteCategory(project, catToEdit)
                        dbHelper.updateCategory(catToEdit.id, null, null, null, DBBill.STATE_OK)
                    } catch (e: Exception) {
                        Log.e(TAG, "EDIT CATEGORY FAILED for ${catToEdit.name}, might not exist remotely", e)
                        // If it fails, we keep the state as EDITED so it tries again next time, 
                        // or we could set it to OK if we think it's a permanent mismatch.
                        // For now just log it.
                    }
                }

                val categoriesToDelete = dbHelper.getCategoriesOfProjectWithState(project.id, DBBill.STATE_DELETED)
                for (catToDel in categoriesToDelete) {
                    try {
                        client!!.deleteRemoteCategory(project, catToDel.remoteId)
                        dbHelper.deleteCategory(catToDel.id)
                    } catch (e: NextcloudHttpRequestFailedException) {
                        if (e.statusCode == 404 || e.statusCode == 400) {
                            Log.d(TAG, "failed to delete category on remote project (code ${e.statusCode}) : delete it locally anyway")
                            dbHelper.deleteCategory(catToDel.id)
                        } else {
                            throw e
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DELETE CATEGORY FAILED for ${catToDel.name}", e)
                    }
                }

                val paymentModesToAdd = dbHelper.getPaymentModesOfProjectWithState(project.id, DBBill.STATE_ADDED)
                if (paymentModesToAdd.isNotEmpty()) {
                    try {
                        val pmsResponse = client!!.getPaymentModes(project)
                        val remotePms = pmsResponse.getPaymentModes(project.id)
                        val remotePmsNames = remotePms.map { it.name }
                        for (pmToAdd in paymentModesToAdd) {
                            val searchIndex = remotePmsNames.indexOf(pmToAdd.name)
                            if (searchIndex != -1) {
                                val remotePm = remotePms[searchIndex]
                                dbHelper.updatePaymentMode(pmToAdd.id, null, null, null, DBBill.STATE_OK, remotePm.remoteId)
                                pmToAdd.remoteId = remotePm.remoteId
                            } else {
                                val createRemotePaymentModeResponse = client!!.createRemotePaymentMode(project, pmToAdd)
                                val newRemoteId = createRemotePaymentModeResponse.remotePaymentModeId
                                if (newRemoteId > 0) {
                                    dbHelper.updatePaymentMode(pmToAdd.id, null, null, null, DBBill.STATE_OK, newRemoteId)
                                    pmToAdd.remoteId = newRemoteId
                                }
                            }
                        }
                    } catch (e: NextcloudHttpRequestFailedException) {
                        Log.e(TAG, "GET PAYMENT MODES FAILED : " + e.message)
                    }
                }

                val paymentModesToEdit = dbHelper.getPaymentModesOfProjectWithState(project.id, DBBill.STATE_EDITED)
                for (pmToEdit in paymentModesToEdit) {
                    try {
                        client!!.editRemotePaymentMode(project, pmToEdit)
                        dbHelper.updatePaymentMode(pmToEdit.id, null, null, null, DBBill.STATE_OK)
                    } catch (e: Exception) {
                        Log.e(TAG, "EDIT PAYMENT MODE FAILED for ${pmToEdit.name}, might not exist remotely", e)
                    }
                }

                val paymentModesToDelete = dbHelper.getPaymentModesOfProjectWithState(project.id, DBBill.STATE_DELETED)
                for (pmToDel in paymentModesToDelete) {
                    try {
                        client!!.deleteRemotePaymentMode(project, pmToDel.remoteId)
                        dbHelper.deletePaymentMode(pmToDel.id)
                    } catch (e: NextcloudHttpRequestFailedException) {
                        if (e.statusCode == 404 || e.statusCode == 400) {
                            Log.d(TAG, "failed to delete payment mode on remote project (code ${e.statusCode}) : delete it locally anyway")
                            dbHelper.deletePaymentMode(pmToDel.id)
                        } else {
                            throw e
                        }
                    } catch (_: Exception) {
                    }
                }

                val categories = dbHelper.getCategories(project.id)
                val categoryIdToRemoteId = categories.associate { it.id to it.remoteId }.toMutableMap()
                // Map hardcoded constants to themselves if not in DB
                categoryIdToRemoteId[DBBill.CATEGORY_REIMBURSEMENT] = DBBill.CATEGORY_REIMBURSEMENT
                categories.filter { it.remoteId < 0 }.forEach { categoryIdToRemoteId[it.remoteId] = it.remoteId }

                val paymentModes = dbHelper.getPaymentModes(project.id)
                val paymentModeIdToRemoteId = paymentModes.associate { it.id to it.remoteId }.toMutableMap()
                paymentModes.filter { it.remoteId < 0 }.forEach { paymentModeIdToRemoteId[it.remoteId] = it.remoteId }

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
                        val returnedRemoteId = editRemoteBillResponse.remoteBillId
                        if (returnedRemoteId == bToEdit.remoteId || (returnedRemoteId == 0L && !project.getRequestBaseUrl(true).contains("/ocs/v2.php"))) {
                            dbHelper.setBillState(bToEdit.id, DBBill.STATE_OK)
                            Log.d(TAG, "SUCCESSFUL remote bill edition ($returnedRemoteId)")
                        } else if (returnedRemoteId > 0) {
                            dbHelper.setBillState(bToEdit.id, DBBill.STATE_OK)
                            Log.d(TAG, "SUCCESSFUL remote bill edition ($returnedRemoteId)")
                        } else {
                            Log.d(TAG, "FAILED to edit remote bill ($returnedRemoteId)")
                        }
                    } catch (_: Exception) {
                        Log.d(TAG, "FAILED to edit remote bill: it probably does not exist remotely")
                    }
                }

                val toAdd = dbHelper.getBillsOfProjectWithState(project.id, DBBill.STATE_ADDED)
                for (bToAdd in toAdd) {
                    val createRemoteBillResponse = client!!.createRemoteBill(project, bToAdd, memberIdToRemoteId, categoryIdToRemoteId, paymentModeIdToRemoteId)
                    val newRemoteId = createRemoteBillResponse.remoteBillId
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
                        val newRemoteId = createRemoteCurrencyResponse.remoteCurrencyId
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

        /**
         * Brings the local copy of the project in line with the server: its own row, then each of
         * its collections, then its bills. Each step is applied as it goes, so a failure part way
         * through leaves the earlier steps written - the next sync picks up where this one stopped.
         */
        private fun pullRemoteChanges(): LoginStatus {
            Log.d(TAG, "pullRemoteChanges($project)")
            return try {
                val projResponse = client!!.getProject(project, 0, null)

                updateLocalProject(projResponse)
                syncPaymentModes(projResponse)
                syncCategories(projResponse)
                syncCurrencies(projResponse)
                val remoteMembersByRemoteId = syncMembers(projResponse)

                // Bills arrive with the server's ids for their member, category and payment mode,
                // so the maps have to be built after those collections are in place.
                val idMaps = buildRemoteIdMaps()

                val localBills = dbHelper.getBillsOfProject(project.id)
                val localBillsByRemoteId = localBills.associateBy { it.remoteId }
                val pulled = fetchRemoteBills(idMaps, localBillsByRemoteId)

                applyRemoteBills(pulled.bills, localBillsByRemoteId)
                deleteVanishedBills(pulled, localBills)
                deleteVanishedMembers(remoteMembersByRemoteId)

                dbHelper.updateProject(
                    projId = project.id,
                    newLastSyncedTimestamp = pulled.syncTimestamp
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
        private fun updateLocalProject(projResponse: ServerResponse.ProjectResponse) {
            val name = projResponse.name
            val email = projResponse.email
            val currencyName = projResponse.currencyName
            val deletionDisabled = projResponse.deletionDisabled
            val myAccessLevel = projResponse.myAccessLevel
            val archivedTs = projResponse.archivedTs

            val unchanged = project.name.isNotEmpty() &&
                name == project.name &&
                !project.email.isNullOrEmpty() &&
                email == project.email &&
                project.isDeletionDisabled == deletionDisabled &&
                project.myAccessLevel == myAccessLevel &&
                project.archivedTs == archivedTs &&
                project.currencyName != null &&
                currencyName == project.currencyName
            if (unchanged) return

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

        private fun syncPaymentModes(projResponse: ServerResponse.ProjectResponse) {
            val remote = projResponse.getPaymentModes(project.id)
            for (pm in remote) {
                val local = dbHelper.getPaymentMode(pm.remoteId, project.id)
                if (local == null) {
                    Log.d(TAG, "Add local pm : $pm")
                    dbHelper.addPaymentMode(pm)
                } else if (pm.name == local.name && pm.color == local.color && pm.icon == local.icon) {
                    Log.d(TAG, "Nothing to do for pm : $local")
                } else {
                    Log.d(TAG, "Update local pm : $pm")
                    dbHelper.updatePaymentMode(local.id, pm.name, pm.icon, pm.color)
                }
            }

            // Only settled rows may be dropped; one still waiting to be pushed is not "gone".
            val remoteIds = remote.map { it.remoteId }.toSet()
            for (local in dbHelper.getPaymentModes(project.id)) {
                if (local.state == DBBill.STATE_OK && local.remoteId !in remoteIds) {
                    dbHelper.deletePaymentMode(local.id)
                    Log.d(TAG, "Delete local pm : $local")
                }
            }
        }

        private fun syncCategories(projResponse: ServerResponse.ProjectResponse) {
            val remote = projResponse.getCategories(project.id)
            for (c in remote) {
                if (c.remoteId == DBBill.CATEGORY_REIMBURSEMENT) continue
                val local = dbHelper.getCategory(c.remoteId, project.id)
                if (local == null) {
                    Log.d(TAG, "Add local category : $c")
                    dbHelper.addCategory(c)
                } else if (c.name == local.name && c.color == local.color && c.icon == local.icon) {
                    Log.d(TAG, "Nothing to do for category : $local")
                } else {
                    Log.d(TAG, "Update local category : $c")
                    dbHelper.updateCategory(local.id, c.name, c.icon, c.color)
                }
            }

            val remoteIds = remote.map { it.remoteId }.toSet()
            for (local in dbHelper.getCategories(project.id)) {
                if (local.state == DBBill.STATE_OK && local.remoteId !in remoteIds) {
                    dbHelper.deleteCategory(local.id)
                    Log.d(TAG, "Delete local category : $local")
                }
            }
        }

        private fun syncCurrencies(projResponse: ServerResponse.ProjectResponse) {
            val remote = projResponse.getCurrencies(project.id)
            for (c in remote) {
                val local = dbHelper.getCurrency(c.remoteId, project.id)
                if (local == null) {
                    Log.d(TAG, "Add local currency : $c")
                    dbHelper.addCurrency(c)
                } else if (c.name == local.name && c.exchangeRate == local.exchangeRate) {
                    Log.d(TAG, "Nothing to do for currency : $local")
                } else {
                    Log.d(TAG, "Update local currency : $c")
                    dbHelper.updateCurrency(local.id, c.name, c.exchangeRate)
                }
            }

            val remoteIds = remote.map { it.remoteId }.toSet()
            for (local in dbHelper.getCurrencies(project.id)) {
                if (local.state == DBBill.STATE_OK && local.remoteId !in remoteIds) {
                    dbHelper.deleteCurrency(local.id)
                    Log.d(TAG, "Delete local currency : $local")
                }
            }
        }

        /** Returns the members the server listed, keyed by remote id, for the later cleanup pass. */
        private fun syncMembers(projResponse: ServerResponse.ProjectResponse): Map<Long, DBMember> {
            val remote = projResponse.getMembers(project.id)
            for (m in remote) {
                val local = dbHelper.getMember(m.remoteId, project.id)
                if (local == null) {
                    Log.d(TAG, "Add local member : $m")
                    val mid = dbHelper.addMember(m)
                    if (!m.ncUserId.isNullOrEmpty()) {
                        updateMemberAvatar(mid)
                    }
                } else {
                    updateLocalMember(m, local)
                }
            }
            return remote.associateBy { it.remoteId }
        }

        private fun updateLocalMember(remote: DBMember, local: DBMember) {
            val ncUserIdChanged = remote.ncUserId != local.ncUserId
            Log.d(TAG, "member NC user id : ${local.ncUserId} => ${remote.ncUserId} ID changed $ncUserIdChanged")
            if (ncUserIdChanged && remote.ncUserId == null) {
                remote.ncUserId = ""
            }

            // The server omits colours it has never been told about, which is not the same as
            // clearing them, so a null triple means "unchanged" rather than "no colour".
            val remoteHasNoColour = remote.r == null && remote.g == null && remote.b == null
            val colourUnchanged = remoteHasNoColour ||
                (remote.r == local.r && remote.g == local.g && remote.b == local.b)

            if (remote.name == local.name && remote.weight == local.weight &&
                remote.isActivated == local.isActivated && colourUnchanged && !ncUserIdChanged
            ) {
                Log.d(TAG, "Nothing to do for member : $local")
                if (!local.ncUserId.isNullOrEmpty() && local.avatar.isNullOrEmpty()) {
                    Log.d(TAG, "except updating avatar")
                    updateMemberAvatar(local.id)
                }
                return
            }

            Log.d(TAG, "Update local member : $remote")
            val r = if (remoteHasNoColour) local.r else remote.r
            val g = if (remoteHasNoColour) local.g else remote.g
            val b = if (remoteHasNoColour) local.b else remote.b
            val needAvatarUpdate = ncUserIdChanged && !remote.ncUserId.isNullOrEmpty()
            dbHelper.updateMember(
                local.id, remote.name, remote.weight,
                remote.isActivated, null, null,
                r, g, b, remote.ncUserId, if (ncUserIdChanged) "" else null
            )
            if (needAvatarUpdate) {
                updateMemberAvatar(local.id)
            }
        }

        private fun buildRemoteIdMaps(): RemoteIdMaps {
            val members = dbHelper.getMembersOfProject(project.id, null)
                .associate { it.remoteId to it.id }

            val categories = dbHelper.getCategories(project.id)
                .associate { it.remoteId to it.id }
                .toMutableMap()
            // The built-in categories keep their negative ids rather than getting local rows.
            categories[DBBill.CATEGORY_REIMBURSEMENT] = DBBill.CATEGORY_REIMBURSEMENT
            dbHelper.getCategories(project.id).filter { it.remoteId < 0 }
                .forEach { categories[it.remoteId] = it.id }

            val paymentModes = dbHelper.getPaymentModes(project.id)
                .associate { it.remoteId to it.id }
                .toMutableMap()
            dbHelper.getPaymentModes(project.id).filter { it.remoteId < 0 }
                .forEach { paymentModes[it.remoteId] = it.id }

            return RemoteIdMaps(members, categories, paymentModes)
        }

        /**
         * Fetches the bills, by paged walk where the server supports one and by complete fetch
         * otherwise. Falls back to the complete fetch whenever the walk cannot be trusted, so the
         * caller always gets a usable result.
         */
        private fun fetchRemoteBills(
            idMaps: RemoteIdMaps,
            localBillsByRemoteId: Map<Long, DBBill>
        ): RemoteBills {
            val usePagedWalk = project.type == ProjectType.COSPEND && !forceFullSync &&
                localBillsByRemoteId.isNotEmpty() && client!!.supportsPagedBills
            if (usePagedWalk) {
                walkBillPages(idMaps, localBillsByRemoteId)?.let { return it }
            }
            return fetchAllBills(idMaps)
        }

        /**
         * Walks back through pages of bills, newest first, until it has seen a run of
         * [UNCHANGED_RUN_TO_SETTLE] consecutive bills that already match locally - at which point
         * everything older is taken on trust.
         *
         * That only terminates while the server really does reverse the order and honour the
         * offset. One that does neither returns the same oldest-first page every time, and since
         * the walk compares against local rows it never writes, a single unknown bill keeps the
         * run at zero and the same page is requested forever. Returns null when the responses
         * show that happening, so the caller can fall back to the complete fetch.
         */
        private fun walkBillPages(
            idMaps: RemoteIdMaps,
            localBillsByRemoteId: Map<Long, DBBill>
        ): RemoteBills? {
            Log.d(TAG, "Starting partial sync for project ${project.remoteId}")
            val limit = 50
            val bills = mutableListOf<DBBill>()
            var syncTimestamp = project.lastSyncedTimestamp
            var offset = 0
            var previousPageIds: List<Long>? = null
            // Counted across pages, not restarted at each one: a run that begins near the end of
            // a page still finishes on the next.
            var unchangedRun = 0

            while (true) {
                val response = client!!.getBills(project, offset, limit, true, 0)
                val page = response.getBillsCospend(
                    project.id, idMaps.members, idMaps.categories, idMaps.paymentModes
                )
                if (page.isEmpty()) break

                if (page.first().timestamp < page.last().timestamp) {
                    Log.w(TAG, "Server returned bills oldest-first; the paged walk cannot be trusted")
                    return null
                }
                val pageIds = page.map { it.remoteId }
                if (pageIds == previousPageIds) {
                    Log.w(TAG, "Server returned the same page for offset $offset; it is ignoring the offset")
                    return null
                }
                previousPageIds = pageIds

                bills.addAll(page)
                if (offset == 0 && response.syncTimestamp > 0) {
                    syncTimestamp = response.syncTimestamp
                }

                var settled = false
                for (remote in page) {
                    val local = localBillsByRemoteId[remote.remoteId]
                    if (local != null && !hasChanged(local, remote)) {
                        unchangedRun++
                        if (unchangedRun >= UNCHANGED_RUN_TO_SETTLE) {
                            settled = true
                            break
                        }
                    } else {
                        unchangedRun = 0
                    }
                }

                // A short page is the end of the collection: there is nothing older to walk back
                // to, so asking for the next offset would only refetch it.
                if (settled || page.size < limit) break
                offset += limit
            }

            // A walk reports no allIds, so it never causes a local deletion.
            return RemoteBills(bills, emptyList(), syncTimestamp)
        }

        private fun fetchAllBills(idMaps: RemoteIdMaps): RemoteBills {
            Log.d(TAG, "Starting full sync for project ${project.remoteId}")
            val response = client!!.getBills(project)
            return if (project.type == ProjectType.IHATEMONEY) {
                val bills = response.getBillsIHM(
                    project.id, idMaps.members, idMaps.categories, idMaps.paymentModes
                )
                RemoteBills(bills, bills.map { it.remoteId }, 0L)
            } else {
                RemoteBills(
                    response.getBillsCospend(
                        project.id, idMaps.members, idMaps.categories, idMaps.paymentModes
                    ),
                    response.allBillIds,
                    response.syncTimestamp
                )
            }
        }

        private fun applyRemoteBills(
            remoteBills: List<DBBill>,
            localBillsByRemoteId: Map<Long, DBBill>
        ) {
            for (remoteBill in remoteBills) {
                val localBill = localBillsByRemoteId[remoteBill.remoteId]
                if (localBill == null) {
                    dbHelper.addBill(remoteBill)
                    nbPulledNewBills++
                    newBillsDialogText += "+ ${remoteBill.what}\n"
                    continue
                }

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
                } else {
                    Log.d(TAG, "Nothing to do for bill : $localBill")
                }

                syncBillOwers(localBill, remoteBill)
            }
        }

        private fun syncBillOwers(localBill: DBBill, remoteBill: DBBill) {
            val localMemberIds = localBill.billOwers.map { it.memberId }.toSet()
            val remoteMemberIds = remoteBill.billOwers.map { it.memberId }.toSet()

            for (rbo in remoteBill.billOwers) {
                if (rbo.memberId !in localMemberIds) {
                    dbHelper.addBillower(localBill.id, rbo.memberId)
                    Log.d(TAG, "Add local billOwer : $rbo")
                }
            }
            for (lbo in localBill.billOwers) {
                if (lbo.memberId !in remoteMemberIds) {
                    dbHelper.deleteBillOwer(lbo.id)
                    Log.d(TAG, "Delete local billOwer : $lbo")
                }
            }
        }

        /**
         * Drops local bills the server no longer has. An empty allIds means the server never told
         * us the full set - after a paged walk, say - and nothing may be deleted on that basis.
         */
        private fun deleteVanishedBills(pulled: RemoteBills, localBills: List<DBBill>) {
            if (pulled.allIds.isEmpty()) return

            val stillRemote: Set<Long> =
                if (project.type == ProjectType.COSPEND || project.type == ProjectType.IHATEMONEY) {
                    pulled.allIds.toSet()
                } else {
                    pulled.bills.map { it.remoteId }.toSet()
                }

            for (localBill in localBills) {
                if (localBill.remoteId !in stillRemote) {
                    dbHelper.deleteBill(localBill.id)
                    nbPulledDeletedBills++
                    deletedBillsDialogText += "🗑 ${localBill.what}\n"
                    Log.d(TAG, "Delete local bill : $localBill")
                }
            }
        }

        private fun deleteVanishedMembers(remoteMembersByRemoteId: Map<Long, DBMember>) {
            for (localMember in dbHelper.getMembersOfProject(project.id, null)) {
                if (remoteMembersByRemoteId.containsKey(localMember.remoteId)) continue

                // A member still named by a bill cannot be removed without orphaning it.
                if (dbHelper.getBillsOfMember(localMember.id).isEmpty() &&
                    dbHelper.getBillowersOfMember(localMember.id).isEmpty()
                ) {
                    dbHelper.deleteMember(localMember.id)
                    Log.d(TAG, "Delete local member : $localMember")
                } else {
                    Log.d(TAG, "WARNING local member : ${localMember.name} does not exist remotely but is still involved in some bills")
                }
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

    fun editRemoteProject(
        projId: Long, 
        newName: String? = null, 
        newEmail: String? = null,
        newPassword: String? = null, 
        newMainCurrencyName: String? = null, 
        newArchivedTs: Long? = null, 
        callback: ICallback
    ): Boolean {
        updateNetworkStatus()
        if (isSyncPossible) {
            EditRemoteProjectTask(projId, newName, newEmail, newPassword, newMainCurrencyName, newArchivedTs, callback).execute()
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
        private val newArchivedTs: Long?,
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
                // Pass current project values if the new ones are null to ensure a complete project object is sent to the server
                val currentProj = project!!
                val finalName = (newName ?: currentProj.name).let { if (it.isBlank() || it == "null") currentProj.remoteId else it }
                // Stay null when the project has no main currency, so the PUT omits currencyName
                // instead of silently setting the server-side currency.
                val finalCurrency = (newMainCurrencyName ?: currentProj.currencyName)
                    ?.takeUnless { it.isBlank() || it == "null" }
                
                val response = client!!.editRemoteProject(
                    currentProj,
                    finalName,
                    newEmail ?: currentProj.email,
                    newPassword,
                    finalCurrency,
                    newArchivedTs
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
                    projId = project!!.id,
                    newName = newName,
                    newEmail = newEmail,
                    newPassword = newPassword,
                    newArchivedTs = newArchivedTs
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

    internal fun hasChanged(localBill: DBBill, remoteBill: DBBill): Boolean {
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
            if (status == LoginStatus.OK) {
                preferences.edit {
                    putLong(appContext.getString(R.string.pref_key_last_account_sync_timestamp), System.currentTimeMillis())
                }
            }
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

        /**
         * How many consecutive bills, walking newest to oldest, must already match locally before
         * the paged walk concludes that every older bill matches too.
         *
         * A whole page of 50 had to match before, so one edit anywhere in a page forced another
         * page to be fetched. A trailing run is the same bet on a smaller sample: it can settle
         * part way into a page, and it carries across page boundaries rather than restarting.
         */
        private const val UNCHANGED_RUN_TO_SETTLE = 25

        private var instance: CowspentServerSyncHelper? = null
        private val projectIdsToSync: MutableList<Long> = ArrayList()

        @Synchronized
        fun getInstance(dbHelper: CowspentSQLiteOpenHelper): CowspentServerSyncHelper {
            if (instance == null) {
                instance = CowspentServerSyncHelper(dbHelper)
            }
            return instance!!
        }

        @VisibleForTesting
        fun resetInstance() {
            instance = null
            projectIdsToSync.clear()
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
