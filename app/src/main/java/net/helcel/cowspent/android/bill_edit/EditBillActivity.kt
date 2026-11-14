package net.helcel.cowspent.android.bill_edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.QrCodeScannerActivity
import net.helcel.cowspent.android.helper.showToast
import net.helcel.cowspent.android.main.MainConstants
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBBillOwer
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.BillParser
import net.helcel.cowspent.util.BillPrefill
import net.helcel.cowspent.util.BillTemplate
import net.helcel.cowspent.util.CategorySuggester
import net.helcel.cowspent.util.PrefillMode
import net.helcel.cowspent.util.SupportUtil
import java.text.ParseException
import java.time.ZoneId
import java.util.Calendar

class EditBillActivity : AppCompatActivity() {

    internal val viewModel: EditBillViewModel by viewModels()
    private lateinit var db: CowspentSQLiteOpenHelper
    private lateinit var bill: DBBill
    private var projectType: ProjectType = ProjectType.LOCAL
    private val calendar = Calendar.getInstance()

    // The pre-fill mode the form was opened with; null when the bill is not a fresh one.
    private var prefillMode: PrefillMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        db = CowspentSQLiteOpenHelper.getInstance(this)

        lifecycleScope.launch {
            initBill()

            setContent {
                ThemeUtils.CowspentTheme {

                    val categories = remember {
                        db.getCategories(bill.projectId)
                    }
                    val paymentModes = remember {
                        db.getPaymentModes(bill.projectId)
                    }

                    EditBillScreen(
                        viewModel = viewModel,
                        categories = categories,
                        paymentModes = paymentModes,
                        onSave = { saveBillAsked() },
                        onBack = { onBack() },
                        onDateClick = {
                            DatePickerDialog(
                                this@EditBillActivity,
                                { _, year, month, day ->
                                    calendar[Calendar.YEAR] = year
                                    calendar[Calendar.MONTH] = month
                                    calendar[Calendar.DAY_OF_MONTH] = day
                                    viewModel.timestamp = calendar.timeInMillis / 1000
                                },
                                calendar[Calendar.YEAR],
                                calendar[Calendar.MONTH],
                                calendar[Calendar.DAY_OF_MONTH]
                            ).show()
                        },
                        onTimeClick = {
                            TimePickerDialog(
                                this@EditBillActivity,
                                { _, hour, minute ->
                                    calendar[Calendar.HOUR_OF_DAY] = hour
                                    calendar[Calendar.MINUTE] = minute
                                    viewModel.timestamp = calendar.timeInMillis / 1000
                                },
                                calendar[Calendar.HOUR_OF_DAY],
                                calendar[Calendar.MINUTE],
                                true
                            ).show()
                        },
                        onScan = {
                            val createIntent = Intent(this@EditBillActivity, QrCodeScannerActivity::class.java)
                            scanQRCodeLauncher.launch(createIntent)
                        },
                        onDuplicate = if (!viewModel.isNewBill) {
                            { duplicateCurrentBill() }
                        } else null,
                        onDelete = if (!viewModel.isNewBill) {
                            { deleteBillAsked() }
                        } else null,
                        onPrefillModeSelected = { onPrefillModeSelected(it) },
                        onSavePrefillPreset = { onSavePrefillPreset() },
                        accessLevel = db.getProject(bill.projectId)?.myAccessLevel ?: DBProject.ACCESS_LEVEL_ADMIN
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private suspend fun initBill() {
        val billId = intent.getLongExtra(PARAM_BILL_ID, 0)
        val groupedBillIds = intent.getLongArrayExtra(PARAM_GROUPED_BILL_IDS)
        val projectId = intent.getLongExtra(PARAM_PROJECT_ID, 0)
        val projectTypeStr = intent.getStringExtra(PARAM_PROJECT_TYPE)
        projectType = if (!projectTypeStr.isNullOrEmpty()) {
            ProjectType.getTypeById(projectTypeStr) ?: ProjectType.LOCAL
        } else {
            ProjectType.LOCAL
        }

        withContext(Dispatchers.IO) {
            var customSplits: Map<Long, Double>? = null
            if (groupedBillIds != null && groupedBillIds.isNotEmpty()) {
                val sourceBills: List<DBBill> = groupedBillIds.map { db.getBill(it) }.filterNotNull()
                if (sourceBills.isNotEmpty()) {
                    val first = sourceBills[0]
                    val totalAmount = sourceBills.sumOf { it.amount }
                    bill = DBBill(
                        first.id, 0, first.projectId, first.payerId, totalAmount,
                        first.timestamp, first.what, first.state, first.repeat,
                        first.paymentMode, first.categoryId, first.comment, first.paymentModeId
                    )

                    val splits = mutableMapOf<Long, Double>()
                    for (b in sourceBills) {
                        val owersCount = b.billOwers.size
                        if (owersCount > 0) {
                            val part = b.amount / owersCount
                            for (ower in b.billOwers) {
                                splits[ower.memberId] = (splits[ower.memberId] ?: 0.0) + part
                            }
                        }
                    }
                    customSplits = splits
                }
            } else if (billId > 0) {
                bill = db.getBill(billId)!!
            } else {
                val billIdToDuplicate = intent.getLongExtra(PARAM_BILL_ID_TO_DUPLICATE, 0)
                val timeNowSeconds = System.currentTimeMillis() / 1000
                if (billIdToDuplicate == 0L) {
                    val mode = BillPrefill.mode(applicationContext, projectId)
                    bill = newBill(
                        projectId,
                        timeNowSeconds,
                        BillPrefill.templateFor(applicationContext, db, projectId, mode)
                    )
                    prefillMode = mode
                } else {
                    val btd = db.getBill(billIdToDuplicate)!!
                    bill = DBBill(
                        0, 0, projectId, btd.payerId, btd.amount,
                        timeNowSeconds, btd.what, DBBill.STATE_ADDED,
                        btd.repeat, btd.paymentMode, btd.categoryId,
                        btd.comment, btd.paymentModeId
                    )
                    val btdOwers = btd.billOwers
                    val newBillOwers = btdOwers.filter {
                        val m = db.getMember(it.memberId)
                        m != null && m.isActivated
                    }
                    bill.billOwers = newBillOwers
                }
            }
            calendar.timeInMillis = bill.timestamp * 1000
            val allMembers = db.getMembersOfProject(bill.projectId, null)
            val billOwerIds = bill.billOwersIds
            val members = allMembers.filter { m ->
                m.isActivated || m.id == bill.payerId || billOwerIds.contains(m.id)
            }

            val project = db.getProject(bill.projectId)
            val currencies = db.getCurrencies(bill.projectId)

            if (project != null) {
                db.ensureDefaultLabels(bill.projectId, project.type)
            }

            val hasPreset = BillPrefill.preset(applicationContext, bill.projectId) != null
            val categoryMatcher = if (bill.id == 0L) autoCategoryMatcher(bill.projectId) else null

            withContext(Dispatchers.Main) {
                viewModel.currencies = currencies
                viewModel.mainCurrencyName = project?.currencyName ?: ""
                viewModel.isNewBill = (bill.id == 0L)
                viewModel.prefillMode = prefillMode
                viewModel.hasPrefillPreset = hasPreset
                viewModel.initFromBill(bill, members, customSplits)
                categoryMatcher?.let { viewModel.enableAutoCategorize(it) }
            }
        }
    }

    @WorkerThread
    private fun autoCategoryMatcher(projectId: Long): ((String) -> Long?)? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // The switch lives under Extra Features; with those off it is hidden, so it must not act.
        if (!preferences.getBoolean(getString(R.string.pref_key_extra_features), false)) return null
        if (!preferences.getBoolean(getString(R.string.pref_key_auto_categorize), false)) return null
        val categorized = db.getBillsOfProject(projectId)
            .filter { it.categoryId != DBBill.CATEGORY_NONE && it.state != DBBill.STATE_DELETED }
        // Only categories the picker offers: the project's own, plus the built-in reimbursement.
        val allowed = db.getCategories(projectId).map { it.id }.toSet() + DBBill.CATEGORY_REIMBURSEMENT
        return { what -> CategorySuggester.confidentMatch(what, categorized, allowed) }
    }

    @WorkerThread
    private fun newBill(projectId: Long, timestamp: Long, template: BillTemplate?): DBBill {
        val project = db.getProject(projectId)
        val lastPayerId = project?.lastPayerId ?: 0
        if (template == null) {
            return DBBill(
                0, 0, projectId, lastPayerId, 0.0, timestamp,
                "", DBBill.STATE_ADDED, DBBill.NON_REPEATED,
                DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE, "", DBBill.PAYMODE_ID_NONE
            )
        }
        val activeIds = db.getMembersOfProject(projectId, null).filter { it.isActivated }.map { it.id }.toSet()
        return DBBill(
            0, 0, projectId, if (template.payerId in activeIds) template.payerId else lastPayerId,
            0.0, timestamp, "", DBBill.STATE_ADDED, template.repeat,
            template.paymentMode, template.categoryId, "", template.paymentModeId
        ).apply {
            billOwers = template.owerIds.filter { it in activeIds }.map { DBBillOwer(0, 0, it) }
        }
    }

    private fun onPrefillModeSelected(mode: PrefillMode) {
        val projectId = bill.projectId
        lifecycleScope.launch {
            val template = withContext(Dispatchers.IO) {
                BillPrefill.setMode(applicationContext, projectId, mode)
                newBill(projectId, bill.timestamp, BillPrefill.templateFor(applicationContext, db, projectId, mode))
            }
            // Keep the reference bill in step, so the unsaved-changes check compares against
            // what pre-filling put there rather than flagging it as the user's edit.
            bill.payerId = template.payerId
            bill.billOwers = template.billOwers
            bill.categoryId = template.categoryId
            bill.paymentMode = template.paymentMode
            bill.paymentModeId = template.paymentModeId
            bill.repeat = template.repeat
            viewModel.prefillMode = mode
            viewModel.applyPrefill(template)
        }
    }

    private fun currentTemplate() = BillTemplate(
        payerId = viewModel.payerId,
        owerIds = viewModel.getOwersIds(),
        categoryId = viewModel.categoryId,
        paymentModeId = viewModel.paymentModeId,
        paymentMode = bill.paymentMode,
        repeat = viewModel.repeat
    )

    /** Saves the form's current values as the project's preset and switches to using it. */
    private fun onSavePrefillPreset() {
        val projectId = bill.projectId
        val template = currentTemplate()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                BillPrefill.savePreset(applicationContext, projectId, template)
                BillPrefill.setMode(applicationContext, projectId, PrefillMode.PRESET)
            }
            viewModel.hasPrefillPreset = true
            viewModel.prefillMode = PrefillMode.PRESET
            showToast(this@EditBillActivity, getString(R.string.prefill_preset_saved))
        }
    }

    private val scanQRCodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val scannedBill = result.data?.getStringExtra(MainConstants.KEY_QR_CODE)
                if (scannedBill != null) {
                    try {
                        val austrianBill = BillParser.parseAustrianBillFromQrCode(scannedBill)
                        calendar.timeInMillis = austrianBill.date.time
                        viewModel.timestamp = calendar.timeInMillis / 1000
                        viewModel.amount = austrianBill.amount.toString()
                        viewModel.updateSplits()
                        return@registerForActivityResult
                    } catch (_: ParseException) {
                    }
                    try {
                        val croatianBill = BillParser.parseCroatianBillFromQrCode(scannedBill)
                        if (croatianBill.date != null) {
                            calendar.timeInMillis =
                                croatianBill.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            viewModel.timestamp = calendar.timeInMillis / 1000
                        }
                        viewModel.amount = croatianBill.amount.toString()
                        viewModel.updateSplits()
                        return@registerForActivityResult
                    } catch (_: ParseException) {
                    }
                    showToast(this, getString(R.string.error_scanning_bill_qr_code))
                }
            }
        }

    private fun duplicateCurrentBill() {
        bill = DBBill(
            0, 0, bill.projectId, viewModel.payerId, viewModel.amountAsDouble,
            System.currentTimeMillis() / 1000, viewModel.what, DBBill.STATE_ADDED,
            viewModel.repeat, bill.paymentMode, viewModel.categoryId,
            viewModel.getFinalComment(), viewModel.paymentModeId
        )
        calendar.timeInMillis = System.currentTimeMillis()
        viewModel.timestamp = calendar.timeInMillis / 1000
        viewModel.isNewBill = true

        showToast(this, "Duplicating bill...")
    }

    private fun onBack() {
        if (!valuesHaveChanged()) {
            finish()
            return
        }
        viewModel.showDialog(
            title = getString(R.string.dialog_unsaved_changes_title),
            message = getString(R.string.dialog_unsaved_changes_msg),
            positiveText = getString(R.string.action_save),
            onConfirm = { saveBillAsked() },
            negativeText = getString(R.string.action_discard),
            onCancel = { finish() }
        )
    }

    private fun saveBillAsked() {
        val validationError = viewModel.getValidationError(
            getString(R.string.error_invalid_bill_name),
            getString(R.string.error_invalid_bill_date),
            getString(R.string.error_invalid_bill_payer),
            getString(R.string.error_invalid_bill_owers),
            getString(R.string.error_generic)
        )

        if (validationError != null) {
            showToast(this, validationError)
        } else {
            // Taken before saving: saving can split the bill into one per ower, and the template
            // should hold the whole selection rather than whichever split was written last.
            val createdHere = bill.id == 0L
            val template = currentTemplate()
            lifecycleScope.launch {
                val savedBillId = withContext(Dispatchers.IO) {
                    saveBill().also { id ->
                        if (createdHere && id != 0L) {
                            BillPrefill.recordCreatedOnDevice(applicationContext, bill.projectId, template)
                        }
                    }
                }
                val data = Intent()
                data.putExtra(MainConstants.SAVED_BILL_ID, savedBillId)
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    private fun deleteBillAsked() {
        viewModel.showDialog(
            title = getString(R.string.title_confirm),
            message = bill.what,
            positiveText = getString(R.string.action_delete),
            onConfirm = {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val groupedBillIds = intent.getLongArrayExtra(PARAM_GROUPED_BILL_IDS)
                        if (groupedBillIds != null && groupedBillIds.isNotEmpty()) {
                            for (id in groupedBillIds) {
                                db.setBillState(id, DBBill.STATE_DELETED)
                            }
                        } else if (bill.id > 0) {
                            db.setBillState(bill.id, DBBill.STATE_DELETED)
                        }
                        val proj = db.getProject(bill.projectId)
                        if (proj != null) db.syncIfRemote(proj)
                    }
                    val data = Intent()
                    data.putExtra(MainConstants.DELETED_BILL, bill.id)
                    setResult(RESULT_OK, data)
                    finish()
                }
            },
            negativeText = getString(R.string.simple_cancel)
        )
    }

    private fun valuesHaveChanged(): Boolean {
        val newOwersIds = viewModel.getOwersIds().toSet()
        val billOwersIds = bill.billOwersIds.toSet()
        val owersChanged = newOwersIds != billOwersIds

        return !(bill.what == viewModel.what &&
                bill.timestamp == viewModel.timestamp &&
                bill.amount == viewModel.getFinalAmount() &&
                bill.payerId == viewModel.payerId &&
                bill.comment == viewModel.getFinalComment() &&
                (bill.repeat ?: DBBill.NON_REPEATED) == viewModel.repeat &&
                bill.categoryId == viewModel.categoryId &&
                bill.paymentModeId == viewModel.paymentModeId &&
                !owersChanged)
    }

    private suspend fun saveBill(): Long = withContext(Dispatchers.IO) {
        val groupedBillIds = intent.getLongArrayExtra(PARAM_GROUPED_BILL_IDS)
        val splitMode = viewModel.splitMode

        if (splitMode != SplitMode.EVEN) {
            val splits: Map<Long, Double> = if (splitMode == SplitMode.CUSTOM) {
                viewModel.owersCustomSplit.filter { (id, amountStr) ->
                    viewModel.owersSelection[id] == true && viewModel.parseAmountFromUi(amountStr) > 0
                }.mapValues {
                    val uiAmount = viewModel.parseAmountFromUi(it.value)
                    SupportUtil.round2(uiAmount * viewModel.selectedCurrencyRate)
                }
            } else { // PERCENT
                val totalAmount = viewModel.amountAsDouble
                viewModel.owersPercentSplit.filter { (id, percentStr) ->
                    viewModel.owersSelection[id] == true && viewModel.parseAmountFromUi(percentStr) > 0
                }.mapValues {
                    val percent = viewModel.parseAmountFromUi(it.value)
                    val uiAmount = SupportUtil.round2(totalAmount * percent / 100.0)
                    SupportUtil.round2(uiAmount * viewModel.selectedCurrencyRate)
                }
            }

            if (splits.isEmpty()) return@withContext 0L

            val finalComment = viewModel.getFinalComment()
            val splitEntries = splits.entries.toList()

            // Pool of existing bills in this group that we can potentially reuse
            val billsToPool = mutableListOf<Long>()
            if (bill.id != 0L) billsToPool.add(bill.id)
            groupedBillIds?.forEach { if (it != bill.id) billsToPool.add(it) }

            val processedBillIds = mutableSetOf<Long>()
            var firstSavedId = 0L

            for (entry in splitEntries) {
                val memberId = entry.key
                val amount = entry.value

                // Try to find a bill in the pool that already exists for this exact member
                var billToUseId = billsToPool.find { id ->
                    val b = db.getBill(id)
                    b?.billOwers?.size == 1 && b.billOwers[0].memberId == memberId
                }

                // Fallback: just take any available bill from the pool
                if (billToUseId == null) {
                    billToUseId = billsToPool.firstOrNull()
                }

                if (billToUseId != null) {
                    billsToPool.remove(billToUseId)
                    processedBillIds.add(billToUseId)

                    val existingBill = db.getBill(billToUseId)!!
                    db.updateBillAndSync(
                        existingBill,
                        viewModel.payerId,
                        amount,
                        viewModel.timestamp,
                        viewModel.what,
                        listOf(memberId),
                        viewModel.repeat,
                        existingBill.paymentMode,
                        viewModel.paymentModeId,
                        viewModel.categoryId,
                        finalComment
                    )
                    if (firstSavedId == 0L) firstSavedId = billToUseId
                } else {
                    // Create a new bill for this payee
                    val newBill = DBBill(
                        0, 0, bill.projectId, viewModel.payerId, amount,
                        viewModel.timestamp, viewModel.what, DBBill.STATE_ADDED, viewModel.repeat,
                        bill.paymentMode, viewModel.categoryId, finalComment, viewModel.paymentModeId
                    )
                    newBill.billOwers = listOf(DBBillOwer(0, 0, memberId))
                    newBill.categoryId = viewModel.categoryId
                    newBill.paymentModeId = viewModel.paymentModeId
                    val newId = db.addBill(newBill)
                    if (firstSavedId == 0L) firstSavedId = newId
                }
            }

            // Mark any remaining bills in the original group as deleted
            for (id in billsToPool) {
                db.setBillState(id, DBBill.STATE_DELETED)
            }

            val proj = db.getProject(bill.projectId)
            if (proj != null) db.syncIfRemote(proj)
            db.updateProject(
                projId = bill.projectId,
                newLastPayerId = viewModel.payerId
            )

            return@withContext firstSavedId
        } else {
            val newAmount = viewModel.getFinalAmount()
            val finalComment = viewModel.getFinalComment()
            val newOwersIds = viewModel.getOwersIds()

            if (bill.id != 0L) {
                if (valuesHaveChanged()) {
                    db.updateBillAndSync(
                        bill,
                        viewModel.payerId,
                        newAmount,
                        viewModel.timestamp,
                        viewModel.what,
                        newOwersIds,
                        viewModel.repeat,
                        bill.paymentMode,
                        viewModel.paymentModeId,
                        viewModel.categoryId,
                        finalComment
                    )
                    if (groupedBillIds != null) {
                        for (id in groupedBillIds) {
                            if (id != bill.id) {
                                db.setBillState(id, DBBill.STATE_DELETED)
                            }
                        }
                        val proj = db.getProject(bill.projectId)
                        if (proj != null) db.syncIfRemote(proj)
                    }
                }
                return@withContext bill.id
            } else {
                val newBill = DBBill(
                    0, 0, bill.projectId, viewModel.payerId, newAmount,
                    viewModel.timestamp, viewModel.what, DBBill.STATE_ADDED, viewModel.repeat,
                    bill.paymentMode, viewModel.categoryId, finalComment, viewModel.paymentModeId
                )
                newOwersIds.forEach { newBill.billOwers += DBBillOwer(0, 0, it) }
                val newBillId = db.addBill(newBill)
                db.updateProject(
                    projId = bill.projectId,
                    newLastPayerId = viewModel.payerId
                )
                val proj = db.getProject(bill.projectId)
                if (proj != null) db.syncIfRemote(proj)
                return@withContext newBillId
            }
        }
    }

    companion object {
        const val PARAM_BILL_ID = "billId"
        const val PARAM_GROUPED_BILL_IDS = "grouped_bill_ids"
        const val PARAM_PROJECT_ID = "projectId"
        const val PARAM_PROJECT_TYPE = "projectType"
        const val PARAM_BILL_ID_TO_DUPLICATE = "billToDuplicate"
    }
}
