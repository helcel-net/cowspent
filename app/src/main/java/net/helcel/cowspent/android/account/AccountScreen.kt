@file:Suppress("SameParameterValue", "SameParameterValue", "SameParameterValue",
    "SameParameterValue", "SameParameterValue", "SameParameterValue"
)

package net.helcel.cowspent.android.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.theme.ThemeUtils

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSsoClick: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    AccountScreenContent(
        isLoggedIn = viewModel.isLoggedIn,
        isValidatingLogin = viewModel.isValidatingLogin,
        useSso = viewModel.useSso,
        serverUrl = viewModel.serverUrl,
        username = viewModel.username,
        password = viewModel.password,
        isUrlValid = viewModel.isUrlValid,
        showUrlWarning = viewModel.showUrlWarning,
        isSubmitting = viewModel.isSubmitting,
        isFormValid = viewModel.isFormValid,
        onServerUrlChange = {
            viewModel.serverUrl = it
            viewModel.validateUrl()
        },
        onUsernameChange = { viewModel.username = it },
        onPasswordChange = { viewModel.password = it },
        onBack = onBack,
        onConnect = onConnect,
        onSsoClick = onSsoClick,
        onLogout = onLogout
    )
}

@Composable
fun AccountScreenContent(
    isLoggedIn: Boolean,
    isValidatingLogin: Boolean,
    useSso: Boolean,
    serverUrl: String,
    username: String,
    password: String,
    isUrlValid: Boolean,
    showUrlWarning: Boolean,
    isSubmitting: Boolean,
    isFormValid: Boolean,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSsoClick: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_account)) },
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
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            if (isValidatingLogin) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (isLoggedIn) {
                Text(
                    text = "CURRENT ACCOUNT",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = null, 
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.msg_logged_in_as, username),
                            style = MaterialTheme.typography.subtitle1
                        )
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_logout))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!isValidatingLogin) {
                Text(
                    text = "CONNECTION SETTINGS",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, top = if (isLoggedIn) 16.dp else 0.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Sync, 
                        contentDescription = null, 
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    Text(stringResource(R.string.label_use_sso), modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
                    Switch(
                        checked = useSso,
                        onCheckedChange = { onSsoClick(it) },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            uncheckedTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }

                if (!useSso) {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        placeholder = { Text(stringResource(R.string.label_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingIcon = {
                            if (isUrlValid) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Green
                                )
                            }
                        },
                        singleLine = true
                    )

                    if (showUrlWarning) {
                        Text(
                            stringResource(R.string.settings_url_warn_http),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        placeholder = { Text(stringResource(R.string.label_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    @Suppress("DEPRECATION")
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = { Text(stringResource(R.string.label_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFormValid && !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CustomCircularProgressIndicator(size = 24.dp, color = Color.White)
                        } else {
                            Text(stringResource(R.string.action_connect))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomCircularProgressIndicator(size: Dp, color: Color) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp
    )
}

@Preview(showBackground = true)
@Composable
fun AccountScreenPreview() {
    ThemeUtils.CowspentTheme {
        AccountScreenContent(
            isLoggedIn = false,
            isValidatingLogin = false,
            useSso = false,
            serverUrl = "https://nextcloud.example.com",
            username = "user",
            password = "",
            isUrlValid = true,
            showUrlWarning = false,
            isSubmitting = false,
            isFormValid = true,
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onBack = {},
            onConnect = {},
            onSsoClick = {},
            onLogout = {}
        )
    }
}
