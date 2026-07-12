package net.helcel.cowspent.android.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LoginDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onInitiateSsoLogin: () -> Unit,
    errorMessage: String? = null
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            shape = MaterialTheme.shapes.large,
            title = { Text("Login") },
            text = {
                if (errorMessage != null) {
                    Text(text = errorMessage)
                } else {
                    Text(text = "Please choose a login method.")
                }
            },
            confirmButton = {
                TextButton(onClick = onInitiateSsoLogin) {
                    Text("Login with Nextcloud SSO")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            },
            modifier = Modifier
        )
    }
}

@Composable
fun LoginDialogContent(
    onInitiateSsoLogin: () -> Unit,
    onDismissRequest: () -> Unit,
    errorMessage: String? = null
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "LOGIN",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (errorMessage != null) {
                Text(text = errorMessage)
            } else {
                Text(text = "Please choose a login method.")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
                TextButton(onClick = onInitiateSsoLogin) {
                    Text("Login with Nextcloud SSO")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginDialogPreview() {
    MaterialTheme {
        LoginDialogContent(
            onDismissRequest = {},
            onInitiateSsoLogin = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginDialogErrorPreview() {
    MaterialTheme {
        LoginDialogContent(
            onDismissRequest = {},
            onInitiateSsoLogin = {},
            errorMessage = "Invalid credentials. Please try again."
        )
    }
}
