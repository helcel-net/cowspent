package net.helcel.cowspent.android.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType

@Composable
fun ProjectOptionsDialogContent(
    onEditProject: () -> Unit,
    onRemoveProject: () -> Unit,
    onManageMembers: () -> Unit,
    onManageCurrencies: () -> Unit,
    onManageLabels: () -> Unit,
    onStatistics: () -> Unit,
    onSettle: () -> Unit,
    onShareProject: () -> Unit,
    onExportProject: () -> Unit,
    onDismiss: () -> Unit,
    isArchived: Boolean = false,
    projectType: ProjectType = ProjectType.LOCAL,
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
                text = stringResource(R.string.choose_project_management_action).uppercase(),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val isMaintainer = accessLevel >= DBProject.ACCESS_LEVEL_MAINTAINER || accessLevel == DBProject.ACCESS_LEVEL_UNKNOWN
            val isParticipant = accessLevel >= DBProject.ACCESS_LEVEL_PARTICIPANT || accessLevel == DBProject.ACCESS_LEVEL_UNKNOWN

            val row1 = mutableListOf<ProjectOption>()
            val row2 = mutableListOf<ProjectOption>()
            val row3 = mutableListOf<ProjectOption>()

            if (!isArchived && isMaintainer) {
                row1.add(ProjectOption(stringResource(R.string.action_edit), Icons.Default.Edit, onEditProject))
            }
            if (isShareable && isParticipant) {
                row1.add(ProjectOption(stringResource(R.string.action_share), Icons.Default.Share, onShareProject))
            }
            if (projectType == ProjectType.COSPEND) {
                val archiveLabel = if (isArchived) stringResource(R.string.action_unarchive) else stringResource(R.string.action_archive)
                val archiveIcon = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive
                row1.add(ProjectOption(archiveLabel, archiveIcon, onRemoveProject))
            } else {
                row1.add(ProjectOption(stringResource(R.string.action_delete), Icons.Default.Delete, onRemoveProject))
            }

            // Row 2: Manage Member, Manage Labels, Manage Currencies
            if (!isArchived && isMaintainer) {
                row2.add(ProjectOption(stringResource(R.string.action_members), Icons.Default.Group, onManageMembers))
                if (projectType == ProjectType.LOCAL || projectType == ProjectType.COSPEND) {
                    row2.add(ProjectOption(stringResource(R.string.action_labels), Icons.AutoMirrored.Filled.Label, onManageLabels))
                }
                row2.add(ProjectOption(stringResource(R.string.action_currencies), Icons.Default.MonetizationOn, onManageCurrencies))
            }

            // Row 3: Statistics, Settle, Export
            row3.add(ProjectOption(stringResource(R.string.action_stats), Icons.Default.BarChart, onStatistics))
            if (!isArchived && isParticipant) {
                row3.add(ProjectOption(stringResource(R.string.action_settle), Icons.Default.Handshake, onSettle))
            }
            row3.add(ProjectOption(stringResource(R.string.action_export), Icons.Default.Download, onExportProject))

            val allRows = listOf(row1, row2, row3).filter { it.isNotEmpty() }

            allRows.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val emptySpace = 3 - rowOptions.size
                    if (emptySpace > 0) {
                        Spacer(modifier = Modifier.weight(emptySpace / 2f))
                    }

                    rowOptions.forEach { option ->
                        ProjectOptionItem(
                            option = option,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (emptySpace > 0) {
                        Spacer(modifier = Modifier.weight(emptySpace / 2f))
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
            onManageLabels = {},
            onShareProject = {},
            onExportProject = {},
            onDismiss = {},
            isArchived = false,
            projectType = ProjectType.COSPEND,
            accessLevel = DBProject.ACCESS_LEVEL_ADMIN,
            isShareable = true
        )
    }
}


@Preview(showBackground = true)
@Composable
fun ProjectOptionsDialogPreview2() {
    MaterialTheme {
        ProjectOptionsDialogContent(
            onEditProject = {},
            onRemoveProject = {},
            onManageMembers = {},
            onManageCurrencies = {},
            onStatistics = {},
            onSettle = {},
            onManageLabels = {},
            onShareProject = {},
            onExportProject = {},
            onDismiss = {},
            isArchived = true,
            projectType = ProjectType.COSPEND,
            accessLevel = DBProject.ACCESS_LEVEL_ADMIN,
            isShareable = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectOptionsDialogPreview3() {
    MaterialTheme {
        ProjectOptionsDialogContent(
            onEditProject = {},
            onRemoveProject = {},
            onManageMembers = {},
            onManageCurrencies = {},
            onStatistics = {},
            onSettle = {},
            onManageLabels = {},
            onShareProject = {},
            onExportProject = {},
            onDismiss = {},
            isArchived = false,
            projectType = ProjectType.LOCAL,
            accessLevel = DBProject.ACCESS_LEVEL_ADMIN,
            isShareable = true
        )
    }
}
