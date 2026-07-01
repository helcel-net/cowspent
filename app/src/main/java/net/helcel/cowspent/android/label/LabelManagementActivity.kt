package net.helcel.cowspent.android.label

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import net.helcel.cowspent.theme.ThemeUtils

class LabelManagementActivity : AppCompatActivity() {
    private val viewModel: LabelManagementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, 0L)
        if (projectId == 0L) {
            finish()
            return
        }

        viewModel.loadLabels(projectId)

        setContent {
            ThemeUtils.CowspentTheme {
                LabelManagementScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT_ID = "EXTRA_PROJECT_ID"

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, LabelManagementActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}
