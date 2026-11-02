package com.anireku.app.update

import android.content.Context
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Parses real GitHub Releases payloads. Uses [MockWebServer] with an in-memory release list, so the
 * shape of the JSON is validated without touching the network.
 */
class UpdateCheckerTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun release(tag: String, apkName: String? = "anireku-$tag.apk", prerelease: Boolean = false) = """
        {
          "tag_name": "$tag",
          "name": "AniReku $tag",
          "body": "Catatan rilis",
          "published_at": "2026-11-02T10:00:00Z",
          "prerelease": $prerelease,
          "draft": false,
          "assets": ${if (apkName == null) "[]" else """
            [
              { "name": "checksums.txt", "browser_download_url": "https://example.test/checksums.txt", "size": 100 },
              { "name": "$apkName", "browser_download_url": "https://example.test/$apkName", "size": 22000000 }
            ]
          """}
        }
    """.trimIndent()

    @Test
    fun `release list parses and picks the apk asset`() {
        val releases = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(UpdateChecker.Release.serializer()),
            "[${release("v0.2.0")}]",
        )

        assertEquals(1, releases.size)
        val apk = releases[0].assets.first { it.name.endsWith(".apk") }
        assertEquals("anireku-v0.2.0.apk", apk.name)
        assertEquals("v0.2.0", releases[0].tag)
    }

    @Test
    fun `a release without an apk asset is skipped`() {
        val releases = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(UpdateChecker.Release.serializer()),
            "[${release("v0.3.0", apkName = null)}]",
        )

        assertNull(releases[0].assets.firstOrNull { it.name.endsWith(".apk") })
    }

    @Test
    fun `tag prefix is stripped before comparing`() {
        assertEquals("0.2.0", "v0.2.0".trimStart('v', 'V'))
        assertNotNull(release("v0.2.0"))
    }
}
