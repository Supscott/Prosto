package com.jestk.browser.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVersionTest {
    @Test
    fun compareVersions_sameVersion_isEqual() {
        assertThat(compareVersions("1.2.0", "1.2.0")).isEqualTo(0)
    }

    @Test
    fun compareVersions_ignoresVPrefix() {
        assertThat(compareVersions("v1.2.0", "1.2.0")).isEqualTo(0)
        assertThat(compareVersions("V1.2.0", "v1.2.0")).isEqualTo(0)
    }

    @Test
    fun compareVersions_newerPatch_isPositive() {
        assertThat(compareVersions("1.2.1", "1.2.0")).isGreaterThan(0)
    }

    @Test
    fun compareVersions_newerMinor_isPositive() {
        assertThat(compareVersions("1.3.0", "1.2.9")).isGreaterThan(0)
    }

    @Test
    fun compareVersions_newerMajor_isPositive() {
        assertThat(compareVersions("2.0.0", "1.99.99")).isGreaterThan(0)
    }

    @Test
    fun compareVersions_olderVersion_isNegative() {
        assertThat(compareVersions("1.2.0", "1.2.1")).isLessThan(0)
    }

    @Test
    fun compareVersions_comparesNumericallyNotLexicographically() {
        // "1.10.0" is newer than "1.9.0" even though it sorts before it as text.
        assertThat(compareVersions("1.10.0", "1.9.0")).isGreaterThan(0)
        assertThat(compareVersions("1.9.0", "1.10.0")).isLessThan(0)
    }

    @Test
    fun compareVersions_missingComponentsAreZero() {
        assertThat(compareVersions("1.2", "1.2.0")).isEqualTo(0)
        assertThat(compareVersions("1.2.1", "1.2")).isGreaterThan(0)
    }

    @Test
    fun compareVersions_prereleaseIsOlderThanRelease() {
        assertThat(compareVersions("1.2.0-beta1", "1.2.0")).isLessThan(0)
        assertThat(compareVersions("1.2.0", "1.2.0-beta1")).isGreaterThan(0)
    }
}
