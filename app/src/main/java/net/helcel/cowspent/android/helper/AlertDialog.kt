package net.helcel.cowspent.android.helper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class DialogState(
    val title: String? = null,
    val message: String? = null,
    val icon: ImageVector? = null,
    val items: List<CharSequence>? = null,
    val itemIcons: List<ImageVector>? = null,
    val positiveText: String? = null,
    val negativeText: String? = null,
    val neutralText: String? = null,
    val onConfirm: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null,
    val onNeutral: (() -> Unit)? = null,
    val onItemSelected: ((Int) -> Unit)? = null
)

@Composable
fun StatefulAlertDialog(
    state: DialogState?,
    onDismissRequest: () -> Unit
) {
    if (state != null) {
        AlertDialog(
            showDialog = true,
            onDismissRequest = onDismissRequest,
            title = state.title,
            message = state.message,
            icon = state.icon,
            items = state.items,
            itemIcons = state.itemIcons,
            positiveText = state.positiveText,
            negativeText = state.negativeText,
            neutralText = state.neutralText,
            onConfirm = {
                state.onConfirm?.invoke()
                onDismissRequest()
            },
            onCancel = {
                state.onCancel?.invoke()
                onDismissRequest()
            },
            onNeutral = {
                state.onNeutral?.invoke()
                onDismissRequest()
            }
        ) {
            state.onItemSelected?.invoke(it)
            onDismissRequest()
        }
    }
}

@Composable
fun AlertDialog(
    modifier: Modifier = Modifier,
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    icon: ImageVector? = null,
    items: List<CharSequence>? = null,
    itemIcons: List<ImageVector>? = null,
    positiveText: String? = null,
    negativeText: String? = null,
    neutralText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onNeutral: (() -> Unit)? = null,
    onItemSelected: ((Int) -> Unit)? = null
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            shape = MaterialTheme.shapes.large,
            title = if (title != null || icon != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (title != null) {
                            Text(text = title)
                        }
                    }
                }
            } else null,
            text = if (message != null || items != null) {
                {
                    Column {
                        if (message != null) {
                            Text(text = message)
                        }
                        if (items != null) {
                            if (message != null) Spacer(Modifier.height(8.dp))
                            LazyColumn {
                                itemsIndexed(items) { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                onItemSelected?.invoke(index)
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (itemIcons != null && index < itemIcons.size) {
                                            Icon(
                                                itemIcons[index], 
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        Text(
                                            text = item.toString(),
                                            style = MaterialTheme.typography.body1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else null,
            buttons = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (neutralText != null) {
                        TextButton(onClick = { onNeutral?.invoke() }) {
                            Text(neutralText)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (negativeText != null) {
                        TextButton(onClick = { onCancel?.invoke() }) {
                            Text(negativeText)
                        }
                    }
                    if (positiveText != null) {
                        TextButton(onClick = { onConfirm?.invoke() }) {
                            Text(positiveText)
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun AlertDialogContent(
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    icon: ImageVector? = null,
    items: Array<out CharSequence>? = null,
    itemIcons: Array<out ImageVector>? = null,
    positiveText: String? = null,
    negativeText: String? = null,
    neutralText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onNeutral: (() -> Unit)? = null,
    onItemSelected: ((Int) -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(24.dp,16.dp)) {
            if (title != null || icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (title != null) {
                        Text(text = title, style = MaterialTheme.typography.h6)
                    }
                }
            }

            if (message != null || items != null) {
                Column {
                    if (message != null) {
                        Text(text = message, style = MaterialTheme.typography.body1)
                    }
                    if (items != null) {
                        if (message != null) Spacer(Modifier.height(8.dp))
                        LazyColumn {
                            itemsIndexed(items) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onItemSelected?.invoke(index)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (itemIcons != null && index < itemIcons.size) {
                                        Icon(
                                            itemIcons[index],
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Text(
                                        text = item.toString(),
                                        style = MaterialTheme.typography.body1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (positiveText != null || negativeText != null || neutralText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (neutralText != null) {
                        TextButton(onClick = { onNeutral?.invoke() }) {
                            Text(neutralText)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (negativeText != null) {
                        TextButton(onClick = { onCancel?.invoke() }) {
                            Text(negativeText)
                        }
                    }
                    if (positiveText != null) {
                        TextButton(onClick = { onConfirm?.invoke() }) {
                            Text(positiveText)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleAlertDialogPreview() {
    MaterialTheme {
        AlertDialogContent(
            title = "Info",
            message = "This is a simple alert message.",
            positiveText = "OK"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfirmationDialogPreview() {
    MaterialTheme {
        AlertDialogContent(
            title = "Confirm Action",
            message = "Are you sure you want to proceed?",
            icon = Icons.Default.Info,
            positiveText = "Yes",
            negativeText = "No"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ListDialogWithIconsPreview() {
    MaterialTheme {
        AlertDialogContent(
            title = "Select Option",
            items = arrayOf("Edit", "Delete", "Share"),
            itemIcons = arrayOf(Icons.Default.Edit, Icons.Default.Delete, Icons.Default.Share),
            negativeText = "Cancel"
        )
    }
}
