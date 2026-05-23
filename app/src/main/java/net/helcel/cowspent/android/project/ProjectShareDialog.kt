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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
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
    val qrCodeLinkWarn = stringResource(R.string.qrcode_link_open_attempt_warning)
    
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom =  8.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.share_dialog_title),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShareCard(
                    title = stringResource(R.string.share_project_public_url_title),
                    url = publicWebUrl,
                    description = stringResource(R.string.share_project_public_url_dialog_message),
                    icon = Icons.Default.Link,
                    onUrlClick = {
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            data = publicWebUrl.toUri()
                        }
                        context.startActivity(i)
                    },
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(publicWebUrl))
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                ShareCard(
                    title = stringResource(R.string.share_project_public_qrcode_title),
                    url = shareUrl,
                    description = stringResource(R.string.share_project_dialog_message),
                    icon = Icons.Default.QrCode,
                    qrBitmap = qrBitmap,
                    onUrlClick = {
                        Toast.makeText(context, qrCodeLinkWarn, Toast.LENGTH_SHORT).show()
                    },
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(shareUrl))
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
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
    icon: ImageVector,
    onUrlClick: () -> Unit,
    onCopyClick: () -> Unit,
    qrBitmap: Bitmap? = null
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorResource(R.color.fg_default_low).copy(alpha = 0.04f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                )
            }

            if (qrBitmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.size(128.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colors.surface,
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
                            tint = colorResource(R.color.fg_default_low)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
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
                    text = stringResource(R.string.simple_share_share).uppercase(),
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
