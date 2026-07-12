package net.helcel.cowspent.android.helper

import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

sealed class TextIcon {
    data class Symbol(val text: String) : TextIcon()
}

/**
 * A helper Composable to display a [TextIcon].
 */
@Composable
fun TextIconDisplay(
    textIcon: TextIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    when (textIcon) {
        is TextIcon.Symbol -> Text(
            text = textIcon.text,
            modifier = modifier,
            color = tint,
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold
        )
    }
}
