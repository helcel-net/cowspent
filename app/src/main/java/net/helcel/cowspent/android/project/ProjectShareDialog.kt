package net.helcel.cowspent.android.project

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.zxing.WriterException
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.util.ColorUtils

@Composable
fun ProjectShareDialogContent(
    proj: DBProject,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val qrCodeLinkWarn = stringResource(R.string.msg_share_qr_warn)
    
    val shareUrl = remember { proj.getShareUrl() }
    val publicWebUrl = remember { proj.getPublicWebUrl() }

    val qrBitmap = remember(shareUrl) {
        try {
            ColorUtils.encodeAsBitmap(shareUrl)
        } catch (_: WriterException) {
            null
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colors.surface,
        contentColor = contentColorFor(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.title_share).uppercase(),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ShareCard(
                    title = stringResource(R.string.title_share_web),
                    url = publicWebUrl,
                    description = stringResource(R.string.msg_share_web),
                    onUrlClick = {
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            data = publicWebUrl.toUri()
                        }
                        context.startActivity(i)
                    },
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(publicWebUrl))
                        Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                    }
                )

                ShareCard(
                    title = stringResource(R.string.title_share_qr),
                    url = shareUrl,
                    description = stringResource(R.string.msg_share_qr),
                    qrBitmap = qrBitmap,
                    onUrlClick = {
                        Toast.makeText(context, qrCodeLinkWarn, Toast.LENGTH_SHORT).show()
                    },
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(shareUrl))
                        Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            DialogActions(
                onShare = { onShare(shareUrl) },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ShareCard(
    title: String,
    url: String,
    description: String,
    onUrlClick: () -> Unit,
    onCopyClick: () -> Unit,
    qrBitmap: Bitmap? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (qrBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .clickable { onUrlClick() }
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = url,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onCopyClick) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = description,
                style = MaterialTheme.typography.caption,
                textAlign = TextAlign.Start,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun DialogActions(
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = stringResource(R.string.simple_ok).uppercase(),
                style = MaterialTheme.typography.button,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onShare,
            shape = MaterialTheme.shapes.small,
            elevation = null
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_share).uppercase(),
                    style = MaterialTheme.typography.button,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectShareDialogContentPreview() {
    MaterialTheme {
        ProjectShareDialogContent(
            proj = DBProject(
                1, "Vacation", "", "vacation", null, null, null,
                ProjectType.LOCAL, 0L, null, false, 0, null
            ),
            onShare = {},
            onDismiss = {}
        )
    }
}
