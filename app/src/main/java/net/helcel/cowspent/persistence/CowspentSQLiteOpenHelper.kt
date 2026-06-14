package net.helcel.cowspent.persistence

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R
import net.helcel.cowspent.android.main.BillsListViewActivity
import net.helcel.cowspent.model.*
import net.helcel.cowspent.util.SupportUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helps to add, get, update and delete bills, members, projects with the option to trigger a sync with the server.
 */
class CowspentSQLiteOpenHelper private constructor(val context: Context) :
    SQLiteOpenHelper(context, database_name, null, database_version) {

    val cowspentServerSyncHelper: CowspentServerSyncHelper = CowspentServerSyncHelper.getInstance(this)

    override fun onCreate(db: SQLiteDatabase) {
        createTableMembers(db)
        createTableBills(db)
        createTableBillowers(db)
        createTableProjects(db)
        createTableAccountProjects(db)
        createTableCategories(db)
        createTablePaymentModes(db)
        createTableCurrencies(db)
        createIndexes(db)
    }

    private fun createTableMembers(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_members ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId INTEGER, " +
                    "$key_projectid INTEGER, " +
                    "$key_name TEXT, " +
                    "$key_activated INTEGER, " +
                    "$key_weight FLOAT, " +
                    "$key_r INTEGER, " +
                    "$key_g INTEGER, " +
                    "$key_b INTEGER, " +
                    "$key_nc_userid TEXT, " +
                    "$key_avatar TEXT, " +
                    "$key_state INTEGER)"
        )
    }

    private fun createTableProjects(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_projects ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId TEXT, " +
                    "$key_name TEXT, " +
                    "$key_ihmUrl TEXT, " +
                    "$key_password TEXT, " +
                    "$key_bearer_token TEXT, " +
                    "$key_currencyName TEXT, " +
                    "$key_deletionDisabled INTEGER, " +
                    "$key_myAccessLevel INTEGER DEFAULT ${DBProject.ACCESS_LEVEL_UNKNOWN}, " +
                    "$key_lastPayerId INTEGER, " +
                    "$key_lastSyncTimestamp INTEGER DEFAULT 0, " +
                    "$key_email TEXT, " +
                    "$key_type TEXT, " +
                    "$key_archived INTEGER DEFAULT 0)"
        )
    }

    private fun createTableBills(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_bills ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId INTEGER, " +
                    "$key_projectid INTEGER, " +
                    "$key_payer_id INTEGER, " +
                    "$key_amount FLOAT, " +
                    "$key_what TEXT, " +
                    "$key_state INTEGER, " +
                    "$key_timestamp INTEGER, " +
                    "$key_payment_mode TEXT DEFAULT \"n\", " +
                    "$key_category_id INTEGER DEFAULT 0, " +
                    "$key_repeat TEXT, " +
                    "$key_comment TEXT DEFAULT \"\", " +
                    "$key_payment_mode_id INTEGER DEFAULT 0)"
        )
    }

    private fun createTableBillowers(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_billowers ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_billId INTEGER, " +
                    "$key_member_id INTEGER)"
        )
    }

    private fun createTableAccountProjects(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_account_projects ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId TEXT, " +
                    "$key_name TEXT, " +
                    "$key_ncUrl TEXT, " +
                    "$key_bearer_token TEXT, " +
                    "$key_password TEXT, " +
                    "$key_archived INTEGER DEFAULT 0)"
        )
    }

    private fun createTableCategories(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_categories ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId INTEGER, " +
                    "$key_projectid INTEGER, " +
                    "$key_name TEXT, " +
                    "$key_icon TEXT, " +
                    "$key_color TEXT)"
        )
    }

    private fun createTablePaymentModes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_payment_modes ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId INTEGER, " +
                    "$key_projectid INTEGER, " +
                    "$key_name TEXT, " +
                    "$key_icon TEXT, " +
                    "$key_color TEXT)"
        )
    }

    private fun createTableCurrencies(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $table_currencies ( " +
                    "$key_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$key_remoteId INTEGER, " +
                    "$key_projectid INTEGER, " +
                    "$key_name TEXT, " +
                    "$key_exchangeRate FLOAT, " +
                    "$key_state INTEGER)"
        )
    }

    @SuppressLint("Range")
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_lastPayerId INTEGER DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_repeat TEXT")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_type TEXT")
            val projects = getProjectsCustom("", arrayOf(), default_order, db)
            for (project in projects) {
                val url = project.serverUrl
                project.type = if (url == null) ProjectType.LOCAL else ProjectType.COSPEND
                updateProject(
                    project.id, project.name, project.email,
                    project.password, project.lastPayerId, project.type,
                    project.lastSyncedTimestamp, project.currencyName,
                    project.isDeletionDisabled, project.myAccessLevel, project.bearerToken,
                    project.archivedTs, db
                )
            }
        }
        if (oldVersion < 5) {
            createTableAccountProjects(db)
            createIndex(db, table_account_projects)
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_payment_mode TEXT DEFAULT \"n\"")
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_category_id INTEGER DEFAULT 0")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE $table_members ADD COLUMN $key_r INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $table_members ADD COLUMN $key_g INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE $table_members ADD COLUMN $key_b INTEGER DEFAULT NULL")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_lastSyncTimestamp INTEGER DEFAULT 0")
        }
        if (oldVersion < 9) {
            createTableCategories(db)
            createIndex(db, table_categories)
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_currencyName TEXT")
            createTableCurrencies(db)
            createIndex(db, table_currencies)
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_timestamp INTEGER")
            val idToTs: MutableMap<Long, Long> = HashMap()
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            val cursor = db.query(table_bills, arrayOf(key_id, "DATE"), "", arrayOf(), null, null, null)
            val dateNow = Date()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndex(key_id))
                val dateStr = cursor.getString(cursor.getColumnIndex("DATE"))
                val date = try {
                    sdfDate.parse(dateStr)
                } catch (_: Exception) {
                    dateNow
                }
                val timestamp = (date?.time ?: System.currentTimeMillis()) / 1000
                idToTs[id] = timestamp
            }
            cursor.close()
            for (billId in idToTs.keys) {
                val timestamp = idToTs[billId]!!
                val values = ContentValues()
                values.put(key_timestamp, timestamp)
                db.update(table_bills, values, "$key_id = ?", arrayOf(billId.toString()))
            }
        }
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE $table_members ADD COLUMN $key_nc_userid TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE $table_members ADD COLUMN $key_avatar TEXT DEFAULT NULL")
        }
        if (oldVersion < 13) {
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_comment TEXT DEFAULT \"\"")
        }
        if (oldVersion < 14) {
            val projects = getProjectsCustom("", arrayOf(), default_order, db)
            for (project in projects) {
                updateProject(
                    project.id, project.name, project.email,
                    project.password, project.lastPayerId, project.type,
                    0L, project.currencyName, project.isDeletionDisabled, project.myAccessLevel, project.bearerToken,
                    project.archivedTs, db
                )
            }
        }
        if (oldVersion < 15) {
            db.execSQL("ALTER TABLE $table_bills ADD COLUMN $key_payment_mode_id INTEGER DEFAULT 0")
            for (key in DBBill.oldPmIdToNew.keys) {
                val values = ContentValues()
                values.put(key_payment_mode_id, DBBill.oldPmIdToNew[key])
                db.update(table_bills, values, "$key_payment_mode = ?", arrayOf(key))
            }
            createTablePaymentModes(db)
            createIndex(db, table_payment_modes)
        }
        if (oldVersion < 16) {
            db.execSQL("ALTER TABLE $table_currencies ADD COLUMN $key_state INTEGER DEFAULT ${DBBill.STATE_OK}")
        }
        if (oldVersion < 17) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_deletionDisabled INTEGER")
        }
        if (oldVersion < 18) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_myAccessLevel INTEGER DEFAULT ${DBProject.ACCESS_LEVEL_UNKNOWN}")
        }
        if (oldVersion < 19) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_bearer_token TEXT")
        }
        if (oldVersion < 20) {
            db.execSQL("ALTER TABLE $table_projects ADD COLUMN $key_archived INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $table_account_projects ADD COLUMN $key_archived INTEGER DEFAULT 0")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        recreateDatabase(db)
    }

    private fun recreateDatabase(db: SQLiteDatabase) {
        dropIndexes(db)
        db.execSQL("DROP TABLE $table_members")
        db.execSQL("DROP TABLE $table_projects")
        db.execSQL("DROP TABLE $table_bills")
        db.execSQL("DROP TABLE $table_billowers")
        db.execSQL("DROP TABLE $table_account_projects")
        db.execSQL("DROP TABLE $table_categories")
        db.execSQL("DROP TABLE $table_payment_modes")
        db.execSQL("DROP TABLE $table_currencies")
        onCreate(db)
    }

    private fun dropIndexes(db: SQLiteDatabase) {
        val c = db.query("sqlite_master", arrayOf("name"), "type=?", arrayOf("index"), null, null, null)
        while (c.moveToNext()) {
            db.execSQL("DROP INDEX " + c.getString(0))
        }
        c.close()
    }

    private fun createIndexes(db: SQLiteDatabase) {
        createIndex(db, table_members)
        createIndex(db, table_projects)
        createIndex(db, table_bills)
        createIndex(db, table_billowers)
        createIndex(db, table_account_projects)
        createIndex(db, table_categories)
        createIndex(db, table_payment_modes)
        createIndex(db, table_currencies)
    }

    private fun createIndex(db: SQLiteDatabase, table: String) {
        val indexName = "${table}_${key_id}_idx"
        db.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $table($key_id)")
    }

    fun addAccountProject(accountProject: DBAccountProject): Long {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, accountProject.remoteId)
        values.put(key_password, accountProject.password)
        values.put(key_ncUrl, accountProject.ncUrl)
        values.put(key_name, accountProject.name)
        values.put(key_archived, accountProject.archivedTs ?: 0L)
        return db.insert(table_account_projects, null, values)
    }

    val accountProjects: List<DBAccountProject>
        get() = getAccountProjectsCustom("", arrayOf(), default_order)

    @WorkerThread
    private fun getAccountProjectsCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBAccountProject> {
        return getAccountProjectsCustom(selection, selectionArgs, orderBy, readableDatabase)
    }

    @WorkerThread
    private fun getAccountProjectsCustom(selection: String, selectionArgs: Array<String>, orderBy: String?, db: SQLiteDatabase): List<DBAccountProject> {
        val cursor = db.query(table_account_projects, columnsAccountProjects, selection, selectionArgs, null, null, orderBy)
        val accountProjects: MutableList<DBAccountProject> = ArrayList()
        while (cursor.moveToNext()) {
            accountProjects.add(getAccountProjectFromCursor(cursor))
        }
        cursor.close()
        return accountProjects
    }

    @SuppressLint("Range")
    private fun getAccountProjectFromCursor(cursor: Cursor): DBAccountProject {
        val archivedTs = cursor.getLong(cursor.getColumnIndex(key_archived))
        return DBAccountProject(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getString(cursor.getColumnIndex(key_remoteId)),
            cursor.getString(cursor.getColumnIndex(key_password)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getString(cursor.getColumnIndex(key_ncUrl)),
            if (archivedTs > 0) archivedTs else null
        )
    }

    fun clearAccountProjects() {
        val db = writableDatabase
        db.delete(table_account_projects, null, null)
    }

    fun addPaymentMode(paymentMode: DBPaymentMode): Long {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, paymentMode.remoteId)
        values.put(key_projectid, paymentMode.projectId)
        values.put(key_name, paymentMode.name)
        values.put(key_icon, paymentMode.icon)
        values.put(key_color, paymentMode.color)
        return db.insert(table_payment_modes, null, values)
    }

    fun getPaymentMode(remoteId: Long, projectId: Long): DBPaymentMode? {
        val paymentModes = getPaymentModesCustom(
            "$key_remoteId = ? AND $key_projectid = ?",
            arrayOf(remoteId.toString(), projectId.toString()),
            null
        )
        return if (paymentModes.isEmpty()) null else paymentModes[0]
    }

    fun getPaymentModes(projectId: Long): List<DBPaymentMode> {
        return getPaymentModesCustom("$key_projectid = ?", arrayOf(projectId.toString()), null)
    }

    @WorkerThread
    private fun getPaymentModesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBPaymentMode> {
        return getPaymentModesCustom(selection, selectionArgs, orderBy, readableDatabase)
    }

    @WorkerThread
    private fun getPaymentModesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?, db: SQLiteDatabase): List<DBPaymentMode> {
        val cursor = db.query(table_payment_modes, columnsPaymentModes, selection, selectionArgs, null, null, orderBy)
        val paymentModes: MutableList<DBPaymentMode> = ArrayList()
        while (cursor.moveToNext()) {
            paymentModes.add(getPaymentModeFromCursor(cursor))
        }
        cursor.close()
        return paymentModes
    }

    @SuppressLint("Range")
    private fun getPaymentModeFromCursor(cursor: Cursor): DBPaymentMode {
        return DBPaymentMode(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_remoteId)),
            cursor.getLong(cursor.getColumnIndex(key_projectid)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getString(cursor.getColumnIndex(key_icon)),
            cursor.getString(cursor.getColumnIndex(key_color))
        )
    }

    fun updatePaymentMode(id: Long, name: String?, icon: String?, color: String?) {
        val db = writableDatabase
        val values = ContentValues()
        if (name != null) values.put(key_name, name)
        if (icon != null) values.put(key_icon, icon)
        if (color != null) values.put(key_color, color)
        if (values.size() > 0) {
            db.update(table_payment_modes, values, "$key_id = ?", arrayOf(id.toString()))
        }
    }

    fun deletePaymentMode(id: Long) {
        val db = writableDatabase
        db.delete(table_payment_modes, "$key_id = ?", arrayOf(id.toString()))
    }

    fun addCategory(category: DBCategory): Long {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, category.remoteId)
        values.put(key_projectid, category.projectId)
        values.put(key_name, category.name)
        values.put(key_icon, category.icon)
        values.put(key_color, category.color)
        return db.insert(table_categories, null, values)
    }

    fun getCategory(remoteId: Long, projectId: Long): DBCategory? {
        val categories = getCategoriesCustom(
            "$key_remoteId = ? AND $key_projectid = ?",
            arrayOf(remoteId.toString(), projectId.toString()),
            null
        )
        return if (categories.isEmpty()) null else categories[0]
    }

    fun getCategories(projectId: Long): List<DBCategory> {
        return getCategoriesCustom("$key_projectid = ?", arrayOf(projectId.toString()), null)
    }

    @WorkerThread
    private fun getCategoriesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBCategory> {
        return getCategoriesCustom(selection, selectionArgs, orderBy, readableDatabase)
    }

    @WorkerThread
    private fun getCategoriesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?, db: SQLiteDatabase): List<DBCategory> {
        val cursor = db.query(table_categories, columnsCategories, selection, selectionArgs, null, null, orderBy)
        val categories: MutableList<DBCategory> = ArrayList()
        while (cursor.moveToNext()) {
            categories.add(getCategoryFromCursor(cursor))
        }
        cursor.close()
        return categories
    }

    @SuppressLint("Range")
    private fun getCategoryFromCursor(cursor: Cursor): DBCategory {
        return DBCategory(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_remoteId)),
            cursor.getLong(cursor.getColumnIndex(key_projectid)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getString(cursor.getColumnIndex(key_icon)),
            cursor.getString(cursor.getColumnIndex(key_color))
        )
    }

    fun updateCategory(id: Long, name: String?, icon: String?, color: String?) {
        val db = writableDatabase
        val values = ContentValues()
        if (name != null) values.put(key_name, name)
        if (icon != null) values.put(key_icon, icon)
        if (color != null) values.put(key_color, color)
        if (values.size() > 0) {
            db.update(table_categories, values, "$key_id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.delete(table_categories, "$key_id = ?", arrayOf(id.toString()))
    }

    fun addCurrency(currency: DBCurrency): Long {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, currency.remoteId)
        values.put(key_projectid, currency.projectId)
        values.put(key_name, currency.name)
        values.put(key_exchangeRate, currency.exchangeRate)
        values.put(key_state, currency.state)
        return db.insert(table_currencies, null, values)
    }

    fun addCurrencyAndSync(m: DBCurrency) {
        addCurrency(m)
        val proj = getProject(m.projectId)
        if (proj != null) syncIfRemote(proj)
    }

    fun syncIfRemote(proj: DBProject) {
        if (!proj.isLocal) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val offlineMode = preferences.getBoolean(context.getString(R.string.pref_key_offline_mode), false)
            if (!offlineMode) {
                cowspentServerSyncHelper.scheduleSync(true, proj.id)
            }
        }
    }

    fun getCurrency(remoteId: Long, projectId: Long): DBCurrency? {
        val currencies = getCurrenciesCustom(
            "$key_remoteId = ? AND $key_projectid = ?",
            arrayOf(remoteId.toString(), projectId.toString()),
            null
        )
        return if (currencies.isEmpty()) null else currencies[0]
    }

    fun getCurrency(id: Long): DBCurrency? {
        val currencies = getCurrenciesCustom("$key_id = ?", arrayOf(id.toString()), null)
        return if (currencies.isEmpty()) null else currencies[0]
    }

    fun updateCurrency(id: Long, name: String?, exchangeRate: Double?) {
        val db = writableDatabase
        val values = ContentValues()
        if (name != null) values.put(key_name, name)
        if (exchangeRate != null) values.put(key_exchangeRate, exchangeRate)
        if (values.size() > 0) {
            db.update(table_currencies, values, "$key_id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteCurrency(id: Long) {
        val db = writableDatabase
        db.delete(table_currencies, "$key_id = ?", arrayOf(id.toString()))
    }

    fun getCurrencies(projectId: Long): List<DBCurrency> {
        return getCurrenciesCustom(
            "$key_projectid = ? AND $key_state != ?",
            arrayOf(projectId.toString(), DBBill.STATE_DELETED.toString()),
            null
        )
    }

    @WorkerThread
    private fun getCurrenciesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBCurrency> {
        return getCurrenciesCustom(selection, selectionArgs, orderBy, readableDatabase)
    }

    @WorkerThread
    private fun getCurrenciesCustom(selection: String, selectionArgs: Array<String>, orderBy: String?, db: SQLiteDatabase): List<DBCurrency> {
        val cursor = db.query(table_currencies, columnsCurrencies, selection, selectionArgs, null, null, orderBy)
        val currencies: MutableList<DBCurrency> = ArrayList()
        while (cursor.moveToNext()) {
            currencies.add(getCurrencyFromCursor(cursor))
        }
        cursor.close()
        return currencies
    }

    @SuppressLint("Range")
    private fun getCurrencyFromCursor(cursor: Cursor): DBCurrency {
        return DBCurrency(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_remoteId)),
            cursor.getLong(cursor.getColumnIndex(key_projectid)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getDouble(cursor.getColumnIndex(key_exchangeRate)),
            cursor.getInt(cursor.getColumnIndex(key_state))
        )
    }

    fun setCurrencyStateSync(currencyId: Long, state: Int) {
        setCurrencyState(currencyId, state)
        val currency = getCurrency(currencyId)
        if (currency != null) {
            val project = getProject(currency.projectId)
            if (project != null) syncIfRemote(project)
        }
    }

    fun setCurrencyState(currencyId: Long, state: Int) {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_state, state)
        db.update(table_currencies, values, "$key_id = ?", arrayOf(currencyId.toString()))
    }

    fun addProject(project: DBProject): Long {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, project.remoteId)
        values.put(key_password, project.password)
        values.put(key_bearer_token, project.bearerToken)
        values.put(key_email, project.email)
        values.put(key_name, project.name)
        values.put(key_ihmUrl, project.serverUrl)
        values.put(key_type, project.type.id)
        values.put(key_archived, project.archivedTs ?: 0L)
        return db.insert(table_projects, null, values)
    }

    fun getProject(id: Long): DBProject? {
        val projects = getProjectsCustom("$key_id = ?", arrayOf(id.toString()), null)
        return if (projects.isEmpty()) null else projects[0]
    }

    val projects: List<DBProject>
        get() = getProjectsCustom("", arrayOf(), default_order)

    @WorkerThread
    private fun getProjectsCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBProject> {
        return getProjectsCustom(selection, selectionArgs, orderBy, readableDatabase)
    }

    @WorkerThread
    private fun getProjectsCustom(selection: String, selectionArgs: Array<String>, orderBy: String?, db: SQLiteDatabase): List<DBProject> {
        val cursor = db.query(table_projects, columnsProjects, selection, selectionArgs, null, null, orderBy)
        val projects: MutableList<DBProject> = ArrayList()
        while (cursor.moveToNext()) {
            projects.add(getProjectFromCursor(cursor))
        }
        cursor.close()
        return projects
    }

    @SuppressLint("Range")
    private fun getProjectFromCursor(cursor: Cursor): DBProject {
        val archivedTs = cursor.getLong(cursor.getColumnIndex(key_archived))
        return DBProject(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getString(cursor.getColumnIndex(key_remoteId)),
            cursor.getString(cursor.getColumnIndex(key_password)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getString(cursor.getColumnIndex(key_ihmUrl)),
            cursor.getString(cursor.getColumnIndex(key_email)),
            cursor.getLong(cursor.getColumnIndex(key_lastPayerId)),
            ProjectType.getTypeById(cursor.getString(cursor.getColumnIndex(key_type)))!!,
            cursor.getLong(cursor.getColumnIndex(key_lastSyncTimestamp)),
            cursor.getString(cursor.getColumnIndex(key_currencyName)),
            cursor.getInt(cursor.getColumnIndex(key_deletionDisabled)) != 0,
            cursor.getInt(cursor.getColumnIndex(key_myAccessLevel)),
            cursor.getString(cursor.getColumnIndex(key_bearer_token)),
            if (archivedTs > 0) archivedTs else null,
            cursor.getLong(cursor.getColumnIndex(key_latest_bill_ts))
        )
    }

    fun deleteProject(id: Long) {
        val db = writableDatabase
        for (b in getBillsOfProject(id)) {
            deleteBill(b.id)
        }
        db.delete(table_members, "$key_projectid = ?", arrayOf(id.toString()))
        db.delete(table_projects, "$key_id = ?", arrayOf(id.toString()))
    }

    fun updateProject(
        projId: Long, newName: String?, newEmail: String?,
        newPassword: String?, newLastPayerId: Long?,
        newLastSyncedTimestamp: Long?,
        newCurrencyName: String?, newDeletionDisabled: Boolean?,
        newMyAccessLevel: Int?, newBearerToken: String?,
        newArchivedTs: Long? = null
    ) {
        val db = writableDatabase
        val values = ContentValues()
        if (newName != null) values.put(key_name, newName)
        if (newEmail != null) values.put(key_email, newEmail)
        if (newPassword != null) values.put(key_password, newPassword)
        if (newBearerToken != null) values.put(key_bearer_token, newBearerToken)
        if (newLastPayerId != null) values.put(key_lastPayerId, newLastPayerId)
        if (newLastSyncedTimestamp != null) values.put(key_lastSyncTimestamp, newLastSyncedTimestamp)
        if (newCurrencyName != null) values.put(key_currencyName, newCurrencyName)
        if (newDeletionDisabled != null) values.put(key_deletionDisabled, if (newDeletionDisabled) 1 else 0)
        if (newMyAccessLevel != null) values.put(key_myAccessLevel, newMyAccessLevel)
        if (newArchivedTs != null) values.put(key_archived, newArchivedTs)
        if (values.size() > 0) {
            db.update(table_projects, values, "$key_id = ?", arrayOf(projId.toString()))
        }
    }

    fun updateProject(
        projId: Long, newName: String?, newEmail: String?,
        newPassword: String?, newLastPayerId: Long?,
        projectType: ProjectType, newLastSyncedTimestamp: Long?,
        newCurrencyName: String?, newDeletionDisabled: Boolean?,
        newMyAccessLevel: Int?, newBearerToken: String?,
        newArchivedTs: Long? = null
    ) {
        val db = writableDatabase
        updateProject(
            projId, newName, newEmail, newPassword, newLastPayerId, projectType,
            newLastSyncedTimestamp, newCurrencyName, newDeletionDisabled, newMyAccessLevel,
            newBearerToken, newArchivedTs, db
        )
    }

    private fun updateProject(
        projId: Long, newName: String?, newEmail: String?,
        newPassword: String?, newLastPayerId: Long?,
        projectType: ProjectType, newLastSyncedTimestamp: Long?,
        newCurrencyName: String?, newDeletionDisabled: Boolean?,
        newMyAccessLevel: Int?, newBearerToken: String?,
        newArchivedTs: Long?, db: SQLiteDatabase
    ) {
        val values = ContentValues()
        if (newName != null) values.put(key_name, newName)
        if (newEmail != null) values.put(key_email, newEmail)
        if (newPassword != null) values.put(key_password, newPassword)
        if (newBearerToken != null) values.put(key_bearer_token, newBearerToken)
        if (newLastPayerId != null) values.put(key_lastPayerId, newLastPayerId)
        if (newLastSyncedTimestamp != null) values.put(key_lastSyncTimestamp, newLastSyncedTimestamp)
        if (newCurrencyName != null) values.put(key_currencyName, newCurrencyName)
        if (newDeletionDisabled != null) values.put(key_deletionDisabled, if (newDeletionDisabled) 1 else 0)
        if (newMyAccessLevel != null) values.put(key_myAccessLevel, newMyAccessLevel)
        if (newArchivedTs != null) values.put(key_archived, newArchivedTs)
        values.put(key_type, projectType.id)
        if (values.size() > 0) {
            db.update(table_projects, values, "$key_id = ?", arrayOf(projId.toString()))
        }
    }

    fun addMember(m: DBMember): Long {
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[add member]") }
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, m.remoteId)
        values.put(key_projectid, m.projectId)
        values.put(key_name, m.name)
        values.put(key_activated, if (m.isActivated) "1" else "0")
        values.put(key_weight, m.weight)
        values.put(key_state, m.state)
        values.put(key_r, m.r)
        values.put(key_g, m.g)
        values.put(key_b, m.b)
        values.put(key_nc_userid, m.ncUserId)
        values.put(key_avatar, m.avatar)
        return db.insert(table_members, null, values)
    }

    fun addMemberAndSync(m: DBMember) {
        addMember(m)
        val proj = getProject(m.projectId)
        if (proj != null) syncIfRemote(proj)
    }

    fun updateMember(
        memberId: Long, newName: String?, newWeight: Double?,
        newActivated: Boolean?, newState: Int?, newRemoteId: Long?,
        newR: Int?, newG: Int?, newB: Int?,
        newNcUserId: String?, newAvatar: String?
    ) {
        val db = writableDatabase
        val values = ContentValues()
        if (newName != null) values.put(key_name, newName)
        if (newWeight != null) values.put(key_weight, newWeight)
        if (newRemoteId != null) values.put(key_remoteId, newRemoteId)
        if (newActivated != null) values.put(key_activated, if (newActivated) 1 else 0)
        if (newState != null) values.put(key_state, newState)
        if (newR != null) values.put(key_r, newR)
        if (newG != null) values.put(key_g, newG)
        if (newB != null) values.put(key_b, newB)
        if (newNcUserId != null) values.put(key_nc_userid, newNcUserId)
        if (newAvatar != null) values.put(key_avatar, newAvatar)
        if (values.size() > 0) {
            db.update(table_members, values, "$key_id = ?", arrayOf(memberId.toString()))
        }
    }

    fun updateMemberAndSync(
        m: DBMember, newName: String?, newWeight: Double?,
        newActivated: Boolean?,
        newR: Int?, newG: Int?, newB: Int?,
        newNcUserId: String?, newAvatar: String?
    ) {
        val newState = if (m.state == DBBill.STATE_ADDED) DBBill.STATE_ADDED else DBBill.STATE_EDITED
        updateMember(
            m.id, newName, newWeight, newActivated, newState, null,
            newR, newG, newB, newNcUserId, newAvatar
        )
        Log.v(TAG, "UPDATE MEMBER AND SYNC")
        val proj = getProject(m.projectId)
        if (proj != null) syncIfRemote(proj)
    }

    fun getMembersOfProject(projId: Long, orderByParam: String?): List<DBMember> {
        val orderBy = orderByParam ?: "LOWER($key_name)"
        return getMembersCustom("$key_projectid = ?", arrayOf(projId.toString()), "$orderBy ASC")
    }

    fun getMembersOfProjectWithState(projId: Long, state: Int): List<DBMember> {
        return getMembersCustom(
            "$key_projectid = ? AND $key_state = ?",
            arrayOf(projId.toString(), state.toString()),
            "$key_name ASC"
        )
    }

    fun getActivatedMembersOfProject(projId: Long): List<DBMember> {
        return getMembersCustom(
            "$key_projectid = ? AND $key_activated = 1",
            arrayOf(projId.toString()),
            "$key_name ASC"
        )
    }

    fun getMember(remoteId: Long, projId: Long): DBMember? {
        val members = getMembersCustom(
            "$key_remoteId = ? AND $key_projectid = ?",
            arrayOf(remoteId.toString(), projId.toString()),
            null
        )
        return if (members.isEmpty()) null else members[0]
    }

    fun getMember(id: Long): DBMember? {
        val members = getMembersCustom("$key_id = ?", arrayOf(id.toString()), null)
        return if (members.isEmpty()) null else members[0]
    }

    @WorkerThread
    private fun getMembersCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBMember> {
        val db = readableDatabase
        val cursor = db.query(table_members, columnsMembers, selection, selectionArgs, null, null, orderBy)
        val members: MutableList<DBMember> = ArrayList()
        while (cursor.moveToNext()) {
            members.add(getMemberFromCursor(cursor))
        }
        cursor.close()
        return members
    }

    @SuppressLint("Range")
    private fun getMemberFromCursor(cursor: Cursor): DBMember {
        return DBMember(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_remoteId)),
            cursor.getLong(cursor.getColumnIndex(key_projectid)),
            cursor.getString(cursor.getColumnIndex(key_name)),
            cursor.getInt(cursor.getColumnIndex(key_activated)) == 1,
            cursor.getDouble(cursor.getColumnIndex(key_weight)),
            cursor.getInt(cursor.getColumnIndex(key_state)),
            if (cursor.isNull(cursor.getColumnIndex(key_r))) null else cursor.getInt(cursor.getColumnIndex(key_r)),
            if (cursor.isNull(cursor.getColumnIndex(key_g))) null else cursor.getInt(cursor.getColumnIndex(key_g)),
            if (cursor.isNull(cursor.getColumnIndex(key_b))) null else cursor.getInt(cursor.getColumnIndex(key_b)),
            if (cursor.isNull(cursor.getColumnIndex(key_nc_userid))) null else cursor.getString(cursor.getColumnIndex(key_nc_userid)),
            if (cursor.isNull(cursor.getColumnIndex(key_avatar))) null else cursor.getString(cursor.getColumnIndex(key_avatar))
        )
    }

    fun deleteMember(id: Long) {
        val db = writableDatabase
        db.delete(table_members, "$key_id = ?", arrayOf(id.toString()))
    }

    fun addBill(b: DBBill): Long {
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[add bill]") }
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_remoteId, b.remoteId)
        values.put(key_projectid, b.projectId)
        values.put(key_payer_id, b.payerId)
        values.put(key_amount, b.amount)
        values.put(key_timestamp, b.timestamp)
        values.put(key_what, b.what)
        values.put(key_state, b.state)
        values.put(key_repeat, b.repeat)
        values.put(key_payment_mode, b.paymentMode)
        values.put(key_payment_mode_id, b.paymentModeRemoteId)
        values.put(key_category_id, b.categoryRemoteId)
        values.put(key_comment, b.comment)
        val billId = db.insert(table_bills, null, values)
        for (bo in b.billOwers) {
            addBillower(billId, bo.memberId)
        }
        return billId
    }

    fun setBillState(billId: Long, state: Int) {
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_state, state)
        db.update(table_bills, values, "$key_id = ?", arrayOf(billId.toString()))
    }

    fun updateBill(
        billId: Long, newRemoteId: Long?, newPayerId: Long?,
        newAmount: Double?, newTimestamp: Long?,
        newWhat: String?, newState: Int?,
        newRepeat: String?,
        newPaymentMode: String?, newPaymentModeRemoteId: Int?,
        newCategoryId: Int?, newComment: String?
    ) {
        val db = writableDatabase
        val values = ContentValues()
        if (newTimestamp != null) values.put(key_timestamp, newTimestamp)
        if (newWhat != null) values.put(key_what, newWhat)
        if (newRemoteId != null) values.put(key_remoteId, newRemoteId)
        if (newPayerId != null) values.put(key_payer_id, newPayerId)
        if (newAmount != null) values.put(key_amount, newAmount)
        if (newState != null) values.put(key_state, newState)
        if (newRepeat != null) values.put(key_repeat, newRepeat)
        if (newPaymentMode != null) values.put(key_payment_mode, newPaymentMode)
        if (newPaymentModeRemoteId != null) values.put(key_payment_mode_id, newPaymentModeRemoteId)
        if (newCategoryId != null) values.put(key_category_id, newCategoryId)
        if (newComment != null) values.put(key_comment, newComment)
        if (values.size() > 0) {
            db.update(table_bills, values, "$key_id = ?", arrayOf(billId.toString()))
        }
    }

    fun updateBillAndSync(
        bill: DBBill, newPayerId: Long, newAmount: Double,
        newTimestamp: Long?, newWhat: String?,
        newOwersIds: List<Long>?, newRepeat: String?,
        newPaymentMode: String?, newPaymentModeRemoteId: Int?,
        newCategoryId: Int?,
        newComment: String?
    ) {
        val newState = if (bill.state == DBBill.STATE_ADDED) DBBill.STATE_ADDED else DBBill.STATE_EDITED
        updateBill(
            bill.id, null, newPayerId, newAmount, newTimestamp, newWhat, newState,
            newRepeat, newPaymentMode, newPaymentModeRemoteId, newCategoryId, newComment
        )
        val dbBillOwers = getBillowersOfBill(bill.id)
        val dbBillOwersByMemberId: MutableMap<Long, DBBillOwer> = HashMap()
        for (bo in dbBillOwers) {
            dbBillOwersByMemberId[bo.memberId] = bo
        }
        if (newOwersIds != null) {
            for (newOwerId in newOwersIds) {
                if (!dbBillOwersByMemberId.containsKey(newOwerId)) {
                    addBillower(bill.id, newOwerId)
                }
            }
            for (dbBo in dbBillOwers) {
                if (!newOwersIds.contains(dbBo.memberId)) {
                    deleteBillOwer(dbBo.id)
                }
            }
        }
        Log.v(TAG, "UPDATE BILL AND SYNC")
        val proj = getProject(bill.projectId)
        if (proj != null) syncIfRemote(proj)
    }

    fun getBillsOfProject(projId: Long): List<DBBill> {
        return getBillsCustom("$key_projectid = ?", arrayOf(projId.toString()), "$key_timestamp ASC")
    }

    fun getBillsOfProjectWithState(projId: Long, state: Int): List<DBBill> {
        return getBillsCustom(
            "$key_projectid = ? AND $key_state = ?",
            arrayOf(projId.toString(), state.toString()),
            "$key_timestamp ASC"
        )
    }

    fun getBillsOfMember(memberId: Long): List<DBBill> {
        return getBillsCustom("$key_payer_id = ?", arrayOf(memberId.toString()), "$key_timestamp ASC")
    }

    @Suppress("unused")
    fun getBill(remoteId: Long, projId: Long): DBBill? {
        val bills = getBillsCustom(
            "$key_remoteId = ? AND $key_projectid = ?",
            arrayOf(remoteId.toString(), projId.toString()),
            null
        )
        return if (bills.isEmpty()) null else bills[0]
    }

    fun getBill(billId: Long): DBBill? {
        val bills = getBillsCustom("$key_id = ?", arrayOf(billId.toString()), null)
        return if (bills.isEmpty()) null else bills[0]
    }

    fun getCurrenciesOfProjectWithState(projId: Long, state: Int): List<DBCurrency> {
        return getCurrenciesCustom(
            "$key_projectid = ? AND $key_state = ?",
            arrayOf(projId.toString(), state.toString()),
            null
        )
    }

    @WorkerThread
    fun searchBills(query: CharSequence?, projectId: Long): List<DBBill> {
        val andWhere: MutableList<String> = ArrayList()
        val args: MutableList<String> = ArrayList()
        andWhere.add("($key_projectid = $projectId)")
        andWhere.add("($key_state != ${DBBill.STATE_DELETED})")
        if (query != null) {
            args.add("%$query%")
            var whereStr = "($key_what LIKE ?"
            if (SupportUtil.isDouble(query.toString())) {
                whereStr += " OR ($key_amount <= (? + 10) AND $key_amount >= (? - 10))"
                args.add(query.toString())
                args.add(query.toString())
            }
            val members = getMembersOfProject(projectId, null)
            val memberNames: MutableList<String> = ArrayList()
            val memberIds: MutableList<Long> = ArrayList()
            for (m in members) {
                memberNames.add(m.name.lowercase(Locale.ROOT))
                memberIds.add(m.id)
            }
            val queryStr = query.toString()
            val words = queryStr.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var nameSql = ""
            for (word in words) {
                if (word.startsWith("+")) {
                    val nameQuery = word.replace("^\\+".toRegex(), "")
                    val memberIndex = memberNames.indexOf(nameQuery.lowercase(Locale.ROOT))
                    if (memberIndex != -1) {
                        val searchMemberId = memberIds[memberIndex]
                        nameSql += "($key_payer_id=?) AND "
                        args.add(searchMemberId.toString())
                    }
                }
                if (word.startsWith("-")) {
                    val nameQuery = word.replace("^-".toRegex(), "")
                    val memberIndex = memberNames.indexOf(nameQuery.lowercase(Locale.ROOT))
                    if (memberIndex != -1) {
                        val searchMemberId = memberIds[memberIndex]
                        val joinOwer = "select $table_bills.$key_id from $table_bills inner join $table_billowers " +
                                "where $key_member_id=? and $table_bills.$key_id=$table_billowers.$key_billId"
                        nameSql += "($key_id IN ($joinOwer)) AND "
                        args.add(searchMemberId.toString())
                    }
                }
                if (word.startsWith("@")) {
                    val nameQuery = word.replace("^@".toRegex(), "")
                    val memberIndex = memberNames.indexOf(nameQuery.lowercase(Locale.ROOT))
                    if (memberIndex != -1) {
                        val searchMemberId = memberIds[memberIndex]
                        nameSql += "( ($key_payer_id=?) OR "
                        args.add(searchMemberId.toString())
                        val joinOwer = "select $table_bills.$key_id from $table_bills inner join $table_billowers " +
                                "where $key_member_id=? and $table_bills.$key_id=$table_billowers.$key_billId"
                        nameSql += "($key_id IN ($joinOwer)) ) AND "
                        args.add(searchMemberId.toString())
                    }
                }
            }
            if (nameSql != "") {
                nameSql = nameSql.replace(" AND $".toRegex(), "")
                whereStr += " OR ($nameSql)"
            }
            whereStr += ")"
            andWhere.add(whereStr)
        }
        val order = "$key_timestamp DESC"
        return getBillsCustom(TextUtils.join(" AND ", andWhere), args.toTypedArray(), order)
    }

    @WorkerThread
    private fun getBillsCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBBill> {
        val db = readableDatabase
        val cursor = db.query(table_bills, columnsBills, selection, selectionArgs, null, null, orderBy)
        val bills: MutableList<DBBill> = ArrayList()
        while (cursor.moveToNext()) {
            val bill = getBillFromCursor(cursor)
            bill.billOwers = getBillowersOfBill(bill.id)
            bills.add(bill)
        }
        cursor.close()
        return bills
    }

    @SuppressLint("Range")
    private fun getBillFromCursor(cursor: Cursor): DBBill {
        return DBBill(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_remoteId)),
            cursor.getLong(cursor.getColumnIndex(key_projectid)),
            cursor.getLong(cursor.getColumnIndex(key_payer_id)),
            cursor.getDouble(cursor.getColumnIndex(key_amount)),
            cursor.getLong(cursor.getColumnIndex(key_timestamp)),
            cursor.getString(cursor.getColumnIndex(key_what)),
            cursor.getInt(cursor.getColumnIndex(key_state)),
            cursor.getString(cursor.getColumnIndex(key_repeat)),
            cursor.getString(cursor.getColumnIndex(key_payment_mode)),
            cursor.getInt(cursor.getColumnIndex(key_category_id)),
            cursor.getString(cursor.getColumnIndex(key_comment)),
            cursor.getInt(cursor.getColumnIndex(key_payment_mode_id))
        )
    }

    fun deleteBill(id: Long) {
        val db = writableDatabase
        db.delete(table_billowers, "$key_billId = ?", arrayOf(id.toString()))
        db.delete(table_bills, "$key_id = ?", arrayOf(id.toString()))
    }

    fun addBillower(billId: Long, memberId: Long) {
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[add billower]") }
        val db = writableDatabase
        val values = ContentValues()
        values.put(key_billId, billId)
        values.put(key_member_id, memberId)
        db.insert(table_billowers, null, values)
    }

    fun getBillowersOfBill(billId: Long): List<DBBillOwer> {
        return getBillOwersCustom("$key_billId = ?", arrayOf(billId.toString()), null)
    }

    fun getBillowersOfMember(memberId: Long): List<DBBillOwer> {
        return getBillOwersCustom("$key_member_id = ?", arrayOf(memberId.toString()), null)
    }

    @WorkerThread
    private fun getBillOwersCustom(selection: String, selectionArgs: Array<String>, orderBy: String?): List<DBBillOwer> {
        val db = readableDatabase
        val cursor = db.query(table_billowers, columnsBillowers, selection, selectionArgs, null, null, orderBy)
        val billOwers: MutableList<DBBillOwer> = ArrayList()
        while (cursor.moveToNext()) {
            billOwers.add(getBillOwerFromCursor(cursor))
        }
        cursor.close()
        return billOwers
    }

    @SuppressLint("Range")
    private fun getBillOwerFromCursor(cursor: Cursor): DBBillOwer {
        return DBBillOwer(
            cursor.getLong(cursor.getColumnIndex(key_id)),
            cursor.getLong(cursor.getColumnIndex(key_billId)),
            cursor.getLong(cursor.getColumnIndex(key_member_id))
        )
    }

    fun deleteBillOwer(id: Long) {
        val db = writableDatabase
        db.delete(table_billowers, "$key_id = ?", arrayOf(id.toString()))
    }

    @Suppress("ConstPropertyName")
    companion object {
        private val TAG = CowspentSQLiteOpenHelper::class.java.simpleName
        private const val database_version = 20
        private const val database_name = "COWSPENT"
        private const val table_members = "MEMBERS"
        const val key_id = "ID"
        const val key_remoteId = "REMOTEID"
        private const val key_projectid = "PROJECTID"
        const val key_name = "NAME"
        private const val key_activated = "ACTIVATED"
        private const val key_weight = "WEIGHT"
        private const val key_state = "STATE"
        private const val key_r = "R"
        private const val key_g = "G"
        private const val key_b = "B"
        const val key_nc_userid = "NCUSERID"
        const val key_avatar = "AVATAR"
        private const val table_projects = "PROJECTS"
        private const val key_email = "EMAIL"
        private const val key_password = "PASSWORD"
        private const val key_bearer_token = "BEARERTOKEN"
        private const val key_ihmUrl = "IHMURL"
        private const val key_lastPayerId = "LASTPAYERID"
        private const val key_type = "TYPE"
        private const val key_lastSyncTimestamp = "LASTSYNCED"
        private const val key_currencyName = "CURRENCYNAME"
        private const val key_deletionDisabled = "DELETIONDISABLED"
        private const val key_myAccessLevel = "MYACCESSLEVEL"
        private const val key_archived = "ARCHIVED"
        private const val table_bills = "BILLS"
        private const val key_payer_id = "PAYERID"
        private const val key_amount = "AMOUNT"
        private const val key_timestamp = "TIMESTAMP"
        private const val key_what = "WHAT"
        private const val key_repeat = "REPEAT"
        private const val key_payment_mode = "PAYMENTMODE"
        private const val key_payment_mode_id = "PAYMENTMODEID"
        private const val key_category_id = "CATEGORYID"
        private const val key_comment = "COMMENT"
        private const val table_billowers = "BILLOWERS"
        private const val key_billId = "BILLID"
        private const val key_member_id = "MEMBERID"
        private const val table_account_projects = "ACCOUNTPROJECTS"
        private const val key_ncUrl = "NCURL"
        private const val table_categories = "CATEGORIES"
        private const val key_icon = "ICON"
        private const val key_color = "COLOR"
        private const val table_payment_modes = "PAYMENTMODES"
        private const val key_latest_bill_ts = "LATEST_BILL_TS"
        private const val table_currencies = "CURRENCIES"
        private const val key_exchangeRate = "EXCHANGERATE"

        private val columnsMembers = arrayOf(
            key_id, key_remoteId, key_projectid, key_name, key_activated, key_weight, key_state,
            key_r, key_g, key_b, key_nc_userid, key_avatar
        )
        private val columnsProjects = arrayOf(
            key_id, key_remoteId, key_password, key_name, key_ihmUrl,
            key_email, key_lastPayerId, key_type, key_lastSyncTimestamp, key_currencyName,
            key_deletionDisabled, key_myAccessLevel, key_bearer_token, key_archived,
            "(SELECT MAX($key_timestamp) FROM $table_bills WHERE $key_projectid = $table_projects.$key_id AND $key_state != ${DBBill.STATE_DELETED}) AS $key_latest_bill_ts"
        )
        private val columnsBills = arrayOf(
            key_id, key_remoteId, key_projectid, key_payer_id, key_amount,
            key_timestamp, key_what, key_state, key_repeat, key_payment_mode, key_category_id,
            key_comment, key_payment_mode_id
        )
        private val columnsBillowers = arrayOf(
            key_id, key_billId, key_member_id
        )
        private val columnsAccountProjects = arrayOf(
            key_id, key_remoteId, key_password, key_name, key_ncUrl, key_archived
        )
        private val columnsCategories = arrayOf(
            key_id, key_remoteId, key_projectid, key_name, key_icon, key_color
        )
        private val columnsPaymentModes = arrayOf(
            key_id, key_remoteId, key_projectid, key_name, key_icon, key_color
        )
        private val columnsCurrencies = arrayOf(
            key_id, key_remoteId, key_projectid, key_name, key_exchangeRate, key_state
        )
        private const val default_order = "$key_id DESC"

        @Volatile
        private var instance: CowspentSQLiteOpenHelper? = null

        @JvmStatic
        fun getInstance(context: Context): CowspentSQLiteOpenHelper {
            return instance ?: synchronized(this) {
                instance ?: CowspentSQLiteOpenHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
