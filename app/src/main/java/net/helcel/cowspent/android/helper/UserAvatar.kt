package net.helcel.cowspent.android.helper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.util.Base64

@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    r: Int? = null,
    g: Int? = null,
    b: Int? = null,
    avatar: String? = null,
    disabled: Boolean = false,
    alpha: Float = 1f
) {
    val bitmap = remember(avatar) {
        if (!avatar.isNullOrEmpty()) {
            try {
                val bytes = Base64.decode(avatar, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            alpha = alpha
        )
    } else {
        val backgroundColor = remember(name, r, g, b) {
            if (r != null && g != null && b != null) {
                Color(r, g, b)
            } else {
                Color(TextDrawable.getColorFromName(name))
            }
        }

        val initials = name.take(1).uppercase()
        val isLight = remember(backgroundColor) {
            // Simple luminance check
            val luminance =
                0.2126 * backgroundColor.red + 0.7152 * backgroundColor.green + 0.0722 * backgroundColor.blue
            luminance > 0.5
        }

        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background((if (disabled) Color.Gray else backgroundColor).copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = (if (isLight) Color.Black else Color.White).copy(alpha = alpha),
                fontSize = (size.value * 0.5).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview
@Composable
fun UserAvatarPreview() {
    UserAvatar(name = "Alice")
}

@Preview
@Composable
fun UserAvatarCustomColorPreview() {
    UserAvatar(name = "Bob", r = 255, g = 0, b = 0)
}

@Preview
@Composable
fun UserAvatarDisabledPreview() {
    UserAvatar(name = "Charlie", disabled = true)
}
