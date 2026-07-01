package net.helcel.cowspent.android.label

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.ColorPicker
import net.helcel.cowspent.android.helper.StatefulAlertDialog
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBPaymentMode

@Composable
fun LabelManagementScreen(
    viewModel: LabelManagementViewModel,
    onBack: () -> Unit
) {
    LabelManagementScreenContent(
        categories = viewModel.categories,
        paymentModes = viewModel.paymentModes,
        dialogState = viewModel.dialogState,
        onBack = onBack,
        onAddCategory = viewModel::addCategory,
        onUpdateCategory = viewModel::updateCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onAddPaymentMode = viewModel::addPaymentMode,
        onUpdatePaymentMode = viewModel::updatePaymentMode,
        onDeletePaymentMode = viewModel::deletePaymentMode,
        onDismissDialog = { viewModel.dialogState = null },
        onShowDialog = { viewModel.dialogState = it }
    )
}

@Composable
fun LabelManagementScreenContent(
    categories: List<DBCategory>,
    paymentModes: List<DBPaymentMode>,
    dialogState: net.helcel.cowspent.android.helper.DialogState?,
    onBack: () -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    onUpdateCategory: (Long, String, String, String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onAddPaymentMode: (String, String, String) -> Unit,
    onUpdatePaymentMode: (Long, String, String, String) -> Unit,
    onDeletePaymentMode: (Long) -> Unit,
    onDismissDialog: () -> Unit,
    onShowDialog: (net.helcel.cowspent.android.helper.DialogState) -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        stringResource(R.string.label_categories),
        stringResource(R.string.label_payment_modes)
    )

    val deleteLabelTitle = stringResource(R.string.delete_label_confirmation_title)
    val deleteLabelMessage = stringResource(R.string.delete_label_confirmation_message)
    val yesText = stringResource(R.string.simple_yes)
    val noText = stringResource(R.string.simple_no)

    var showEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<DBCategory?>(null) }
    var editingPaymentMode by remember { mutableStateOf<DBPaymentMode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_labels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedTab == 0) {
                    editingCategory = null
                    showEditDialog = true
                } else {
                    editingPaymentMode = null
                    showEditDialog = true
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                CategoryList(
                    categories = categories,
                    onEdit = { 
                        editingCategory = it
                        showEditDialog = true
                    },
                    onDelete = { category ->
                        onShowDialog(net.helcel.cowspent.android.helper.DialogState(
                            title = deleteLabelTitle,
                            message = deleteLabelMessage,
                            positiveText = yesText,
                            negativeText = noText,
                            onConfirm = { onDeleteCategory(category.id) }
                        ))
                    }
                )
            } else {
                PaymentModeList(
                    paymentModes = paymentModes,
                    onEdit = { 
                        editingPaymentMode = it
                        showEditDialog = true
                    },
                    onDelete = { pm ->
                        onShowDialog(net.helcel.cowspent.android.helper.DialogState(
                            title = deleteLabelTitle,
                            message = deleteLabelMessage,
                            positiveText = yesText,
                            negativeText = noText,
                            onConfirm = { onDeletePaymentMode(pm.id) }
                        ))
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        if (selectedTab == 0) {
            EditLabelDialog(
                title = if (editingCategory == null) stringResource(R.string.action_add_project) else stringResource(R.string.action_edit),
                initialName = editingCategory?.name ?: "",
                initialIcon = editingCategory?.icon ?: "",
                initialColor = editingCategory?.color ?: "#FF0000",
                onDismiss = { showEditDialog = false },
                onSave = { name, icon, color ->
                    if (editingCategory == null) {
                        onAddCategory(name, icon, color)
                    } else {
                        onUpdateCategory(editingCategory!!.id, name, icon, color)
                    }
                    showEditDialog = false
                }
            )
        } else {
            EditLabelDialog(
                title = if (editingPaymentMode == null) stringResource(R.string.action_add_project) else stringResource(R.string.action_edit),
                initialName = editingPaymentMode?.name ?: "",
                initialIcon = editingPaymentMode?.icon ?: "",
                initialColor = editingPaymentMode?.color ?: "#00FF00",
                onDismiss = { showEditDialog = false },
                onSave = { name, icon, color ->
                    if (editingPaymentMode == null) {
                        onAddPaymentMode(name, icon, color)
                    } else {
                        onUpdatePaymentMode(editingPaymentMode!!.id, name, icon, color)
                    }
                    showEditDialog = false
                }
            )
        }
    }

    StatefulAlertDialog(
        state = dialogState,
        onDismissRequest = onDismissDialog
    )
}

@Composable
fun CategoryList(
    categories: List<DBCategory>,
    onEdit: (DBCategory) -> Unit,
    onDelete: (DBCategory) -> Unit
) {
    LazyColumn {
        items(categories) { category ->
            LabelItem(
                name = category.name ?: "",
                icon = category.icon,
                color = category.color,
                onEdit = { onEdit(category) },
                onDelete = { onDelete(category) }
            )
        }
    }
}

@Composable
fun PaymentModeList(
    paymentModes: List<DBPaymentMode>,
    onEdit: (DBPaymentMode) -> Unit,
    onDelete: (DBPaymentMode) -> Unit
) {
    LazyColumn {
        items(paymentModes) { pm ->
            LabelItem(
                name = pm.name ?: "",
                icon = pm.icon,
                color = pm.color,
                onEdit = { onEdit(pm) },
                onDelete = { onDelete(pm) }
            )
        }
    }
}

@Composable
fun LabelItem(
    name: String,
    icon: String,
    color: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(parseColor(color)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(32.dp))
        Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colors.error.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun EditLabelDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.label_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { if (it.length <= 2) icon = it },
                    placeholder = { Text(stringResource(R.string.label_icon)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.label_color), style = MaterialTheme.typography.caption)
                ColorPicker(
                    initialColor = parseColorInt(color),
                    onColorChanged = { color = String.format("#%06X", 0xFFFFFF and it) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, icon, color) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.simple_cancel))
            }
        }
    )
}

