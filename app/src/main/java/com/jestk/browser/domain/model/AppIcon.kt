package com.jestk.browser.domain.model

/**
 * An application icon carried through the domain layer as encoded PNG bytes.
 *
 * The domain stays free of Android types, so the icon travels as plain data and the presentation
 * layer decodes it back into a bitmap. The byte array is compared by content, which keeps the
 * enclosing data classes value-based.
 */
class AppIcon(val pngBytes: ByteArray) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is AppIcon && pngBytes.contentEquals(other.pngBytes))

    override fun hashCode(): Int = pngBytes.contentHashCode()

    override fun toString(): String = "AppIcon(${pngBytes.size} bytes)"
}
