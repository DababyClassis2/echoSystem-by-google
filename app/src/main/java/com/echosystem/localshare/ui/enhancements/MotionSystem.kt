package com.echosystem.localshare.ui.enhancements

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

object MotionSystem {
    
    val screenTransitionSpec: ContentTransform =
        (fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 50 }))
            .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -50 }))

    @Composable
    fun AnimatedScreenTransition(
        targetState: Any,
        content: @Composable (targetState: Any) -> Unit
    ) {
        AnimatedContent(
            targetState = targetState,
            transitionSpec = { screenTransitionSpec },
            label = "ScreenTransition"
        ) { target ->
            content(target)
        }
    }
}
