package net.helcel.cowspent.android.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R

import net.helcel.cowspent.model.DBProject

@Composable
fun ProjectOptionsDialogContent(
    onEditProject: () -> Unit,
    onRemoveProject: () -> Unit,
    onManageMembers: () -> Unit,
    onManageCurrencies: () -> Unit,
    onStatistics: () -> Unit,
    onSettle: () -> Unit,
    onShareProject: () -> Unit,
    onExportProject: () -> Unit,
    onDismiss: () -> Unit,
    isArchived: Boolean = false,
    accessLevel: Int = DBProject.ACCESS_LEVEL_ADMIN,
    isShareable: Boolean = true
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.choose_project_management_action),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val options = mutableListOf<ProjectOption>()
            val isMaintainer = accessLevel >= DBProject.ACCESS_LEVEL_MAINTAINER || accessLevel == DBProject.ACCESS_LEVEL_UNKNOWN
            val isParticipant = accessLevel >= DBProject.ACCESS_LEVEL_PARTICIPANT || accessLevel == DBProject.ACCESS_LEVEL_UNKNOWN

            if (!isArchived && isMaintainer) {
                options.add(ProjectOption(stringResource(R.string.action_edit_project), Icons.Default.Edit, onEditProject))
            }
            options.add(ProjectOption(stringResource(R.string.fab_rm_project), Icons.Default.Delete, onRemoveProject))
            if (!isArchived && isMaintainer) {
                options.add(ProjectOption(stringResource(R.string.fab_manage_members), Icons.Default.Group, onManageMembers))
                options.add(ProjectOption(stringResource(R.string.fab_manage_currencies), Icons.Default.MonetizationOn, onManageCurrencies))
            }
            options.add(ProjectOption(stringResource(R.string.fab_statistics), Icons.Default.BarChart, onStatistics))
            if (!isArchived && isParticipant) {
                options.add(ProjectOption(stringResource(R.string.fab_settle), Icons.Default.Handshake, onSettle))
            }
            if (isShareable && isParticipant) {
                options.add(ProjectOption(stringResource(R.string.action_share_project), Icons.Default.Share, onShareProject))
            }
            options.add(ProjectOption(stringResource(R.string.fab_export_project), Icons.Default.Download, onExportProject))

            // Simple 2-column grid using Rows
            for (i in options.indices step 2) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ProjectOptionItem(
                        option = options[i],
                        modifier = Modifier.weight(1f)
                    )
                    if (i + 1 < options.size) {
                        ProjectOptionItem(
                            option = options[i + 1],
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}

data class ProjectOption(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun ProjectOptionItem(
    option: ProjectOption,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { option.onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = option.title,
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectOptionsDialogPreview() {
    MaterialTheme {
        ProjectOptionsDialogContent(
            onEditProject = {},
            onRemoveProject = {},
            onManageMembers = {},
            onManageCurrencies = {},
            onStatistics = {},
            onSettle = {},
            onShareProject = {},
            onExportProject = {},
            onDismiss = {},
            isArchived = false,
            accessLevel = DBProject.ACCESS_LEVEL_ADMIN,
            isShareable = true
        )
    }
}
