package com.jestk.browser.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugSecureWindowSwitchTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setDebuggable(true)
        DebugSecureWindowSwitch.handle(context, intent("1"))
        context.getSharedPreferences(DebugSecureWindowSwitch.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setDebuggable(on: Boolean) {
        val flags = context.applicationInfo.flags
        context.applicationInfo.flags = if (on) flags or ApplicationInfo.FLAG_DEBUGGABLE else flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    private fun intent(enabled: String) = Intent(Intent.ACTION_VIEW, Uri.parse("prosto-debug://secure?enabled=$enabled"))

    @Test
    fun debuggable_offThenOnTogglesTheFlowAndPersists() {
        setDebuggable(true)
        val flow = DebugSecureWindowSwitch.disabled(context)

        assertThat(DebugSecureWindowSwitch.handle(context, intent("0"))).isTrue()
        assertThat(flow.value).isTrue()
        assertThat(context.getSharedPreferences(DebugSecureWindowSwitch.PREFS, Context.MODE_PRIVATE).getBoolean(DebugSecureWindowSwitch.KEY, false)).isTrue()

        DebugSecureWindowSwitch.handle(context, intent("1"))
        assertThat(flow.value).isFalse()
    }

    @Test
    fun nonDebuggable_consumesTheIntentButChangesNothing() {
        setDebuggable(false)

        assertThat(DebugSecureWindowSwitch.handle(context, intent("0"))).isTrue()

        assertThat(DebugSecureWindowSwitch.disabled(context).value).isFalse()
        assertThat(context.getSharedPreferences(DebugSecureWindowSwitch.PREFS, Context.MODE_PRIVATE).contains(DebugSecureWindowSwitch.KEY)).isFalse()
    }

    @Test
    fun otherIntentsDoNotMatch() {
        assertThat(DebugSecureWindowSwitch.matches(Intent(Intent.ACTION_VIEW, Uri.parse("https://secure.example/")))).isFalse()
        assertThat(DebugSecureWindowSwitch.matches(null)).isFalse()
    }

    @Test
    fun shouldBeSecure_releaseIgnoresTheSwitch() {
        assertThat(shouldBeSecure(incognito = true, debugDisabled = true, debuggable = false)).isTrue()
        assertThat(shouldBeSecure(incognito = true, debugDisabled = false, debuggable = true)).isTrue()
        assertThat(shouldBeSecure(incognito = true, debugDisabled = true, debuggable = true)).isFalse()
        assertThat(shouldBeSecure(incognito = false, debugDisabled = false, debuggable = true)).isFalse()
    }
}
