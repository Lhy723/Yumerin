package com.lhy.yumerin.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformAvatar(uri: String?, fallback: String, modifier: Modifier) {
    Text(fallback.take(1))
}