fun parseColor(colorString: String): Color {
    return try {
        Color(colorString.toColorInt())
    } catch (_: Exception) {
        Color.Gray
    }
}

fun parseColorInt(colorString: String): Int {
    return try {
        colorString.toColorInt()
    } catch (_: Exception) {
        Color.Gray.toArgb()
    }
}

@Preview(showBackground = true)
@Composable
fun LabelManagementCategoriesPreview() {
    MaterialTheme {
        LabelManagementScreenContent(
            categories = listOf(
                DBCategory(1, 1, 1, "Groceries", "🛒", "#FF0000"),
                DBCategory(2, 2, 1, "Rent", "🏠", "#00FF00")
            ),
            paymentModes = emptyList(),
            dialogState = null,
            onBack = {},
            onAddCategory = { _, _, _ -> },
            onUpdateCategory = { _, _, _, _ -> },
            onDeleteCategory = { _ -> },
            onAddPaymentMode = { _, _, _ -> },
            onUpdatePaymentMode = { _, _, _, _ -> },
            onDeletePaymentMode = { _ -> },
            onDismissDialog = {},
            onShowDialog = {},
            initialTab = 0
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LabelManagementPaymentModesPreview() {
    MaterialTheme {
        LabelManagementScreenContent(
            categories = emptyList(),
            paymentModes = listOf(
                DBPaymentMode(1, 1, 1, "Cash", "💵", "#0000FF"),
                DBPaymentMode(2, 2, 1, "Credit Card", "💳", "#FFFF00")
            ),
            dialogState = null,
            onBack = {},
            onAddCategory = { _, _, _ -> },
            onUpdateCategory = { _, _, _, _ -> },
            onDeleteCategory = { _ -> },
            onAddPaymentMode = { _, _, _ -> },
            onUpdatePaymentMode = { _, _, _, _ -> },
            onDeletePaymentMode = { _ -> },
            onDismissDialog = {},
            onShowDialog = {},
            initialTab = 1
        )
    }
}
