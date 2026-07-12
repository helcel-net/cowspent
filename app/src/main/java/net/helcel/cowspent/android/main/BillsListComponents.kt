package net.helcel.cowspent.android.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.MemberAvatar
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.util.SupportUtil

@Composable
fun EmptyProjectsState(onConfigureNextcloud: () -> Unit, onAddManually: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_no_projects).uppercase(), 
            style = MaterialTheme.typography.subtitle1, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_projects_text),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onConfigureNextcloud, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.configure_account_choice))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_project_choice))
        }
    }
}

@Composable
fun EmptyMembersState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_no_members).uppercase(), 
            style = MaterialTheme.typography.subtitle1, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_members_text),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmptyBillsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_no_bills).uppercase(), 
            style = MaterialTheme.typography.subtitle1, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_bills_text),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun BillItemRow(bill: DBBill, payer: DBMember?, onClick: () -> Unit) {
    Row {
        Spacer(Modifier.width(16.dp))
        Divider(thickness = 1.dp, modifier=Modifier.width(40.dp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (payer != null) {
            Box {
                MemberAvatar(
                    member = payer,
                    size = 40.dp
                )
                if (bill.repeat != null && bill.repeat != DBBill.NON_REPEATED) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(24.dp)
                            .offset((-8).dp)
                            .background(MaterialTheme.colors.onSurface, CircleShape)
                            .padding(2.dp),
                        tint = MaterialTheme.colors.surface
                    )
                }
            }
        } else {
            Icon(
                Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bill.formattedWhat.ifEmpty { bill.what }, 
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                text = bill.formattedSubtitle.ifEmpty { bill.comment ?: "" },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = SupportUtil.normalNumberFormat.format(bill.amount),
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Divider(thickness = 1.dp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            Text(
                text = title.uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_no_bills).uppercase(), 
            style = MaterialTheme.typography.subtitle1, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface
        )
        Text(
            text = stringResource(R.string.no_bills_text), 
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}
