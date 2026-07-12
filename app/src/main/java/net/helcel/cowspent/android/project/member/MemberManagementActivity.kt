package net.helcel.cowspent.android.project.member

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils

class MemberManagementActivity : AppCompatActivity() {

    private val viewModel: MemberManagementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, 0)
        if (projectId == 0L) {
            finish()
            return
        }

        val db = CowspentSQLiteOpenHelper.getInstance(this)
        viewModel.loadMembers(projectId)

        setContent {
            ThemeUtils.CowspentTheme {
                var showAddMemberDialog by remember { mutableStateOf(false) }
                var editingMember by remember { mutableStateOf<DBMember?>(null) }

                MemberManagementScreen(
                    members = viewModel.members,
                    onAddMember = { showAddMemberDialog = true },
                    onEditMember = { editingMember = it },
                    onToggleMember = { member, isActivated ->
                        db.updateMemberAndSync(member, member.name, member.weight, isActivated, member.r, member.g, member.b, "", "")
                        viewModel.loadMembers(projectId)
                    },
                    onBack = { finish() }
                )

                if (showAddMemberDialog) {
                    Dialog(
                        onDismissRequest = { showAddMemberDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        MemberAddDialogContent(
                            onAdd = { memberName ->
                                val memberNames = db.getMembersOfProject(projectId, null).map { it.name }
                                if (memberNames.contains(memberName)) {
                                    Toast.makeText(this, R.string.member_already_exists, Toast.LENGTH_SHORT).show()
                                } else {
                                    val color = net.helcel.cowspent.android.helper.TextDrawable.getColorFromName(memberName)
                                    db.addMemberAndSync(
                                        DBMember(
                                            0, 0, projectId, memberName, true, 1.0,
                                            DBBill.STATE_ADDED,
                                            android.graphics.Color.red(color),
                                            android.graphics.Color.green(color),
                                            android.graphics.Color.blue(color),
                                            null, null
                                        )
                                    )
                                    viewModel.loadMembers(projectId)
                                    showAddMemberDialog = false
                                }
                            },
                            onDismiss = { showAddMemberDialog = false }
                        )
                    }
                }

                if (editingMember != null) {
                    val member = editingMember!!
                    Dialog(
                        onDismissRequest = { editingMember = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        MemberEditDialogContent(
                            member = member,
                            onSave = { name, weight, isActivated, r, g, b ->
                                db.updateMemberAndSync(member, name, weight, isActivated, r, g, b, "", "")
                                viewModel.loadMembers(projectId)
                                editingMember = null
                            },
                            onDelete = {
                                db.deleteMember(member.id)
                                viewModel.loadMembers(projectId)
                                editingMember = null
                            },
                            onDismiss = { editingMember = null }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, MemberManagementActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}
