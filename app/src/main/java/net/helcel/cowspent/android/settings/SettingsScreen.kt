package net.helcel.cowspent.android.settings

import android.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.ColorPicker
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.ColorUtils
import net.helcel.cowspent.util.Cowspent

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onNightModeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val keyNightMode = stringResource(R.string.pref_key_night_mode)
    val valNightModeSystem = stringResource(R.string.pref_value_night_mode_system)
    val valNightModeNo = stringResource(R.string.pref_value_night_mode_no)
    val valNightModeYes = stringResource(R.string.pref_value_night_mode_yes)

    val keyColorMode = stringResource(R.string.pref_key_color_mode)
    val keyUseServerColor = stringResource(R.string.pref_key_use_server_color)
    val keyUseSystemColor = stringResource(R.string.pref_key_use_system_color)
    val keyColor = stringResource(R.string.pref_key_color)
    val keyOfflineMode = stringResource(R.string.pref_key_offline_mode)
    val keyShowArchived = stringResource(R.string.pref_key_show_archived)

    // States for preferences
    var nightMode by remember(keyNightMode) {
        mutableStateOf(sharedPreferences.getString(keyNightMode, "-1") ?: "-1")
    }

    var colorMode by remember(keyColorMode, keyUseServerColor, keyUseSystemColor) {
        mutableStateOf(sharedPreferences.getString(keyColorMode, null) ?: run {
            val useServer = sharedPreferences.getBoolean(keyUseServerColor, true)
            val useSystem = sharedPreferences.getBoolean(keyUseSystemColor, true)
            when {
                useServer -> "server"
                useSystem -> "system"
                else -> "manual"
            }
        })
    }

    // Apply theme globally only when leaving settings to avoid flickering/restarts during selection
    DisposableEffect(nightMode) {
        onDispose {
            Cowspent.setAppTheme(nightMode.toInt())
        }
    }

    var appColor by remember(keyColor) {
        mutableIntStateOf(sharedPreferences.getInt(keyColor, Color.BLUE))
    }
    var offlineMode by remember(keyOfflineMode) {
        mutableStateOf(sharedPreferences.getBoolean(keyOfflineMode, false))
    }
    var showArchived by remember(keyShowArchived) {
        mutableStateOf(sharedPreferences.getBoolean(keyShowArchived, false))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance
            SettingsCategory(stringResource(R.string.settings_appearance))

            SettingsSwitchPreference(
                title = stringResource(R.string.settings_show_archived),
                icon = Icons.Default.Archive,
                checked = showArchived,
                onCheckedChange = {
                    showArchived = it
                    sharedPreferences.edit {
                        putBoolean(keyShowArchived, it)
                        if (!it) {
                            val selectedProjectId = sharedPreferences.getLong("selected_project", 0)
                            if (selectedProjectId != 0L) {
                                val db = CowspentSQLiteOpenHelper.getInstance(context)
                                val project = db.getProject(selectedProjectId)
                                if (project?.isArchived == true) {
                                    putLong("selected_project", 0)
                                }
                            }
                        }
                    }
                }
            )

            SettingsListPreference(
                title = stringResource(R.string.settings_night_mode),
                icon = Icons.Default.Brightness2,
                value = nightMode,
                entries = mapOf(
                    valNightModeSystem to stringResource(R.string.pref_value_theme_system),
                    valNightModeNo to stringResource(R.string.pref_value_theme_light),
                    valNightModeYes to stringResource(R.string.pref_value_theme_dark)
                ),
                onValueChange = {
                    nightMode = it
                    sharedPreferences.edit {
                        putString(keyNightMode, it)
                    }
                    onNightModeChanged(it)
                }
            )

            SettingsListPreference(
                title = stringResource(R.string.settings_color_mode),
                icon = Icons.Default.Palette,
                value = colorMode,
                entries = mapOf(
                    "system" to stringResource(R.string.pref_value_color_system),
                    "server" to stringResource(R.string.pref_value_color_server),
                    "manual" to stringResource(R.string.pref_value_color_manual)
                ),
                onValueChange = { mode ->
                    colorMode = mode
                    sharedPreferences.edit {
                        putString(keyColorMode, mode)
                        // Keep legacy flags in sync just in case
                        putBoolean(keyUseServerColor, mode == "server")
                        putBoolean(keyUseSystemColor, mode == "system")
                    }
                    if (mode == "server") {
                        CowspentSQLiteOpenHelper.getInstance(context).cowspentServerSyncHelper.runAccountProjectsSync()
                    }
                    onColorSelected(ColorUtils.primaryColor(context))
                }
            )

            if (colorMode == "manual") {
                SettingsColorPreference(
                    title = stringResource(R.string.settings_color_custom),
                    summary = stringResource(R.string.settings_color_custom),
                    icon = Icons.Default.Palette,
                    initialColor = appColor,
                    onColorSelected = {
                        appColor = it
                        sharedPreferences.edit {
                            putInt(keyColor, it)
                        }
                        onColorSelected(it)
                    }
                )
            }

            // Network
            SettingsCategory(stringResource(R.string.settings_network))

            SettingsSwitchPreference(
                title = stringResource(R.string.settings_offline_mode),
                summary = stringResource(R.string.settings_offline_mode_summary),
                icon = Icons.Default.Sync,
                checked = offlineMode,
                onCheckedChange = {
                    offlineMode = it
                    sharedPreferences.edit {
                        putBoolean(keyOfflineMode, it)
                    }
                }
            )

            SettingsPreference(
                title = stringResource(R.string.title_account),
                icon = Icons.Default.AccountCircle,
                onClick = onAccountSettingsClick
            )

            // Other
            SettingsCategory(stringResource(R.string.settings_other))

            SettingsPreference(
                title = stringResource(R.string.title_about),
                icon = Icons.Default.Info,
                onClick = onAboutClick
            )
        }
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colors.onSurface,
        style = MaterialTheme.typography.subtitle1,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsPreference(
    title: String,
    summary: String? = null,
    icon: Any? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(modifier = Modifier.width(32.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
fun SettingsSwitchPreference(
    title: String,
    summary: String? = null,
    icon: Any? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.caption)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colors.onSurface
            )
        )
    }
}

@Composable
fun SettingsListPreference(
    title: String,
    icon: Any? = null,
    value: String,
    entries: Map<String, String>,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsPreference(
        title = title,
        summary = entries[value],
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = (key == value),
                                    onClick = {
                                        onValueChange(key)
                                        showDialog = false
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (key == value),
                                onClick = {
                                    onValueChange(key)
                                    showDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.simple_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsColorPreference(
    title: String,
    summary: String? = null,
    icon: Any? = null,
    initialColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempColor by remember { mutableIntStateOf(initialColor) }

    SettingsPreference(
        title = title,
        summary = summary,
        icon = icon,
        onClick = {
            tempColor = initialColor
            showDialog = true
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                ColorPicker(initialColor = tempColor) {
                    tempColor = it
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onColorSelected(tempColor)
                    showDialog = false
                }) {
                    Text(stringResource(R.string.simple_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.simple_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsIcon(icon: Any?) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (icon) {
            is ImageVector -> Icon(icon, contentDescription = null, tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            is Painter -> Icon(icon, contentDescription = null, tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            onBack = {},
            onAccountSettingsClick = {},
            onAboutClick = {},
            onColorSelected = {},
            onNightModeChanged = {}
        )
    }
}
