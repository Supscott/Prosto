package com.jestk.browser.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DedicatedAppLinksTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ExternalAppResolver.clearBrowserPackagesCache()
        // The Linear app's manifest: verified https links for /auth/redirect and /<team>/issue/...
        val component = ComponentName("app.linear", "app.linear.ui.MainActivity")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
                addDataAuthority("linear.app", null)
                addDataPath("/auth/redirect", PatternMatcher.PATTERN_LITERAL)
                addDataPath("/.*/issue/.*", PatternMatcher.PATTERN_SIMPLE_GLOB)
            },
        )
    }

    @Test
    fun signInHandoffLink_asksToOpenTheApp_andKeepsThePageAsFallback() {
        val url = "https://linear.app/auth/redirect?code=abc"
        val prompt = DedicatedAppLinks.resolve(context, url, "tab_1")

        assertThat(prompt).isNotNull()
        assertThat(prompt!!.appPackage).isEqualTo("app.linear")
        assertThat(prompt.requiresConfirmation).isTrue()
        assertThat(prompt.webFallbackUrl).isEqualTo(url)
        assertThat(prompt.intentUri).contains("package=app.linear")
    }

    @Test
    fun ordinaryLinksOfTheSameSite_keepLoadingInTheBrowser() {
        assertThat(DedicatedAppLinks.resolve(context, "https://linear.app/team/issue/PRO-15", "tab_1")).isNull()
        assertThat(DedicatedAppLinks.resolve(context, "https://linear.app/", "tab_1")).isNull()
        assertThat(DedicatedAppLinks.resolve(context, "https://example.com/auth/redirect", "tab_1")).isNull()
    }

    @Test
    fun offer_deliversThePromptAndStopsTheNavigation() {
        var delivered = 0
        val stop = DedicatedAppLinks.offer(context, "https://linear.app/auth/redirect?code=abc", "tab_1") { _, _, initiated ->
            assertThat(initiated).isTrue()
            delivered++
        }
        assertThat(stop).isTrue()
        assertThat(delivered).isEqualTo(1)
        assertThat(DedicatedAppLinks.offer(context, "https://linear.app/", "tab_1") { _, _, _ -> delivered++ }).isFalse()
        assertThat(delivered).isEqualTo(1)
    }
}
