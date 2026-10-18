package com.lhy.yumerin.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun MessageImageView(
    uri: String,
    contentDescription: String,
    modifier: Modifier,
    fallback: String
) {
    Text(fallback)
}
