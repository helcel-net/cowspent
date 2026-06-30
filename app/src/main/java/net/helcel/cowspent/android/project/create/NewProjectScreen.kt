package net.helcel.cowspent.android.project.create

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.model.ProjectType

@Composable
fun NewProjectScreen(
    viewModel: NewProjectViewModel,
    onScanQrCode: () -> Unit,
    onImportFile: () -> Unit,
    onChooseFromNextcloud: () -> Unit,
    onOkPressed: () -> Unit,
    onBack: () -> Unit,
    onFieldsChanged: () -> Unit
) {
    LaunchedEffect(viewModel.projectUrl, viewModel.projectType) {
        onFieldsChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_add_project)) },
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
            if (viewModel.isFormValid()) {
                FloatingActionButton(onClick = onOkPressed) {
                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.action_save_bill))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            // What to do
            SectionRow(
                label = stringResource(R.string.new_project_what_todo)
            ) {
                Row {
                    ToggleButton(
                        text = stringResource(R.string.todo_join_label),
                        selected = !viewModel.whatTodoIsCreate,
                        onClick = {
                            viewModel.whatTodoIsCreate = false
                            if (viewModel.projectType == ProjectType.LOCAL)
                                viewModel.projectType = ProjectType.COSPEND
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ToggleButton(
                        text = stringResource(R.string.todo_create_label),
                        selected = viewModel.whatTodoIsCreate,
                        onClick = { viewModel.whatTodoIsCreate = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionRow(
                label = stringResource(R.string.new_project_where)
            ) {
                Row {
                    if (viewModel.whatTodoIsCreate) {
                        ToggleButton(
                            text = stringResource(R.string.where_local_short),
                            selected = viewModel.projectType == ProjectType.LOCAL,
                            onClick = { viewModel.projectType = ProjectType.LOCAL }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    ToggleButton(
                        text = stringResource(R.string.where_cospend_short),
                        selected = viewModel.projectType == ProjectType.COSPEND,
                        onClick = { viewModel.projectType = ProjectType.COSPEND }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ToggleButton(
                        text = stringResource(R.string.where_ihatemoney_short),
                        selected = viewModel.projectType == ProjectType.IHATEMONEY,
                        onClick = { viewModel.projectType = ProjectType.IHATEMONEY }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (viewModel.whatTodoIsCreate) {
                if (viewModel.projectType == ProjectType.LOCAL) {
                    Button(
                        onClick = onImportFile
                    ) {
                        Text(stringResource(R.string.import_tooltip))
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onScanQrCode) {
                    Text(text = stringResource(R.string.scan_qrcode))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp, 16.dp),
                    )
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.projectType != ProjectType.LOCAL) {
                OutlinedTextField(
                    value = viewModel.projectUrl,
                    onValueChange = { viewModel.projectUrl = it },
                    label = {
                        Text(
                            stringResource(
                                if (viewModel.projectType == ProjectType.COSPEND) R.string.setting_cospend_project_url
                                else R.string.setting_ihatemoney_project_url
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!viewModel.whatTodoIsCreate || !viewModel.isAuthenticatedAccount || viewModel.projectType != ProjectType.COSPEND) {
                OutlinedTextField(
                    value = viewModel.projectId,
                    onValueChange = { viewModel.projectId = it },
                    label = { Text(stringResource(R.string.setting_project_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (viewModel.projectType != ProjectType.LOCAL && (!viewModel.whatTodoIsCreate || viewModel.projectType == ProjectType.IHATEMONEY)) {
                OutlinedTextField(
                    value = viewModel.projectPassword,
                    onValueChange = { viewModel.projectPassword = it },
                    label = { Text(stringResource(R.string.setting_new_project_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (viewModel.whatTodoIsCreate && viewModel.projectType != ProjectType.LOCAL) {
                OutlinedTextField(
                    value = viewModel.projectName,
                    onValueChange = { viewModel.projectName = it },
                    label = { Text(stringResource(R.string.setting_new_project_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) }
                )
                
                if (!viewModel.isAuthenticatedAccount || viewModel.projectType == ProjectType.IHATEMONEY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.projectEmail,
                        onValueChange = { viewModel.projectEmail = it },
                        label = { Text(stringResource(R.string.setting_new_project_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                    )
                }
            }
        }
    }

    if (viewModel.showAuthWarningDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showAuthWarningDialog = false },
            title = { Text(stringResource(R.string.auth_project_creation_title)) },
            text = { Text(stringResource(R.string.warning_auth_project_creation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showAuthWarningDialog = false
                    onOkPressed()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAuthWarningDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (viewModel.showNextcloudProjectDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showNextcloudProjectDialog = false },
            title = { Text(stringResource(R.string.choose_account_project_dialog_title)) },
            text = {
                Column {
                    viewModel.nextcloudProjects.forEach { project ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = false,
                                    onClick = {
                                        viewModel.projectId = project.remoteId
                                        viewModel.projectUrl = project.ncUrl
                                        viewModel.showNextcloudProjectDialog = false
                                    }
                                )
                                .padding(16.dp)
                        ) {
                            Text(text = project.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.showNextcloudProjectDialog = false }) {
                    Text(stringResource(R.string.simple_cancel))
                }
            }
        )
    }

    if (viewModel.isCreatingRemoteProject) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.simple_loading)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.creating_remote_project))
                }
            },
            confirmButton = {}
        )
    }

    if (viewModel.errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.errorDialogMessage = null },
            title = { Text(stringResource(R.string.simple_error)) },
            text = { Text(viewModel.errorDialogMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.errorDialogMessage = null }) {
                    Text(stringResource(R.string.simple_ok))
                }
            }
        )
    }
}

@Composable
fun SectionRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Column {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
            content()
        }
    }
}

@Composable
fun ToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        } else {
            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
        }
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
        )
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun NewProjectScreenPreview() {
    NewProjectScreen(
        viewModel = NewProjectViewModel().apply {
            whatTodoIsCreate = true
            projectType = ProjectType.COSPEND
        },
        onScanQrCode = {},
        onImportFile = {},
        onChooseFromNextcloud = {},
        onOkPressed = {},
        onBack = {},
        onFieldsChanged = {}
    )
}
