package net.helcel.cowspent.android.project.member

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.MemberAvatar
import net.helcel.cowspent.android.helper.lazyVerticalScrollbar
import net.helcel.cowspent.model.DBMember

@Composable
fun MemberManagementScreen(
    members: List<DBMember>,
    onAddMember: () -> Unit,
    onEditMember: (DBMember) -> Unit,
    onToggleMember: (DBMember, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val activatedMembers = members.filter { it.isActivated }
    val deactivatedMembers = members.filter { !it.isActivated }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_members)) },
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
            FloatingActionButton(onClick = onAddMember) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_save))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyVerticalScrollbar(listState)
            ) {
                item {
                    Text(
                        text = "ACTIVATED MEMBERS",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                items(activatedMembers) { member ->
                    MemberRow(
                        member = member,
                        onEditMember = onEditMember,
                        onToggleMember = { onToggleMember(member, it) }
                    )
                }

                if (deactivatedMembers.isNotEmpty()) {
                    item {
                        Text(
                            text = "DEACTIVATED MEMBERS",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }

                    items(deactivatedMembers) { member ->
                        MemberRow(
                            member = member,
                            onEditMember = onEditMember,
                            onToggleMember = { onToggleMember(member, it) },
                            alpha = 0.6f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemberRow(
    member: DBMember,
    onEditMember: (DBMember) -> Unit,
    onToggleMember: (Boolean) -> Unit,
    alpha: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditMember(member) }
            .padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(
            member = member,
            size = 24.dp,
            alpha = alpha
        )
        Spacer(modifier = Modifier.width(32.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colors.onSurface.copy(alpha = alpha)
        )
        IconButton(onClick = { onToggleMember(!member.isActivated) }) {
            Icon(
                imageVector = if (member.isActivated) Icons.Default.Delete else Icons.Default.Add,
                contentDescription = if (member.isActivated) "Deactivate" else "Activate",
                tint = if (member.isActivated) MaterialTheme.colors.error.copy(alpha = 0.6f) else MaterialTheme.colors.primary.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MemberManagementScreenPreview() {
    MaterialTheme {
        MemberManagementScreen(
            members = listOf(
                DBMember(1, 0, 1, "Alice", true, 1.0, 0, 255, 100, 100, null, null),
                DBMember(2, 0, 1, "Bob", true, 1.0, 0, 100, 255, 100, null, null),
                DBMember(3, 0, 1, "Charlie", false, 1.0, 0, 100, 100, 255, null, null)
            ),
            onAddMember = {},
            onEditMember = {},
            onToggleMember = { _, _ -> },
            onBack = {}
        )
    }
}
