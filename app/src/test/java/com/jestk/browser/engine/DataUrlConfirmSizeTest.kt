package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataUrlConfirmSizeTest {
    private fun sizeOf(url: String) = dataUrlConfirmRequest(url, null, null, null).sizeBytes

    @Test
    fun base64_oneTrailingPadSubtractsOneByte() {
        assertThat(sizeOf("data:text/plain;base64,aGVsbG8=")).isEqualTo(5L)
        assertThat(sizeOf("data:text/plain;base64,aGk=")).isEqualTo(2L)
    }

    @Test
    fun base64_twoTrailingPadsSubtractTwoBytes() {
        assertThat(sizeOf("data:text/plain;base64,aA==")).isEqualTo(1L)
    }

    @Test
    fun base64_withoutPaddingIsUnchanged() {
        assertThat(sizeOf("data:text/plain;base64,YWJj")).isEqualTo(3L)
    }

    @Test
    fun base64_percentEncodedPaddingCountsAsOnePad() {
        assertThat(sizeOf("data:text/plain;base64,aGVsbG8%3D")).isEqualTo(5L)
        assertThat(sizeOf("data:text/plain;base64,aA%3D%3D")).isEqualTo(1L)
        assertThat(sizeOf("data:text/plain;base64,aA%3d=")).isEqualTo(1L)
    }

    @Test
    fun percentInMetadataDoesNotChangeTheSize() {
        assertThat(sizeOf("data:text/plain;name=a%20b;base64,aGVsbG8=")).isEqualTo(5L)
        assertThat(sizeOf("data:text/plain;name=a%20b,hi")).isEqualTo(2L)
    }

    @Test
    fun base64_emptyPayloadHasNoSize() {
        assertThat(sizeOf("data:text/plain;base64,")).isNull()
        assertThat(sizeOf("data:text/plain;base64,==")).isNull()
    }

    @Test
    fun paddingCount_stopsAtThePayloadStartAndAtTwoPads() {
        assertThat(base64PaddingCount("data:;base64,===", 13)).isEqualTo(2)
        assertThat(base64PaddingCount("a=,", 0)).isEqualTo(0)
    }
}
