package net.helcel.cowspent.android.project.member

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.UserAvatar
import net.helcel.cowspent.android.helper.lazyVerticalScrollbar
import net.helcel.cowspent.model.DBMember

@Composable
fun MemberManagementDialogContent(
    members: List<DBMember>,
    onAddMember: () -> Unit,
    onEditMember: (DBMember) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fab_manage_members),
                    style = MaterialTheme.typography.h6
                )
                IconButton(onClick = onAddMember) {
                    Icon(Icons.Default.Add,modifier=Modifier.size(32.dp),
                        contentDescription = stringResource(R.string.fab_add_member))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .lazyVerticalScrollbar(listState)
            ) {
                items(members) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditMember(member) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = member.name,
                            r = member.r,
                            g = member.g,
                            b = member.b,
                            avatar = member.avatar,
                            disabled = !member.isActivated,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.simple_close).uppercase())
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MemberManagementDialogContentPreview() {
    MaterialTheme {
        MemberManagementDialogContent(
            members = listOf(
                DBMember(1, 0, 1, "Alice", true, 1.0, 0, 255, 100, 100, null, null),
                DBMember(2, 0, 1, "Bob", true, 1.0, 0, 100, 255, 100, null, null),
                DBMember(3, 0, 1, "Charlie", false, 1.0, 0, 100, 100, 255, null, null)
            ),
            onAddMember = {},
            onEditMember = {},
            onDismiss = {}
        )
    }
}

