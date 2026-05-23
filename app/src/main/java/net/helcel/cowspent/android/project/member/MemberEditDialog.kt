package net.helcel.cowspent.android.project.member

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.filled.*
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.ColorPicker
import net.helcel.cowspent.android.helper.TextDrawable
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.util.ColorUtils

@Composable
fun MemberEditDialogContent(
    member: DBMember,
    onSave: (String, Double, Boolean, Int?, Int?, Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(member.name) }
    var weight by remember { mutableStateOf(member.weight.toString()) }
    var isActivated by remember { mutableStateOf(member.isActivated) }
    
    val initialColor = remember {
        if (member.r != null && member.g != null && member.b != null) {
            Color.rgb(member.r!!, member.g!!, member.b!!)
        } else {
            TextDrawable.getColorFromName(member.name)
        }
    }
    var selectedColor by remember { mutableIntStateOf(initialColor) }
    var showColorPicker by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.edit_member_dialog_title),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.member_edit_name)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Weight
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(stringResource(R.string.member_edit_weight)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.LineWeight, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Activated
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isActivated = !isActivated }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Block, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.member_edit_toggle), modifier = Modifier.weight(1f))
                Checkbox(checked = isActivated, onCheckedChange = { isActivated = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.member_edit_color), modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(androidx.compose.ui.graphics.Color(selectedColor))
                        .clickable { showColorPicker = true }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (ColorUtils.isLightColor(selectedColor)) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.member_edit_delete))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.simple_cancel).uppercase())
                }
                TextButton(onClick = {
                    val w = weight.replace(',', '.').toDoubleOrNull()
                    if (name.isNotEmpty() && w != null) {
                        onSave(
                            name,
                            w,
                            isActivated,
                            Color.red(selectedColor),
                            Color.green(selectedColor),
                            Color.blue(selectedColor)
                        )
                    }
                }) {
                    Text(stringResource(R.string.simple_ok).uppercase())
                }
            }
        }
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text(stringResource(R.string.settings_colorpicker_title)) },
            text = {
                ColorPicker(initialColor = selectedColor) {
                    selectedColor = it
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text(stringResource(R.string.simple_ok).uppercase())
                }
            },
            dismissButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text(stringResource(R.string.simple_cancel).uppercase())
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MemberEditDialogContentPreview() {
    MaterialTheme {
        MemberEditDialogContent(
            member = DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
            onSave = { _, _, _, _, _, _ -> },
            onDelete = {},
            onDismiss = {}
        )
    }
}
