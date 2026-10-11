package com.lhy.yumerin.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform image loading is kept out of the domain and shared UI state. */
@Composable
expect fun PlatformAvatar(uri: String?, fallback: String, modifier: Modifier = Modifier)
