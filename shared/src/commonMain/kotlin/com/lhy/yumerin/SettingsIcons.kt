package com.lhy.yumerin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val YumerinPaletteIcon: ImageVector = ImageVector.Builder(
    name = "YumerinPaletteIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 2f)
        curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
        curveTo(2f, 17.523f, 6.477f, 22f, 12f, 22f)
        curveTo(17.523f, 22f, 22f, 17.523f, 22f, 12f)
        curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
        close()
        moveTo(7f, 8f)
        curveTo(7.828f, 8f, 8.5f, 8.672f, 8.5f, 9.5f)
        curveTo(8.5f, 10.328f, 7.828f, 11f, 7f, 11f)
        curveTo(6.172f, 11f, 5.5f, 10.328f, 5.5f, 9.5f)
        curveTo(5.5f, 8.672f, 6.172f, 8f, 7f, 8f)
        close()
        moveTo(11f, 4.5f)
        curveTo(11.828f, 4.5f, 12.5f, 5.172f, 12.5f, 6f)
        curveTo(12.5f, 6.828f, 11.828f, 7.5f, 11f, 7.5f)
        curveTo(10.172f, 7.5f, 9.5f, 6.828f, 9.5f, 6f)
        curveTo(9.5f, 5.172f, 10.172f, 4.5f, 11f, 4.5f)
        close()
        moveTo(16.5f, 6.5f)
        curveTo(17.328f, 6.5f, 18f, 7.172f, 18f, 8f)
        curveTo(18f, 8.828f, 17.328f, 9.5f, 16.5f, 9.5f)
        curveTo(15.672f, 9.5f, 15f, 8.828f, 15f, 8f)
        curveTo(15f, 7.172f, 15.672f, 6.5f, 16.5f, 6.5f)
        close()
        moveTo(7.5f, 13.5f)
        curveTo(8.328f, 13.5f, 9f, 14.172f, 9f, 15f)
        curveTo(9f, 15.828f, 8.328f, 16.5f, 7.5f, 16.5f)
        curveTo(6.672f, 16.5f, 6f, 15.828f, 6f, 15f)
        curveTo(6f, 14.172f, 6.672f, 13.5f, 7.5f, 13.5f)
        close()
    }
}.build()

internal val YumerinNotificationIcon: ImageVector = ImageVector.Builder(
    name = "YumerinNotificationIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        curveTo(8.686f, 3f, 6f, 5.686f, 6f, 9f)
        lineTo(6f, 15.25f)
        curveTo(6f, 16.05f, 5.64f, 16.81f, 5.02f, 17.32f)
        lineTo(3.5f, 18.55f)
        lineTo(3.5f, 20f)
        lineTo(20.5f, 20f)
        lineTo(20.5f, 18.55f)
        lineTo(18.98f, 17.32f)
        curveTo(18.36f, 16.81f, 18f, 16.05f, 18f, 15.25f)
        lineTo(18f, 9f)
        curveTo(18f, 5.686f, 15.314f, 3f, 12f, 3f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 21f)
        curveTo(9.4f, 22.15f, 10.55f, 23f, 12f, 23f)
        curveTo(13.45f, 23f, 14.6f, 22.15f, 15f, 21f)
        close()
    }
}.build()

internal val YumerinModelIcon: ImageVector = ImageVector.Builder(
    name = "YumerinModelIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(8f, 5f)
        lineTo(16f, 5f)
        curveTo(17.657f, 5f, 19f, 6.343f, 19f, 8f)
        lineTo(19f, 16f)
        curveTo(19f, 17.657f, 17.657f, 19f, 16f, 19f)
        lineTo(8f, 19f)
        curveTo(6.343f, 19f, 5f, 17.657f, 5f, 16f)
        lineTo(5f, 8f)
        curveTo(5f, 6.343f, 6.343f, 5f, 8f, 5f)
        close()
        moveTo(10f, 8f)
        lineTo(14f, 8f)
        curveTo(15.105f, 8f, 16f, 8.895f, 16f, 10f)
        lineTo(16f, 14f)
        curveTo(16f, 15.105f, 15.105f, 16f, 14f, 16f)
        lineTo(10f, 16f)
        curveTo(8.895f, 16f, 8f, 15.105f, 8f, 14f)
        lineTo(8f, 10f)
        curveTo(8f, 8.895f, 8.895f, 8f, 10f, 8f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 2f); lineTo(10f, 2f); lineTo(10f, 5f); lineTo(8f, 5f); close()
        moveTo(14f, 2f); lineTo(16f, 2f); lineTo(16f, 5f); lineTo(14f, 5f); close()
        moveTo(8f, 19f); lineTo(10f, 19f); lineTo(10f, 22f); lineTo(8f, 22f); close()
        moveTo(14f, 19f); lineTo(16f, 19f); lineTo(16f, 22f); lineTo(14f, 22f); close()
        moveTo(2f, 8f); lineTo(5f, 8f); lineTo(5f, 10f); lineTo(2f, 10f); close()
        moveTo(2f, 14f); lineTo(5f, 14f); lineTo(5f, 16f); lineTo(2f, 16f); close()
        moveTo(19f, 8f); lineTo(22f, 8f); lineTo(22f, 10f); lineTo(19f, 10f); close()
        moveTo(19f, 14f); lineTo(22f, 14f); lineTo(22f, 16f); lineTo(19f, 16f); close()
    }
}.build()

internal val YumerinStorageIcon: ImageVector = ImageVector.Builder(
    name = "YumerinStorageIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 3f)
        curveTo(7.029f, 3f, 3f, 4.79f, 3f, 7f)
        lineTo(3f, 17f)
        curveTo(3f, 19.21f, 7.029f, 21f, 12f, 21f)
        curveTo(16.971f, 21f, 21f, 19.21f, 21f, 17f)
        lineTo(21f, 7f)
        curveTo(21f, 4.79f, 16.971f, 3f, 12f, 3f)
        close()
        moveTo(12f, 4.5f)
        curveTo(16.418f, 4.5f, 19.5f, 5.75f, 19.5f, 7f)
        curveTo(19.5f, 8.25f, 16.418f, 9.5f, 12f, 9.5f)
        curveTo(7.582f, 9.5f, 4.5f, 8.25f, 4.5f, 7f)
        curveTo(4.5f, 5.75f, 7.582f, 4.5f, 12f, 4.5f)
        close()
        moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 12f); lineTo(4f, 12f); close()
        moveTo(4f, 15f); lineTo(20f, 15f); lineTo(20f, 16f); lineTo(4f, 16f); close()
    }
}.build()

internal val YumerinInfoIcon: ImageVector = ImageVector.Builder(
    name = "YumerinInfoIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 2f)
        curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
        curveTo(2f, 17.523f, 6.477f, 22f, 12f, 22f)
        curveTo(17.523f, 22f, 22f, 17.523f, 22f, 12f)
        curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
        close()
        moveTo(12f, 5.5f)
        curveTo(12.828f, 5.5f, 13.5f, 6.172f, 13.5f, 7f)
        curveTo(13.5f, 7.828f, 12.828f, 8.5f, 12f, 8.5f)
        curveTo(11.172f, 8.5f, 10.5f, 7.828f, 10.5f, 7f)
        curveTo(10.5f, 6.172f, 11.172f, 5.5f, 12f, 5.5f)
        close()
        moveTo(11f, 10f); lineTo(13f, 10f); lineTo(13f, 18.5f); lineTo(11f, 18.5f); close()
    }
}.build()
