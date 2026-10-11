package com.lhy.yumerin.designsystem

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformAvatar(uri: String?, fallback: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context, uri) {
        uri?.let { avatarUri -> decodeAvatar(context, avatarUri)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = fallback,
            modifier = modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(fallback.take(1))
    }
}

private fun decodeAvatar(context: Context, uri: String) = runCatching {
    if (uri.startsWith("android.resource://")) {
        val parsed = Uri.parse(uri)
        val resourceName = parsed.pathSegments.lastOrNull() ?: return@runCatching null
        val resourceId = context.resources.getIdentifier(
            resourceName,
            "drawable",
            parsed.authority ?: context.packageName
        )
        if (resourceId == 0) null else BitmapFactory.decodeResource(context.resources, resourceId)
    } else {
        val path = if (uri.startsWith("file://")) Uri.parse(uri).path else uri
        path?.let(BitmapFactory::decodeFile)
    }
}.getOrNull()
