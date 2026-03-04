package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class SharedOverlayButtonStyle {
    SOLID,
    OUTLINE,
    TONAL,
}

internal data class SharedOverlayAction(
    val text: String,
    val style: SharedOverlayButtonStyle = SharedOverlayButtonStyle.OUTLINE,
    val onClick: () -> Unit,
)

internal data class SharedOverlayBreadcrumb(
    val label: String,
    val routeDepth: Int,
)

@Composable
internal fun SharedOverlayDialogButton(
    text: String,
    style: SharedOverlayButtonStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    when (style) {
        SharedOverlayButtonStyle.SOLID -> Button(onClick = onClick, modifier = modifier) { Text(text) }
        SharedOverlayButtonStyle.OUTLINE -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
        SharedOverlayButtonStyle.TONAL -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
internal fun SharedOverlayDialogScaffold(
    title: String,
    showBack: Boolean,
    breadcrumbs: List<SharedOverlayBreadcrumb> = emptyList(),
    onBreadcrumbNavigate: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    headerActions: List<SharedOverlayAction> = emptyList(),
    footerActions: List<SharedOverlayAction> = emptyList(),
    onBack: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(18.dp)
    Card(
        modifier = modifier
            .fillMaxSize(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showBack) {
                    SharedOverlayDialogButton(
                        text = "返回",
                        style = SharedOverlayButtonStyle.OUTLINE,
                        onClick = onBack,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                SharedOverlayDialogButton(
                    text = "关闭",
                    style = SharedOverlayButtonStyle.OUTLINE,
                    onClick = onClose,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            if (headerActions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    headerActions.forEach { action ->
                        SharedOverlayDialogButton(
                            text = action.text,
                            style = action.style,
                            onClick = action.onClick,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
            if (breadcrumbs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    breadcrumbs.forEachIndexed { index, crumb ->
                        val isLast = index == breadcrumbs.lastIndex
                        if (isLast || onBreadcrumbNavigate == null) {
                            Text(
                                text = crumb.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLast) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                            )
                        } else {
                            Text(
                                text = crumb.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onBreadcrumbNavigate(crumb.routeDepth) },
                            )
                        }
                        if (!isLast) {
                            Text(
                                text = "/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val canUseVerticalScroll = maxHeight != Dp.Infinity
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canUseVerticalScroll) {
                                Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    content()
                }
            }

            if (footerActions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    footerActions.forEach { action ->
                        SharedOverlayDialogButton(
                            text = action.text,
                            style = action.style,
                            modifier = Modifier.weight(1f),
                            onClick = action.onClick,
                        )
                    }
                }
            }
        }
    }
}
