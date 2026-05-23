@file:Suppress("SameParameterValue", "SameParameterValue", "SameParameterValue",
    "SameParameterValue", "SameParameterValue", "SameParameterValue"
)

package net.helcel.cowspent.android.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.settings_server_settings)) },
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
                    text = stringResource(R.string.account_logged_in_as, username),
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.account_logout))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!isValidatingLogin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.use_sso_toggle), modifier = Modifier.weight(1f))
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
                        label = { Text(stringResource(R.string.settings_url)) },
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
                        label = { Text(stringResource(R.string.settings_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    @Suppress("DEPRECATION")
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.settings_password)) },
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
                            Text(stringResource(R.string.settings_submit))
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
