package com.jestk.browser.engine.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GithubReleaseParserTest {
    private fun release(
        tag: String = "v1.2.0",
        assets: String = """[{"name":"prosto-1.2.0.apk","browser_download_url":"https://example.com/prosto.apk"}]""",
        body: String = "Bug fixes",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String =
        """
        {
          "tag_name": "$tag",
          "body": "$body",
          "draft": $draft,
          "prerelease": $prerelease,
          "html_url": "https://github.com/Supscott/browser/releases/tag/$tag",
          "assets": $assets
        }
        """.trimIndent()

    @Test
    fun parseGithubRelease_readsTagNotesAndApkAsset() {
        val update = parseGithubRelease(release())

        assertThat(update).isNotNull()
        assertThat(update?.versionName).isEqualTo("1.2.0")
        assertThat(update?.apkUrl).isEqualTo("https://example.com/prosto.apk")
        assertThat(update?.releaseNotes).isEqualTo("Bug fixes")
        assertThat(update?.releaseUrl).contains("/releases/tag/v1.2.0")
    }

    @Test
    fun parseGithubRelease_stripsVPrefixFromTag() {
        assertThat(parseGithubRelease(release(tag = "v2.0.1"))?.versionName).isEqualTo("2.0.1")
        assertThat(parseGithubRelease(release(tag = "2.0.1"))?.versionName).isEqualTo("2.0.1")
    }

    @Test
    fun parseGithubRelease_picksApkAssetAmongOthers() {
        val assets =
            """
            [
              {"name":"source.zip","browser_download_url":"https://example.com/source.zip"},
              {"name":"prosto-1.2.0.apk","browser_download_url":"https://example.com/prosto.apk"}
            ]
            """.trimIndent()

        val update = parseGithubRelease(release(assets = assets))

        assertThat(update?.apkUrl).isEqualTo("https://example.com/prosto.apk")
    }

    @Test
    fun parseGithubRelease_returnsNullWhenNoApkAsset() {
        val assets = """[{"name":"source.zip","browser_download_url":"https://example.com/source.zip"}]"""

        assertThat(parseGithubRelease(release(assets = assets))).isNull()
    }

    @Test
    fun parseGithubRelease_returnsNullForDraftOrPrerelease() {
        assertThat(parseGithubRelease(release(draft = true))).isNull()
        assertThat(parseGithubRelease(release(prerelease = true))).isNull()
    }

    @Test
    fun parseGithubRelease_returnsNullForMalformedPayload() {
        assertThat(parseGithubRelease("")).isNull()
        assertThat(parseGithubRelease("not json")).isNull()
    }
}
