package com.pr4y.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pr4y.app.R

@Composable
fun Pr4yLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentDescription: String? = "PR4Y",
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
