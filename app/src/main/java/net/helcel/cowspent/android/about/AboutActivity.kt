package net.helcel.cowspent.android.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import net.helcel.cowspent.theme.ThemeUtils

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThemeUtils.CowspentTheme {
                AboutScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
