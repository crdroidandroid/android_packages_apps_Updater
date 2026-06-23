/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.data.DeviceMetadata

private data class DeviceLink(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val url: String,
)

@Composable
fun DeviceLinksRow(
    metadata: DeviceMetadata,
    modifier: Modifier = Modifier,
) {
    val links = buildList {
        add(DeviceLink(R.drawable.ic_link_telegram, R.string.updater_link_telegram, metadata.telegram))
        add(DeviceLink(R.drawable.ic_link_donate, R.string.updater_link_donate, metadata.paypal))
        metadata.forum?.takeIf(String::isNotBlank)?.let {
            add(DeviceLink(R.drawable.ic_link_forum, R.string.updater_link_forum, it))
        }
    }
    if (links.isEmpty()) return

    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsDimension.itemPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            LinkTile(
                iconRes = link.iconRes,
                labelRes = link.labelRes,
                modifier = Modifier.weight(1f),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun LinkTile(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@UiModePreviews
@Composable
private fun DeviceLinksRowPreview() {
    SettingsTheme {
        DeviceLinksRow(
            metadata = DeviceMetadata(
                telegram = "https://t.me/example",
                paypal = "https://www.paypal.com/paypalme/example",
                forum = "https://forum.example/thread",
            ),
        )
    }
}
