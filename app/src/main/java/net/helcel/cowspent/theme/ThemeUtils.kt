package net.helcel.cowspent.theme

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.helcel.cowspent.util.ColorUtils

object ThemeUtils {

    val Shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(28.dp)
    )

    @SuppressLint("ConflictingOnColor")
    @Composable
    fun CowspentTheme(
        accentColor: Int? = null,
        darkTheme: Boolean? = null,
        content: @Composable () -> Unit
    ) {
        val context = LocalContext.current
        val config = LocalConfiguration.current
        val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
        val nightModeKey = stringResource(net.helcel.cowspent.R.string.pref_key_night_mode)

        val resolvedDarkTheme = darkTheme ?: run {
            val nightMode = sharedPreferences.getString(nightModeKey, "-1") ?: "-1"
            when (nightMode) {
                "1" -> false
                "2" -> true
                else -> isSystemInDarkTheme()
            }
        }

        val resolvedAccentColor = accentColor ?: remember(resolvedDarkTheme) {
            ColorUtils.primaryColor(context, resolvedDarkTheme)
        }

        val themedContext = remember(resolvedDarkTheme) {

            if (resolvedDarkTheme != (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)) {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (resolvedDarkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                context.createConfigurationContext(config)
            } else {
                context
            }
        }

        val onPrimary = if (ColorUtils.isLightColor(resolvedAccentColor)) Color.Black else Color.White

        val colors = if (resolvedDarkTheme) {
            darkColors(
                primary = Color(resolvedAccentColor),
                primaryVariant = Color(resolvedAccentColor),
                onPrimary = onPrimary,
                secondary = Color(resolvedAccentColor),
                onSecondary = onPrimary,
                background = Color(0xFF121212),
                surface = Color(0xFF121212),
                onBackground = Color.White,
                onSurface = Color.White
            )
        } else {
            lightColors(
                primary = Color(resolvedAccentColor),
                primaryVariant = Color(resolvedAccentColor),
                onPrimary = onPrimary,
                secondary = Color(resolvedAccentColor),
                onSecondary = onPrimary,
                background = Color.White,
                surface = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black
            )
        }

        MaterialTheme(
            colors = colors,
            shapes = Shapes,
            content = {
                CompositionLocalProvider(LocalContext provides themedContext) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.background
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colors.primary)
                                    .statusBarsPadding()
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                content()
                            }
                        }
                    }
                }
            }
        )
    }
}
