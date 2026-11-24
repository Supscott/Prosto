package com.jestk.browser.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.nativeruntime.DefaultNativeRuntimeLoader
import java.util.concurrent.atomic.AtomicReference

/** Guards the `META-INF/services` registration of [SerializedNativeRuntimeLoader] (PRO-112). */
@RunWith(RobolectricTestRunner::class)
class SerializedNativeRuntimeLoaderTest {
    @Test
    fun robolectricUsesTheSerializedLoader() {
        DefaultNativeRuntimeLoader.injectAndLoad()

        val field = DefaultNativeRuntimeLoader::class.java.getDeclaredField("nativeRuntimeLoader")
        field.isAccessible = true
        val loader = (field.get(null) as AtomicReference<*>).get()

        assertThat(loader?.javaClass?.name).isEqualTo(SerializedNativeRuntimeLoader::class.java.name)
    }
}
