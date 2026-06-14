package net.helcel.cowspent.android.currencies

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
            
            // Visual relationship indicator
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("1 ", style = MaterialTheme.typography.body1)
                    Text(viewModel.mainCurrencyName.ifEmpty { "Base" }, fontWeight = FontWeight.Bold)
                    Text(" = ", style = MaterialTheme.typography.h6)
                    Text(viewModel.newCurrencyRate.ifEmpty { "0.0" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Text(" ")
                    Text(viewModel.newCurrencyName.ifEmpty { "Currency" }, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.newCurrencyName,
                    onValueChange = { viewModel.newCurrencyName = it },
                    label = { Text(stringResource(R.string.currency_edit_name)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. USD") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = viewModel.newCurrencyRate,
                    onValueChange = { viewModel.newCurrencyRate = it },
                    label = { Text(stringResource(R.string.currency_rate)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("1.0") }
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
                    CurrencyRow(currency, viewModel.mainCurrencyName, onDelete = { onDelete(currency) })
                }
            }
        }
    }
}

@Composable
fun CurrencyRow(currency: DBCurrency, mainCurrencyName: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Table-like layout
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1 ",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = mainCurrencyName,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " = ",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column {
                Text(
                    text = currency.exchangeRate.toString(),
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = currency.name ?: "",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
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
            mainCurrencyName = "EUR",
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
