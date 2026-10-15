package com.lhy.yumerin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
internal val AppPage.navigationIcon: ImageVector
    get() = when (this) {
        AppPage.HOME -> YumerinChatIcon
        AppPage.EXPLORE -> YumerinCharacterIcon
        AppPage.SETTINGS -> YumerinSettingsIcon
        AppPage.PROFILE -> YumerinProfileIcon
    }

internal val YumerinChatIcon = ImageVector.Builder(
    name = "YumerinChatIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 3f)
        lineTo(20f, 3f)
        curveTo(21.1f, 3f, 22f, 3.9f, 22f, 5f)
        lineTo(22f, 16f)
        curveTo(22f, 17.1f, 21.1f, 18f, 20f, 18f)
        lineTo(7f, 18f)
        lineTo(2f, 22f)
        lineTo(2f, 5f)
        curveTo(2f, 3.9f, 2.9f, 3f, 4f, 3f)
        close()
    }
}.build()

internal val YumerinCharacterIcon = ImageVector.Builder(
    name = "YumerinCharacterIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 11f)
        curveTo(10.21f, 11f, 12f, 9.21f, 12f, 7f)
        curveTo(12f, 4.79f, 10.21f, 3f, 8f, 3f)
        curveTo(5.79f, 3f, 4f, 4.79f, 4f, 7f)
        curveTo(4f, 9.21f, 5.79f, 11f, 8f, 11f)
        close()
        moveTo(16.5f, 11f)
        curveTo(18.43f, 11f, 20f, 9.43f, 20f, 7.5f)
        curveTo(20f, 5.57f, 18.43f, 4f, 16.5f, 4f)
        curveTo(15.87f, 4f, 15.29f, 4.17f, 14.78f, 4.46f)
        curveTo(15.32f, 5.17f, 15.67f, 6.05f, 15.67f, 7f)
        curveTo(15.67f, 8.35f, 15.08f, 9.56f, 14.14f, 10.4f)
        curveTo(14.8f, 10.78f, 15.61f, 11f, 16.5f, 11f)
        close()
        moveTo(8f, 13f)
        curveTo(4.13f, 13f, 1f, 15f, 1f, 17.5f)
        lineTo(1f, 21f)
        lineTo(15f, 21f)
        lineTo(15f, 17.5f)
        curveTo(15f, 15f, 11.87f, 13f, 8f, 13f)
        close()
        moveTo(16.5f, 13f)
        curveTo(15.93f, 13f, 15.39f, 13.05f, 14.87f, 13.14f)
        curveTo(16.22f, 14.13f, 17f, 15.47f, 17f, 17f)
        lineTo(17f, 21f)
        lineTo(23f, 21f)
        lineTo(23f, 17.5f)
        curveTo(23f, 15f, 20.1f, 13f, 16.5f, 13f)
        close()
    }
}.build()

internal val YumerinSettingsIcon = ImageVector.Builder(
    name = "YumerinSettingsIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(19.14f, 12.94f)
        curveTo(19.2f, 12.63f, 19.24f, 12.32f, 19.24f, 12f)
        curveTo(19.24f, 11.68f, 19.2f, 11.37f, 19.14f, 11.06f)
        lineTo(21.18f, 9.49f)
        curveTo(21.36f, 9.35f, 21.41f, 9.1f, 21.3f, 8.89f)
        lineTo(19.37f, 5.55f)
        curveTo(19.25f, 5.34f, 19.01f, 5.26f, 18.79f, 5.34f)
        lineTo(16.39f, 6.31f)
        curveTo(15.89f, 5.93f, 15.34f, 5.61f, 14.74f, 5.37f)
        lineTo(14.38f, 2.82f)
        curveTo(14.35f, 2.59f, 14.15f, 2.42f, 13.91f, 2.42f)
        lineTo(10.09f, 2.42f)
        curveTo(9.85f, 2.42f, 9.65f, 2.59f, 9.62f, 2.82f)
        lineTo(9.26f, 5.37f)
        curveTo(8.66f, 5.61f, 8.11f, 5.93f, 7.61f, 6.31f)
        lineTo(5.21f, 5.34f)
        curveTo(4.99f, 5.26f, 4.75f, 5.34f, 4.63f, 5.55f)
        lineTo(2.7f, 8.89f)
        curveTo(2.59f, 9.1f, 2.64f, 9.35f, 2.82f, 9.49f)
        lineTo(4.86f, 11.06f)
        curveTo(4.8f, 11.37f, 4.76f, 11.68f, 4.76f, 12f)
        curveTo(4.76f, 12.32f, 4.8f, 12.63f, 4.86f, 12.94f)
        lineTo(2.82f, 14.51f)
        curveTo(2.64f, 14.65f, 2.59f, 14.9f, 2.7f, 15.11f)
        lineTo(4.63f, 18.45f)
        curveTo(4.75f, 18.66f, 4.99f, 18.74f, 5.21f, 18.66f)
        lineTo(7.61f, 17.69f)
        curveTo(8.11f, 18.07f, 8.66f, 18.39f, 9.26f, 18.63f)
        lineTo(9.62f, 21.18f)
        curveTo(9.65f, 21.41f, 9.85f, 21.58f, 10.09f, 21.58f)
        lineTo(13.91f, 21.58f)
        curveTo(14.15f, 21.58f, 14.35f, 21.41f, 14.38f, 21.18f)
        lineTo(14.74f, 18.63f)
        curveTo(15.34f, 18.39f, 15.89f, 18.07f, 16.39f, 17.69f)
        lineTo(18.79f, 18.66f)
        curveTo(19.01f, 18.74f, 19.25f, 18.66f, 19.37f, 18.45f)
        lineTo(21.3f, 15.11f)
        curveTo(21.41f, 14.9f, 21.36f, 14.65f, 21.18f, 14.51f)
        close()
        moveTo(15.5f, 12f)
        curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
        curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
        curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
        curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
        close()
    }
}.build()

private val YumerinProfileIcon = ImageVector.Builder(
    name = "YumerinProfileIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 12f)
        curveTo(14.761f, 12f, 17f, 9.761f, 17f, 7f)
        curveTo(17f, 4.239f, 14.761f, 2f, 12f, 2f)
        curveTo(9.239f, 2f, 7f, 4.239f, 7f, 7f)
        curveTo(7f, 9.761f, 9.239f, 12f, 12f, 12f)
        close()
        moveTo(12f, 14f)
        curveTo(7.581f, 14f, 4f, 16.239f, 4f, 19f)
        lineTo(4f, 22f)
        lineTo(20f, 22f)
        lineTo(20f, 19f)
        curveTo(20f, 16.239f, 16.419f, 14f, 12f, 14f)
        close()
    }
}.build()
