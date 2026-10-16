package com.lhy.yumerin

import com.lhy.yumerin.core.model.AppReleaseMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/Lhy723/Yumerin/releases/latest"

internal suspend fun fetchLatestAppRelease(): AppReleaseMetadata = withContext(Dispatchers.IO) {
    val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "Yumerin-Android")
    }

    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("GitHub release check returned HTTP $responseCode")
        }

        val release = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val body = StringBuilder()
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                body.append(buffer, 0, count)
                if (body.length > MAX_RELEASE_RESPONSE_CHARS) {
                    throw IOException("GitHub release response is too large")
                }
            }
            JSONObject(body.toString())
        }

        val versionTag = release.getString("tag_name")
        val releasePageUrl = requireGithubUrl(release.getString("html_url"))
        val apkDownloadUrl = release.optJSONArray("assets")
            ?.let { assets ->
                var firstApk: String? = null
                var arm64Apk: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val url = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                        ?.let(::requireGithubUrl) ?: continue
                    if (firstApk == null) firstApk = url
                    if (name.contains("arm64", ignoreCase = true)) {
                        arm64Apk = url
                        break
                    }
                }
                arm64Apk ?: firstApk
            }

        AppReleaseMetadata(
            versionTag = versionTag,
            releasePageUrl = releasePageUrl,
            apkDownloadUrl = apkDownloadUrl
        )
    } finally {
        connection.disconnect()
    }
}

private fun requireGithubUrl(value: String): String {
    val uri = runCatching { URI(value) }.getOrElse {
        throw IOException("GitHub returned an invalid release URL", it)
    }
    if (uri.scheme != "https" || uri.host != "github.com") {
        throw IOException("GitHub returned a release URL outside github.com")
    }
    return uri.toString()
}

private const val MAX_RELEASE_RESPONSE_CHARS = 256 * 1024
