package com.jestk.browser.ui.overlays

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** PRO-87: the search engine form must not open with the "must contain %s" error. */
class UrlTemplateErrorTest {
    @Test
    fun freshPrefill_showsNoError() {
        assertThat(isUrlTemplatePlaceholderMissing(URL_TEMPLATE_PREFILL)).isFalse()
    }

    @Test
    fun blank_showsNoError() {
        assertThat(isUrlTemplatePlaceholderMissing("")).isFalse()
    }

    @Test
    fun typedUrlWithoutPlaceholder_showsError() {
        assertThat(isUrlTemplatePlaceholderMissing("https://youtube.com/search?q=")).isTrue()
    }

    @Test
    fun urlWithPlaceholder_showsNoError() {
        assertThat(isUrlTemplatePlaceholderMissing("https://youtube.com/search?q=%s")).isFalse()
    }
}
