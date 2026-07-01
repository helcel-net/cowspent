package net.helcel.cowspent.android.project.edit

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.AlertDialog

@Composable
fun EditProjectScreen(
    viewModel: EditProjectViewModel,
    onSave: () -> Unit,
    onDeleteRemote: () -> Unit,
    onBack: () -> Unit
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
                title = { Text(stringResource(R.string.title_edit_project)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!viewModel.isLocal) {
                        IconButton(onClick = onDeleteRemote) {
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSave) {
                Icon(Icons.Default.Done, contentDescription = stringResource(R.string.action_save))
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
            Text(
                text = "GENERAL",
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = { Text(stringResource(R.string.label_project_title)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) }
            )

            if (!viewModel.isLocal) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    placeholder = { Text(stringResource(R.string.label_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                )

                Text(
                    text = "SECURITY",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, top = 24.dp)
                )

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    placeholder = { Text("Old " + stringResource(R.string.label_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.newPassword,
                    onValueChange = { viewModel.newPassword = it },
                    placeholder = { Text("New " + stringResource(R.string.label_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun EditProjectScreenPreview() {
    MaterialTheme {
        EditProjectScreen(
            viewModel = EditProjectViewModel().apply {
                name = "My Awesome Project"
                email = "user@example.com"
            },
            onSave = {},
            onDeleteRemote = {},
            onBack = {}
        )
    }
}
