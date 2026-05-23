package net.helcel.cowspent.android.currencies

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.AlertDialog
import net.helcel.cowspent.model.DBCurrency

@Composable
fun ManageCurrenciesScreen(
    viewModel: ManageCurrenciesViewModel,
    onBack: () -> Unit,
    onSaveMain: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (DBCurrency) -> Unit
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
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(stringResource(R.string.main_currency), style = MaterialTheme.typography.h6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.mainCurrencyName,
                    onValueChange = { viewModel.mainCurrencyName = it },
                    label = { Text(stringResource(R.string.currency_edit_name)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSaveMain, enabled = viewModel.mainCurrencyName.isNotEmpty()) {
                    Text(stringResource(R.string.save_or_discard_bill_dialog_save))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.add_currency_title), style = MaterialTheme.typography.h6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.newCurrencyName,
                    onValueChange = { viewModel.newCurrencyName = it },
                    label = { Text(stringResource(R.string.currency_edit_name)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = viewModel.newCurrencyRate,
                    onValueChange = { viewModel.newCurrencyRate = it },
                    label = { Text(stringResource(R.string.currency_rate)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.isAddEnabled()
            ) {
                Text(stringResource(R.string.simple_add))
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.currencies) { currency ->
                    CurrencyRow(currency, onDelete = { onDelete(currency) })
                }
            }
        }
    }
}

@Composable
fun CurrencyRow(currency: DBCurrency, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(currency.name ?: "", style = MaterialTheme.typography.subtitle1)
            Text("Rate: ${currency.exchangeRate}", style = MaterialTheme.typography.caption)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colors.error)
        }
    }
    Divider()
}

@Preview(showBackground = true)
@Composable
fun CurrencyRowPreview() {
    MaterialTheme {
        CurrencyRow(
            currency = DBCurrency(1, 0, 0, "USD", 1.0, 0),
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
            onDelete = {}
        )
    }
}
