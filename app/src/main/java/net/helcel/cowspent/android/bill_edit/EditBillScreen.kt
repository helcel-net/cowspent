package net.helcel.cowspent.android.bill_edit

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.*
import net.helcel.cowspent.model.*
import net.helcel.cowspent.util.SupportUtil
import java.util.Date
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EditBillScreen(
    viewModel: EditBillViewModel,
    categories: List<DBCategory>,
    paymentModes: List<DBPaymentMode>,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onScan: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    accessLevel: Int = DBProject.ACCESS_LEVEL_ADMIN
) {
    val canEdit = accessLevel == DBProject.ACCESS_LEVEL_UNKNOWN || accessLevel >= DBProject.ACCESS_LEVEL_PARTICIPANT
    val context = LocalContext.current

    StatefulAlertDialog(
        state = viewModel.dialogState,
        onDismissRequest = { viewModel.dismissDialog() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNewBill) R.string.simple_new_bill else R.string.simple_edit_bill)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = onScan) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        }
                        if (onDuplicate != null) {
                            IconButton(onClick = onDuplicate) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                            }
                        }
                        if (onDelete != null) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        },
        floatingActionButton = {
            if (canEdit) {
                val errorWhat = stringResource(R.string.error_invalid_bill_what)
                val errorDate = stringResource(R.string.error_invalid_bill_date)
                val errorPayer = stringResource(R.string.error_invalid_bill_payerid)
                val errorOwers = stringResource(R.string.error_invalid_bill_owers)
                val errorInvalidForm = stringResource(R.string.simple_error)

                FloatingActionButton(onClick = {
                    val validationError = viewModel.getValidationError(
                        errorWhat, errorDate, errorPayer, errorOwers, errorInvalidForm
                    )
                    if (validationError == null) {
                        onSave()
                    } else {
                        showToast(context, validationError)
                    }
                }) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = stringResource(R.string.action_save_bill)
                    )
                }
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            BillBasicInfoSection(
                viewModel = viewModel,
                canEdit = canEdit,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            PayerSection(
                viewModel = viewModel,
                canEdit = canEdit
            )

            Spacer(modifier = Modifier.height(8.dp))

            OwerSelectionSection(
                viewModel = viewModel,
                canEdit = canEdit
            )

            Spacer(modifier = Modifier.height(8.dp))

            BillAdditionalDetailsSection(
                viewModel = viewModel,
                categories = categories,
                paymentModes = paymentModes,
                canEdit = canEdit
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun BillBasicInfoSection(
    viewModel: EditBillViewModel,
    canEdit: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    val context = LocalContext.current
    val currencyDialogTitle =
        stringResource(R.string.currency_dialog_title, viewModel.mainCurrencyName)

    OutlinedTextField(
        value = viewModel.what,
        onValueChange = { viewModel.what = it },
        enabled = canEdit,
        placeholder = { Text(stringResource(R.string.setting_what)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = viewModel.amount,
        onValueChange = {
            viewModel.amount = it
            viewModel.updateSplits()
        },
        enabled = canEdit,
        placeholder = { Text("0") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            val currencyToShow = viewModel.selectedCurrencyName.ifEmpty { viewModel.mainCurrencyName }
            TextIconDisplay(
                textIcon = TextIcon.Symbol(currencyToShow),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            IconButton(
                enabled = canEdit,
                onClick = {
                    val mainLabel = viewModel.mainCurrencyName.ifEmpty { "Main" }
                    val options = listOf("$mainLabel | Base") + viewModel.currencies.map { 
                        "${it.name} | 1 $mainLabel = ${it.exchangeRate} ${it.name}" 
                    }
                    viewModel.showDialog(
                        title = currencyDialogTitle,
                        items = options,
                        onItemSelected = { index ->
                            if (index == 0) {
                                viewModel.resetCurrency()
                            } else {
                                viewModel.convertCurrency(viewModel.currencies[index - 1])
                            }
                        }
                    )
                }
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Change Currency")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    Spacer(modifier = Modifier.height(8.dp))

    val dateFormat =
        remember(context) { android.text.format.DateFormat.getDateFormat(context) }
    val timeFormat =
        remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val dateStr = dateFormat.format(Date(viewModel.timestamp * 1000))
    val timeStr = timeFormat.format(Date(viewModel.timestamp * 1000))

    Row {
        ClickableOutlinedTextField(
            value = dateStr,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
            enabled = canEdit,
            leadingIcon = { Icon(Icons.Default.Event, contentDescription = "Select Date") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        ClickableOutlinedTextField(
            value = timeStr,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
            enabled = canEdit,
            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = "Select Time") }
        )
    }
}

@Composable
fun PayerSection(
    viewModel: EditBillViewModel,
    canEdit: Boolean
) {
    var payerExpanded by remember { mutableStateOf(false) }
    val selectedPayer = viewModel.members.find { it.id == viewModel.payerId }

    EditableExposedDropdownMenu(
        value = selectedPayer?.name ?: "",
        placeholder = stringResource(R.string.setting_payer),
        expanded = payerExpanded,
        onExpandedChange = { payerExpanded = it },
        onDismissRequest = { payerExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                if (selectedPayer != null) {
                    MemberAvatar(
                        member = selectedPayer,
                        size = 24.dp
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
        },
        content = {
            viewModel.members.forEach { member ->
                DropdownMenuItem(onClick = {
                    viewModel.payerId = member.id
                    payerExpanded = false
                }) {
                    MemberAvatar(
                        member = member,
                        size = 24.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(member.name)
                }
            }
        }
    )
}

@Composable
fun OwerSelectionSection(
    viewModel: EditBillViewModel,
    canEdit: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        if (viewModel.owersSelection.all { it.value }) {
            IconButton(
                enabled = canEdit,
                onClick = {
                    viewModel.members.forEach { viewModel.owersSelection[it.id] = false }
                    viewModel.updateSplits()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.RemoveDone,
                    contentDescription = stringResource(R.string.setting_none),
                    modifier = Modifier.scale(0.8f)
                )
            }
        } else {
            IconButton(
                enabled = canEdit,
                onClick = {
                    viewModel.members.forEach { viewModel.owersSelection[it.id] = true }
                    viewModel.updateSplits()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DoneAll,
                    contentDescription = stringResource(R.string.setting_all),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.setting_owers), fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.weight(1f))

        if (viewModel.isCustomSplit) {
            val diff = viewModel.getDiffSplit()
            if (abs(diff) > 0.01) {
                val diffText =
                    if (diff > 0) "Missing: ${SupportUtil.normalNumberFormat.format(diff)}" else "Excess: ${
                        SupportUtil.normalNumberFormat.format(-diff)
                    }"
                Text(
                    diffText,
                    color = MaterialTheme.colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            Text("Even Split", fontSize = 12.sp)
        }
        Switch(
            checked = !viewModel.isCustomSplit,
            enabled = canEdit,
            onCheckedChange = {
                viewModel.isCustomSplit = !it
                viewModel.updateSplits()
            },
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colors.onSurface,
            )
        )
    }

    viewModel.members.forEach { member ->
        val isSelected = viewModel.owersSelection[member.id] ?: false
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canEdit) {
                    viewModel.toggleMember(member.id, !isSelected)
                }

        ) {
            Checkbox(
                checked = isSelected,
                enabled = canEdit,
                onCheckedChange = { viewModel.toggleMember(member.id, it) }
            )
            MemberAvatar(
                member = member,
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(member.name, modifier = Modifier.weight(1f))

            if (isSelected || viewModel.isCustomSplit) {
                val interactionSource = remember { MutableInteractionSource() }
                BasicTextField(
                    value = viewModel.owersCustomSplit[member.id] ?: "",
                    onValueChange = {
                        viewModel.owersCustomSplit[member.id] = it
                        viewModel.owersSelection[member.id] = (it != "")
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .height(46.dp),
                    interactionSource = interactionSource,
                    singleLine = true,
                    enabled = viewModel.isCustomSplit && canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(MaterialTheme.colors.onSurface),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Right,
                        color = MaterialTheme.colors.onSurface
                    ),
                ) { innerTextField ->
                    TextFieldDefaults.OutlinedTextFieldDecorationBox(
                        value = viewModel.owersCustomSplit[member.id] ?: "",
                        visualTransformation = VisualTransformation.None,
                        innerTextField = innerTextField,
                        singleLine = true,
                        enabled = viewModel.isCustomSplit && canEdit,
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            cursorColor = MaterialTheme.colors.onSurface,
                            backgroundColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BillAdditionalDetailsSection(
    viewModel: EditBillViewModel,
    categories: List<DBCategory>,
    paymentModes: List<DBPaymentMode>,
    canEdit: Boolean
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory =
        categories.find { it.remoteId.toInt() == viewModel.categoryRemoteId }

    EditableExposedDropdownMenu(
        value = selectedCategory?.name ?: "",
        placeholder = stringResource(R.string.setting_category),
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = it },
        onDismissRequest = { categoryExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                if (selectedCategory != null) {
                    Text(text = selectedCategory.icon, fontSize = 20.sp)
                } else {
                    Icon(Icons.Default.Category, contentDescription = null)
                }
            }
        },
        content = {
            DropdownMenuItem(onClick = {
                viewModel.categoryRemoteId = 0
                categoryExpanded = false
            }) {
                Icon(Icons.Default.Close, tint = Color.Red, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.category_none))
            }
            categories.forEach { category ->
                DropdownMenuItem(onClick = {
                    viewModel.categoryRemoteId = category.remoteId.toInt()
                    categoryExpanded = false
                }) {
                    Text(text = category.icon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(category.name ?: "")
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    var pmExpanded by remember { mutableStateOf(false) }
    val selectedPm =
        paymentModes.find { it.remoteId.toInt() == viewModel.paymentModeRemoteId }

    EditableExposedDropdownMenu(
        value = selectedPm?.name ?: "",
        placeholder = stringResource(R.string.setting_payment_mode),
        expanded = pmExpanded,
        onExpandedChange = { pmExpanded = it },
        onDismissRequest = { pmExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                if (selectedPm != null) {
                    Text(text = selectedPm.icon, fontSize = 20.sp)
                } else {
                    Icon(Icons.Default.Payment, contentDescription = null)
                }
            }
        },
        content = {
            DropdownMenuItem(onClick = {
                viewModel.paymentModeRemoteId = 0
                pmExpanded = false
            }) {
                Icon(Icons.Default.Close, tint = Color.Red, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.payment_mode_none))
            }
            paymentModes.forEach { pm ->
                DropdownMenuItem(onClick = {
                    viewModel.paymentModeRemoteId = pm.remoteId.toInt()
                    pmExpanded = false
                }) {
                    Text(text = pm.icon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(pm.name ?: "")
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    val repeatOptions = listOf(
        DBBill.NON_REPEATED to stringResource(R.string.repeat_no),
        DBBill.REPEAT_DAY to stringResource(R.string.repeat_day),
        DBBill.REPEAT_WEEK to stringResource(R.string.repeat_week),
        DBBill.REPEAT_FORTNIGHT to stringResource(R.string.repeat_fortnight),
        DBBill.REPEAT_MONTH to stringResource(R.string.repeat_month),
        DBBill.REPEAT_YEAR to stringResource(R.string.repeat_year)
    )
    var repeatExpanded by remember { mutableStateOf(false) }
    val selectedRepeat = repeatOptions.find { it.first == viewModel.repeat }

    EditableExposedDropdownMenu(
        value = selectedRepeat?.second ?: "",
        placeholder = stringResource(R.string.setting_project_repetition),
        expanded = repeatExpanded,
        onExpandedChange = { repeatExpanded = it },
        onDismissRequest = { repeatExpanded = false },
        enabled = canEdit,
        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
        content = {
            repeatOptions.forEach { (value, label) ->
                DropdownMenuItem(onClick = {
                    viewModel.repeat = value
                    repeatExpanded = false
                }) {
                    Text(label)
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = viewModel.comment,
        onValueChange = { viewModel.comment = it },
        enabled = canEdit,
        placeholder = { Text(stringResource(R.string.setting_comment)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.Comment,
                contentDescription = null
            )
        }
    )
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun EditBillScreenPreview() {
    MaterialTheme {
        EditBillScreen(
            viewModel = EditBillViewModel().apply {
                what = "Pizza"
                amount = "12.50"
                mainCurrencyName = "EUR"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
                    DBMember(2, 0, 0, "Bob", true, 1.0, 0, null, null, null, null, null)
                )
            },
            categories = emptyList(),
            paymentModes = emptyList(),
            onSave = {},
            onBack = {},
            onDateClick = {},
            onTimeClick = {},
            onScan = {}
        )
    }
}
