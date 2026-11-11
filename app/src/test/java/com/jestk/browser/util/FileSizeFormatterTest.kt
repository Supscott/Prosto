package com.jestk.browser.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FileSizeFormatterTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun formatFileSize_zeroOrNegative_returnsEmpty() {
        assertThat(FileSizeFormatter.formatFileSize(resources, 0L)).isEmpty()
        assertThat(FileSizeFormatter.formatFileSize(resources, -100L)).isEmpty()
    }

    @Test
    fun formatFileSize_bytes_returnsExactBytes() {
        assertThat(FileSizeFormatter.formatFileSize(resources, 500L)).isEqualTo("500 B")
        assertThat(FileSizeFormatter.formatFileSize(resources, 1023L)).isEqualTo("1023 B")
    }

    @Test
    fun formatFileSize_kilobytes_formatsOneDecimal() {
        assertThat(FileSizeFormatter.formatFileSize(resources, 1024L)).isEqualTo("1.0 KB")
        assertThat(FileSizeFormatter.formatFileSize(resources, 1536L)).isEqualTo("1.5 KB")
    }

    @Test
    fun formatFileSize_megabytes_formatsOneDecimal() {
        assertThat(FileSizeFormatter.formatFileSize(resources, 1024L * 1024L)).isEqualTo("1.0 MB")
        assertThat(FileSizeFormatter.formatFileSize(resources, (2.5 * 1024 * 1024).toLong())).isEqualTo("2.5 MB")
    }

    @Test
    fun formatFileSize_gigabytes_formatsOneDecimal() {
        assertThat(FileSizeFormatter.formatFileSize(resources, 1024L * 1024L * 1024L)).isEqualTo("1.0 GB")
        assertThat(FileSizeFormatter.formatFileSize(resources, (3.7 * 1024 * 1024 * 1024).toLong())).isEqualTo("3.7 GB")
    }

    @Test
    fun formatProgressText_withKnownTotal_formatsProgressAndPercentage() {
        val totalBytes = 100 * 1024 * 1024L // 100 MB
        val downloadedBytes = 25 * 1024 * 1024L // 25 MB
        val result = FileSizeFormatter.formatProgressText(resources, downloadedBytes, totalBytes)
        assertThat(result).isEqualTo("25.0 MB / 100.0 MB (25%)")
    }

    @Test
    fun formatProgressText_withUnknownTotal_returnsDownloadedOnly() {
        val downloadedBytes = 10 * 1024 * 1024L // 10 MB
        val result = FileSizeFormatter.formatProgressText(resources, downloadedBytes, 0L)
        assertThat(result).isEqualTo("10.0 MB")
    }

    @Test
    fun formatProgressText_withZeroDownloaded_returnsZeroBytes() {
        val totalBytes = 50 * 1024 * 1024L
        val result = FileSizeFormatter.formatProgressText(resources, 0L, totalBytes)
        assertThat(result).isEqualTo("0 B / 50.0 MB (0%)")
    }

    @Test
    fun formatSpeed_appendsPerSecond() {
        assertThat(FileSizeFormatter.formatSpeed(resources, 1_572_864L)).isEqualTo("1.5 MB/s")
        assertThat(FileSizeFormatter.formatSpeed(resources, 0L)).isEmpty()
    }

    @Test
    fun formatFileSize_usesLocalizedUnits() {
        val russian = ApplicationProvider.getApplicationContext<Context>()
            .createConfigurationContext(Configuration().apply { setLocale(Locale("ru")) })
            .resources
        assertThat(FileSizeFormatter.formatFileSize(russian, 2048L)).endsWith(" КБ")
        assertThat(FileSizeFormatter.formatSpeed(russian, 2048L)).endsWith(" КБ/с")
    }
}
