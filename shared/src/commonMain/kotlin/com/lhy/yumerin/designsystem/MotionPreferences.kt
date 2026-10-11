package com.lhy.yumerin.designsystem

import androidx.compose.runtime.Composable

/** System-level reduced-motion signal; user setting is layered on top in App. */
@Composable
expect fun isSystemReducedMotion(): Boolean
