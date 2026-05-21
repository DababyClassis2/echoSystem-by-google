package com.echosystem.localshare.ui.enhancements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

object MicroInteractionSystem {

    @Composable
    fun Modifier.clickableWithHaptic(
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
        onClick: () -> Unit
    ): Modifier {
        val haptic = LocalHapticFeedback.current
        return this.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                haptic.performHapticFeedback(hapticType)
                onClick()
            }
        )
    }
}
