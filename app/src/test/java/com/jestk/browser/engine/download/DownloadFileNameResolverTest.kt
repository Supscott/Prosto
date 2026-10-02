package com.jestk.browser.engine.download

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DownloadFileNameResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveFileName_preservesMp3Extension_whenMimeIsOctetStream() {
        val url = "https://example.com/audio/summer_hit.mp3"
        val contentDisposition = "attachment; filename=\"summer_hit.mp3\""
        val mimeType = "application/octet-stream"

        val resolved = DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
        assertThat(resolved).isEqualTo("summer_hit.mp3")
    }

    @Test
    fun resolveFileName_preservesMp3Extension_fromUrlWhenHeaderMissing() {
        val url = "https://cdn.music.com/tracks/track1234.mp3?download=true&token=xyz"
        val mimeType = "application/octet-stream"

        val resolved = DownloadFileNameResolver.resolveFileName(url, null, mimeType)
        assertThat(resolved).isEqualTo("track1234.mp3")
    }

    @Test
    fun resolveFileName_parsesRfc5987Utf8CyrillicFilename() {
        val url = "https://example.com/download?id=999"
        val contentDisposition = "attachment; filename*=UTF-8''%D0%9B%D1%8E%D0%B1%D0%B8%D0%BC%D0%B0%D1%8F%20%D0%BF%D0%B5%D1%81%D0%BD%D1%8F.mp3"
        val mimeType = "audio/mpeg"

        val resolved = DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
        assertThat(resolved).isEqualTo("Любимая песня.mp3")
    }

    @Test
    fun resolveFileName_appendsMp3_whenUrlHasNoExtButMimeIsAudio() {
        val url = "https://example.com/stream/download_audio?song_id=45"
        val mimeType = "audio/mpeg"

        val resolved = DownloadFileNameResolver.resolveFileName(url, null, mimeType)
        assertThat(resolved).endsWith(".mp3")
        assertThat(resolved).startsWith("download_audio")
    }

    @Test
    fun resolveFileName_handlesAudioMp3Mime() {
        val url = "https://example.com/audio/song_stream"
        val mimeType = "audio/mp3"

        val resolved = DownloadFileNameResolver.resolveFileName(url, null, mimeType)
        assertThat(resolved).isEqualTo("song_stream.mp3")
    }

    @Test
    fun resolveFileName_sanitizesIllegalFileSystemCharacters() {
        val url = "https://example.com/test.mp3"
        val contentDisposition = "attachment; filename=\"bad/name:test*question?<>|.mp3\""

        val resolved = DownloadFileNameResolver.resolveFileName(url, contentDisposition, "audio/mpeg")
        assertThat(resolved).doesNotContain("/")
        assertThat(resolved).doesNotContain(":")
        assertThat(resolved).doesNotContain("*")
        assertThat(resolved).doesNotContain("?")
        assertThat(resolved).doesNotContain("<")
        assertThat(resolved).doesNotContain(">")
        assertThat(resolved).doesNotContain("|")
        assertThat(resolved).endsWith(".mp3")
    }

    @Test
    fun resolveUniqueFile_incrementsDuplicateCounter() {
        val dir = tempFolder.root
        val file1 = File(dir, "song.mp3")
        file1.writeText("first")

        val uniqueFile = DownloadFileNameResolver.resolveUniqueFile(dir, "song.mp3")
        assertThat(uniqueFile.name).isEqualTo("song (1).mp3")

        val file2 = File(dir, "song (1).mp3")
        file2.writeText("second")

        val nextUnique = DownloadFileNameResolver.resolveUniqueFile(dir, "song.mp3")
        assertThat(nextUnique.name).isEqualTo("song (2).mp3")
    }
}
