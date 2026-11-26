package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.engine.siteOriginKey
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPrefsSitePermissionStoreTest {
    private lateinit var context: Context
    private lateinit var store: SharedPrefsSitePermissionStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = SharedPrefsSitePermissionStore(context)
        store.clearAll()
    }

    @Test
    fun nothingAllowedByDefault() {
        assertThat(store.isAllowed("https://example.com", SiteResource.CAMERA)).isFalse()
    }

    @Test
    fun allow_isPerOriginAndPerResource() {
        store.allow("https://example.com", setOf(SiteResource.CAMERA))

        assertThat(store.isAllowed("https://example.com", SiteResource.CAMERA)).isTrue()
        assertThat(store.isAllowed("https://example.com", SiteResource.MICROPHONE)).isFalse()
        assertThat(store.isAllowed("https://other.com", SiteResource.CAMERA)).isFalse()
        assertThat(store.isAllowed("http://example.com", SiteResource.CAMERA)).isFalse()
        assertThat(store.isAllowed("https://example.com:8443", SiteResource.CAMERA)).isFalse()
    }

    @Test
    fun allow_addsToWhatIsAlreadyAllowed() {
        store.allow("https://example.com", setOf(SiteResource.CAMERA))
        store.allow("https://example.com", setOf(SiteResource.MICROPHONE))

        assertThat(store.isAllowed("https://example.com", SiteResource.CAMERA)).isTrue()
        assertThat(store.isAllowed("https://example.com", SiteResource.MICROPHONE)).isTrue()
    }

    @Test
    fun grantsSurviveANewStoreInstance() {
        store.allow("https://example.com", setOf(SiteResource.CAMERA))

        val reopened = SharedPrefsSitePermissionStore(context)

        assertThat(reopened.isAllowed("https://example.com", SiteResource.CAMERA)).isTrue()
    }

    @Test
    fun clear_forgetsOneOrigin_andClearAll_forgetsEverything() {
        store.allow("https://a.com", setOf(SiteResource.CAMERA))
        store.allow("https://b.com", setOf(SiteResource.MICROPHONE))

        store.clear("https://a.com")

        assertThat(store.isAllowed("https://a.com", SiteResource.CAMERA)).isFalse()
        assertThat(store.isAllowed("https://b.com", SiteResource.MICROPHONE)).isTrue()

        store.clearAll()

        assertThat(store.isAllowed("https://b.com", SiteResource.MICROPHONE)).isFalse()
    }

    @Test
    fun emptyInput_isIgnored() {
        store.allow("", setOf(SiteResource.CAMERA))
        store.allow("https://example.com", emptySet())

        assertThat(store.isAllowed("", SiteResource.CAMERA)).isFalse()
        assertThat(store.isAllowed("https://example.com", SiteResource.CAMERA)).isFalse()
    }

    @Test
    fun originKey_dropsDefaultPort_lowercases_andRejectsOpaqueOrigins() {
        assertThat(siteOriginKey("HTTPS", "Example.COM", 443)).isEqualTo("https://example.com")
        assertThat(siteOriginKey("https", "example.com", -1)).isEqualTo("https://example.com")
        assertThat(siteOriginKey("http", "127.0.0.1", 8765)).isEqualTo("http://127.0.0.1:8765")
        assertThat(siteOriginKey("http", "example.com", 80)).isEqualTo("http://example.com")
        assertThat(siteOriginKey("file", "", -1)).isNull()
        assertThat(siteOriginKey(null, "example.com", 443)).isNull()
    }
}
