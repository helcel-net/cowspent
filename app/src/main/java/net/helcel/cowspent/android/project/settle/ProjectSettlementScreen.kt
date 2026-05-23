package net.helcel.cowspent.android.project.settle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.Transaction
import net.helcel.cowspent.model.UserItem
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.SupportUtil
import net.helcel.cowspent.util.SupportUtil.SETTLE_OPTIMAL
import net.helcel.cowspent.util.SupportUtil.settleBills

@Composable
fun ProjectSettlementDialogContent(
    proj: DBProject,
    db: CowspentSQLiteOpenHelper,
    onSettleBills: (List<Transaction>) -> Unit,
    onShare: (List<Transaction>, Map<Long, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val centerNoneStr = stringResource(R.string.center_none)
    val memberList = remember(proj.id) { db.getMembersOfProject(proj.id, null) }
    val userList = remember(memberList, centerNoneStr) {
        buildList {
            add(UserItem(SETTLE_OPTIMAL, centerNoneStr))
            addAll(memberList.map { UserItem(it.id, it.name) })
        }
    }

    val membersBalance = remember(proj.id) {
        val balance = mutableMapOf<Long, Double>()
        SupportUtil.getStatsOfProject(
            proj.id, db,
            mutableMapOf(), balance, mutableMapOf(), mutableMapOf(),
            -1000, -1000, null, null
        )
        balance
    }

    val membersSortedByName = remember(proj.id) {
        db.getMembersOfProject(proj.id, CowspentSQLiteOpenHelper.key_name)
    }

    val memberIdToName = remember(membersSortedByName) {
        membersSortedByName.associate { it.id to it.name }
    }

    var selectedMemberId by remember(userList) {
        mutableLongStateOf(userList.firstOrNull()?.id ?: SETTLE_OPTIMAL)
    }

    val transactions = remember(selectedMemberId, membersBalance, membersSortedByName) {
        settleBills(membersSortedByName, membersBalance, selectedMemberId)
    }

    ProjectSettlementUI(
        transactions = transactions,
        userList = userList,
        selectedMemberId = selectedMemberId,
        memberIdToName = memberIdToName,
        onMemberSelected = { selectedMemberId = it },
        onSettleBills = onSettleBills,
        onShare = onShare,
        onDismiss = onDismiss
    )
}

@Composable
fun ProjectSettlementUI(
    transactions: List<Transaction>,
    userList: List<UserItem>,
    selectedMemberId: Long,
    memberIdToName: Map<Long, String>,
    onMemberSelected: (Long) -> Unit,
    onSettleBills: (List<Transaction>) -> Unit,
    onShare: (List<Transaction>, Map<Long, String>) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settle_dialog_title),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (transactions.isEmpty()) {
                BalancedStateMessage()
            } else {
                MemberSelector(
                    selectedMemberId = selectedMemberId,
                    userList = userList,
                    onMemberSelected = onMemberSelected
                )

                Spacer(modifier = Modifier.height(16.dp))

                TransactionTable(
                    transactions = transactions,
                    memberIdToName = memberIdToName,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                transactions = transactions,
                memberIdToName = memberIdToName,
                onShare = onShare,
                onSettleBills = onSettleBills,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun BalancedStateMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.settle_dialog_balanced),
            style = MaterialTheme.typography.body1
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MemberSelector(
    selectedMemberId: Long,
    userList: List<UserItem>,
    onMemberSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = true },
        shape = MaterialTheme.shapes.medium,
        color = colorResource(R.color.fg_default_low).copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorResource(R.color.fg_default_low)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = userList.find { it.id == selectedMemberId }?.name ?: "",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = colorResource(R.color.fg_default_low)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                userList.forEach { user ->
                    DropdownMenuItem(onClick = {
                        onMemberSelected(user.id)
                        expanded = false
                    }) {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionTable(
    transactions: List<Transaction>,
    memberIdToName: Map<Long, String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            TableHeaderText(stringResource(R.string.settle_who), Modifier.weight(1.2f))
            TableHeaderText(stringResource(R.string.settle_to_whom), Modifier.weight(1.2f))
            TableHeaderText(stringResource(R.string.settle_how_much), Modifier.weight(1f), TextAlign.End)
        }

        Divider(color = colorResource(R.color.fg_default_low).copy(alpha = 0.12f))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(transactions) { t ->
                TransactionRow(t, memberIdToName)
            }
        }
    }
}

@Composable
private fun TableHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.overline,
        color = colorResource(R.color.fg_default_low),
        textAlign = textAlign,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    memberIdToName: Map<Long, String>
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = memberIdToName[transaction.owerMemberId] ?: "-",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.body2,
                color = colorResource(R.color.fg_default)
            )
            Text(
                text = memberIdToName[transaction.receiverMemberId] ?: "-",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.body2,
                color = colorResource(R.color.fg_default)
            )
            Text(
                text = SupportUtil.normalNumberFormat.format(transaction.amount),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.fg_default)
            )
        }
    }
}

@Composable
private fun ActionButtons(
    transactions: List<Transaction>,
    memberIdToName: Map<Long, String>,
    onShare: (List<Transaction>, Map<Long, String>) -> Unit,
    onSettleBills: (List<Transaction>) -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (transactions.isNotEmpty()) {
            TextButton(onClick = { onShare(transactions, memberIdToName) }) {
                Text(stringResource(R.string.simple_settle_share).uppercase())
            }
            TextButton(onClick = { onSettleBills(transactions) }) {
                Text(stringResource(R.string.simple_create_bills).uppercase())
            }
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.simple_ok).uppercase())
        }
    }
}

@Preview(showBackground = true, name = "With Transactions")
@Composable
fun ProjectSettlementUIPreview() {
    MaterialTheme {
        ProjectSettlementUI(
            transactions = listOf(
                Transaction(1, 2, 25.50),
                Transaction(3, 1, 10.00),
                Transaction(2, 3, 5.25)
            ),
            userList = listOf(
                UserItem(SETTLE_OPTIMAL, "None (Optimal)"),
                UserItem(1, "Alice"),
                UserItem(2, "Bob"),
                UserItem(3, "Charlie")
            ),
            selectedMemberId = SETTLE_OPTIMAL,
            memberIdToName = mapOf(1L to "Alice", 2L to "Bob", 3L to "Charlie"),
            onMemberSelected = {},
            onSettleBills = {},
            onShare = { _, _ -> },
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, name = "Balanced")
@Composable
fun ProjectSettlementUIBalancedPreview() {
    MaterialTheme {
        ProjectSettlementUI(
            transactions = emptyList(),
            userList = listOf(
                UserItem(SETTLE_OPTIMAL, "None (Optimal)"),
                UserItem(1, "Alice"),
                UserItem(2, "Bob")
            ),
            selectedMemberId = SETTLE_OPTIMAL,
            memberIdToName = mapOf(1L to "Alice", 2L to "Bob"),
            onMemberSelected = {},
            onSettleBills = {},
            onShare = { _, _ -> },
            onDismiss = {}
        )
    }
}
