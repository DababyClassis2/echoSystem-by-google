package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LocalShareShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Snackbar
    small = RoundedCornerShape(8.dp),         // Chip, badge
    medium = RoundedCornerShape(16.dp),       // Card, FAB
    large = RoundedCornerShape(28.dp),        // Dialog, bottom sheet top
    extraLarge = RoundedCornerShape(32.dp),   // Full-screen modal
)

// Custom pill shape for buttons
val PillShape = RoundedCornerShape(50)
