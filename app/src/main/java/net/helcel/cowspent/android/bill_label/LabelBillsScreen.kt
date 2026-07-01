package net.helcel.cowspent.android.bill_label

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.SupportUtil

@Composable
fun LabelBillsScreen(
    viewModel: LabelBillsViewModel,
    members: List<DBMember>,
    db: CowspentSQLiteOpenHelper,
    onBack: () -> Unit
) {
    val currentBill = viewModel.currentBill
    val remainingCount = viewModel.billsToLabel.size - viewModel.currentBillIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_label_bills)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentBill != null) {
                BillSummaryCard(currentBill, members, remainingCount)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_bills_suggested).uppercase(),
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val suggestions = viewModel.suggestedCategories
                Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                    if (suggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { category ->
                                Box(modifier = Modifier.weight(1f)) {
                                    CategoryButton(
                                        icon = category.icon,
                                        name = category.name ?: "",
                                        onClick = { viewModel.labelCurrentBill(db, category.remoteId.toInt()) }
                                    )
                                }
                            }
                            repeat(2 - suggestions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.msg_no_suggestions),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.label_category).uppercase(),
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.categories) { category ->
                        CategoryButton(
                            icon = category.icon,
                            name = category.name ?: "",
                            onClick = { viewModel.labelCurrentBill(db, category.remoteId.toInt()) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { viewModel.skipCurrentBill() },
                    modifier = Modifier.width(128.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)
                ) {
                    Text(stringResource(R.string.label_bills_skip))
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_bill_labeled_done))
                }
            }
        }
    }
}

@Composable
fun BillSummaryCard(bill: DBBill, members: List<DBMember>, remainingCount: Int) {
    val payerName = remember(bill.payerId, members) {
        members.find { it.id == bill.payerId }?.name ?: bill.payerId.toString()
    }
    val owersNames = remember(bill.billOwersIds, members) {
        bill.billOwersIds.joinToString(", ") { id ->
            members.find { it.id == id }?.name ?: id.toString()
        }
    }

    Box {
        Card(
            elevation = 4.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bill.what, maxLines = 2, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                        if (!bill.comment.isNullOrEmpty()) {
                            Text(bill.comment!!, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Text(
                        SupportUtil.normalNumberFormat.format(bill.amount),
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$payerName \u2192 $owersNames",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bill.date,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colors.primary,
            elevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = remainingCount.toString(),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryButton(icon: String, name: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        contentPadding = PaddingValues(2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 20.sp)
            Text(
                name,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun LabelBillsScreenPreview() {
    val viewModel = LabelBillsViewModel().apply {
        billsToLabel = listOf(
            DBBill(1L, 0, 1L, 1L, 120.5, System.currentTimeMillis() / 1000, "Groceries at Aldi", 0, null, null, 0, null, -1)
        )
        val cats = listOf(
            DBCategory(1, 1, 1, "Groceries", "🛒", ""),
            DBCategory(2, 2, 1, "Leisure", "🥳", ""),
            DBCategory(3, 3, 1, "Rent", "🏠", ""),
            DBCategory(4, 4, 1, "Bills", "💸", "")
        )
        categories = cats
        categoriesMap = cats.associateBy { it.remoteId }
    }
    val members = listOf(
        DBMember(1L, 0, 1L, "Alice", true, 1.0, 0, 255, 100, 100, null, null),
        DBMember(2L, 0, 1L, "Bob", true, 1.0, 0, 100, 255, 100, null, null)
    )
    MaterialTheme {
        LabelBillsScreen(
            viewModel = viewModel,
            members = members,
            db = CowspentSQLiteOpenHelper.getInstance(LocalContext.current),
            onBack = {}
        )
    }
}
