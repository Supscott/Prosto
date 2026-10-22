package com.jestk.browser.engine

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers the conversion of a drawable into the domain icon.
 *
 * The native graphics mode is required so that the PNG encoder produces real bytes instead of a
 * shadow placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ExternalAppIconTest {

    @Test
    fun toAppIcon_encodesADrawableAsPngBytes() {
        val drawable = ColorDrawable(Color.RED)

        val icon = drawable.toAppIcon()

        assertThat(icon).isNotNull()
        assertThat(icon!!.pngBytes).isNotEmpty()
        // Every PNG starts with the 8 byte signature.
        assertThat(icon.pngBytes.copyOfRange(0, 4))
            .isEqualTo(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    }

    @Test
    fun toAppIcon_isComparableByContent() {
        val drawable = ColorDrawable(Color.BLUE)

        val first = drawable.toAppIcon()
        val second = drawable.toAppIcon()

        assertThat(first).isEqualTo(second)
        assertThat(first?.hashCode()).isEqualTo(second?.hashCode())
    }
}
