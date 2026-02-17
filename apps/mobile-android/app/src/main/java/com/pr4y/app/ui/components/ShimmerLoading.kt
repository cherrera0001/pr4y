package com.pr4y.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tech Lead Note: Global Shimmer loading component.
 * Standard: Reusable UI components should reside in the components package.
 */
@Composable
fun ShimmerLoading() {
    val shimmerColors = listOf(
        Color.DarkGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.DarkGray.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(translateAnim, translateAnim)
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.height(32.dp))
        Box(modifier = Modifier.fillMaxWidth(0.5f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(brush))
    }
}
