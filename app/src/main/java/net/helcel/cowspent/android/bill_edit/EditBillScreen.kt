package net.helcel.cowspent.android.bill_edit

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.*
import net.helcel.cowspent.model.*
import net.helcel.cowspent.util.CategoryUtils
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (viewModel.isNewBill) {
            focusRequester.requestFocus()
        }
    }

    StatefulAlertDialog(
        state = viewModel.dialogState,
        onDismissRequest = { viewModel.dismissDialog() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNewBill) R.string.action_new_bill else R.string.action_edit)) },
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
                val errorWhat = stringResource(R.string.error_invalid_bill_name)
                val errorDate = stringResource(R.string.error_invalid_bill_date)
                val errorPayer = stringResource(R.string.error_invalid_bill_payer)
                val errorOwers = stringResource(R.string.error_invalid_bill_owers)
                val errorInvalidForm = stringResource(R.string.error_generic)

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
                        contentDescription = stringResource(R.string.action_save)
                    )
                }
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            BillBasicInfoSection(
                viewModel = viewModel,
                canEdit = canEdit,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick,
                amountFocusRequester = focusRequester
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
    onTimeClick: () -> Unit,
    amountFocusRequester: FocusRequester
) {
    Text(
        text = "GENERAL",
        style = MaterialTheme.typography.subtitle1,
        color = MaterialTheme.colors.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currencyDialogTitle =
        stringResource(R.string.currency_dialog_title, viewModel.mainCurrencyName)

    OutlinedTextField(
        value = viewModel.amount,
        onValueChange = { nv->
            val filteredValue = nv.filter { it in "0123456789.+-*/" }
            viewModel.amount = filteredValue
            viewModel.updateSplits()
        },
        enabled = canEdit,
        placeholder = { Text("0") },
        modifier = Modifier.fillMaxWidth().focusRequester(amountFocusRequester),
        leadingIcon = {
            val currencyToShow = viewModel.selectedCurrencyName.ifEmpty { 
                viewModel.mainCurrencyName.ifEmpty { "$" } 
            }
            TextIconDisplay(
                textIcon = TextIcon.Symbol(currencyToShow),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            IconButton(
                enabled = canEdit,
                onClick = {
                    val mainLabel = viewModel.mainCurrencyName.ifEmpty { "$" }
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = viewModel.what,
        onValueChange = { viewModel.what = it },
        enabled = canEdit,
        placeholder = { Text(stringResource(R.string.label_what)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
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
        placeholder = stringResource(R.string.label_payer),
        expanded = payerExpanded,
        onExpandedChange = { payerExpanded = it },
        onDismissRequest = { payerExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier) {
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
            text = stringResource(R.string.label_owers).uppercase(),
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))

        if (viewModel.splitMode != SplitMode.EVEN) {
            val diff = viewModel.getDiffSplit()
            if (abs(diff) > 0.01) {
                val unit = if (viewModel.splitMode == SplitMode.PERCENT) "%" else ""
                val diffText =
                    if (diff > 0) "Missing: ${SupportUtil.normalNumberFormat.format(diff)}$unit" else "Excess: ${
                        SupportUtil.normalNumberFormat.format(-diff)
                    }$unit"
                Text(
                    diffText,
                    color = MaterialTheme.colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        val splitOptions = listOf(SplitMode.EVEN, SplitMode.CUSTOM, SplitMode.PERCENT)
        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f), MaterialTheme.shapes.small)
        ) {
            splitOptions.forEach { mode ->
                val isSelected = viewModel.splitMode == mode
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(if (isSelected) MaterialTheme.colors.primary else Color.Transparent)
                        .clickable(enabled = canEdit) {
                            viewModel.splitMode = mode
                            viewModel.updateSplits()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (mode) {
                            SplitMode.EVEN -> "="
                            SplitMode.CUSTOM -> "#"
                            SplitMode.PERCENT -> "%"
                        },
                        color = if (isSelected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
            val weightSuffix = if (viewModel.hasDifferentWeights && member.weight != 1.0) {
                " (x${member.weight.toString().removeSuffix(".0")})"
            } else ""
            Text("${member.name}$weightSuffix", modifier = Modifier.weight(1f))

            if (isSelected || viewModel.splitMode != SplitMode.EVEN) {
                val focusManager = LocalFocusManager.current
                val interactionSource = remember { MutableInteractionSource() }
                val value = if (viewModel.splitMode == SplitMode.PERCENT) {
                    viewModel.owersPercentSplit[member.id] ?: ""
                } else {
                    viewModel.owersCustomSplit[member.id] ?: ""
                }
                
                BasicTextField(
                    value = value,
                    onValueChange = { nv ->
                        val filteredValue = nv.filter { it in "0123456789.+-*/" }
                        if (viewModel.splitMode == SplitMode.PERCENT) {
                            viewModel.owersPercentSplit[member.id] = filteredValue
                        } else {
                            viewModel.owersCustomSplit[member.id] = filteredValue
                        }
                        viewModel.owersSelection[member.id] = (filteredValue != "")
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .height(46.dp),
                    interactionSource = interactionSource,
                    singleLine = true,
                    enabled = viewModel.splitMode != SplitMode.EVEN && canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    cursorBrush = SolidColor(MaterialTheme.colors.onSurface),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Right,
                        color = MaterialTheme.colors.onSurface
                    ),
                ) { innerTextField ->
                    TextFieldDefaults.OutlinedTextFieldDecorationBox(
                        value = value,
                        visualTransformation = VisualTransformation.None,
                        innerTextField = innerTextField,
                        singleLine = true,
                        enabled = viewModel.splitMode != SplitMode.EVEN && canEdit,
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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
    Text(
        text = "DETAILS",
        style = MaterialTheme.typography.subtitle1,
        color = MaterialTheme.colors.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
    )

    val context = LocalContext.current
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory =
        categories.find { it.id == viewModel.categoryId } ?: CategoryUtils.getCategoryById(context, viewModel.categoryId)

    EditableExposedDropdownMenu(
        value = selectedCategory?.name ?: "",
        placeholder = stringResource(R.string.label_category),
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = it },
        onDismissRequest = { categoryExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier) {
                if (selectedCategory != null) {
                    Text(text = selectedCategory.icon, fontSize = 20.sp)
                } else {
                    Icon(Icons.Default.Category, contentDescription = null)
                }
            }
        },
        content = {
            DropdownMenuItem(onClick = {
                viewModel.categoryId = 0
                categoryExpanded = false
            }) {
                Icon(Icons.Default.Close, tint = Color.Red, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.category_none))
            }
            
            DropdownMenuItem(onClick = {
                viewModel.categoryId = DBBill.CATEGORY_REIMBURSEMENT
                categoryExpanded = false
            }) {
                Text(text = "\uD83D\uDCB0", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.category_reimbursement))
            }

            categories.filter { it.remoteId != DBBill.CATEGORY_REIMBURSEMENT }.forEach { category ->
                DropdownMenuItem(onClick = {
                    viewModel.categoryId = category.id
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
        paymentModes.find { it.id == viewModel.paymentModeId } ?: CategoryUtils.getPaymentModeById(context, viewModel.paymentModeId)

    EditableExposedDropdownMenu(
        value = selectedPm?.name ?: "",
        placeholder = stringResource(R.string.label_mode),
        expanded = pmExpanded,
        onExpandedChange = { pmExpanded = it },
        onDismissRequest = { pmExpanded = false },
        enabled = canEdit,
        leadingIcon = {
            Box(modifier = Modifier) {
                if (selectedPm != null) {
                    Text(text = selectedPm.icon, fontSize = 20.sp)
                } else {
                    Icon(Icons.Default.Payment, contentDescription = null)
                }
            }
        },
        content = {
            DropdownMenuItem(onClick = {
                viewModel.paymentModeId = 0
                pmExpanded = false
            }) {
                Icon(Icons.Default.Close, tint = Color.Red, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.payment_mode_none))
            }
            paymentModes.forEach { pm ->
                DropdownMenuItem(onClick = {
                    viewModel.paymentModeId = pm.id
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
        placeholder = stringResource(R.string.label_repeat),
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
        placeholder = { Text(stringResource(R.string.label_comment)) },
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
@Preview(showBackground = true, name = "Standard Split")
@Composable
fun EditBillScreenPreview() {
    MaterialTheme {
        EditBillScreen(
            viewModel = EditBillViewModel().apply {
                what = "Pizza"
                amount = "12.00"
                mainCurrencyName = "EUR"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
                    DBMember(2, 0, 0, "Bob", true, 1.0, 0, null, null, null, null, null)
                )
                owersSelection[1] = true
                owersSelection[2] = true
                updateSplits()
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

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "Weighted Split")
@Composable
fun EditBillScreenWeightedPreview() {
    MaterialTheme {
        EditBillScreen(
            viewModel = EditBillViewModel().apply {
                what = "Weighted Pizza"
                amount = "60.00"
                mainCurrencyName = "EUR"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 2.0, 0, null, null, null, null, null),
                    DBMember(2, 0, 0, "Bob", true, 1.0, 0, null, null, null, null, null),
                    DBMember(3, 0, 0, "Charlie", true, 1.0, 0, null, null, null, null, null)
                )
                owersSelection[1] = true
                owersSelection[2] = true
                owersSelection[3] = true
                updateSplits()
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

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "Custom Split (#)")
@Composable
fun EditBillScreenCustomPreview() {
    MaterialTheme {
        EditBillScreen(
            viewModel = EditBillViewModel().apply {
                what = "Custom Split Pizza"
                amount = "60.00"
                mainCurrencyName = "EUR"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
                    DBMember(2, 0, 0, "Bob", true, 1.0, 0, null, null, null, null, null)
                )
                splitMode = SplitMode.CUSTOM
                owersSelection[1] = true
                owersSelection[2] = true
                owersCustomSplit[1] = "40.00"
                owersCustomSplit[2] = "20.00"
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

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "Percent Split (%)")
@Composable
fun EditBillScreenPercentPreview() {
    MaterialTheme {
        EditBillScreen(
            viewModel = EditBillViewModel().apply {
                what = "Percent Split Pizza"
                amount = "100.00"
                mainCurrencyName = "EUR"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
                    DBMember(2, 0, 0, "Bob", true, 1.0, 0, null, null, null, null, null)
                )
                splitMode = SplitMode.PERCENT
                owersSelection[1] = true
                owersSelection[2] = true
                owersPercentSplit[1] = "70"
                owersPercentSplit[2] = "30"
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
