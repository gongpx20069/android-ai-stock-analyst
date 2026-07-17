package com.gongpx.aistockanalyst.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

interface AppUpdateChecker {
    suspend fun check(currentVersion: String): AppUpdate?
}

class GitHubAppUpdateChecker(
    private val client: OkHttpClient,
    private val json: Json,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) : AppUpdateChecker {
    override suspend fun check(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AI-Stock-Analyst-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                return@withContext null
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub update check failed with HTTP ${response.code}")
            }
            val body = response.body.string()
            parseAppUpdate(json, body, currentVersion)
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/gongpx20069/android-ai-stock-analyst/releases/latest"
    }
}

internal fun parseAppUpdate(
    json: Json,
    body: String,
    currentVersion: String,
): AppUpdate? = try {
    val release =
        json.parseToJsonElement(body).jsonObject
    val tagName = release["tag_name"]?.jsonPrimitive?.content
        ?: throw IOException("GitHub release response has no tag")
    val latestVersion = parseVersion(tagName)
        ?: throw IOException("GitHub release tag is not a semantic version")
    val installedVersion = parseVersion(currentVersion)
        ?: throw IOException("Installed app version is not a semantic version")
    if (latestVersion <= installedVersion) {
        return null
    }
    val releaseUrl = release["html_url"]?.jsonPrimitive?.content
        ?: throw IOException("GitHub release response has no page URL")
    if (!isTrustedReleaseUrl(releaseUrl)) {
        throw IOException("GitHub returned an unexpected release URL")
    }
    val apkUrl = release["assets"]
        ?.jsonArray
        ?.map { it.jsonObject }
        ?.firstOrNull { asset ->
            asset["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
        }
        ?.get("browser_download_url")
        ?.jsonPrimitive
        ?.content
        ?.takeIf(::isTrustedApkDownloadUrl)
        ?: throw IOException("The latest GitHub Release has no trusted APK asset")
    AppUpdate(
        versionName = latestVersion.toString(),
        downloadUrl = apkUrl,
        releaseUrl = releaseUrl,
    )
} catch (failure: IOException) {
    throw failure
} catch (failure: IllegalArgumentException) {
    throw IOException("GitHub returned an invalid release response", failure)
}

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

internal fun parseVersion(value: String): SemanticVersion? {
    val match = Regex("""v?(\d+)\.(\d+)\.(\d+)""").matchEntire(value.trim()) ?: return null
    return SemanticVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
    )
}

private fun isTrustedApkDownloadUrl(value: String): Boolean {
    val url = value.toHttpUrlOrNull() ?: return false
    return url.isHttps &&
        url.host == "github.com" &&
        url.encodedPath.startsWith(
            "/gongpx20069/android-ai-stock-analyst/releases/download/",
        )
}

private fun isTrustedReleaseUrl(value: String): Boolean {
    val url = value.toHttpUrlOrNull() ?: return false
    return url.isHttps &&
        url.host == "github.com" &&
        url.encodedPath.startsWith(
            "/gongpx20069/android-ai-stock-analyst/releases/tag/",
        )
}
