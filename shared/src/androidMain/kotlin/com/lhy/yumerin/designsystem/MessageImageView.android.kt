package com.lhy.yumerin.designsystem

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun MessageImageView(
    uri: String,
    contentDescription: String,
    modifier: Modifier,
    fallback: String
) {
    val context = LocalContext.current
    val bitmap = remember(context, uri) {
        runCatching {
            val path = if (uri.startsWith("file://")) {
                android.net.Uri.parse(uri).path
            } else {
                uri
            }
            path?.let(BitmapFactory::decodeFile)
        }.getOrNull()?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(fallback)
    }
}
