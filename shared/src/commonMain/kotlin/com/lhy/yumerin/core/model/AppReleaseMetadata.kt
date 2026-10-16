package com.lhy.yumerin.core.model

/** Public GitHub release metadata used by the Android update screen. */
data class AppReleaseMetadata(
    val versionTag: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?
)
