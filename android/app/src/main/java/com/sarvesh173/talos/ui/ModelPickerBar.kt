package com.sarvesh173.talos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

/**
 * Horizontal model picker that sits directly above the composer.
 *
 * A bottom sheet would cover the message the user is answering, and the picker
 * is a per-message decision, not a destination — so it lives inline as a
 * scrollable rail. It disappears the moment the composer takes focus; the
 * message field always wins the space.
 *
 * Models come from the core's live provider snapshot. While that is loading it
 * says so instead of rendering placeholder chips.
 */
@Composable
fun ModelPickerBar(
    client: TalosClient,
    selectedModel: String?,
    isDark: Boolean,
    brandColor: Color,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    val providers by client.providers.collectAsState()
    val providersLoading by client.providersLoading.collectAsState()
    val providersError by client.providersError.collectAsState()

    LaunchedEffect(Unit) { client.fetchProviders() }

    val models = remember(providers) {
        providers
            .flatMap { p -> p.models.map { m -> p.name to m } }
            .distinctBy { it.second.id }
    }

    val surface = if (isDark) Color(0xFF1A1A1E).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.97f)
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(shape)
            .background(surface)
            .border(BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)), shape)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // "Loading models" was a permanent lie when the fetch failed.
                text = when {
                    providersError != null -> "Models unavailable"
                    providersLoading && models.isEmpty() -> "Loading models"
                    models.isEmpty() -> "No models"
                    else -> "${models.size} models"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Hide",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = brandColor,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .applePressable(0.94f, pressHaptic = true) { onClose() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(models, key = { it.second.id }) { (providerName, model) ->
                val isSelected = selectedModel == model.id
                val chipShape = RoundedCornerShape(12.dp)
                val bg = when {
                    isSelected -> brandColor.copy(alpha = 0.18f)
                    isDark -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Black.copy(alpha = 0.04f)
                }
                Column(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(chipShape)
                        .background(bg)
                        .border(
                            BorderStroke(0.8.dp, if (isSelected) brandColor.copy(alpha = 0.45f) else Color.Transparent),
                            chipShape
                        )
                        .applePressable(0.96f, pressHaptic = true) { onSelect(model.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = model.id.substringAfterLast("/"),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) brandColor else if (isDark) Color.White else Color(0xFF1D1D1F),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (model.contextLength != null) {
                        Text(
                            text = "${model.contextLength!! / 1024}k",
                            fontSize = 11.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f)
                        )
                    } else {
                        Text(
                            text = providerName,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}
