package net.helcel.cowspent.android.currencies

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.AlertDialog
import net.helcel.cowspent.android.helper.TextIcon
import net.helcel.cowspent.android.helper.TextIconDisplay
import net.helcel.cowspent.android.helper.formatAmount
import net.helcel.cowspent.model.DBCurrency

@Composable
fun ManageCurrenciesScreen(
    viewModel: ManageCurrenciesViewModel,
    onBack: () -> Unit,
    onSaveMain: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (DBCurrency) -> Unit,
    onEdit: (DBCurrency) -> Unit,
    onCancelEdit: () -> Unit
) {
    val dialogState = viewModel.dialogState
    if (dialogState != null) {
        AlertDialog(
            showDialog = true,
            onDismissRequest = { viewModel.dismissDialog() },
            title = dialogState.title,
            message = dialogState.message,
            icon = dialogState.icon,
            items = dialogState.items,
            positiveText = dialogState.positiveText,
            negativeText = dialogState.negativeText,
            neutralText = dialogState.neutralText,
            onConfirm = {
                dialogState.onConfirm?.invoke()
                viewModel.dismissDialog()
            },
            onCancel = {
                dialogState.onCancel?.invoke()
                viewModel.dismissDialog()
            },
            onNeutral = {
                dialogState.onNeutral?.invoke()
                viewModel.dismissDialog()
            }
        ) {
            dialogState.onItemSelected?.invoke(it)
            viewModel.dismissDialog()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.currency_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.02f))
        ) {
            LaunchedEffect(viewModel.mainCurrencyName) {
                if (viewModel.mainCurrencyName.isNotEmpty()) {
                    delay(500)
                    onSaveMain()
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Section 1: Main Currency
                Text(
                    text = stringResource(R.string.main_currency).uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = viewModel.mainCurrencyName,
                        onValueChange = { viewModel.mainCurrencyName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. EUR", fontSize = 14.sp) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section 2: Exchange Rates
                val isEditing = viewModel.editingCurrencyId != null
                Text(
                    text = (if (isEditing) "Edit exchange rate" else "Add exchange rate").uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Integrated Add/Edit Ribbon
                Surface(
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1 ", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text(viewModel.mainCurrencyName.ifEmpty { "$" }, fontWeight = FontWeight.Bold)
                        Text(
                            text = " = ",
                            style = MaterialTheme.typography.h6,
                            color = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        
                        OutlinedTextField(
                            value = viewModel.newCurrencyRate,
                            onValueChange = { viewModel.newCurrencyRate = it },
                            modifier = Modifier.weight(1.2f).height(52.dp),
                            placeholder = { Text("1", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.02f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = viewModel.newCurrencyName,
                            onValueChange = { viewModel.newCurrencyName = it },
                            modifier = Modifier.weight(1f).height(52.dp),
                            placeholder = { Text(viewModel.mainCurrencyName.ifEmpty { "$" }, fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.02f)
                            )
                        )
                        
                        Row(modifier = Modifier.padding(start = 4.dp)) {
                            IconButton(
                                onClick = onAdd,
                                enabled = viewModel.isAddEnabled()
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Done else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (viewModel.isAddEnabled()) {
                                        if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
                                    } else MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
                                )
                            }
                            
                            if (isEditing) {
                                IconButton(onClick = onCancelEdit) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colors.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "SAVED RATES",
                    style = MaterialTheme.typography.overline,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.currencies) { currency ->
                    CurrencyRow(
                        currency = currency,
                        mainCurrencyName = viewModel.mainCurrencyName,
                        isEditing = viewModel.editingCurrencyId == currency.id,
                        onEdit = { onEdit(currency) },
                        onDelete = { onDelete(currency) }
                    )
                }
            }
        }
    }
}

@Composable
fun CurrencyRow(
    currency: DBCurrency,
    mainCurrencyName: String,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = if (isEditing) 4.dp else 1.dp,
        border = if (isEditing) BorderStroke(1.dp, MaterialTheme.colors.secondary.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1 ",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = mainCurrencyName.ifEmpty { "$" },
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " = ",
                    style = MaterialTheme.typography.h6,
                    color = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = formatAmount(currency.exchangeRate),
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isEditing) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface
                )
                Text(
                    text = " ${currency.name ?: ""}",
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colors.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CurrencyRowPreview() {
    MaterialTheme {
        CurrencyRow(
            currency = DBCurrency(1, 0, 0, "USD", 1.0, 0),
            mainCurrencyName = "EUR",
            isEditing = false,
            onEdit = {},
            onDelete = {}
        )
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun ManageCurrenciesScreenPreview() {
    MaterialTheme {
        ManageCurrenciesScreen(
            viewModel = ManageCurrenciesViewModel().apply {
                mainCurrencyName = "EUR"
                currencies = listOf(
                    DBCurrency(1, 0, 0, "USD", 1.1, 0),
                    DBCurrency(2, 0, 0, "GBP", 0.85, 0)
                )
            },
            onBack = {},
            onSaveMain = {},
            onAdd = {},
            onDelete = {},
            onEdit = {},
            onCancelEdit = {}
        )
    }
}
