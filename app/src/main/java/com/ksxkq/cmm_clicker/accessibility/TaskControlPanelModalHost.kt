package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlin.math.roundToInt

internal enum class TaskControlModalTone {
    DEFAULT,
    SUCCESS,
    FAILURE,
    WARNING,
}

internal data class TaskControlModalAction(
    val key: String,
    val text: String,
)

internal data class TaskControlModalModel(
    val title: String,
    val message: String,
    val tone: TaskControlModalTone = TaskControlModalTone.DEFAULT,
    val dismissOnBackdropTap: Boolean = true,
    val actions: List<TaskControlModalAction>,
)

@Composable
internal fun TaskControlModalHost(
    visibleState: MutableTransitionState<Boolean>,
    model: TaskControlModalModel?,
    onDismissRequest: () -> Unit,
    onAction: (String) -> Unit,
) {
    val logTag = "TaskControlModalHost"
    var retainedModel by remember { mutableStateOf<TaskControlModalModel?>(null) }
    if (model != null) {
        retainedModel = model
    }
    LaunchedEffect(model, visibleState.currentState, visibleState.targetState) {
        if (model == null && !visibleState.currentState && !visibleState.targetState) {
            retainedModel = null
        }
    }
    val resolvedModel = model ?: retainedModel
    if (resolvedModel == null && !visibleState.currentState && !visibleState.targetState) {
        return
    }
    if (resolvedModel == null) {
        return
    }
    LaunchedEffect(
        resolvedModel.title,
        visibleState.currentState,
        visibleState.targetState,
        visibleState.isIdle,
    ) {
        Log.d(
            logTag,
            "modal_host title=${resolvedModel.title} current=${visibleState.currentState} " +
                "target=${visibleState.targetState} idle=${visibleState.isIdle}",
        )
    }
    val scrimFadeSpec = tween<Float>(
        durationMillis = 180,
        easing = FastOutSlowInEasing,
    )
    val cardFadeSpec = tween<Float>(
        durationMillis = 180,
        easing = FastOutSlowInEasing,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = scrimFadeSpec),
            exit = fadeOut(animationSpec = scrimFadeSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (resolvedModel.dismissOnBackdropTap) {
                                onDismissRequest()
                            }
                        },
                    ),
            )
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = cardFadeSpec) + slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> (fullHeight * 0.08f).roundToInt() },
            ),
            exit = fadeOut(animationSpec = cardFadeSpec) + slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> (fullHeight * 0.05f).roundToInt() },
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, toneBorderColor(tone = resolvedModel.tone)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = resolvedModel.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = toneTitleColor(tone = resolvedModel.tone),
                    )
                    Text(
                        text = resolvedModel.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        resolvedModel.actions.forEach { action ->
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onAction(action.key) },
                            ) {
                                Text(action.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun toneBorderColor(tone: TaskControlModalTone) = when (tone) {
    TaskControlModalTone.DEFAULT -> MaterialTheme.colorScheme.outline
    TaskControlModalTone.SUCCESS -> MaterialTheme.colorScheme.primary
    TaskControlModalTone.FAILURE -> MaterialTheme.colorScheme.error
    TaskControlModalTone.WARNING -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun toneTitleColor(tone: TaskControlModalTone) = when (tone) {
    TaskControlModalTone.DEFAULT -> MaterialTheme.colorScheme.onSurface
    TaskControlModalTone.SUCCESS -> MaterialTheme.colorScheme.primary
    TaskControlModalTone.FAILURE -> MaterialTheme.colorScheme.error
    TaskControlModalTone.WARNING -> MaterialTheme.colorScheme.tertiary
}
