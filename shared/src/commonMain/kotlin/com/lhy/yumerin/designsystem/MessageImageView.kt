package com.lhy.yumerin.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a locally stored message image with rounded-rectangle cropping.
 * Unlike [PlatformAvatar] this is not clipped to a circle and scales to the
 * given modifier; platforms without local file access render the fallback.
 */
@Composable
expect fun MessageImageView(uri: String, contentDescription: String, modifier: Modifier = Modifier, fallback: String = "图片")
