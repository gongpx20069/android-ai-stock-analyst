package com.gongpx.aistockanalyst.update

import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAppUpdateCheckerTest {
    @Test
    fun `returns trusted apk when GitHub release is newer`() {
        val update = parseAppUpdate(Json, releaseResponse(tag = "v1.0.3"), "1.0.2")

        assertEquals("1.0.3", update?.versionName)
        assertEquals(
            "https://github.com/gongpx20069/android-ai-stock-analyst/releases/download/v1.0.3/app.apk",
            update?.downloadUrl,
        )
    }

    @Test
    fun `returns null when installed version is current`() {
        assertNull(parseAppUpdate(Json, releaseResponse(tag = "v1.0.3"), "1.0.3"))
    }

    @Test(expected = IOException::class)
    fun `rejects apk hosted outside the project release path`() {
        parseAppUpdate(
            Json,
            releaseResponse(
                tag = "v1.0.3",
                downloadUrl = "https://example.com/app.apk",
            ),
            "1.0.2",
        )
    }

    @Test
    fun `semantic version comparison is numeric`() {
        assertEquals(
            true,
            requireNotNull(parseVersion("1.0.10")) > requireNotNull(parseVersion("1.0.9")),
        )
    }

    private fun releaseResponse(
        tag: String,
        downloadUrl: String =
            "https://github.com/gongpx20069/android-ai-stock-analyst/releases/download/$tag/app.apk",
    ): String =
        """
            {
              "tag_name": "$tag",
              "html_url": "https://github.com/gongpx20069/android-ai-stock-analyst/releases/tag/$tag",
              "assets": [
                {
                  "name": "app.apk",
                  "browser_download_url": "$downloadUrl"
                }
              ]
            }
        """.trimIndent()
}
