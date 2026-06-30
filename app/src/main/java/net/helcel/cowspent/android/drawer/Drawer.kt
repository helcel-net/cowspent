package net.helcel.cowspent.android.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.MemberAvatar
import net.helcel.cowspent.android.helper.formatBalance
import net.helcel.cowspent.android.helper.lazyVerticalScrollbar
import net.helcel.cowspent.android.helper.TextIcon
import net.helcel.cowspent.android.helper.TextIconDisplay
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType

@Composable
fun Drawer(
    projects: List<DBProject>,
    members: List<DBMember>,
    memberBalances: Map<Long, Double> = emptyMap(),
    selectedProjectId: Long,
    selectedMemberId: Long?,
    lastSyncText: String,
    mainCurrency: String? = null,
    showArchived: Boolean = false,
    onProjectClick: (Long) -> Unit,
    onProjectOptionsClick: (Long) -> Unit,
    onMemberClick: (Long?) -> Unit,
    onAddProjectClick: () -> Unit,
    onAppSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.surface),
    ) {
        DrawerHeader(
            lastSyncText = lastSyncText,
            onAddProjectClick = onAddProjectClick,
            onAppSettingsClick = onAppSettingsClick
        )

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val maxBottomHeight = maxHeight / 2

            Column(modifier = Modifier.fillMaxSize()) {
                // Projects Section
                val activeProjects = projects.filter { !it.isArchived }
                    .sortedWith(compareByDescending<DBProject> { it.latestBillTs }.thenByDescending { it.id })
                val archivedProjects = projects.filter { it.isArchived }.sortedByDescending { it.archivedTs }
                val archivedToDisplay = if (showArchived) archivedProjects else archivedProjects.filter { it.id == selectedProjectId }

                val projectsState = rememberLazyListState()
                LazyColumn(
                    state = projectsState,
                    modifier = Modifier.weight(1f).lazyVerticalScrollbar(projectsState)
                ) {
                    items(activeProjects) { project ->
                        ProjectDrawerItem(
                            project = project,
                            isSelected = project.id == selectedProjectId,
                            onClick = { onProjectClick(project.id) },
                            onOptionsClick = { onProjectOptionsClick(project.id) }
                        )
                    }
                    if (archivedToDisplay.isNotEmpty()) {
                        item { Divider(Modifier.height(4.dp)) }
                        items(archivedToDisplay) { project ->
                            ProjectDrawerItem(
                                project = project,
                                isSelected = project.id == selectedProjectId,
                                onClick = { onProjectClick(project.id) },
                                onOptionsClick = { onProjectOptionsClick(project.id) },
                                alpha = 0.6f,
                                icon = Icons.Default.Archive
                            )
                        }
                    }
                }

                Divider(Modifier.height(4.dp))

                // Members Section
                val membersState = rememberLazyListState()
                val sortedMembers = remember(members, memberBalances, selectedMemberId) {
                    members.filter {
                        val balance = memberBalances[it.id] ?: 0.0
                        it.isActivated || balance > 0.01 || balance < -0.01 || it.id == selectedMemberId
                    }.sortedWith(compareBy<DBMember> {
                        val balance = memberBalances[it.id] ?: 0.0
                        when {
                            balance > 0.01 -> 0
                            balance < -0.01 -> 1
                            else -> 2
                        }
                    }.thenBy {
                        val balance = memberBalances[it.id] ?: 0.0
                        if (balance > 0.01) -balance else balance
                    }.thenBy { it.name })
                }

                LazyColumn(
                    state = membersState,
                    modifier = Modifier
                        .heightIn(max = maxBottomHeight)
                        .lazyVerticalScrollbar(membersState)
                ) {
                    if (sortedMembers.isNotEmpty()) {
                        item {
                            DrawerItem(
                                icon = Icons.Default.Receipt,
                                text = stringResource(R.string.label_all_bills),
                                secondaryText = mainCurrency,
                                selected = selectedMemberId == null,
                                onClick = { onMemberClick(null) }
                            )
                        }
                        items(sortedMembers) { member ->
                            val balance = memberBalances[member.id] ?: 0.0
                            MemberDrawerItem(
                                member = member,
                                balance = balance,
                                isSelected = member.id == selectedMemberId,
                                onClick = { onMemberClick(member.id) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerHeader(
    lastSyncText: String,
    onAddProjectClick: () -> Unit,
    onAppSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.primary)
            .padding(16.dp, 4.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(id = R.string.app_name),
                    color = MaterialTheme.colors.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onAddProjectClick,
                    modifier = Modifier.scale(0.8f)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.action_add_project),
                        tint = MaterialTheme.colors.onPrimary
                    )
                }
                IconButton(
                    onClick = onAppSettingsClick,
                    modifier = Modifier.scale(0.8f)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.action_settings),
                        tint = MaterialTheme.colors.onPrimary
                    )
                }
            }
            if (lastSyncText.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colors.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(lastSyncText, color = MaterialTheme.colors.onPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProjectDrawerItem(
    project: DBProject,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    alpha: Float = 1f,
    icon: ImageVector = Icons.Default.Folder
) {
    val projectName = project.name.ifEmpty { project.remoteId }
    DrawerItem(
        icon = icon,
        text = projectName,
        selected = isSelected,
        onClick = onClick,
        onSecondaryClick = onOptionsClick,
        alpha = alpha
    )
}

@Composable
private fun MemberDrawerItem(
    member: DBMember,
    balance: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val balanceText = formatBalance(balance)
    val balanceColor = when {
        balance > 0.01 -> Color(0xFF4CAF50)
        balance < -0.01 -> Color(0xFFF44336)
        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    }

    DrawerItem(
        member = member,
        balanceText = balanceText.ifEmpty { null },
        balanceColor = balanceColor,
        selected = isSelected,
        onClick = onClick
    )
}

@Composable
fun DrawerItem(
    icon: ImageVector? = null,
    member: DBMember? = null,
    text: String? = null,
    balanceText: String? = null,
    secondaryText: String? = null,
    balanceColor: Color = Color.Unspecified,
    selected: Boolean = false,
    alpha: Float = 1f,
    onClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null
) {
    val backgroundColor = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = (if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface).copy(alpha = alpha)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (member != null) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (member != null) {
            MemberAvatar(
                member = member,
                size = 24.dp,
                alpha = alpha
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = 0.6f * alpha))
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        val itemText = text ?: member?.name ?: ""
        Text(
            text = itemText,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        
        if (balanceText != null) {
            Text(
                text = balanceText,
                color = balanceColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        if (secondaryText != null) {
            Box(modifier = Modifier
                .padding(end = 8.dp)
           ) {
                TextIconDisplay(
                    textIcon = TextIcon.Symbol(secondaryText),
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        }
        
        if (onSecondaryClick != null) {
            IconButton(onClick = onSecondaryClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        } else if (member != null) {
            // Equalize height with project items that have a secondary button
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DrawerItemPreview() {
    MaterialTheme {
        DrawerItem(
            icon = Icons.Default.Folder,
            text = "My Project",
            selected = true,
            onClick = {},
            onSecondaryClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DrawerPreview() {
    MaterialTheme {
        Drawer(
            projects = listOf(
                DBProject(1, "Vacation", "", "vacation", null, null, null, ProjectType.LOCAL, 0L, null, false, 0, null, null),
                DBProject(2, "Home", "", "home", null, null, null, ProjectType.LOCAL, 0L, null, false, 0, null, 123456789L)
            ),
            members = listOf(
                DBMember(1, 0, 1, "Alice", true, 1.0, 0, null, null, null, null, null),
                DBMember(2, 0, 1, "Bob", true, 1.0, 0, null, null, null, null, null)
            ),
            selectedProjectId = 1,
            selectedMemberId = null,
            lastSyncText = "Last sync: 5 mins ago",
            mainCurrency = "EUR",
            onProjectClick = {},
            onProjectOptionsClick = {},
            onMemberClick = {},
            onAddProjectClick = {},
            onAppSettingsClick = {}
        )
    }
}
