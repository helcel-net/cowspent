package net.helcel.cowspent.android.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.helcel.cowspent.BuildConfig
import net.helcel.cowspent.R
import net.helcel.cowspent.theme.ThemeUtils

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.simple_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(156.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))

            AboutItem(
                title = stringResource(R.string.about_maintainer_title),
                content = stringResource(R.string.about_maintainer)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AboutItem(
                title = stringResource(R.string.about_license_title),
                content = stringResource(R.string.about_license)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val sourceUrl = stringResource(R.string.about_source)
            AboutItem(
                title = stringResource(R.string.about_source_title),
                content = sourceUrl,
                onClick = { uriHandler.openUri(sourceUrl) }
            )
        }
    }
}

@Composable
fun AboutItem(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var itemModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        itemModifier = itemModifier.clickable(onClick = onClick)
    }

    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            color = if (onClick != null) MaterialTheme.colors.primary else MaterialTheme.typography.body1.color,
            textDecoration = if (onClick != null) TextDecoration.Underline else null
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    ThemeUtils.CowspentTheme {
        AboutScreen(onBack = {})
    }
}