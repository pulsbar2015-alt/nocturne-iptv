package com.nocturne.iptv.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.data.EpgProgram
import com.nocturne.iptv.player.source.Transports
import com.nocturne.iptv.ui.theme.NocturnePalette

/**
 * A single channel line: logo (or a TV glyph), name, group, current EPG title,
 * and a favourite star. Tapping the row plays; tapping the star favourites.
 */
@Composable
fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    currentProgram: EpgProgram?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = NocturnePalette.Crypt,
        border = BorderStroke(1.dp, NocturnePalette.Coffin)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(channel)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = NocturnePalette.Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProgram?.title ?: channel.group,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (currentProgram != null) NocturnePalette.Ember else NocturnePalette.Ash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = Transports.label(channel.url),
                        style = MaterialTheme.typography.labelSmall,
                        color = NocturnePalette.Ash,
                        maxLines = 1
                    )
                    if (channel.hasAlternates) {
                        Text(
                            text = "· ${channel.allSources.size} SRC",
                            style = MaterialTheme.typography.labelSmall,
                            color = NocturnePalette.Ember,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) "Remove from vault" else "Add to vault",
                tint = if (isFavorite) NocturnePalette.Ember else NocturnePalette.Ash,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggleFavorite)
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun ChannelLogo(channel: Channel) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(NocturnePalette.Coffin, NocturnePalette.Abyss)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(shape)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = NocturnePalette.Blood,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** Horizontal, wrapping-free row of group filters. */
@Composable
fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = if (selected) NocturnePalette.Blood else NocturnePalette.Crypt,
        border = BorderStroke(1.dp, if (selected) NocturnePalette.Ember else NocturnePalette.Coffin)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) NocturnePalette.Bone else NocturnePalette.Ash,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** Empty-state block used across screens. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = NocturnePalette.Bone
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NocturnePalette.Ash
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}